package dev.vertexx.fauna;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Tab;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.generator.structure.StructurePiece;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * As criaturas rejeitadas da votação de mobs: Rascal (2022), Golem de Tufo (2022), Moobloom (2020) e
 * Glare (2021). Cada uma é um mob invisível de verdade com um modelo de displays por cima
 * (veja {@link Critter}). O tipo fica gravado no PersistentDataContainer, então sobrevive a reinícios.
 */
public final class FaunaManager implements Listener {

    private final PluginMain plugin;
    private final NamespacedKey kindKey;
    private final NamespacedKey partKey;
    private final NamespacedKey shornKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey homeKey;
    private final NamespacedKey heldKey;
    private final NamespacedKey capeKey;
    private final NamespacedKey ignoreKey;
    private final Map<UUID, Critter> critters = new HashMap<>();
    private BukkitTask task;
    private int tick;

    public FaunaManager(PluginMain plugin) {
        this.plugin = plugin;
        this.kindKey = new NamespacedKey(plugin, "fauna_kind");
        this.partKey = new NamespacedKey(plugin, "fauna_part");
        this.shornKey = new NamespacedKey(plugin, "fauna_shorn");
        this.ownerKey = new NamespacedKey(plugin, "fauna_owner");
        this.homeKey = new NamespacedKey(plugin, "fauna_home");
        this.heldKey = new NamespacedKey(plugin, "fauna_held");
        this.capeKey = new NamespacedKey(plugin, "fauna_cape");
        this.ignoreKey = new NamespacedKey(plugin, "fauna_ignore");
    }

    PluginMain plugin() {
        return plugin;
    }

    NamespacedKey partKey() {
        return partKey;
    }

    NamespacedKey shornKey() {
        return shornKey;
    }

    NamespacedKey ownerKey() {
        return ownerKey;
    }

    NamespacedKey homeKey() {
        return homeKey;
    }

    NamespacedKey heldKey() {
        return heldKey;
    }

    NamespacedKey capeKey() {
        return capeKey;
    }

    NamespacedKey ignoreKey() {
        return ignoreKey;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("fauna.enabled", true);
    }

