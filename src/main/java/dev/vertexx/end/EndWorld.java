package dev.vertexx.end;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Biomas das ilhas externas do End (além de 1000 blocos da origem), sem gerador próprio: ao nascer um chunk novo,
 * um ruído determinístico (da semente do mundo) decide a zona de cada coluna e a superfície de End Stone é trocada.
 * <ul>
 *   <li><b>Floresta de Dossel do Coro</b>: nilium (musgo do Vazio), árvores altas com copa de verruga e lanternas
 *       penduradas em correntes, fungos que dão Queda Lenta;</li>
 *   <li><b>Abismos de Enderita</b>: ardósia escura com cristais de ametista, fendas, veios de minério de Enderita
 *       (detrito antigo registrado no chunk) e geodos de obsidiana; levitação nas bordas.</li>
 * </ul>
 */
public final class EndWorld implements Listener {

    public enum Zone { NONE, CANOPY, WASTES }

    private static final int MIN_RADIUS = 1000;

    private final PluginMain plugin;
    private final NamespacedKey oreKey;
    private final Material chain;
    private final Map<UUID, Integer> still = new HashMap<>();
    private final Map<UUID, Location> last = new HashMap<>();

    public EndWorld(PluginMain plugin) {
        this.plugin = plugin;
        this.oreKey = new NamespacedKey(plugin, "enderite_ore");
        this.chain = Material.matchMaterial("OXIDIZED_COPPER_CHAIN") != null ? Material.OXIDIZED_COPPER_CHAIN : Material.IRON_CHAIN;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::hazards, 40L, 10L);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("end.biomes.enabled", true);
    }

    // ── Ruído ──────────────────────────────────────────────────

    private static double hash(long seed, int x, int z) {
        long h = seed * 341873128712L + x * 132897987541L + z * 42317861L;
        h = (h ^ (h >>> 31)) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return ((h >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
    }

    private static double value(long seed, double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(seed, x0, z0);
        double b = hash(seed, x0 + 1, z0);
        double c = hash(seed, x0, z0 + 1);
        double d = hash(seed, x0 + 1, z0 + 1);
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }

    /** Zona do ponto: determinística (não precisa do chunk carregado). */
    public static Zone zoneAt(World world, int x, int z) {
        double r = Math.sqrt((double) x * x + (double) z * z);
        if (r < MIN_RADIUS) {
            return Zone.NONE;
        }
        long seed = world.getSeed();
        double n = value(seed, x / 190.0, z / 190.0) * 0.7 + value(seed + 7, x / 70.0, z / 70.0) * 0.3;
        if (n >= 0.53 || (r > 2600 && n >= 0.40)) {
            return Zone.WASTES;
        }
        return n < 0.47 ? Zone.CANOPY : Zone.NONE;
    }

    // ── Geração ────────────────────────────────────────────────

    @EventHandler
    public void onChunk(ChunkLoadEvent event) {
        World world = event.getWorld();
        if (!event.isNewChunk() || world.getEnvironment() != World.Environment.THE_END || !enabled()) {
            return;
        }
        Chunk chunk = event.getChunk();
        double cx = chunk.getX() * 16 + 8;
        double cz = chunk.getZ() * 16 + 8;
        if (Math.sqrt(cx * cx + cz * cz) < MIN_RADIUS - 16) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded()) {
                try {
                    process(chunk);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Falha ao gerar os biomas do End em " + chunk.getX() + "," + chunk.getZ() + ": " + e);
                }
            }
        });
    }

    private static boolean soil(Material m) {
        return m == Material.END_STONE || m == Material.BLACKSTONE || m == Material.COBBLED_DEEPSLATE || m == Material.WARPED_NYLIUM;
    }

    /** Aplica os biomas ao chunk; devolve quantos blocos mudaram (usado pelo comando de teste). */
    public int process(Chunk chunk) {
        World world = chunk.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int baseX = chunk.getX() * 16;
        int baseZ = chunk.getZ() * 16;
        int changed = 0;
        int[][] tops = new int[16][16];
        Zone[][] zones = new Zone[16][16];
        List<Integer> ores = new ArrayList<>();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;
                Zone zone = zoneAt(world, wx, wz);
                zones[lx][lz] = zone;
                tops[lx][lz] = -1;
                if (zone == Zone.NONE) {
                    continue;
                }
                int y = world.getHighestBlockYAt(wx, wz);
                if (y <= world.getMinHeight()) {
                    continue;
                }
                while (y > world.getMinHeight() && !chunk.getBlock(lx, y, lz).getType().isSolid()) {
                    y--;
                }
                if (chunk.getBlock(lx, y, lz).getType() != Material.END_STONE) {
                    continue;
                }
                tops[lx][lz] = y;
                if (zone == Zone.CANOPY) {
                    changed += canopyColumn(chunk, lx, y, lz, rnd);
                } else {
                    changed += wastesColumn(chunk, lx, y, lz, rnd, ores);
                }
            }
        }
        // fendas e paredes de cristal
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                if (zones[lx][lz] == Zone.WASTES && tops[lx][lz] > 0) {
                    changed += fissure(chunk, lx, tops[lx][lz], lz, baseX + lx, baseZ + lz, rnd, ores);
                }
            }
        }
        // árvores e geodos
        Zone center = zones[8][8];
        if (center == Zone.CANOPY) {
            int trees = rnd.nextInt(3);
            for (int i = 0; i < trees; i++) {
                int lx = 5 + rnd.nextInt(6);
                int lz = 5 + rnd.nextInt(6);
                if (zones[lx][lz] == Zone.CANOPY && tops[lx][lz] > 0
                        && chunk.getBlock(lx, tops[lx][lz], lz).getType() == Material.WARPED_NYLIUM) {
                    changed += tree(chunk, lx, tops[lx][lz] + 1, lz, rnd);
                }
            }
        } else if (center == Zone.WASTES && rnd.nextDouble() < 0.25) {
            int lx = 5 + rnd.nextInt(6);
            int lz = 5 + rnd.nextInt(6);
            if (tops[lx][lz] > 12) {
                changed += geode(chunk, lx, tops[lx][lz] - 8, lz, ores);
            }
        }
        if (!ores.isEmpty()) {
            int[] packed = ores.stream().mapToInt(Integer::intValue).toArray();
            int[] old = chunk.getPersistentDataContainer().get(oreKey, PersistentDataType.INTEGER_ARRAY);
            if (old != null) {
                int[] merged = new int[old.length + packed.length];
                System.arraycopy(old, 0, merged, 0, old.length);
                System.arraycopy(packed, 0, merged, old.length, packed.length);
                packed = merged;
            }
            chunk.getPersistentDataContainer().set(oreKey, PersistentDataType.INTEGER_ARRAY, packed);
        }
        return changed;
    }

    private static int pack(int lx, int y, int lz) {
        return (lx << 24) | (lz << 16) | (y & 0xFFFF);
    }

    private int canopyColumn(Chunk chunk, int lx, int y, int lz, ThreadLocalRandom rnd) {
        int n = 0;
        chunk.getBlock(lx, y, lz).setType(Material.WARPED_NYLIUM, false);
        n++;
        Block under = chunk.getBlock(lx, y - 1, lz);
        if (under.getType() == Material.END_STONE) {
            under.setType(Material.PURPUR_BLOCK, false);
            n++;
        }
        Block above = chunk.getBlock(lx, y + 1, lz);
        if (above.getType().isAir()) {
            double r = rnd.nextDouble();
            Material plant = r < 0.14 ? Material.WARPED_ROOTS : r < 0.19 ? Material.WARPED_FUNGUS
                    : r < 0.23 ? Material.NETHER_SPROUTS : null;
            if (plant != null) {
                above.setType(plant, false);
                n++;
            }
        }
        return n;
    }

    private int wastesColumn(Chunk chunk, int lx, int y, int lz, ThreadLocalRandom rnd, List<Integer> ores) {
        int n = 0;
        for (int d = 0; d < 6; d++) {
            Block b = chunk.getBlock(lx, y - d, lz);
            if (b.getType() != Material.END_STONE) {
                break;
            }
            b.setType(rnd.nextDouble() < (d == 0 ? 0.12 : 0.3) ? Material.COBBLED_DEEPSLATE : Material.BLACKSTONE, false);
            n++;
        }
        Block above = chunk.getBlock(lx, y + 1, lz);
        if (above.getType().isAir()) {
            double r = rnd.nextDouble();
            Material crystal = r < 0.03 ? Material.AMETHYST_CLUSTER : r < 0.05 ? Material.LARGE_AMETHYST_BUD
                    : r < 0.07 ? Material.MEDIUM_AMETHYST_BUD : null;
            if (crystal != null) {
                above.setType(crystal, false);
                n++;
            }
        }
        // face de baixo da ilha: cristais pendurados e veios de Enderita expostos
        int low = y;
        while (low > chunk.getWorld().getMinHeight() + 1 && chunk.getBlock(lx, low - 1, lz).getType().isSolid()) {
            low--;
        }
        if (low > chunk.getWorld().getMinHeight() + 1) {
            Block bottom = chunk.getBlock(lx, low, lz);
            Block below = chunk.getBlock(lx, low - 1, lz);
            double r = rnd.nextDouble();
            if (r < 0.03 && below.getType().isAir()) {
                below.setType(Material.AMETHYST_CLUSTER, false);
                if (below.getBlockData() instanceof Directional dir) {
                    dir.setFacing(BlockFace.DOWN);
                    below.setBlockData(dir, false);
                }
                n++;
            } else if (r < 0.045 && soil(bottom.getType())) {
                bottom.setType(Material.ANCIENT_DEBRIS, false);
                ores.add(pack(lx, low, lz));
                n++;
                if (rnd.nextBoolean() && soil(chunk.getBlock(lx, low + 1, lz).getType())) {
                    chunk.getBlock(lx, low + 1, lz).setType(Material.ANCIENT_DEBRIS, false);
                    ores.add(pack(lx, low + 1, lz));
                    n++;
                }
            }
        }
        return n;
    }

    /** Fendas profundas e contínuas entre chunks (função do mundo, não do chunk), com cristais nas paredes. */
    private int fissure(Chunk chunk, int lx, int top, int lz, int wx, int wz, ThreadLocalRandom rnd, List<Integer> ores) {
        long seed = chunk.getWorld().getSeed();
        int band = Math.floorDiv(wz, 90);
        if (hash(seed + 31, band, 0) > 0.45) {
            return 0;
        }
        double zc = band * 90 + 45 + 14 * Math.sin(wx / 23.0 + band * 1.7);
        double dist = Math.abs(wz - zc);
        int n = 0;
        if (dist <= 1.6) {
            for (int y = top; y > chunk.getWorld().getMinHeight() + 2; y--) {
                Block b = chunk.getBlock(lx, y, lz);
                if (soil(b.getType()) || b.getType() == Material.OBSIDIAN) {
                    b.setType(Material.AIR, false);
                    n++;
                }
            }
        } else if (dist <= 2.7) {
            BlockFace toward = wz < zc ? BlockFace.SOUTH : BlockFace.NORTH;
            for (int y = top; y > Math.max(chunk.getWorld().getMinHeight() + 2, top - 60); y--) {
                Block b = chunk.getBlock(lx, y, lz);
                if (!soil(b.getType())) {
                    continue;
                }
                Block side = b.getRelative(toward);
                if (!side.getType().isAir()) {
                    continue;
                }
                double r = rnd.nextDouble();
                if (r < 0.06) {
                    side.setType(Material.AMETHYST_CLUSTER, false);
                    if (side.getBlockData() instanceof Directional dir) {
                        dir.setFacing(toward);
                        side.setBlockData(dir, false);
                    }
                    n++;
                } else if (r < 0.075 && lx >= 0 && lz >= 1 && lz <= 14) {
                    b.setType(Material.ANCIENT_DEBRIS, false);
                    ores.add(pack(lx, y, lz));
                    n++;
                }
            }
        }
        return n;
    }

    private int geode(Chunk chunk, int cx, int cy, int cz, List<Integer> ores) {
        int n = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > 2.7) {
                        continue;
                    }
                    int x = cx + dx;
                    int z = cz + dz;
                    if (x < 0 || x > 15 || z < 0 || z > 15) {
                        continue;
                    }
                    Block b = chunk.getBlock(x, cy + dy, z);
                    if (!soil(b.getType())) {
                        continue;
                    }
                    if (d <= 1.5) {
                        b.setType(Material.ANCIENT_DEBRIS, false);
                        ores.add(pack(x, cy + dy, z));
                    } else {
                        b.setType(Material.OBSIDIAN, false);
                    }
                    n++;
                }
            }
        }
        return n;
    }

    // ── Árvores ────────────────────────────────────────────────

    private void put(Chunk chunk, int x, int y, int z, Material type, int[] count) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < chunk.getWorld().getMinHeight() || y >= chunk.getWorld().getMaxHeight()) {
            return;
        }
        Block b = chunk.getBlock(x, y, z);
        if (b.getType().isAir() || b.getType() == Material.WARPED_ROOTS || b.getType() == Material.WARPED_FUNGUS
                || b.getType() == Material.NETHER_SPROUTS) {
            b.setType(type, false);
            count[0]++;
        }
    }

    private void blob(Chunk chunk, double cx, double cy, double cz, double radius, int[] count, List<int[]> undersides) {
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.sqrt(dx * dx + (dy * 1.5) * (dy * 1.5) + dz * dz);
                    if (d <= radius) {
                        int x = (int) Math.floor(cx) + dx;
                        int y = (int) Math.floor(cy) + dy;
                        int z = (int) Math.floor(cz) + dz;
                        put(chunk, x, y, z, Material.WARPED_WART_BLOCK, count);
                        if (dy <= -1 && undersides != null) {
                            undersides.add(new int[]{x, y, z});
                        }
                    }
                }
            }
        }
    }

    private int tree(Chunk chunk, int lx, int y0, int lz, ThreadLocalRandom rnd) {
        int[] count = new int[1];
        int h = 10 + rnd.nextInt(7);
        for (int y = 0; y < h; y++) {
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    put(chunk, lx + dx, y0 + y, lz + dz, Material.OAK_WOOD, count);
                }
            }
        }
        List<int[]> under = new ArrayList<>();
        blob(chunk, lx + 1, y0 + h, lz + 1, 3.0, count, under);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int i = 0; i < 3; i++) {
            int[] d = dirs[rnd.nextInt(4)];
            int by = y0 + h - 7 + i * 2;
            int sx = d[0] > 0 ? lx + 2 : d[0] < 0 ? lx - 1 : lx + rnd.nextInt(2);
            int sz = d[1] > 0 ? lz + 2 : d[1] < 0 ? lz - 1 : lz + rnd.nextInt(2);
            for (int k = 0; k < 2; k++) {
                put(chunk, sx + d[0] * k, by + k, sz + d[1] * k, Material.OAK_WOOD, count);
            }
            blob(chunk, sx + d[0] * 2, by + 2, sz + d[1] * 2, 2.0, count, under);
        }
        for (int[] u : under) {
            if (rnd.nextDouble() > 0.06) {
                continue;
            }
            Block b = chunk.getBlock(u[0], u[1], u[2]);
            if (b.getType() != Material.WARPED_WART_BLOCK) {
                continue;
            }
            int len = 2 + rnd.nextInt(4);
            int y = u[1] - 1;
            boolean ok = true;
            for (int k = 0; k <= len; k++) {
                if (!chunk.getBlock(u[0], y - k, u[2]).getType().isAir()) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            for (int k = 0; k < len; k++) {
                chunk.getBlock(u[0], y - k, u[2]).setType(chain, false);
            }
            Block lamp = chunk.getBlock(u[0], y - len, u[2]);
            lamp.setType(Material.LANTERN, false);
            if (lamp.getBlockData() instanceof org.bukkit.block.data.type.Lantern data) {
                data.setHanging(true);
                lamp.setBlockData(data, false);
            }
            count[0] += len + 1;
        }
        return count[0];
    }

    // ── Minério de Enderita ────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.ANCIENT_DEBRIS || block.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        Chunk chunk = block.getChunk();
        int[] ores = chunk.getPersistentDataContainer().get(oreKey, PersistentDataType.INTEGER_ARRAY);
        if (ores == null) {
            return;
        }
        int key = pack(block.getX() & 15, block.getY(), block.getZ() & 15);
        int index = -1;
        for (int i = 0; i < ores.length; i++) {
            if (ores[i] == key) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        int[] rest = new int[ores.length - 1];
        System.arraycopy(ores, 0, rest, 0, index);
        System.arraycopy(ores, index + 1, rest, index, ores.length - index - 1);
        chunk.getPersistentDataContainer().set(oreKey, PersistentDataType.INTEGER_ARRAY, rest);
        event.setDropItems(false);
        event.setExpToDrop(3);
        Material tool = event.getPlayer().getInventory().getItemInMainHand().getType();
        if (tool == Material.DIAMOND_PICKAXE || tool == Material.NETHERITE_PICKAXE) {
            int fortune = event.getPlayer().getInventory().getItemInMainHand()
                    .getEnchantmentLevel(org.bukkit.enchantments.Enchantment.FORTUNE);
            ItemStack drop = EndItem.ENDERITE_FRAGMENT.create(plugin);
            drop.setAmount(1 + ThreadLocalRandom.current().nextInt(2 + fortune));
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
        }
    }

    // ── Perigos e fungos ───────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ()) {
            return;
        }
        if (to.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        Material feet = to.getBlock().getType();
        if (feet == Material.WARPED_FUNGUS || to.clone().subtract(0, 1, 0).getBlock().getType() == Material.WARPED_FUNGUS) {
            Player player = event.getPlayer();
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, true, true, true));
            player.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, to.clone().add(0, 0.4, 0), 8, 0.3, 0.2, 0.3, 0);
        }
    }

    private boolean onEdge(Location l) {
        Block feet = l.getBlock();
        if (!feet.getRelative(0, -1, 0).getType().isSolid()) {
            return false;
        }
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block side = feet.getRelative(face);
            if (side.getType().isAir() && side.getRelative(0, -1, 0).getType().isAir() && side.getRelative(0, -2, 0).getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private void hazards() {
        if (!enabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location l = player.getLocation();
            UUID id = player.getUniqueId();
            if (l.getWorld().getEnvironment() != World.Environment.THE_END || player.getGameMode() == GameMode.SPECTATOR
                    || player.getGameMode() == GameMode.CREATIVE
                    || zoneAt(l.getWorld(), l.getBlockX(), l.getBlockZ()) != Zone.WASTES) {
                still.remove(id);
                last.remove(id);
                continue;
            }
            Location before = last.put(id, l);
            boolean steady = before != null && before.getWorld() == l.getWorld() && before.distanceSquared(l) < 0.04;
            int time = steady ? still.merge(id, 10, Integer::sum) : 0;
            still.put(id, time);
            if (!onEdge(l)) {
                continue;
            }
            if (time >= 40) {
                l.getWorld().spawnParticle(Particle.REVERSE_PORTAL, l.clone().add(0, 0.2, 0), 12, 0.5, 0.1, 0.5, 0.05);
            }
            if (time >= 60 && !plugin.endGear().temperedBoots(player)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 1, true, true, true));
                Msg.actionBar(player, "<dark_purple>O vazio puxa você para cima…");
                still.put(id, 0);
            }
        }
    }

    /** Descrição para o comando de teste. */
    public String describe(World world, int x, int z) {
        return zoneAt(world, x, z).name();
    }
}
