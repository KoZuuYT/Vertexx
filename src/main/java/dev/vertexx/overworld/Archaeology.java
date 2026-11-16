package dev.vertexx.overworld;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import dev.vertexx.end.EndItem;
import dev.vertexx.fauna.FaunaKind;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrushableBlock;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Arqueologia profunda: bolsões de cascalho suspeito nas cavernas de deepslate (Y &lt; 0) que, escovados, rendem
 * Fragmentos de Âmbar e Fósseis Calcificados; e a Incubadora Geotérmica (caldeirão com lava cercado por 4 blocos de
 * magma e um pedestal de cobre ou ametista por cima), que em 3 minutos ressuscita um Anquilossauro de Ardósia ou um
 * Pterodáctilo de Caverna com 4 fragmentos + 1 fóssil.
 */
public final class Archaeology implements Listener {

    private static final Set<Material> PEDESTALS = EnumSet.of(Material.COPPER_BLOCK, Material.EXPOSED_COPPER,
            Material.WEATHERED_COPPER, Material.OXIDIZED_COPPER, Material.WAXED_COPPER_BLOCK,
            Material.WAXED_EXPOSED_COPPER, Material.WAXED_WEATHERED_COPPER, Material.WAXED_OXIDIZED_COPPER,
            Material.AMETHYST_BLOCK);
    private static final int AMBER_NEEDED = 4;

    private static final class Incubator {
        int amber;
        int fossil;
        long deadline;
    }

    private final PluginMain plugin;
    private final File file;
    private final Map<String, Incubator> incubators = new HashMap<>();
    private BukkitTask task;
    private int tick;

