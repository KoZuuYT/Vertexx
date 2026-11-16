package dev.vertexx.overworld;

import dev.vertexx.PluginMain;
import dev.vertexx.end.EndItem;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Cocoa;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Regiões áridas reformuladas: oásis com palmeiras (deserto), baobás e cupinzeiros (savana), cactos-barril e
 * tumbleweeds ao vento (badlands). Estruturas só em chunks novos e dentro do próprio chunk. Também cuida dos
 * itens dessas regiões: Coco, Larva de Cupim (isca) e Ovo de Avestruz (choca em fardos de feno).
 */
public final class Arid implements Listener {

    private static final class Tumble {
        final ItemDisplay display;
        final Interaction hitbox;
        float roll;

        Tumble(ItemDisplay display, Interaction hitbox) {
            this.display = display;
            this.hitbox = hitbox;
        }
    }

    private final PluginMain plugin;
    private final NamespacedKey moundKey;
    private final NamespacedKey tumbleKey;
    private final Map<Long, int[]> mounds = new HashMap<>();     // chave do chunk -> {x, y, z, altura}*
    private final List<Tumble> tumbles = new ArrayList<>();
    private final Map<String, Long> hatchery = new HashMap<>();
    private final File hatchFile;
    private BukkitTask task;
    private int tick;
    private Vector wind = new Vector(1, 0, 0);

    public Arid(PluginMain plugin) {
        this.plugin = plugin;
        this.moundKey = new NamespacedKey(plugin, "termite_mounds");
        this.tumbleKey = new NamespacedKey(plugin, "tumbleweed");
        this.hatchFile = new File(plugin.getDataFolder(), "hatchery.yml");
    }