    // ── Ciclo ──────────────────────────────────────────────────

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 20L, 2L);
        // criaturas que já estavam carregadas (por exemplo após /reload)
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                adopt(entity);
            }
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (Critter critter : critters.values()) {
            critter.remove();
        }
        critters.clear();
    }

    private void tickAll() {
        tick += 2;
        // cópia: remover o mob (Rascal sumindo, golem desmontado…) dispara EntityRemoveEvent, que mexe no mapa
        for (Critter critter : new java.util.ArrayList<>(critters.values())) {
            boolean keep;
            try {
                keep = critter.tick();
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Falha em " + critter.kind() + ": " + e);
                keep = false;
            }
            if (!keep) {
                critter.remove();
                critters.values().removeIf(other -> other == critter);
            }
        }
        long interval = Math.max(2, plugin.getConfig().getInt("fauna.spawn-interval-seconds", 12)) * 20L;
        if (enabled() && tick % interval == 0) {
            spawnScan();
        }
    }

    FaunaKind kindOf(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
        return raw == null ? null : FaunaKind.parse(raw);
    }

    private Critter critterOf(Entity entity) {
        return critters.get(entity.getUniqueId());
    }

    /** Cria o modelo para uma entidade marcada, se ainda não houver um. */
    private void adopt(Entity entity) {
        if (!(entity instanceof Mob mob) || !entity.isValid() || critters.containsKey(entity.getUniqueId())) {
            return;
        }
        FaunaKind kind = kindOf(entity);
        if (kind == null) {
            return;
        }
        Critter critter = switch (kind) {
            case RASCAL -> new Rascal(this, mob);
            case TUFF_GOLEM -> new TuffGolem(this, mob);
            case MOOBLOOM -> new Moobloom(this, mob);
            case GLARE -> new Glare(this, mob);
        };
        critters.put(entity.getUniqueId(), critter);
        critter.start();
    }

    // ── Criação ────────────────────────────────────────────────

    /** Cria uma criatura (administração e regras de surgimento). */
    public Mob spawn(Location at, FaunaKind kind) {
        Mob mob = at.getWorld().spawn(at, kind.base(), entity -> {
            entity.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind.key());
            entity.setPersistent(true);
            entity.setRemoveWhenFarAway(false);
            if (entity instanceof Cow cow) {
                cow.setAdult();
                cow.setAgeLock(true);
                cow.setBreed(false);
            }
            AttributeInstance scale = entity.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(switch (kind) {
                    case RASCAL -> 0.55;
                    case TUFF_GOLEM -> 0.85;
                    case MOOBLOOM -> 1.0;
                    case GLARE -> 1.1;
                });
            }
            entity.setInvisible(true);
        });
        adopt(mob);
        return mob;
    }

    /** Alguns cows de florestas floridas e campos nascem como Moobloom. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!enabled() || !(event.getEntity() instanceof Cow cow) || kindOf(cow) != null || !cow.isAdult()) {
            return;
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL && reason != CreatureSpawnEvent.SpawnReason.CHUNK_GEN) {
            return;
        }
        Biome biome = cow.getWorld().getBiome(cow.getLocation());
        double chance = plugin.getConfig().getDouble("fauna.moobloom.chance", 0.35);
        double factor = biome == Biome.FLOWER_FOREST ? 1.0 : biome == Biome.MEADOW ? 0.4 : biome == Biome.SUNFLOWER_PLAINS ? 0.25 : 0;
        if (factor == 0 || ThreadLocalRandom.current().nextDouble() > chance * factor) {
            return;
        }
        cow.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, FaunaKind.MOOBLOOM.key());
        Bukkit.getScheduler().runTask(plugin, () -> adopt(cow));
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            adopt(entity);
        }
    }

    @EventHandler
    public void onRemoved(EntityRemoveEvent event) {
        Critter critter = critters.remove(event.getEntity().getUniqueId());
        if (critter != null) {
            critter.remove();
        }
    }

    // ── Interação e dano ───────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Critter critter = critterOf(event.getRightClicked());
        if (critter == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        FaunaKind kind = critter.kind();
        if (kind == FaunaKind.MOOBLOOM) {
            if (hand.getType() == Material.SHEARS) {
                event.setCancelled(true);
                shear(player, (Mob) event.getRightClicked(), hand);
            }
            return;
        }
        event.setCancelled(true);
        critter.interact(player, hand);
    }

    private void shear(Player player, Mob mob, ItemStack shears) {
        Long until = mob.getPersistentDataContainer().get(shornKey, PersistentDataType.LONG);
        long now = mob.getWorld().getFullTime();
        if (until != null && until > now) {
            player.sendActionBar(Msg.mm("<gray>As flores ainda estão crescendo de novo."));
            return;
        }
        mob.getPersistentDataContainer().set(shornKey, PersistentDataType.LONG, now + 6000L);
        Location at = mob.getLocation().add(0, 1.4, 0);
        mob.getWorld().dropItemNaturally(at, new ItemStack(Material.DANDELION, 4 + ThreadLocalRandom.current().nextInt(3)));
        mob.getWorld().playSound(at, Sound.ENTITY_MOOSHROOM_SHEAR, 1f, 1.2f);
        mob.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 10, 0.4, 0.2, 0.4, 0);
        if (player.getGameMode() != GameMode.CREATIVE) {
            shears.damage(1, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        Critter critter = critterOf(event.getEntity());
        if (!(critter instanceof TuffGolem golem)) {
            return;
        }
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player player
                && (player.isSneaking() || player.getGameMode() == GameMode.CREATIVE)) {
            golem.dismantle();
        } else if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player player) {
            player.sendActionBar(Msg.mm("<gray>Agache e bata para desmontar o Golem de Tufo."));
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Critter critter = critterOf(event.getEntity());
        if (critter == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (critter.kind() == FaunaKind.GLARE) {
            event.getDrops().clear();
            event.getDrops().add(new ItemStack(Material.GLOW_BERRIES, 1 + random.nextInt(3)));
        } else if (critter.kind() == FaunaKind.MOOBLOOM) {
            event.getDrops().add(new ItemStack(Material.DANDELION, 1 + random.nextInt(2)));
        }
    }

    // ── Golem de Tufo: construção ──────────────────────────────

    /**
     * Tufo, tufo (ou lã) e tufo cinzelado por cima: o último bloco colocado vira a cabeça.
     * Um bloco de lã no meio dá ao golem um manto da mesma cor.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!enabled() || !plugin.getConfig().getBoolean("fauna.tuff-golem.enabled", true)
                || event.getBlockPlaced().getType() != Material.CHISELED_TUFF) {
            return;
        }
        Block head = event.getBlockPlaced();
        Block middle = head.getRelative(0, -1, 0);
        Block base = head.getRelative(0, -2, 0);
        if (base.getType() != Material.TUFF) {
            return;
        }
        Material cape = null;
        if (middle.getType() != Material.TUFF) {
            if (!middle.getType().name().endsWith("_WOOL")) {
                return;
            }
            cape = middle.getType();
        }
        Material capeUsed = cape;
        Location at = base.getLocation().add(0.5, 0, 0.5);
        float yaw = event.getPlayer().getLocation().getYaw() + 180f;
        head.setType(Material.AIR, false);
        middle.setType(Material.AIR, false);
        base.setType(Material.AIR, false);
        at.setYaw(yaw);
        World world = at.getWorld();
        world.spawnParticle(Particle.BLOCK, at.clone().add(0, 1, 0), 40, 0.3, 0.7, 0.3, 0, Material.TUFF.createBlockData());
        world.playSound(at, Sound.BLOCK_STONE_PLACE, 1f, 0.7f);
        world.playSound(at, Sound.ENTITY_IRON_GOLEM_REPAIR, 0.8f, 0.7f);
        Mob mob = world.spawn(at, FaunaKind.TUFF_GOLEM.base(), entity -> {
            entity.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, FaunaKind.TUFF_GOLEM.key());
            if (capeUsed != null) {
                entity.getPersistentDataContainer().set(capeKey, PersistentDataType.STRING, capeUsed.name());
            }
            if (entity instanceof Cow cow) {
                cow.setAdult();
                cow.setAgeLock(true);
                cow.setBreed(false);
            }
            AttributeInstance scale = entity.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(0.85);
            }
            entity.setRotation(yaw, 0);
        });
        adopt(mob);
        event.getPlayer().sendActionBar(Msg.mm("<gray>Golem de Tufo criado. Clique com um item para ele exibir; agache e bata para desmontar."));
    }

    // ── Surgimento natural ─────────────────────────────────────

    private void spawnScan() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location l = player.getLocation();
            World world = l.getWorld();
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            if (plugin.getConfig().getBoolean("fauna.rascal.enabled", true) && l.getY() < 0 && count(FaunaKind.RASCAL, l, 64) == 0
                    && random.nextDouble() < plugin.getConfig().getDouble("fauna.rascal.chance", 0.12) && inMineshaft(l)) {
                spawnNear(player, FaunaKind.RASCAL, 9, 18, true);
            }
            if (plugin.getConfig().getBoolean("fauna.glare.enabled", true) && l.getY() < 64
                    && world.getBiome(l) == Biome.LUSH_CAVES && count(FaunaKind.GLARE, l, 48) < 2
                    && random.nextDouble() < plugin.getConfig().getDouble("fauna.glare.chance", 0.15)) {
                spawnNear(player, FaunaKind.GLARE, 8, 18, false);
            }
        }
    }

    private int count(FaunaKind kind, Location around, double radius) {
        int n = 0;
        for (Critter critter : critters.values()) {
            if (critter.kind() == kind && critter.mob.getWorld().equals(around.getWorld())
                    && critter.mob.getLocation().distanceSquared(around) < radius * radius) {
                n++;
            }
        }
        return n;
    }

    private static boolean inMineshaft(Location l) {
        Vector at = l.toVector();
        for (Structure structure : List.of(Structure.MINESHAFT, Structure.MINESHAFT_MESA)) {
            for (GeneratedStructure generated : l.getChunk().getStructures(structure)) {
                for (StructurePiece piece : generated.getPieces()) {
                    if (piece.getBoundingBox().clone().expand(8).contains(at)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void spawnNear(Player player, FaunaKind kind, int min, int max, boolean hidden) {
        World world = player.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 16; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = min + random.nextDouble() * (max - min);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            for (int dy = 3; dy >= -4; dy--) {
                Block b = world.getBlockAt(x, player.getLocation().getBlockY() + dy, z);
                boolean ground = kind != FaunaKind.GLARE;
                if (!b.isPassable() || b.isLiquid() || !b.getRelative(0, 1, 0).isPassable() || !b.getRelative(0, 2, 0).isPassable()
                        || (ground && b.getRelative(0, -1, 0).isPassable())) {
                    continue;
                }
                if (kind == FaunaKind.GLARE && world.getBiome(b.getLocation()) != Biome.LUSH_CAVES) {
                    continue;
                }
                Location spot = b.getLocation().add(0.5, 0, 0.5);
                if (hidden) {
                    Location eye = player.getEyeLocation();
                    Vector dir = spot.clone().add(0, 0.5, 0).toVector().subtract(eye.toVector());
                    double length = dir.length();
                    if (length < 1 || world.rayTraceBlocks(eye, dir.normalize(), length, FluidCollisionMode.NEVER, true) == null) {
                        break;
                    }
                }
                spawn(spot, kind);
                return;
            }
        }
    }

    // ── Administração ──────────────────────────────────────────

    public void command(CommandSender sender, String[] args) {
        String sub = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "spawn", "criar" -> {
                FaunaKind kind = args.length < 3 ? null : FaunaKind.parse(args[2]);
                if (kind == null) {
                    Msg.send(sender, "<gray>Uso: <white>/vertexx rejeitado spawn <rascal|tuffgolem|moobloom|glare>");
                    return;
                }
                Location at;
                if (sender instanceof Player player) {
                    at = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(3));
                } else {
                    at = Bukkit.getWorlds().get(0).getSpawnLocation().add(0.5, 1, 0.5);
                }
                spawn(at, kind);
                Msg.send(sender, "<green>" + kind.display() + " criado.");
            }
            case "limpar" -> {
                int n = 0;
                for (Critter critter : new ArrayList<>(critters.values())) {
                    critter.mob.remove();
                    n++;
                }
                Msg.send(sender, "<green>" + n + " criatura(s) removida(s).");
            }
            case "lista" -> {
                for (Critter critter : critters.values()) {
                    Location l = critter.mob.getLocation();
                    Msg.send(sender, "<gray>" + critter.kind().display() + " <dark_gray>@ <white>" + l.getWorld().getName()
                            + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
                }
                Msg.send(sender, "<gray>Total: <white>" + critters.size());
            }
            default -> Msg.send(sender, "<gray>Uso: <white>/vertexx rejeitado <spawn|limpar|lista>");
        }
    }

    public List<String> tab(String[] args) {
        if (args.length == 2) {
            return Tab.of(args[1], "spawn", "limpar", "lista");
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("spawn")) {
            return Tab.of(args[2], "rascal", "tuffgolem", "moobloom", "glare");
        }
        return List.of();
    }
}
