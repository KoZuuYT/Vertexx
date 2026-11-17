package dev.vertexx.boss;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Tab;
import dev.vertexx.core.YamlStore;
import dev.vertexx.rpg.Stat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.generator.structure.StructurePiece;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

/**
 * Bosses do Vertexx: salas (castelo de gelo e câmara do Wildfire), lutas ativas, descoberta de locais para
 * construir as estruturas e comandos de administração.
 */
public final class BossManager implements Listener {

    private static final Set<String> COLD_BIOMES = Set.of("snowy_plains", "ice_spikes", "snowy_taiga", "snowy_slopes",
            "grove");
    private static final Set<EntityDamageEvent.DamageCause> ALLOWED = Set.of(EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK, EntityDamageEvent.DamageCause.PROJECTILE,
            EntityDamageEvent.DamageCause.MAGIC, EntityDamageEvent.DamageCause.THORNS,
            EntityDamageEvent.DamageCause.KILL, EntityDamageEvent.DamageCause.CUSTOM);

    private final PluginMain plugin;
    private final YamlStore store;
    private final NamespacedKey bossKey;
    private final NamespacedKey partKey;
    private final NamespacedKey projectileKey;
    private final NamespacedKey hitKey;
    private final Map<String, Arena> arenas = new HashMap<>();
    private final Map<String, BossFight> fights = new HashMap<>();
    private final Set<String> decided = new HashSet<>();
    private final Map<String, Integer> attempts = new HashMap<>();
    private final Set<Long> sealedBlocks = new HashSet<>();
    private boolean building;
    private BukkitTask fastTask;
    private BukkitTask slowTask;
    private BukkitTask scanTask;
    private int slowTick;