    public void start() {
        if (hatchFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(hatchFile);
            for (String key : yaml.getKeys(false)) {
                hatchery.put(key, yaml.getLong(key));
            }
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 2L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (Tumble t : tumbles) {
            t.display.remove();
            t.hitbox.remove();
        }
        tumbles.clear();
        saveHatchery();
    }

    private double cfg(String path, double def) {
        return plugin.getConfig().getDouble("overworld.arid." + path, def);
    }

    // ── Geração ────────────────────────────────────────────────

    private static long key(Chunk c) {
        return ((long) c.getX() << 32) ^ (c.getZ() & 0xffffffffL);
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        int[] saved = chunk.getPersistentDataContainer().get(moundKey, PersistentDataType.INTEGER_ARRAY);
        if (saved != null) {
            mounds.put(key(chunk), saved);
        }
        if (!event.isNewChunk() || !plugin.getConfig().getBoolean("overworld.arid.generation", true)
                || chunk.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        try {
            generate(chunk);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Falha ao gerar região árida em " + chunk.getX() + "," + chunk.getZ() + ": " + e);
        }
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        mounds.remove(key(event.getChunk()));
    }

    private void generate(Chunk chunk) {
        World world = chunk.getWorld();
        int cx = chunk.getX() * 16 + 8;
        int cz = chunk.getZ() * 16 + 8;
        int y = world.getHighestBlockYAt(cx, cz);
        String biome = world.getBiome(cx, y, cz).getKey().getKey();
        Random r = new Random(world.getSeed() ^ (chunk.getX() * 341873128712L) ^ (chunk.getZ() * 132897987541L));
        if (biome.equals("desert") && r.nextDouble() < cfg("oasis-chance", 0.025)) {
            oasis(world, cx, y, cz, r);
        } else if (biome.startsWith("savanna") && !biome.contains("plateau") || biome.equals("savanna_plateau")) {
            double roll = r.nextDouble();
            if (roll < cfg("baobab-chance", 0.05)) {
                baobab(world, cx, y, cz, r);
            } else if (roll < cfg("baobab-chance", 0.05) + cfg("termite-chance", 0.06)) {
                mound(chunk, world, cx + r.nextInt(-4, 5), cz + r.nextInt(-4, 5), r);
            }
        } else if (biome.contains("badlands")) {
            if (r.nextDouble() < cfg("cactus-chance", 0.2)) {
                barrelCactus(world, cx + r.nextInt(-5, 6), cz + r.nextInt(-5, 6));
            }
            if (r.nextDouble() < cfg("tumbleweed-chance", 0.1)) {
                spawnTumble(new Location(world, cx + 0.5, y + 1, cz + 0.5));
            }
        }
    }

    /** Teste/admin: gera uma estrutura em (x, z) do mundo principal. */
    public String demo(String kind, World world, int x, int z) {
        Random r = new Random();
        int y = world.getHighestBlockYAt(x, z);
        switch (kind.toLowerCase()) {
            case "oasis" -> oasis(world, x, y, z, r);
            case "baobab" -> baobab(world, x, y, z, r);
            case "mound" -> mound(world.getChunkAt(x >> 4, z >> 4), world, x, z, r);
            case "cactus" -> barrelCactus(world, x, z);
            case "tumble" -> spawnTumble(new Location(world, x + 0.5, y + 1, z + 0.5));
            default -> {
                return null;
            }
        }
        return kind;
    }

    private void oasis(World world, int cx, int y0, int cz, Random r) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 5.6) {
                    continue;
                }
                for (int dy = 1; dy <= 8; dy++) {
                    world.getBlockAt(cx + dx, y0 + dy, cz + dz).setType(Material.AIR, false);
                }
                if (d <= 3.3) {
                    world.getBlockAt(cx + dx, y0, cz + dz).setType(Material.WATER, false);
                    world.getBlockAt(cx + dx, y0 - 1, cz + dz).setType(Material.SAND, false);
                } else {
                    world.getBlockAt(cx + dx, y0, cz + dz).setType(d <= 4.8 ? Material.GRASS_BLOCK : Material.SAND, false);
                    world.getBlockAt(cx + dx, y0 - 1, cz + dz).setType(d <= 4.8 ? Material.DIRT : Material.SAND, false);
                }
            }
        }
        int palms = 2 + r.nextInt(2);
        for (int i = 0; i < palms; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            palm(world, cx + (int) Math.round(Math.cos(a) * 4.2), y0 + 1, cz + (int) Math.round(Math.sin(a) * 4.2),
                    (int) Math.signum(Math.cos(a)), (int) Math.signum(Math.sin(a)), r);
        }
        for (int i = 0; i < 4; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            Block ground = world.getBlockAt(cx + (int) Math.round(Math.cos(a) * 5), y0, cz + (int) Math.round(Math.sin(a) * 5));
            if (ground.getType() == Material.SAND && ground.getRelative(0, 1, 0).getType().isAir()) {
                ground.getRelative(0, 1, 0).setType(Material.DEAD_BUSH, false);
            }
        }
    }

    private void palm(World world, int x, int y, int z, int lx, int lz, Random r) {
        int h = 5 + r.nextInt(3);
        int px = x;
        int pz = z;
        for (int i = 0; i < h; i++) {
            if (i >= 2 && i % 2 == 0) {
                px += lx;
                pz += lz;
            }
            Block b = world.getBlockAt(px, y + i, pz);
            b.setType(Material.JUNGLE_WOOD, false);
        }
        int ty = y + h - 1;
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace f : faces) {
            for (int len = 1; len <= 3; len++) {
                Block leaf = world.getBlockAt(px + f.getModX() * len, ty + (len == 3 ? 0 : 1), pz + f.getModZ() * len);
                leaf(leaf);
            }
            leaf(world.getBlockAt(px + f.getModX() + f.getModZ(), ty + 1, pz + f.getModZ() + f.getModX()));
        }
        leaf(world.getBlockAt(px, ty + 1, pz));
        leaf(world.getBlockAt(px, ty + 2, pz));
        for (BlockFace f : faces) {
            Block pod = world.getBlockAt(px, ty - 1, pz).getRelative(f);
            if (r.nextInt(2) == 0 && pod.getType().isAir()) {
                Cocoa data = (Cocoa) Material.COCOA.createBlockData();
                data.setFacing(f.getOppositeFace());
                data.setAge(2);
                pod.setBlockData(data, false);
            }
        }
    }

    private void leaf(Block b) {
        if (b.getType().isAir() || b.getType() == Material.DEAD_BUSH) {
            Leaves data = (Leaves) Material.JUNGLE_LEAVES.createBlockData();
            data.setPersistent(true);
            b.setBlockData(data, false);
        }
    }

    private void baobab(World world, int cx, int y0, int cz, Random r) {
        Block ground = world.getBlockAt(cx, y0, cz);
        if (!(ground.getType() == Material.GRASS_BLOCK || ground.getType() == Material.DIRT
                || ground.getType() == Material.COARSE_DIRT)) {
            return;
        }
        int h = 9 + r.nextInt(4);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 1; dy <= h; dy++) {
                    world.getBlockAt(cx + dx, y0 + dy, cz + dz).setType(Material.ACACIA_WOOD, false);
                }
            }
        }
        for (int layer = 0; layer < 3; layer++) {
            int radius = layer == 1 ? 6 : layer == 0 ? 4 : 3;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius + 1 || (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && layer == 0)) {
                        continue;
                    }
                    Block b = world.getBlockAt(cx + dx, y0 + h + layer, cz + dz);
                    if (b.getType().isAir()) {
                        Leaves data = (Leaves) Material.ACACIA_LEAVES.createBlockData();
                        data.setPersistent(true);
                        b.setBlockData(data, false);
                    }
                }
            }
        }
    }

    private void mound(Chunk chunk, World world, int x, int z, Random r) {
        int y0 = world.getHighestBlockYAt(x, z);
        Block ground = world.getBlockAt(x, y0, z);
        if (!(ground.getType() == Material.GRASS_BLOCK || ground.getType() == Material.DIRT
                || ground.getType() == Material.COARSE_DIRT || ground.getType() == Material.SAND)) {
            return;
        }
        int h = 4 + r.nextInt(3);
        for (int dy = 1; dy <= h; dy++) {
            int radius = dy == 1 ? 1 : dy <= 3 ? 1 : 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius == 1 && dy >= 2 && Math.abs(dx) + Math.abs(dz) == 2) {
                        continue;
                    }
                    world.getBlockAt(x + dx, y0 + dy, z + dz).setType(
                            r.nextInt(3) == 0 || dy >= h - 1 ? Material.TERRACOTTA : Material.PACKED_MUD, false);
                }
            }
        }
        int[] old = chunk.getPersistentDataContainer().get(moundKey, PersistentDataType.INTEGER_ARRAY);
        int[] next = old == null ? new int[4] : java.util.Arrays.copyOf(old, old.length + 4);
        int i = next.length - 4;
        next[i] = x;
        next[i + 1] = y0;
        next[i + 2] = z;
        next[i + 3] = h;
        chunk.getPersistentDataContainer().set(moundKey, PersistentDataType.INTEGER_ARRAY, next);
        mounds.put(key(chunk), next);
    }

    private void barrelCactus(World world, int x, int z) {
        int y0 = world.getHighestBlockYAt(x, z);
        Block ground = world.getBlockAt(x, y0, z);
        if (ground.getType() != Material.SAND && ground.getType() != Material.RED_SAND) {
            return;
        }
        for (int dy = 1; dy <= 2; dy++) {
            world.getBlockAt(x, y0 + dy, z).setType(Material.CACTUS, false);
        }
        world.getBlockAt(x, y0 + 3, z).setType(Material.CACTUS_FLOWER, false);
    }

    // ── Cupinzeiros, cocos, iscas ──────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        Material m = b.getType();
        if (m == Material.COCOA && b.getBiome().getKey().getKey().equals("desert")
                && ((Cocoa) b.getBlockData()).getAge() >= 2) {
            Cocoa data = (Cocoa) b.getBlockData();
            if (b.getRelative(data.getFacing()).getType() == Material.JUNGLE_WOOD) {
                event.setDropItems(false);
                ItemStack coconut = EndItem.COCONUT.create(plugin);
                coconut.setAmount(1 + new Random().nextInt(2));
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), coconut);
            }
            return;
        }
        if (m != Material.PACKED_MUD && m != Material.TERRACOTTA) {
            return;
        }
        int[] list = mounds.get(key(b.getChunk()));
        if (list == null) {
            return;
        }
        for (int i = 0; i + 3 < list.length; i += 4) {
            if (Math.abs(b.getX() - list[i]) <= 1 && Math.abs(b.getZ() - list[i + 2]) <= 1
                    && b.getY() > list[i + 1] && b.getY() <= list[i + 1] + list[i + 3]) {
                if (new Random().nextInt(100) < 55) {
                    ItemStack larva = EndItem.TERMITE_LARVA.create(plugin);
                    larva.setAmount(1 + new Random().nextInt(2));
                    b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), larva);
                }
                return;
            }
        }
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.FISHING) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack off = p.getInventory().getItemInOffHand();
        if (EndItem.of(off) != EndItem.TERMITE_LARVA) {
            return;
        }
        off.setAmount(off.getAmount() - 1);
        FishHook hook = event.getHook();
        hook.setMinWaitTime(30);
        hook.setMaxWaitTime(100);
    }

    // ── Choco de ovos de avestruz ──────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onUseEgg(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.HAY_BLOCK
                || event.getBlockFace() != BlockFace.UP || EndItem.of(event.getItem()) != EndItem.OSTRICH_EGG) {
            return;
        }
        Block spot = event.getClickedBlock().getRelative(BlockFace.UP);
        if (!spot.getType().isAir()) {
            return;
        }
        event.setCancelled(true);
        event.getItem().setAmount(event.getItem().getAmount() - 1);
        spot.setType(Material.TURTLE_EGG, false);
        long when = System.currentTimeMillis() + (long) (cfg("hatch-seconds", 180) * 1000);
        hatchery.put(id(spot), when);
        saveHatchery();
        spot.getWorld().playSound(spot.getLocation(), org.bukkit.Sound.ENTITY_TURTLE_LAY_EGG, 1f, 0.7f);
        event.getPlayer().sendActionBar(dev.vertexx.core.Msg.mm("<yellow>O ovo vai chocar sobre o feno em alguns minutos."));
    }

    private static String id(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private void saveHatchery() {
        YamlConfiguration yaml = new YamlConfiguration();
        hatchery.forEach(yaml::set);
        try {
            yaml.save(hatchFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Não foi possível salvar hatchery.yml: " + e.getMessage());
        }
    }

    private void checkHatchery() {
        long now = System.currentTimeMillis();
        boolean dirty = false;
        for (Iterator<Map.Entry<String, Long>> it = hatchery.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (entry.getValue() > now) {
                continue;
            }
            String[] p = entry.getKey().split(";");
            World world = Bukkit.getWorld(p[0]);
            if (world == null || !world.isChunkLoaded(Integer.parseInt(p[1]) >> 4, Integer.parseInt(p[3]) >> 4)) {
                continue;
            }
            Block b = world.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
            it.remove();
            dirty = true;
            if (b.getType() != Material.TURTLE_EGG) {
                continue;
            }
            b.setType(Material.AIR, false);
            Location at = b.getLocation().add(0.5, 0, 0.5);
            if (b.getRelative(BlockFace.DOWN).getType() != Material.HAY_BLOCK) {
                world.dropItemNaturally(at, EndItem.OSTRICH_EGG.create(plugin));
                continue;
            }
            org.bukkit.entity.Mob foal = plugin.fauna().spawn(at, dev.vertexx.fauna.FaunaKind.OSTRICH);
            if (foal instanceof org.bukkit.entity.Ageable ageable) {
                if (foal instanceof org.bukkit.entity.Breedable breedable) {
                    breedable.setAgeLock(false);
                }
                ageable.setBaby();
            }
            world.playSound(at, org.bukkit.Sound.ENTITY_TURTLE_EGG_HATCH, 1f, 0.8f);
            world.spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 0.5, 0), 12, 0.3, 0.3, 0.3);
        }
        if (dirty) {
            saveHatchery();
        }
    }

    // ── Tumbleweeds ────────────────────────────────────────────

    private void spawnTumble(Location at) {
        if (tumbles.size() >= (int) cfg("tumbleweed-cap", 40)) {
            return;
        }
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.DEAD_BUSH));
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(2);
            d.setInterpolationDuration(2);
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(1.3f, 1.3f, 1.3f), new AxisAngle4f(0, 0, 0, 1)));
        });
        Interaction hitbox = at.getWorld().spawn(at, Interaction.class, i -> {
            i.setInteractionWidth(0.9f);
            i.setInteractionHeight(0.9f);
            i.setResponsive(true);
            i.setPersistent(false);
            i.getPersistentDataContainer().set(tumbleKey, PersistentDataType.BYTE, (byte) 1);
        });
        tumbles.add(new Tumble(display, hitbox));
    }

    private void breakTumble(Entity hit, Player by) {
        for (Iterator<Tumble> it = tumbles.iterator(); it.hasNext(); ) {
            Tumble t = it.next();
            if (t.hitbox.equals(hit)) {
                Location at = t.display.getLocation();
                it.remove();
                t.display.remove();
                t.hitbox.remove();
                World w = at.getWorld();
                w.dropItemNaturally(at, new ItemStack(Material.STICK, 1 + new Random().nextInt(2)));
                if (new Random().nextInt(100) < 60) {
                    w.dropItemNaturally(at, new ItemStack(Material.WHEAT_SEEDS, 1 + new Random().nextInt(2)));
                }
                w.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.5, 0), 10, 0.3, 0.3, 0.3, 0.05,
                        Material.DEAD_BUSH.createBlockData());
                w.playSound(at, org.bukkit.Sound.BLOCK_GRASS_BREAK, 1f, 1.2f);
                return;
            }
        }
    }

    @EventHandler
    public void onAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event) {
        if (event.getAttacked() instanceof Interaction i && i.getPersistentDataContainer().has(tumbleKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            breakTumble(i, event.getPlayer());
        }
    }

    @EventHandler
    public void onInteractTumble(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Interaction i
                && i.getPersistentDataContainer().has(tumbleKey, PersistentDataType.BYTE)) {
            breakTumble(i, event.getPlayer());
        }
    }

    // ── Relógio ────────────────────────────────────────────────

    private void tick() {
        tick += 2;
        if (tick % 600 == 0) {
            double a = Math.random() * Math.PI * 2;
            wind = new Vector(Math.cos(a), 0, Math.sin(a));
        }
        if (tick % 100 == 0) {
            checkHatchery();
            refill();
        }
        // tumbleweeds rolam ao vento
        for (Iterator<Tumble> it = tumbles.iterator(); it.hasNext(); ) {
            Tumble t = it.next();
            if (!t.display.isValid() || !t.hitbox.isValid()) {
                t.display.remove();
                t.hitbox.remove();
                it.remove();
                continue;
            }
            Location at = t.display.getLocation();
            if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)
                    || at.getWorld().getNearbyPlayers(at, 72).isEmpty()) {
                continue;
            }
            Location next = at.clone().add(wind.getX() * 0.09, 0, wind.getZ() * 0.09);
            int top = next.getWorld().getHighestBlockYAt(next);
            double ground = top + 1.0;
            if (Math.abs(ground - at.getY()) > 2.5 || next.getBlock().isLiquid()
                    || next.getWorld().getHighestBlockAt(next).isLiquid()) {
                wind.multiply(-1);
                continue;
            }
            next.setY(at.getY() + Math.max(-0.3, Math.min(0.3, ground - at.getY())));
            t.display.teleport(next);
            t.hitbox.teleport(next);
            t.roll += 0.18f;
            t.display.setInterpolationDelay(0);
            t.display.setInterpolationDuration(2);
            t.display.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0),
                    new AxisAngle4f(t.roll, (float) -wind.getZ(), 0, (float) wind.getX()),
                    new Vector3f(1.3f, 1.3f, 1.3f), new AxisAngle4f(0, 0, 0, 1)));
        }
        // insetos nos cupinzeiros, só para quem está perto
        if (tick % 20 == 0) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                int pcx = p.getLocation().getBlockX() >> 4;
                int pcz = p.getLocation().getBlockZ() >> 4;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long k = ((long) (pcx + dx) << 32) ^ ((pcz + dz) & 0xffffffffL);
                        int[] list = mounds.get(k);
                        if (list == null) {
                            continue;
                        }
                        for (int i = 0; i + 3 < list.length; i += 4) {
                            Location top = new Location(p.getWorld(), list[i] + 0.5, list[i + 1] + list[i + 3] + 0.8, list[i + 2] + 0.5);
                            if (top.distanceSquared(p.getLocation()) < 16 * 16) {
                                p.spawnParticle(Particle.DUST, top, 4, 0.9, 1.2, 0.9, 0,
                                        new Particle.DustOptions(Color.fromRGB(40, 28, 20), 0.6f));
                            }
                        }
                    }
                }
            }
        }
    }

    /** Mantém alguns tumbleweeds perto de quem está nas badlands. */
    private void refill() {
        if (!plugin.getConfig().getBoolean("overworld.arid.generation", true)) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getLocation().getBlock().getBiome().getKey().getKey().contains("badlands")) {
                continue;
            }
            long near = tumbles.stream().filter(t -> t.display.getWorld() == p.getWorld()
                    && t.display.getLocation().distanceSquared(p.getLocation()) < 48 * 48).count();
            if (near < 4 && new Random().nextInt(3) == 0) {
                double a = Math.random() * Math.PI * 2;
                int x = p.getLocation().getBlockX() + (int) (Math.cos(a) * 32);
                int z = p.getLocation().getBlockZ() + (int) (Math.sin(a) * 32);
                if (p.getWorld().isChunkLoaded(x >> 4, z >> 4)) {
                    spawnTumble(new Location(p.getWorld(), x + 0.5, p.getWorld().getHighestBlockYAt(x, z) + 1, z + 0.5));
                }
            }
        }
    }
}