    public Archaeology(PluginMain plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "incubators.yml");
    }

    public void start() {
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String key : yaml.getKeys(false)) {
                Incubator i = new Incubator();
                i.amber = yaml.getInt(key + ".amber");
                i.fossil = yaml.getInt(key + ".fossil");
                i.deadline = yaml.getLong(key + ".deadline");
                incubators.put(key, i);
            }
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        save();
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        incubators.forEach((key, i) -> {
            yaml.set(key + ".amber", i.amber);
            yaml.set(key + ".fossil", i.fossil);
            yaml.set(key + ".deadline", i.deadline);
        });
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Não foi possível salvar incubators.yml: " + e.getMessage());
        }
    }

    private double cfg(String path, double def) {
        return plugin.getConfig().getDouble("overworld.archaeology." + path, def);
    }

    // ── Bolsões de cascalho suspeito ───────────────────────────

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk() || !plugin.getConfig().getBoolean("overworld.archaeology.generation", true)
                || event.getChunk().getWorld().getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        try {
            pockets(event.getChunk());
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Falha ao gerar bolsões fósseis: " + e);
        }
    }

    private void pockets(Chunk chunk) {
        World world = chunk.getWorld();
        Random r = new Random(world.getSeed() ^ (chunk.getX() * 668265263L) ^ (chunk.getZ() * 374761393L));
        if (r.nextDouble() >= cfg("pocket-chance", 0.3)) {
            return;
        }
        for (int attempt = 0; attempt < 40; attempt++) {
            int x = chunk.getX() * 16 + 3 + r.nextInt(10);
            int z = chunk.getZ() * 16 + 3 + r.nextInt(10);
            int y = -50 + r.nextInt(44);
            Block b = world.getBlockAt(x, y, z);
            if (b.getType() != Material.DEEPSLATE || !exposed(b)) {
                continue;
            }
            int placed = 0;
            for (int i = 0; i < 24 && placed < 5; i++) {
                Block c = b.getRelative(r.nextInt(-2, 3), r.nextInt(-2, 3), r.nextInt(-2, 3));
                if (c.getType() == Material.DEEPSLATE || c.getType() == Material.STONE || c.getType() == Material.TUFF) {
                    fill(c, r);
                    placed++;
                }
            }
            return;
        }
    }

    private boolean exposed(Block b) {
        for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (b.getRelative(f).getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private void fill(Block b, Random r) {
        ItemStack loot;
        double roll = r.nextDouble();
        if (roll < 0.52) {
            loot = EndItem.AMBER_FRAGMENT.create(plugin);
            loot.setAmount(1 + r.nextInt(2));
        } else if (roll < 0.74) {
            loot = EndItem.CALCIFIED_FOSSIL.create(plugin);
        } else {
            loot = new ItemStack(new Material[]{Material.BONE, Material.COAL, Material.IRON_NUGGET, Material.AMETHYST_SHARD}[r.nextInt(4)]);
        }
        b.setType(Material.SUSPICIOUS_GRAVEL, false);
        BlockState state = b.getState();
        if (state instanceof BrushableBlock brushable) {
            brushable.setItem(loot);
            brushable.update(true, false);
        }
    }

    /** Teste/admin: cria um bolsão em volta de (x, y, z). */
    public int demoPocket(World world, int x, int y, int z) {
        Random r = new Random();
        int n = 0;
        for (int i = 0; i < 5; i++) {
            Block c = world.getBlockAt(x + r.nextInt(-1, 2), y + r.nextInt(-1, 2), z + r.nextInt(-1, 2));
            fill(c, r);
            n++;
        }
        return n;
    }

    // ── Incubadora Geotérmica ──────────────────────────────────

    private static String id(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private static boolean valid(Block pedestal) {
        Block cauldron = pedestal.getRelative(BlockFace.DOWN);
        if (cauldron.getType() != Material.LAVA_CAULDRON) {
            return false;
        }
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (cauldron.getRelative(f).getType() != Material.MAGMA_BLOCK) {
                return false;
            }
        }
        return true;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        Block b = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || b == null
                || !PEDESTALS.contains(b.getType()) || !valid(b)) {
            return;
        }
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        EndItem kind = EndItem.of(hand);
        Incubator inc = incubators.computeIfAbsent(id(b), k -> new Incubator());
        event.setCancelled(true);
        if (inc.deadline > 0) {
            long left = Math.max(0, (inc.deadline - System.currentTimeMillis()) / 1000);
            Msg.actionBar(p, "<gold>Incubando... <white>" + left + "s");
            return;
        }
        if (kind == EndItem.AMBER_FRAGMENT && inc.amber < AMBER_NEEDED) {
            hand.setAmount(hand.getAmount() - 1);
            inc.amber++;
            b.getWorld().playSound(b.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        } else if (kind == EndItem.CALCIFIED_FOSSIL && inc.fossil < 1) {
            hand.setAmount(hand.getAmount() - 1);
            inc.fossil++;
            b.getWorld().playSound(b.getLocation(), Sound.BLOCK_BONE_BLOCK_PLACE, 1f, 0.8f);
        }
        if (inc.amber >= AMBER_NEEDED && inc.fossil >= 1) {
            inc.deadline = System.currentTimeMillis() + (long) (cfg("incubation-seconds", 180) * 1000);
            b.getWorld().playSound(b.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1f);
            Msg.actionBar(p, "<gold>A incubadora esquentou. Algo está despertando...");
        } else {
            Msg.actionBar(p, "<yellow>Âmbar <white>" + inc.amber + "/" + AMBER_NEEDED + " <dark_gray>• <yellow>Fóssil <white>" + inc.fossil + "/1");
        }
        save();
    }

    private void tick() {
        tick++;
        long now = System.currentTimeMillis();
        boolean dirty = false;
        for (Iterator<Map.Entry<String, Incubator>> it = incubators.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Incubator inc = entry.getValue();
            String[] p = entry.getKey().split(";");
            World world = Bukkit.getWorld(p[0]);
            if (world == null) {
                continue;
            }
            int x = Integer.parseInt(p[1]);
            int y = Integer.parseInt(p[2]);
            int z = Integer.parseInt(p[3]);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Block pedestal = world.getBlockAt(x, y, z);
            if (!PEDESTALS.contains(pedestal.getType()) || !valid(pedestal)) {
                if (inc.amber > 0 || inc.fossil > 0) {
                    Location at = pedestal.getLocation().add(0.5, 1, 0.5);
                    for (int i = 0; i < inc.amber; i++) {
                        world.dropItemNaturally(at, EndItem.AMBER_FRAGMENT.create(plugin));
                    }
                    if (inc.fossil > 0) {
                        world.dropItemNaturally(at, EndItem.CALCIFIED_FOSSIL.create(plugin));
                    }
                }
                it.remove();
                dirty = true;
                continue;
            }
            if (inc.deadline <= 0) {
                continue;
            }
            Location top = pedestal.getLocation().add(0.5, 1.2, 0.5);
            world.spawnParticle(Particle.FLAME, top, 3, 0.25, 0.1, 0.25, 0.01);
            world.spawnParticle(Particle.SMOKE, top, 2, 0.2, 0.3, 0.2, 0.01);
            if (tick % 2 == 0) {
                world.playSound(top, Sound.ENTITY_WARDEN_HEARTBEAT, 0.7f, 1f);
            }
            if (now >= inc.deadline) {
                it.remove();
                dirty = true;
                pedestal.getRelative(BlockFace.DOWN).setType(Material.CAULDRON, false);
                FaunaKind kind = new Random().nextBoolean() ? FaunaKind.DEEPSLATE_ANKYLO : FaunaKind.CAVE_GLIDER;
                plugin.fauna().spawn(top, kind);
                world.playSound(top, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.2f, 0.8f);
                world.spawnParticle(Particle.POOF, top, 24, 0.5, 0.5, 0.5, 0.05);
                for (Player pl : world.getNearbyPlayers(top, 12)) {
                    Msg.actionBar(pl, "<gold>" + kind.display() + " despertou!");
                }
            }
        }
        if (dirty) {
            save();
        }
    }
}