    public BossManager(PluginMain plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "bosses.yml");
        this.bossKey = new NamespacedKey(plugin, "boss_body");
        this.partKey = new NamespacedKey(plugin, "boss_part");
        this.projectileKey = new NamespacedKey(plugin, "boss_projectile");
        this.hitKey = new NamespacedKey(plugin, "boss_hit");
    }

    PluginMain plugin() {
        return plugin;
    }

    NamespacedKey bossKey() {
        return bossKey;
    }

    NamespacedKey partKey() {
        return partKey;
    }

    NamespacedKey projectileKey() {
        return projectileKey;
    }

    NamespacedKey hitKey() {
        return hitKey;
    }

    double damageMultiplier() {
        return plugin.getConfig().getDouble("bosses.damage-multiplier", 1.0);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("bosses.enabled", true);
    }

    // ── Persistência ───────────────────────────────────────────

    public YamlConfiguration read() {
        return store.load();
    }

    public void apply(YamlConfiguration yaml) {
        arenas.clear();
        decided.clear();
        ConfigurationSection section = yaml.getConfigurationSection("arenas");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection child = section.getConfigurationSection(id);
                Arena arena = child == null ? null : Arena.load(id, child);
                if (arena != null) {
                    arenas.put(id, arena);
                }
            }
        }
        decided.addAll(yaml.getStringList("decided"));
        sealedBlocks.clear();
        for (Arena arena : arenas.values()) {
            if (arena.sealed) {
                indexSeals(arena, true);
            }
        }
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Arena arena : arenas.values()) {
            if (!arena.temp) {
                arena.save(yaml, "arenas." + arena.id);
            }
        }
        yaml.set("decided", new ArrayList<>(decided));
        return yaml;
    }

    public void save() {
        store.saveAsync(snapshot());
    }

    public void saveNow() {
        store.saveNow(snapshot());
    }

    // ── Ciclo de vida ──────────────────────────────────────────

    public void start() {
        fastTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickFights, 1L, 1L);
        slowTask = Bukkit.getScheduler().runTaskTimer(plugin, this::manageFights, 40L, 20L);
        int interval = Math.max(2, plugin.getConfig().getInt("bosses.scan-interval-seconds", 5)) * 20;
        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scan, 200L, interval);
    }

    public void shutdown() {
        if (fastTask != null) {
            fastTask.cancel();
        }
        if (slowTask != null) {
            slowTask.cancel();
        }
        if (scanTask != null) {
            scanTask.cancel();
        }
        for (BossFight fight : new ArrayList<>(fights.values())) {
            fight.remove();
            if (fight.arena.sealed) {
                seal(fight.arena, false);
            }
        }
        fights.clear();
    }

    private void tickFights() {
        if (fights.isEmpty()) {
            return;
        }
        for (BossFight fight : new ArrayList<>(fights.values())) {
            try {
                fight.tick();
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Erro na luta de " + fight.kind.display() + ": " + e);
                e.printStackTrace();
                endFight(fight, false);
            }
        }
    }

    /** A cada segundo: cria o boss dormente perto de jogadores, remove o que ficou sem ninguém e abre selos órfãos. */
    private void manageFights() {
        slowTick++;
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Arena arena : new ArrayList<>(arenas.values())) {
            World world = arena.bukkitWorld();
            if (world == null || !world.isChunkLoaded(arena.cx >> 4, arena.cz >> 4)) {
                continue;
            }
            BossFight fight = fights.get(arena.id);
            if (fight == null) {
                if (arena.sealed) {
                    seal(arena, false);
                }
                if (arena.ready(now) && playerNear(arena, 80)) {
                    spawnFight(arena);
                }
            }
        }
        for (BossFight fight : new ArrayList<>(fights.values())) {
            if (fight.dormant() && !fight.arena.temp && !playerNear(fight.arena, 110)) {
                endFight(fight, false);
            }
        }
    }

    private boolean playerNear(Arena arena, double range) {
        World world = arena.bukkitWorld();
        if (world == null) {
            return false;
        }
        double limit = range * range;
        for (Player player : world.getPlayers()) {
            if (arena.distanceSquared(player.getLocation()) <= limit) {
                return true;
            }
        }
        return false;
    }

    private BossFight spawnFight(Arena arena) {
        BossFight fight = switch (arena.kind) {
            case WILDFIRE -> new Wildfire(this, arena);
            case VOID_SCARAB -> new VoidScarab(this, arena);
            default -> new Iceologer(this, arena);
        };
        fights.put(arena.id, fight);
        setAltar(arena, false);
        fight.spawn();
        return fight;
    }

    void endFight(BossFight fight, boolean defeated) {
        fights.remove(fight.arena.id);
        setAltar(fight.arena, false);
        fight.remove();
        if (fight.arena.sealed) {
            seal(fight.arena, false);
        }
        if (fight.arena.temp) {
            arenas.remove(fight.arena.id);
        }
        save();
    }

    // ── Selos ──────────────────────────────────────────────────

    private static long blockKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | ((y + 2048) & 0xFFF);
    }

    private void indexSeals(Arena arena, boolean on) {
        for (int[] pos : arena.seals) {
            long key = blockKey(pos[0], pos[1], pos[2]);
            if (on) {
                sealedBlocks.add(key);
            } else {
                sealedBlocks.remove(key);
            }
        }
    }

    void seal(Arena arena, boolean on) {
        World world = arena.bukkitWorld();
        if (world == null || arena.sealed == on) {
            return;
        }
        arena.sealed = on;
        indexSeals(arena, on);
        for (int[] pos : arena.seals) {
            Location at = new Location(world, pos[0] + 0.5, pos[1] + 0.5, pos[2] + 0.5);
            world.getBlockAt(pos[0], pos[1], pos[2]).setType(on ? arena.sealMaterial : Material.AIR, false);
            if (on) {
                world.spawnParticle(arena.kind == BossKind.WILDFIRE ? Particle.FLAME : arena.kind == BossKind.VOID_SCARAB
                        ? Particle.PORTAL : Particle.SNOWFLAKE, at, 3, 0.3, 0.3, 0.3, 0.02);
            } else if (ThreadLocalRandom.current().nextInt(3) == 0) {
                world.spawnParticle(Particle.BLOCK, at, 6, 0.3, 0.3, 0.3, 0.1, arena.sealMaterial.createBlockData());
            }
        }
        if (!arena.seals.isEmpty()) {
            int[] mid = arena.seals.get(arena.seals.size() / 2);
            Location at = new Location(world, mid[0] + 0.5, mid[1] + 0.5, mid[2] + 0.5);
            Fx.worldSound(at, on ? Sound.BLOCK_ANVIL_LAND : Sound.BLOCK_GLASS_BREAK, 1.5f, on ? 0.6f : 0.8f);
            Fx.worldSound(at, on ? Sound.BLOCK_IRON_DOOR_CLOSE : Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.7f);
        }
        save();
    }

    // ── Vitória ────────────────────────────────────────────────

    void victory(BossFight fight) {
        Arena arena = fight.arena;
        Location at = fight.pos.clone().add(0, 1.5, 0);
        World world = at.getWorld();
        long minutes = arena.kind == BossKind.VOID_SCARAB ? plugin.getConfig().getLong("bosses.void-scarab.respawn-minutes", 30)
                : plugin.getConfig().getLong("bosses.respawn-minutes", 90);
        if (!arena.temp) {
            arena.readyAt = minutes <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + minutes * 60_000L;
            arena.kills++;
        }
        if (arena.sealed) {
            seal(arena, false);
        }
        List<Player> winners = new ArrayList<>();
        for (UUID id : fight.participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.getWorld().equals(world) && player.getLocation().distanceSquared(at) < 60 * 60) {
                winners.add(player);
            }
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<ItemStack> loot = new ArrayList<>();
        loot.add(plugin.mobs().createEssence(6 + random.nextInt(4)));
        if (arena.kind == BossKind.VOID_SCARAB) {
            loot.add(dev.vertexx.end.EndItem.CRYSTAL_HEART.create(plugin));
            ItemStack plates = dev.vertexx.end.EndItem.CHITIN_PLATE.create(plugin);
            plates.setAmount(4 + random.nextInt(3));
            loot.add(plates);
            loot.add(dev.vertexx.end.EndItem.SCARAB_MANDIBLE.create(plugin));
            loot.add(new ItemStack(Material.AMETHYST_SHARD, 16 + random.nextInt(17)));
            loot.add(new ItemStack(Material.ENDER_PEARL, 4 + random.nextInt(5)));
        } else if (arena.kind == BossKind.WILDFIRE) {
            loot.add(new ItemStack(Material.NETHERITE_SCRAP, 3 + random.nextInt(3)));
            loot.add(new ItemStack(Material.GOLD_INGOT, 16 + random.nextInt(17)));
            loot.add(new ItemStack(Material.BLAZE_ROD, 8 + random.nextInt(7)));
            if (random.nextBoolean()) {
                loot.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
            }
            loot.add(dev.vertexx.end.EndItem.WILDFIRE_CORE.create(plugin));
        } else {
            loot.add(new ItemStack(Material.DIAMOND, 4 + random.nextInt(5)));
            loot.add(new ItemStack(Material.BLUE_ICE, 24 + random.nextInt(17)));
            loot.add(new ItemStack(Material.AMETHYST_SHARD, 16 + random.nextInt(17)));
            if (random.nextBoolean()) {
                loot.add(new ItemStack(Material.TOTEM_OF_UNDYING));
            }
            loot.add(dev.vertexx.end.EndItem.ICEOLOGER_CORE.create(plugin));
        }
        Location dropAt = arena.kind == BossKind.VOID_SCARAB && arena.altar != null
                ? new Location(world, arena.altar[0] + 0.5, arena.altar[1] + 2, arena.altar[2] + 0.5) : at;
        for (ItemStack item : loot) {
            org.bukkit.entity.Item dropped = world.dropItemNaturally(dropAt, item);
            if (arena.kind == BossKind.VOID_SCARAB) {
                // protegido contra o Vazio e contra o despawn por 10 minutos
                dropped.setInvulnerable(true);
                dropped.setUnlimitedLifetime(true);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (dropped.isValid()) {
                        dropped.setUnlimitedLifetime(false);
                        dropped.setInvulnerable(false);
                        dropped.setTicksLived(1);
                    }
                }, 20L * 60 * 10);
            }
        }
        for (int i = 0; i < 15; i++) {
            world.spawn(at, ExperienceOrb.class, orb -> orb.setExperience(90));
        }
        double xp = plugin.getConfig().getDouble("bosses.xp-reward", 600);
        for (Player player : winners) {
            plugin.rpg().addXp(player, Stat.COMBAT, xp);
            plugin.rpg().cinematics().bossSlain(player, fight.kind.color() + fight.kind.display());
        }
        StringBuilder names = new StringBuilder();
        for (Player player : winners) {
            names.append(names.length() == 0 ? "" : "<gray>, ").append("<white>").append(Msg.esc(player.getName()));
        }
        if (names.length() > 0) {
            Bukkit.getServer().sendMessage(Msg.mm(Msg.PREFIX + names + " <gray>derrotou" + (winners.size() > 1 ? "ram" : "")
                    + " " + fight.kind.color() + "<bold>" + fight.kind.display() + "</bold><gray>!"));
        }
        save();
    }

    // ── Eventos ────────────────────────────────────────────────

    private BossFight fightOf(Entity entity) {
        if (!(entity instanceof IronGolem)) {
            return null;
        }
        String id = entity.getPersistentDataContainer().get(bossKey, PersistentDataType.STRING);
        BossFight fight = id == null ? null : fights.get(id);
        return fight != null && fight.ownsHitbox(entity) ? fight : null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        // dano causado pelos ataques dos bosses a jogadores (bolas de fogo)
        if (event instanceof EntityDamageByEntityEvent byEntity && event.getEntity() instanceof Player
                && byEntity.getDamager() instanceof Projectile projectile) {
            Double damage = projectile.getPersistentDataContainer().get(projectileKey, PersistentDataType.DOUBLE);
            if (damage != null) {
                event.setDamage(damage * damageMultiplier());
                if (projectile instanceof org.bukkit.entity.ShulkerBullet && event.getEntity() instanceof Player victim) {
                    // espinhos de coro: Levitação I por 2,5 s (no lugar dos 10 s do vanilla)
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        victim.removePotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION);
                        victim.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION, 50, 0));
                    });
                }
            }
            return;
        }
        BossFight fight = fightOf(event.getEntity());
        if (fight == null) {
            return;
        }
        if (!fight.vulnerable() || !ALLOWED.contains(event.getCause())) {
            if (event.getCause() != EntityDamageEvent.DamageCause.KILL) {
                event.setCancelled(true);
                return;
            }
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof Projectile projectile && fight.blocksProjectile(projectile)) {
                projectile.remove();
                event.setCancelled(true);
                return;
            }
            if (byEntity.getDamager() instanceof Player player && !isSurvival(player)) {
                event.setCancelled(true);
                return;
            }
        }
        event.setDamage(fight.damageTaken(event.getDamage()));
        if (fight.model != null && event.getDamage() > 0) {
            fight.model.hurtFlash(10);
        }
        Location at = fight.pos.clone().add(0, 1.8, 0);
        if (fight.kind == BossKind.VOID_SCARAB) {
            fight.world.spawnParticle(Particle.PORTAL, at, 10, 0.8, 0.8, 0.8, 0.3);
        } else if (fight.kind == BossKind.WILDFIRE) {
            fight.world.spawnParticle(Particle.FLAME, at, 8, 0.4, 0.5, 0.4, 0.05);
            Fx.worldSound(at, Sound.ENTITY_BLAZE_HURT, 1f, 0.6f);
        } else {
            fight.world.spawnParticle(Particle.SNOWFLAKE, at, 10, 0.4, 0.6, 0.4, 0.05);
            Fx.worldSound(at, Sound.ENTITY_EVOKER_HURT, 1f, 0.8f);
            Fx.worldSound(at, Sound.BLOCK_GLASS_HIT, 0.8f, 1.2f);
        }
    }

    private static boolean isSurvival(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    /** Peças (Interaction) do Void-Scarab: dá o golpe ao corpo-base indicando qual peça foi atingida. */
    private boolean routeHit(Entity entity, Entity source, double amount) {
        String tag = entity.getPersistentDataContainer().get(hitKey, PersistentDataType.STRING);
        if (tag == null) {
            return false;
        }
        String[] parts = tag.split(":");
        BossFight fight = fights.get(parts[0]);
        if (fight instanceof VoidScarab scarab && fight.vulnerable() && parts.length == 2) {
            scarab.hitPart(VoidScarab.Part.valueOf(parts[1]));
            fight.hitbox.damage(amount, source);
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttackPart(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event) {
        Entity target = event.getAttacked();
        if (!target.getPersistentDataContainer().has(hitKey, PersistentDataType.STRING)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!isSurvival(player)) {
            return;
        }
        var attr = player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        double base = attr == null ? 1 : attr.getValue();
        float charge = player.getAttackCooldown();
        routeHit(target, player, base * (0.2 + 0.8 * charge * charge));
        player.swingMainHand();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectilePart(ProjectileHitEvent event) {
        Entity hit = event.getHitEntity();
        if (hit == null || !hit.getPersistentDataContainer().has(hitKey, PersistentDataType.STRING)) {
            return;
        }
        event.setCancelled(true);
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter) || !isSurvival(shooter)) {
            projectile.remove();
            return;
        }
        double amount = projectile instanceof org.bukkit.entity.AbstractArrow arrow
                ? arrow.getDamage() * Math.min(3.0, arrow.getVelocity().length()) : 4;
        routeHit(hit, shooter, amount);
        projectile.remove();
    }

    // ── Altar de Cristal do Vazio ──────────────────────────────

    private void setAltar(Arena arena, boolean eye) {
        if (arena.kind != BossKind.VOID_SCARAB || arena.altar == null) {
            return;
        }
        World world = arena.bukkitWorld();
        if (world == null || !world.isChunkLoaded(arena.altar[0] >> 4, arena.altar[2] >> 4)) {
            return;
        }
        org.bukkit.block.Block block = world.getBlockAt(arena.altar[0], arena.altar[1], arena.altar[2]);
        if (block.getType() == Material.END_PORTAL_FRAME
                && block.getBlockData() instanceof org.bukkit.block.data.type.EndPortalFrame frame) {
            frame.setEye(eye);
            block.setBlockData(frame, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAltar(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.END_PORTAL_FRAME || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        org.bukkit.block.Block clicked = event.getClickedBlock();
        for (BossFight fight : fights.values()) {
            Arena arena = fight.arena;
            if (arena.kind != BossKind.VOID_SCARAB || arena.altar == null || arena.altar[0] != clicked.getX()
                    || arena.altar[1] != clicked.getY() || arena.altar[2] != clicked.getZ()) {
                continue;
            }
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (!fight.dormant()) {
                return;
            }
            var inventory = player.getInventory();
            int fragments = 0;
            for (ItemStack item : inventory.getContents()) {
                if (dev.vertexx.end.EndItem.ENDERITE_FRAGMENT.matches(item)) {
                    fragments += item.getAmount();
                }
            }
            if (!inventory.contains(Material.ENDER_EYE) || fragments < 2) {
                Msg.actionBar(player, "<dark_purple>O altar pede <white>1 Olho do Fim <dark_purple>e <white>2 Fragmentos de Enderita<dark_purple>.");
                Fx.deny(player);
                return;
            }
            inventory.removeItem(new ItemStack(Material.ENDER_EYE, 1));
            int left = 2;
            for (ItemStack item : inventory.getContents()) {
                if (left > 0 && dev.vertexx.end.EndItem.ENDERITE_FRAGMENT.matches(item)) {
                    int take = Math.min(left, item.getAmount());
                    item.setAmount(item.getAmount() - take);
                    left -= take;
                }
            }
            setAltar(arena, true);
            Fx.worldSound(clicked.getLocation().add(0.5, 1, 0.5), Sound.BLOCK_END_PORTAL_FRAME_FILL, 2f, 0.6f);
            clicked.getWorld().spawnParticle(Particle.REVERSE_PORTAL, clicked.getLocation().add(0.5, 1.2, 0.5), 80, 0.5, 0.8, 0.5, 0.3);
            fight.awaken();
            return;
        }
    }

    /** Botão do meio no boss: não entrega o ovo do golem de ferro da hitbox. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPick(io.papermc.paper.event.player.PlayerPickEntityEvent event) {
        if (fightOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Entity hit = event.getHitEntity();
        if (hit == null) {
            return;
        }
        BossFight fight = fightOf(hit);
        if (fight != null && fight.blocksProjectile(event.getEntity())) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        BossFight fight = fightOf(event.getEntity());
        if (fight != null && !fight.dying()) {
            fight.onDeath(event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() != null && fightOf(event.getTarget()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity.getPersistentDataContainer().has(partKey, PersistentDataType.BYTE)
                    || entity.getPersistentDataContainer().has(bossKey, PersistentDataType.STRING)
                    && fightOf(entity) == null) {
                entity.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!sealedBlocks.isEmpty() && sealedBlocks.contains(blockKey(event.getBlock().getX(), event.getBlock().getY(),
                event.getBlock().getZ()))) {
            event.setCancelled(true);
            Msg.actionBar(event.getPlayer(), "<red>O selo da sala só cede quando o boss cair.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!sealedBlocks.isEmpty()) {
            event.blockList().removeIf(block -> sealedBlocks.contains(blockKey(block.getX(), block.getY(), block.getZ())));
        }
    }

    // ── Descoberta de locais ───────────────────────────────────

    private void scan() {
        if (!enabled() || building) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            if (world.getEnvironment() == World.Environment.NETHER) {
                scanFortress(player);
            } else if (world.getEnvironment() == World.Environment.NORMAL) {
                scanCastle(player);
            } else if (world.getEnvironment() == World.Environment.THE_END) {
                scanSpire(player);
            }
            if (building) {
                return;
            }
        }
    }

    private boolean farFromOthers(BossKind kind, World world, double x, double z, double spacing) {
        for (Arena arena : arenas.values()) {
            if (arena.kind == kind && arena.world.equals(world.getName())
                    && Math.hypot(arena.cx - x, arena.cz - z) < spacing) {
                return false;
            }
        }
        return true;
    }

    private void scanFortress(Player player) {
        World world = player.getWorld();
        double chance = plugin.getConfig().getDouble("bosses.wildfire.fortress-chance", 0.8);
        int pcx = player.getLocation().getBlockX() >> 4;
        int pcz = player.getLocation().getBlockZ() >> 4;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (!world.isChunkLoaded(pcx + dx, pcz + dz)) {
                    continue;
                }
                Chunk chunk = world.getChunkAt(pcx + dx, pcz + dz, false);
                for (GeneratedStructure structure : chunk.getStructures(Structure.FORTRESS)) {
                    BoundingBox box = structure.getBoundingBox();
                    String key = "fortress:" + world.getName() + ":" + (int) box.getMinX() + ":" + (int) box.getMinZ();
                    if (decided.contains(key)) {
                        continue;
                    }
                    decided.add(key);
                    save();
                    Random random = new Random(world.getSeed() ^ key.hashCode());
                    if (random.nextDouble() >= chance || !enabled()) {
                        continue;
                    }
                    if (planWildfire(world, structure, random)) {
                        return;
                    }
                }
            }
        }
    }

    /** Escolhe o lado da fortaleza e o ponto da câmara, e a constrói. */
    private boolean planWildfire(World world, GeneratedStructure structure, Random random) {
        BoundingBox whole = structure.getBoundingBox();
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] out = sides[random.nextInt(4)];
        StructurePiece best = null;
        double bestScore = -Double.MAX_VALUE;
        for (StructurePiece piece : structure.getPieces()) {
            BoundingBox b = piece.getBoundingBox();
            double score = out[0] * b.getCenterX() + out[1] * b.getCenterZ() + random.nextDouble();
            if (score > bestScore) {
                bestScore = score;
                best = piece;
            }
        }
        if (best == null) {
            return false;
        }
        BoundingBox b = best.getBoundingBox();
        int reach = 15 + 14;
        int cx;
        int cz;
        if (out[0] != 0) {
            cx = (int) (out[0] > 0 ? b.getMaxX() + 1 : b.getMinX()) + out[0] * reach;
            cz = (int) b.getCenterZ();
        } else {
            cz = (int) (out[1] > 0 ? b.getMaxZ() + 1 : b.getMinZ()) + out[1] * reach;
            cx = (int) b.getCenterX();
        }
        if (!farFromOthers(BossKind.WILDFIRE, world, cx, cz, 160)) {
            return false;
        }
        int fy = (int) Math.max(34, Math.min(88, b.getMinY() + 5));
        int sampleX = (int) b.getCenterX();
        int sampleZ = (int) b.getCenterZ();
        int fx = -out[0];
        int fz = -out[1];
        int floorY = fy;
        if (world.isChunkLoaded(sampleX >> 4, sampleZ >> 4)) {
            floorY = findFloor(world, sampleX, sampleZ, (int) b.getMinY(), (int) b.getMaxY(), fy);
        }
        int y = Math.max(34, Math.min(88, floorY));
        plugin.getLogger().info("Construindo a Câmara do Wildfire em " + world.getName() + " " + cx + " " + y + " " + cz);
        buildWildfire(world, cx, y, cz, fx, fz, reach + 3, false);
        return true;
    }

    /** Procura, na coluna da peça da fortaleza, a altura de um piso onde se caminha (sólido, com 3 de ar acima). */
    private static int findFloor(World world, int x, int z, int minY, int maxY, int fallback) {
        for (int y = minY; y <= maxY - 3; y++) {
            if (world.getBlockAt(x, y - 1, z).getType().isSolid() && !world.getBlockAt(x, y, z).getType().isSolid()
                    && !world.getBlockAt(x, y + 1, z).getType().isSolid()
                    && !world.getBlockAt(x, y + 2, z).getType().isSolid()) {
                return y;
            }
        }
        return fallback;
    }

    private void buildWildfire(World world, int cx, int fy, int cz, int fx, int fz, int corridorEnd, boolean notify) {
        building = true;
        BlockQueue queue = new BlockQueue(world);
        List<int[]> seals = Structures.wildfireHall(queue, cx, cz, fy, fx, fz, corridorEnd);
        queue.run(plugin, 6000, () -> {
            Arena arena = Arena.create(BossKind.WILDFIRE, world, cx, fy, cz, fx, fz, false);
            arena.seals.addAll(seals);
            arena.sealMaterial = Material.OBSIDIAN;
            arenas.put(arena.id, arena);
            building = false;
            save();
            plugin.getLogger().info("Câmara do Wildfire pronta em " + cx + " " + fy + " " + cz + ".");
        });
    }

    private void scanCastle(Player player) {
        Location at = player.getLocation();
        World world = at.getWorld();
        String biome = at.getBlock().getBiome().getKey().getKey();
        if (!COLD_BIOMES.contains(biome)) {
            return;
        }
        int cell = Math.max(300, plugin.getConfig().getInt("bosses.iceologer.cell-size", 1100));
        String key = "castle:" + world.getName() + ":" + Math.floorDiv(at.getBlockX(), cell) + ":"
                + Math.floorDiv(at.getBlockZ(), cell);
        if (decided.contains(key)) {
            return;
        }
        double spawnDistance = Math.hypot(at.getX() - world.getSpawnLocation().getX(), at.getZ() - world.getSpawnLocation().getZ());
        if (spawnDistance < plugin.getConfig().getDouble("bosses.iceologer.min-spawn-distance", 500)) {
            return;
        }
        Random random = new Random(world.getSeed() ^ key.hashCode());
        double chance = plugin.getConfig().getDouble("bosses.iceologer.castle-chance", 0.6);
        if (random.nextDouble() >= chance || !farFromOthers(BossKind.ICEOLOGER, world, at.getX(), at.getZ(),
                plugin.getConfig().getDouble("bosses.iceologer.min-spacing", 900))) {
            decided.add(key);
            save();
            return;
        }
        int tries = attempts.merge(key, 1, Integer::sum);
        if (tries > 4) {
            decided.add(key);
            save();
            return;
        }
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        double distance = 70 + ThreadLocalRandom.current().nextDouble() * 50;
        int cx = (int) (at.getX() + Math.cos(angle) * distance);
        int cz = (int) (at.getZ() + Math.sin(angle) * distance);
        building = true;
        loadAround(world, cx, cz, 3).whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || !evaluateCastle(world, cx, cz, key)) {
                building = false;
            }
        }));
    }

    private CompletableFuture<?> loadAround(World world, int x, int z, int radius) {
        List<CompletableFuture<?>> list = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                list.add(world.getChunkAtAsync((x >> 4) + dx, (z >> 4) + dz, true));
            }
        }
        return CompletableFuture.allOf(list.toArray(new CompletableFuture<?>[0]));
    }

    /** Verifica bioma, relevo e água; se servir, constrói. Devolve verdadeiro quando a construção começou. */
    private boolean evaluateCastle(World world, int cx, int cz, String key) {
        String biome = world.getBiome(cx, world.getHighestBlockYAt(cx, cz), cz).getKey().getKey();
        if (!COLD_BIOMES.contains(biome)) {
            return false;
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        List<Integer> heights = new ArrayList<>();
        for (int dx = -20; dx <= 20; dx += 10) {
            for (int dz = -20; dz <= 20; dz += 10) {
                int y = world.getHighestBlockYAt(cx + dx, cz + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (world.getBlockAt(cx + dx, y, cz + dz).isLiquid()) {
                    return false;
                }
                min = Math.min(min, y);
                max = Math.max(max, y);
                heights.add(y);
            }
        }
        if (max - min > 14) {
            return false;
        }
        heights.sort(Integer::compare);
        int ground = heights.get(heights.size() / 2);
        int fy = ground + 1;
        // a entrada olha para o lado mais baixo do terreno
        int[][] sides = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        int[] forward = sides[0];
        int lowest = Integer.MAX_VALUE;
        int gateGround = ground;
        for (int[] side : sides) {
            int y = world.getHighestBlockYAt(cx + side[0] * 28, cz + side[1] * 28, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (y < lowest) {
                lowest = y;
                forward = side;
                gateGround = y;
            }
        }
        int ramp = Math.max(0, Math.min(14, fy - 1 - gateGround));
        decided.add(key);
        save();
        plugin.getLogger().info("Construindo o Castelo de Gelo em " + world.getName() + " " + cx + " " + fy + " " + cz);
        buildCastle(world, cx, fy, cz, forward[0], forward[1], min, ramp);
        return true;
    }

    private void buildCastle(World world, int cx, int fy, int cz, int fx, int fz, int lowY, int ramp) {
        building = true;
        BlockQueue queue = new BlockQueue(world);
        List<int[]> seals = Structures.iceCastle(queue, cx, cz, fy, fx, fz, lowY, ramp);
        // abre uma trincheira de acesso caso o terreno diante do portão seja mais alto
        for (int lz = 22; lz <= 34; lz++) {
            for (int lx = -3; lx <= 3; lx++) {
                for (int y = fy; y <= fy + 6; y++) {
                    queue.set(cx + fx * lz + fz * lx, y, cz + fz * lz - fx * lx, Material.AIR);
                }
            }
        }
        queue.run(plugin, 6000, () -> {
            Arena arena = Arena.create(BossKind.ICEOLOGER, world, cx, fy, cz, fx, fz, false);
            arena.seals.addAll(seals);
            arena.sealMaterial = Material.BLUE_ICE;
            arenas.put(arena.id, arena);
            building = false;
            save();
            plugin.getLogger().info("Castelo de Gelo pronto em " + cx + " " + fy + " " + cz + ".");
        });
    }

    // ── Pináculo de Quitina (Abismos de Enderita) ──────────────

    private void scanSpire(Player player) {
        World world = player.getWorld();
        int pcx = player.getLocation().getBlockX() >> 4;
        int pcz = player.getLocation().getBlockZ() >> 4;
        double chance = 1.0 / Math.max(1, plugin.getConfig().getInt("bosses.void-scarab.chunk-odds", 650));
        double minRadius = plugin.getConfig().getDouble("bosses.void-scarab.min-radius", 2500);
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int chx = pcx + dx;
                int chz = pcz + dz;
                int x = chx * 16 + 8;
                int z = chz * 16 + 8;
                if (Math.hypot(x, z) < minRadius) {
                    continue;
                }
                String key = "spire:" + world.getName() + ":" + chx + ":" + chz;
                if (decided.contains(key)) {
                    continue;
                }
                Random random = new Random(world.getSeed() * 31 + chx * 341873128712L + chz * 132897987541L);
                if (random.nextDouble() >= chance) {
                    continue;
                }
                if (!world.isChunkLoaded(chx, chz) || !world.isChunkLoaded(chx - 1, chz) || !world.isChunkLoaded(chx + 1, chz)
                        || !world.isChunkLoaded(chx, chz - 1) || !world.isChunkLoaded(chx, chz + 1)) {
                    continue;
                }
                decided.add(key);
                save();
                if (dev.vertexx.end.EndWorld.zoneAt(world, x, z) != dev.vertexx.end.EndWorld.Zone.WASTES
                        || !farFromOthers(BossKind.VOID_SCARAB, world, x, z, plugin.getConfig().getDouble("bosses.void-scarab.min-spacing", 400))) {
                    continue;
                }
                if (planSpire(world, x, z)) {
                    return;
                }
            }
        }
    }

    /** Área sólida mínima de 32x32 de pedra do End, com relevo quase plano; escolhe a entrada para o lado mais firme. */
    private boolean planSpire(World world, int x, int z) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        List<Integer> heights = new ArrayList<>();
        for (int dx = -16; dx <= 16; dx += 8) {
            for (int dz = -16; dz <= 16; dz += 8) {
                int y = world.getHighestBlockYAt(x + dx, z + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                Material type = world.getBlockAt(x + dx, y, z + dz).getType();
                if (type.isAir() || !type.isSolid() || y < 10) {
                    return false;
                }
                min = Math.min(min, y);
                max = Math.max(max, y);
                heights.add(y);
            }
        }
        if (max - min > 8) {
            return false;
        }
        heights.sort(Integer::compare);
        int fy = heights.get(heights.size() / 2) + 1;
        int[][] sides = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        int[] forward = sides[0];
        int best = Integer.MIN_VALUE;
        for (int[] side : sides) {
            int y = world.getHighestBlockYAt(x + side[0] * 26, z + side[1] * 26, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            int score = world.getBlockAt(x + side[0] * 26, y, z + side[1] * 26).getType().isSolid() ? y : -1000;
            if (score > best) {
                best = score;
                forward = side;
            }
        }
        plugin.getLogger().info("Construindo o Pináculo de Quitina em " + world.getName() + " " + x + " " + fy + " " + z);
        buildSpire(world, x, fy, z, forward[0], forward[1]);
        return true;
    }

    private void buildSpire(World world, int cx, int fy, int cz, int fx, int fz) {
        building = true;
        BlockQueue queue = new BlockQueue(world);
        List<int[]> seals = Structures.chitinSpire(queue, cx, cz, fy, fx, fz);
        queue.run(plugin, 6000, () -> {
            Arena arena = Arena.create(BossKind.VOID_SCARAB, world, cx, fy, cz, fx, fz, false);
            arena.seals.addAll(seals);
            arena.sealMaterial = Material.AMETHYST_BLOCK;
            arena.altar = new int[] {cx, fy + 1, cz};
            arenas.put(arena.id, arena);
            building = false;
            save();
            plugin.getLogger().info("Pináculo de Quitina pronto em " + cx + " " + fy + " " + cz + ".");
        });
    }

    // ── Administração ──────────────────────────────────────────

    public List<String> describeArenas() {
        List<String> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Arena arena : arenas.values()) {
            String state = fights.containsKey(arena.id) ? "<green>ativa" : arena.ready(now) ? "<gray>disponível"
                    : "<yellow>renasce em " + formatMinutes(arena.readyAt - now);
            lines.add(arena.kind.color() + arena.kind.display() + " <dark_gray>• <white>" + arena.world + " "
                    + arena.cx + " " + arena.fy + " " + arena.cz + " <dark_gray>• " + state + " <dark_gray>• <gray>"
                    + arena.kills + " vitória" + (arena.kills == 1 ? "" : "s"));
        }
        return lines;
    }

    private static String formatMinutes(long ms) {
        if (ms > 3_000_000_000L) {
            return "nunca";
        }
        long minutes = Math.max(1, ms / 60_000);
        return minutes + " min";
    }

    /** Arenas conhecidas para o painel (cópia). */
    public List<Location> arenaLocations(BossKind kind) {
        List<Location> list = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            if (kind == null || arena.kind == kind) {
                Location center = arena.center();
                if (center != null) {
                    list.add(center);
                }
            }
        }
        return list;
    }

    public boolean isBuilding() {
        return building;
    }

    public int arenaCount() {
        return arenas.size();
    }

    public int activeFights() {
        return fights.size();
    }

    /** Cria um boss provisório (sem sala construída) no local, para testes. */
    public String spawnTest(Location at, BossKind kind) {
        if (at.getWorld() == null) {
            return "Mundo inválido.";
        }
        Arena arena = Arena.create(kind, at.getWorld(), at.getBlockX(), at.getBlockY(), at.getBlockZ(), 0, 1, true);
        arenas.put(arena.id, arena);
        spawnFight(arena);
        return null;
    }

    /** Constrói a sala do boss no local do administrador, voltada para onde ele olha. */
    public String buildHere(Player player, BossKind kind) {
        if (building) {
            return "Já há uma construção em andamento.";
        }
        Location at = player.getLocation();
        float yaw = at.getYaw();
        int fx = 0;
        int fz = 1;
        double yawAbs = ((yaw % 360) + 360) % 360;
        if (yawAbs >= 45 && yawAbs < 135) {
            fx = -1;
            fz = 0;
        } else if (yawAbs >= 135 && yawAbs < 225) {
            fx = 0;
            fz = -1;
        } else if (yawAbs >= 225 && yawAbs < 315) {
            fx = 1;
            fz = 0;
        }
        return buildAt(player.getWorld(), at.getBlockX(), at.getBlockY(), at.getBlockZ(), fx, fz, kind);
    }

    /** Constrói a sala diante do ponto (x, y, z), com a entrada voltada para ele e olhando na direção (fx, fz). */
    public String buildAt(World world, int x, int y, int z, int fx, int fz, BossKind kind) {
        if (building) {
            return "Já há uma construção em andamento.";
        }
        if (kind == BossKind.VOID_SCARAB) {
            buildSpire(world, x + fx * 24, y, z + fz * 24, -fx, -fz);
        } else if (kind == BossKind.WILDFIRE) {
            buildWildfire(world, x + fx * 30, y, z + fz * 30, -fx, -fz, 33, true);
        } else {
            buildCastle(world, x + fx * 28, y, z + fz * 28, -fx, -fz, y - 4, 0);
        }
        return null;
    }

    /** Subcomando {@code /vertexx boss ...}. */
    public void command(CommandSender sender, String[] args) {
        String sub = args.length < 2 ? "list" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                List<String> lines = describeArenas();
                if (lines.isEmpty()) {
                    Msg.send(sender, "<gray>Nenhuma sala de boss construída ainda. Elas surgem ao explorar terras geladas "
                            + "(Castelo de Gelo) e fortalezas do Nether (Câmara do Wildfire).");
                }
                lines.forEach(line -> Msg.send(sender, line));
                Msg.send(sender, "<dark_gray>Lutas ativas: " + fights.size() + (building ? " • construindo…" : ""));
            }
            case "spawn" -> {
                BossKind kind = args.length > 2 ? BossKind.parse(args[2]) : null;
                if (kind == null) {
                    Msg.send(sender, "<gray>Uso: <white>/vertexx boss spawn <wildfire|iceologer|void_scarab> [mundo x y z]");
                    return;
                }
                Location at = null;
                if (args.length >= 7) {
                    World world = Bukkit.getWorld(args[3]);
                    try {
                        if (world != null) {
                            at = new Location(world, Double.parseDouble(args[4]), Double.parseDouble(args[5]),
                                    Double.parseDouble(args[6]));
                        }
                    } catch (NumberFormatException ignored) {
                        at = null;
                    }
                } else if (sender instanceof Player player) {
                    at = player.getLocation();
                }
                if (at == null) {
                    Msg.send(sender, "<red>Informe o local: <white>/vertexx boss spawn " + kind.key() + " <mundo> <x> <y> <z>");
                    return;
                }
                String error = spawnTest(at, kind);
                Msg.send(sender, error == null ? "<green>" + kind.display() + " criado em modo de teste "
                        + "<gray>(dormente: entre na área ou use <white>/vertexx boss wake</white>)." : "<red>" + error);
            }
            case "build" -> {
                BossKind kind = args.length > 2 ? BossKind.parse(args[2]) : null;
                String error;
                if (kind != null && args.length >= 7 && Bukkit.getWorld(args[3]) != null) {
                    try {
                        int[] dir = switch (args.length > 7 ? args[7].toLowerCase(Locale.ROOT) : "s") {
                            case "n" -> new int[] {0, -1};
                            case "e" -> new int[] {1, 0};
                            case "w" -> new int[] {-1, 0};
                            default -> new int[] {0, 1};
                        };
                        error = buildAt(Bukkit.getWorld(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]),
                                Integer.parseInt(args[6]), dir[0], dir[1], kind);
                    } catch (NumberFormatException e) {
                        error = "Coordenadas inválidas.";
                    }
                } else if (sender instanceof Player player && kind != null) {
                    error = buildHere(player, kind);
                } else {
                    Msg.send(sender, "<gray>Uso: <white>/vertexx boss build <wildfire|iceologer|void_scarab> [mundo x y z [n|s|e|w]]");
                    return;
                }
                Msg.send(sender, error == null ? "<green>Construindo " + kind.place() + " à sua frente…" : "<red>" + error);
            }
            case "wake" -> {
                int count = 0;
                for (BossFight fight : new ArrayList<>(fights.values())) {
                    if (fight.dormant()) {
                        fight.awaken();
                        count++;
                    }
                }
                Msg.send(sender, "<green>" + count + " boss(es) despertado(s).");
            }
            case "attack" -> {
                BossKind kind = args.length > 2 ? BossKind.parse(args[2]) : null;
                String move = args.length > 3 ? args[3] : "";
                int count = 0;
                for (BossFight fight : fights.values()) {
                    if ((kind == null || fight.kind == kind) && fight.forceMove(move)) {
                        count++;
                    }
                }
                Msg.send(sender, count > 0 ? "<green>Ataque iniciado." : "<red>Nenhum boss ativo aceitou o ataque "
                        + "<gray>(precisa estar desperto e livre; use <white>/vertexx boss wake</white>).");
            }
            case "reset" -> {
                for (BossFight fight : new ArrayList<>(fights.values())) {
                    endFight(fight, false);
                }
                for (Arena arena : arenas.values()) {
                    arena.readyAt = 0;
                    if (arena.sealed) {
                        seal(arena, false);
                    }
                }
                save();
                Msg.send(sender, "<green>Bosses reiniciados: todas as salas estão disponíveis.");
            }
            case "kill" -> {
                int count = fights.size();
                for (BossFight fight : new ArrayList<>(fights.values())) {
                    endFight(fight, false);
                }
                Msg.send(sender, "<green>" + count + " luta(s) encerrada(s).");
            }
            case "tp" -> {
                BossKind kind = args.length > 2 ? BossKind.parse(args[2]) : null;
                if (!(sender instanceof Player player)) {
                    Msg.send(sender, "<red>Apenas jogadores.");
                    return;
                }
                Location best = null;
                for (Location candidate : arenaLocations(kind)) {
                    if (candidate.getWorld().equals(player.getWorld()) && (best == null
                            || candidate.distanceSquared(player.getLocation()) < best.distanceSquared(player.getLocation()))) {
                        best = candidate;
                    }
                }
                if (best == null) {
                    for (Location candidate : arenaLocations(kind)) {
                        best = candidate;
                        break;
                    }
                }
                if (best == null) {
                    Msg.send(sender, "<red>Nenhuma sala encontrada.");
                } else {
                    player.teleportAsync(best.add(0, 1, 0));
                    Msg.send(sender, "<gray>Teleportado para a sala.");
                }
            }
            default -> Msg.send(sender, "<gray>Uso: <white>/vertexx boss [list|spawn|build|wake|attack|reset|kill|tp]");
        }
    }

    public List<String> tab(String[] args) {
        if (args.length == 2) {
            return Tab.of(args[1], "list", "spawn", "build", "wake", "attack", "reset", "kill", "tp");
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3 && List.of("spawn", "build", "attack", "tp").contains(sub)) {
            return Tab.of(args[2], "wildfire", "iceologer", "void_scarab");
        }
        if (args.length == 4 && sub.equals("attack")) {
            BossKind kind = BossKind.parse(args[2]);
            return kind == null ? List.of() : Tab.of(args[3], kind == BossKind.WILDFIRE
                    ? List.of("shockwave", "rush", "lunge") : kind == BossKind.VOID_SCARAB
                    ? List.of("clamp", "volley", "stinger", "swarm") : List.of("spikes", "iceberg"));
        }
        if (args.length == 4 && (sub.equals("spawn") || sub.equals("build"))) {
            return Tab.of(args[3], Bukkit.getWorlds().stream().map(World::getName).toList());
        }
        return List.of();
    }
}
