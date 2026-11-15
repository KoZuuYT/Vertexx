package dev.vertexx.mob;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.StructurePiece;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

/**
 * Nivelamento de mobs hostis (distância do spawn, dimensão, estruturas), traits e elites.
 * O estado fica no PersistentDataContainer da própria entidade, então sobrevive a reinícios.
 */
public final class MobManager implements Listener {

    private final PluginMain plugin;
    private final NamespacedKey levelKey;
    private final NamespacedKey eliteKey;
    private final NamespacedKey traitsKey;
    private final NamespacedKey essenceKey;
    private final NamespacedKey labelKey;
    private final Map<UUID, Tracker> bars = new HashMap<>();
    private BukkitTask task;
    private int tick;

    private record Calc(int level, int structureBonus) {
    }

    /** Pedido de teste: aplicado ao próximo mob do tipo criado por {@link #spawnTest}. */
    private record Forced(EntityType type, int level, boolean elite, List<Trait> traits) {
    }

    private Forced forced;

    private static final class Tracker {
        final BossBar bar;
        final UUID mob;
        long lastHit;

        Tracker(BossBar bar, UUID mob, long lastHit) {
            this.bar = bar;
            this.mob = mob;
            this.lastHit = lastHit;
        }
    }

    public MobManager(PluginMain plugin) {
        this.plugin = plugin;
        this.levelKey = new NamespacedKey(plugin, "mob_level");
        this.eliteKey = new NamespacedKey(plugin, "mob_elite");
        this.traitsKey = new NamespacedKey(plugin, "mob_traits");
        this.essenceKey = new NamespacedKey(plugin, "essence");
        this.labelKey = new NamespacedKey(plugin, "mob_label");
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 20L, 20L);
        // mobs já carregados antes do plugin (reinício, /reload) ganham o rótulo novo
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (LivingEntity living : world.getLivingEntities()) {
                    if (level(living) > 0 && findLabel(living) == null) {
                        refreshName(living);
                    }
                }
            }
        });
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        bars.forEach((id, tracker) -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.hideBossBar(tracker.bar);
            }
        });
        bars.clear();
    }

    // ── Leitura do estado ──────────────────────────────────────

    private ConfigurationSection cfg() {
        return plugin.getConfig().getConfigurationSection("mobs");
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("mobs.enabled", true);
    }

    /** Nível do mob, ou 0 se não for um mob nivelado. */
    public int level(Entity entity) {
        Integer value = entity.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        return value == null ? 0 : value;
    }

    public boolean isElite(Entity entity) {
        return entity.getPersistentDataContainer().has(eliteKey, PersistentDataType.BYTE);
    }

    public Set<Trait> traits(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(traitsKey, PersistentDataType.STRING);
        Set<Trait> set = EnumSet.noneOf(Trait.class);
        if (raw != null && !raw.isEmpty()) {
            for (String part : raw.split(",")) {
                Trait trait = Trait.parse(part);
                if (trait != null) {
                    set.add(trait);
                }
            }
        }
        return set;
    }

    private boolean eligible(LivingEntity entity) {
        if (entity instanceof Player || !(entity instanceof Enemy) || entity instanceof Boss) {
            return false;
        }
        return entity.getType() != EntityType.WARDEN;
    }

    // ── Atribuição ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (enabled() && event.getEntity() instanceof LivingEntity living) {
            Forced request = forced;
            if (request != null && living.getType() == request.type()) {
                forced = null;
                assign(living, false, request.level(), request.elite(), true, request.traits());
                return;
            }
            assign(living, event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SLIME_SPLIT, -1, false, false, null);
        }
    }

    /** Mobs que já existiam antes do plugin (ou de chunks antigos) também recebem nível. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!enabled()) {
            return;
        }
        for (Entity entity : event.getEntities()) {
            if (entity instanceof TextDisplay display
                    && display.getPersistentDataContainer().has(labelKey, PersistentDataType.BYTE)
                    && display.getVehicle() == null) {
                display.remove(); // rótulo órfão (o mob sumiu enquanto o chunk estava descarregado)
            } else if (entity instanceof LivingEntity living) {
                if (level(living) == 0) {
                    assign(living, true, -1, false, false, null);
                } else if (findLabel(living) == null) {
                    refreshName(living);
                }
            }
        }
    }

    /**
     * Cria um mob com nível, elite e traits definidos (comando e painel de administração).
     * O pedido é registrado antes do spawn porque o próprio evento de nascimento já atribui um nível natural.
     *
     * @param traits traits desejados; vazio sorteia pelas regras normais
     * @return o mob criado, ou {@code null} se o tipo não aceita nível (ex.: aldeões, bosses)
     */
    public LivingEntity spawnTest(Location at, EntityType type, int level, boolean elite, List<Trait> traits) {
        forced = new Forced(type, level, elite, traits == null ? List.of() : traits);
        Entity entity;
        try {
            entity = at.getWorld().spawnEntity(at, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
        } finally {
            forced = null;
        }
        return entity instanceof LivingEntity living && level(living) > 0 ? living : null;
    }

    /** Tipos que recebem nível (hostis comuns), para sugestões e para o painel. */
    public static boolean accepts(EntityType type) {
        Class<? extends Entity> cls = type.getEntityClass();
        return type.isSpawnable() && cls != null && Enemy.class.isAssignableFrom(cls)
                && LivingEntity.class.isAssignableFrom(cls) && !Boss.class.isAssignableFrom(cls)
                && type != EntityType.WARDEN;
    }

    private void assign(LivingEntity entity, boolean noElite, int forcedLevel, boolean forceElite, boolean force,
                        List<Trait> forcedTraits) {
        if (!eligible(entity) || level(entity) > 0) {
            return;
        }
        ConfigurationSection c = cfg();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Calc calc = compute(entity.getLocation());
        int level = forcedLevel > 0 ? forcedLevel : calc.level();

        double eliteChance = c.getDouble("elite.chance", 0.04) * (1 + calc.structureBonus() / 6.0);
        boolean elite = forceElite || (!noElite && random.nextDouble() < eliteChance);
        if (elite && entity.getType() == EntityType.ELDER_GUARDIAN) {
            elite = false;
        }

        List<Trait> traits = new ArrayList<>();
        if (forcedTraits != null && !forcedTraits.isEmpty()) {
            traits.addAll(forcedTraits);
        } else {
        double traitChance = c.getDouble("trait-chance", 0.30) + calc.structureBonus() * 0.01;
        if (elite) {
            traits.addAll(pick(Trait.normal(), 1 + random.nextInt(2), traits));
            traits.addAll(pick(Trait.eliteOnly(), 1, traits));
        } else if (!noElite && random.nextDouble() < traitChance) {
            traits.addAll(pick(Trait.normal(), 1, traits));
            if (random.nextDouble() < 0.5) {
                traits.addAll(pick(Trait.normal(), 1, traits));
            }
        }
        }

        applyAttributes(entity, level, elite, traits);
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(levelKey, PersistentDataType.INTEGER, level);
        if (elite) {
            data.set(eliteKey, PersistentDataType.BYTE, (byte) 1);
        }
        if (!traits.isEmpty()) {
            data.set(traitsKey, PersistentDataType.STRING,
                    String.join(",", traits.stream().map(Trait::name).toList()));
        }
        if (traits.contains(Trait.REGENERATING)) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                    PotionEffect.INFINITE_DURATION, 0, false, false));
        }
        refreshName(entity);
        if (elite) {
            announceElite(entity);
        }
    }

    private static List<Trait> pick(List<Trait> pool, int count, List<Trait> already) {
        List<Trait> candidates = new ArrayList<>(pool);
        candidates.removeAll(already);
        List<Trait> result = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count && !candidates.isEmpty(); i++) {
            result.add(candidates.remove(random.nextInt(candidates.size())));
        }
        return result;
    }

    private void applyAttributes(LivingEntity entity, int level, boolean elite, List<Trait> traits) {
        ConfigurationSection c = cfg();
        double health = (level - 1) * c.getDouble("health-per-level", 0.05);
        if (elite) {
            health = (1 + health) * c.getDouble("elite.health-multiplier", 2.0) - 1;
        }
        if (traits.contains(Trait.TOUGH)) {
            health += 0.3;
        }
        if (traits.contains(Trait.TITAN)) {
            health += 0.6;
        }
        modify(entity, Attribute.MAX_HEALTH, "mob_health", health, AttributeModifier.Operation.ADD_SCALAR);
        if (elite) {
            modify(entity, Attribute.SCALE, "mob_scale", c.getDouble("elite.scale", 1.2) - 1,
                    AttributeModifier.Operation.ADD_SCALAR);
        }
        if (traits.contains(Trait.SWIFT)) {
            modify(entity, Attribute.MOVEMENT_SPEED, "mob_swift", 0.25, AttributeModifier.Operation.ADD_SCALAR);
        }
        if (traits.contains(Trait.ARMORED)) {
            modify(entity, Attribute.ARMOR, "mob_armor", 4, AttributeModifier.Operation.ADD_NUMBER);
        }
        if (traits.contains(Trait.TITAN)) {
            modify(entity, Attribute.KNOCKBACK_RESISTANCE, "mob_titan", 1.0, AttributeModifier.Operation.ADD_NUMBER);
        }
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            entity.setHealth(Math.min(max.getValue(), 1024));
        }
    }

    private void modify(LivingEntity entity, Attribute attribute, String id, double amount,
                        AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null || amount == 0) {
            return;
        }
        instance.addModifier(new AttributeModifier(new NamespacedKey(plugin, id), amount, operation,
                EquipmentSlotGroup.ANY));
    }

    // ── Cálculo do nível ───────────────────────────────────────

    private Calc compute(Location loc) {
        ConfigurationSection c = cfg();
        World world = loc.getWorld();
        double originX = 0;
        double originZ = 0;
        if (world.getEnvironment() == World.Environment.NORMAL) {
            Location spawn = world.getSpawnLocation();
            originX = spawn.getX();
            originZ = spawn.getZ();
        }
        double distance = Math.hypot(loc.getX() - originX, loc.getZ() - originZ);
        double level = 1 + distance / Math.max(1, c.getDouble("blocks-per-level", 300));
        switch (world.getEnvironment()) {
            case NETHER -> level += c.getInt("dimension-bonus.nether", 6);
            case THE_END -> level += c.getInt("dimension-bonus.the-end", 12);
            default -> {
                if (loc.getY() < 0) {
                    level += c.getInt("deep-bonus", 2);
                }
            }
        }
        int bonus = structureBonus(loc);
        level += bonus + ThreadLocalRandom.current().nextInt(3) - 1;
        int max = c.getInt("max-level", 60);
        return new Calc((int) Math.max(1, Math.min(max, Math.round(level))), bonus);
    }

    private int structureBonus(Location loc) {
        int best = 0;
        try {
            for (GeneratedStructure generated : loc.getChunk().getStructures()) {
                if (!inside(generated, loc)) {
                    continue;
                }
                best = Math.max(best, bonusFor(structureName(generated)));
            }
        } catch (RuntimeException ignored) {
            // chunk sem dados de estrutura: sem bônus
        }
        return best;
    }

    private static String structureName(GeneratedStructure generated) {
        NamespacedKey key = io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.STRUCTURE).getKey(generated.getStructure());
        return key == null ? "" : key.getKey();
    }

    private static boolean inside(GeneratedStructure generated, Location loc) {
        for (StructurePiece piece : generated.getPieces()) {
            BoundingBox box = piece.getBoundingBox().clone().expand(2);
            if (box.contains(loc.getX(), loc.getY(), loc.getZ())) {
                return true;
            }
        }
        return false;
    }

    private static int bonusFor(String path) {
        if (path.startsWith("village") || path.equals("ruined_portal") || path.startsWith("ruined_portal")) {
            return 0;
        }
        return switch (path) {
            case "ancient_city", "mansion" -> 14;
            case "stronghold", "monument", "end_city", "trial_chambers" -> 10;
            case "bastion_remnant" -> 9;
            case "fortress" -> 7;
            case "pillager_outpost" -> 5;
            case "mineshaft", "mineshaft_mesa", "desert_pyramid", "jungle_pyramid" -> 4;
            case "swamp_hut", "igloo", "trail_ruins", "ocean_ruin_cold", "ocean_ruin_warm", "shipwreck",
                 "shipwreck_beached" -> 3;
            default -> 2;
        };
    }

    // ── Nome ───────────────────────────────────────────────────

    public static String levelColor(int level) {
        if (level < 10) {
            return "<green>";
        } else if (level < 20) {
            return "<yellow>";
        } else if (level < 35) {
            return "<gold>";
        } else if (level < 50) {
            return "<red>";
        }
        return "<dark_purple>";
    }

    public void refreshName(LivingEntity entity) {
        int level = level(entity);
        if (level == 0 || !plugin.getConfig().getBoolean("mobs.show-name", true)) {
            return;
        }
        Component existing = entity.customName();
        if (existing != null && !isOurName(entity)) {
            return; // nome dado por jogador (name tag): respeita
        }
        boolean elite = isElite(entity);
        StringBuilder suffix = new StringBuilder();
        for (Trait trait : traits(entity)) {
            suffix.append(' ').append(trait.color()).append(trait.symbol());
        }
        if (plugin.getConfig().getBoolean("mobs.show-health", true)) {
            AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
            double maxHealth = max == null ? entity.getHealth() : max.getValue();
            suffix.append(" <red>❤ <white>").append((int) Math.ceil(Math.max(0, entity.getHealth())))
                    .append("<dark_gray>/<gray>").append((int) Math.ceil(maxHealth));
        }
        Component name = Msg.mm("<dark_gray>[" + levelColor(level) + "Lv." + level + "<dark_gray>] "
                        + (elite ? "<gold><bold>Elite</bold> " : "<white>"))
                .append(Component.translatable(entity.getType().translationKey()).color(
                        elite ? NamedTextColor.GOLD : NamedTextColor.WHITE))
                .append(Msg.mm(suffix.toString()));
        entity.customName(name);
        entity.setCustomNameVisible(false); // o texto vem do rótulo (TextDisplay com profundidade), que não atravessa paredes
        updateLabel(entity, name);
    }

    // ── Rótulo sobre a cabeça ──────────────────────────────────

    private TextDisplay findLabel(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof TextDisplay display
                    && display.getPersistentDataContainer().has(labelKey, PersistentDataType.BYTE)) {
                return display;
            }
        }
        return null;
    }

    /** Rótulo como display passageiro: ao contrário do nome vanilla, é ocultado por blocos. */
    private void updateLabel(LivingEntity entity, Component name) {
        TextDisplay label = findLabel(entity);
        if (label == null) {
            if (!entity.isValid()) {
                return;
            }
            label = entity.getWorld().spawn(entity.getLocation(), TextDisplay.class, display -> {
                display.setPersistent(true);
                display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                display.setSeeThrough(false);
                display.setShadowed(true);
                display.setDefaultBackground(false);
                display.setBackgroundColor(org.bukkit.Color.fromARGB(90, 0, 0, 0));
                display.setViewRange(0.35f);
                display.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                display.getPersistentDataContainer().set(labelKey, PersistentDataType.BYTE, (byte) 1);
            });
            entity.addPassenger(label);
        }
        label.text(name);
        float lift = (float) Math.max(0.1, entity.getHeight() - 1.7 + 0.35);
        label.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(0, lift, 0),
                new org.joml.Quaternionf(), new org.joml.Vector3f(1, 1, 1), new org.joml.Quaternionf()));
    }

    private void removeLabel(Entity entity) {
        TextDisplay label = findLabel(entity);
        if (label != null) {
            label.remove();
        }
    }

    @EventHandler
    public void onRemoved(EntityRemoveEvent event) {
        if (event.getCause() != EntityRemoveEvent.Cause.UNLOAD && level(event.getEntity()) > 0) {
            removeLabel(event.getEntity());
        }
    }

    private boolean isOurName(LivingEntity entity) {
        Component name = entity.customName();
        return name != null && Msg.plain(name).startsWith("[Lv.");
    }

    private void announceElite(LivingEntity entity) {
        Location at = entity.getLocation();
        Fx.burst(at.clone().add(0, 1, 0), Particle.END_ROD, 20, 0.5, 0.05);
        for (Player player : at.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(at) < 24 * 24) {
                player.sendActionBar(Msg.mm("<gold><bold>⚠ Um mob Elite nasceu por perto!"));
                Fx.sound(player, Sound.ENTITY_WITHER_SPAWN, 0.25f, 1.6f);
            }
        }
    }

    // ── Combate ────────────────────────────────────────────────

    private double damageMultiplier(LivingEntity attacker) {
        ConfigurationSection c = cfg();
        int level = level(attacker);
        double mult = 1 + (level - 1) * c.getDouble("damage-per-level", 0.02);
        Set<Trait> traits = traits(attacker);
        if (isElite(attacker)) {
            mult *= c.getDouble("elite.damage-multiplier", 1.3);
        }
        if (traits.contains(Trait.BRUTE)) {
            mult *= 1.25;
        }
        if (traits.contains(Trait.SANGUINE)) {
            mult *= 1.15;
        }
        if (traits.contains(Trait.FURIOUS) && lowHealth(attacker)) {
            mult *= 1.4;
        }
        return Math.min(mult, c.getDouble("max-damage-multiplier", 4.0));
    }

    private boolean lowHealth(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max != null && entity.getHealth() < max.getValue() * 0.5;
    }

    private static LivingEntity shooterOrSelf(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return damager instanceof LivingEntity living ? living : null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled()) {
            return;
        }
        LivingEntity attacker = shooterOrSelf(event.getDamager());
        Entity victim = event.getEntity();

        if (attacker != null && !(attacker instanceof Player) && level(attacker) > 0 && victim instanceof Player player) {
            mobHitsPlayer(attacker, player, event);
        }
        if (victim instanceof LivingEntity mob && !(victim instanceof Player) && level(mob) > 0) {
            playerHitsMob(mob, event);
        }
    }

    private void mobHitsPlayer(LivingEntity attacker, Player player, EntityDamageByEntityEvent event) {
        event.setDamage(event.getDamage() * damageMultiplier(attacker));
        Set<Trait> traits = traits(attacker);
        if (traits.contains(Trait.VAMPIRIC) || traits.contains(Trait.SANGUINE)) {
            double share = traits.contains(Trait.SANGUINE) ? 0.8 : 0.4;
            AttributeInstance max = attacker.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                attacker.setHealth(Math.min(max.getValue(), attacker.getHealth() + event.getDamage() * share));
                refreshLater(attacker);
            }
        }
        if (traits.contains(Trait.VENOMOUS)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0));
        }
        if (traits.contains(Trait.FROSTY)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
            player.setFreezeTicks(Math.min(player.getMaxFreezeTicks(), player.getFreezeTicks() + 60));
        }
        if (traits.contains(Trait.BLAZING)) {
            player.setFireTicks(Math.max(player.getFireTicks(), 60));
        }
        if (traits.contains(Trait.STORMCALLER) && ThreadLocalRandom.current().nextDouble() < 0.3) {
            Location at = player.getLocation();
            at.getWorld().strikeLightningEffect(at);
            Bukkit.getScheduler().runTask(plugin, () -> player.damage(4.0));
        }
    }

    private void playerHitsMob(LivingEntity mob, EntityDamageByEntityEvent event) {
        Set<Trait> traits = traits(mob);
        if (traits.contains(Trait.WARDEN)) {
            event.setDamage(event.getDamage() * 0.75);
        }
        Player hitter = null;
        if (event.getDamager() instanceof Player direct) {
            hitter = direct;
            if (traits.contains(Trait.THORNY)) {
                double reflected = event.getFinalDamage() * 0.2;
                Bukkit.getScheduler().runTask(plugin, () -> direct.damage(Math.max(0.5, reflected)));
            }
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            hitter = shooter;
        }
        if (traits.contains(Trait.BLINKER) && hitter != null && ThreadLocalRandom.current().nextDouble() < 0.25) {
            Location target = hitter.getLocation().clone();
            target.add(hitter.getLocation().getDirection().multiply(-2.5));
            target.setY(hitter.getLocation().getY());
            Location from = mob.getLocation();
            Fx.burst(from.clone().add(0, 1, 0), Particle.PORTAL, 20, 0.4, 0.2);
            mob.teleportAsync(target);
            Fx.worldSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);
        }
        if (traits.contains(Trait.FURIOUS) && lowHealth(mob)) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, false, false));
        }
        refreshLater(mob);
        if (hitter != null) {
            showTarget(hitter, mob, event.getFinalDamage());
        }
    }

    private void refreshLater(LivingEntity mob) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (mob.isValid()) {
                refreshName(mob);
            }
        });
    }

    /** Barra de chefe para elites e linha de ação com o resumo do alvo. */
    private void showTarget(Player player, LivingEntity mob, double damage) {
        int level = level(mob);
        Set<Trait> traits = traits(mob);
        StringBuilder text = new StringBuilder("<gray>Lv." + level + " ");
        if (!traits.isEmpty()) {
            text.append(String.join("<gray>, ", traits.stream().map(Trait::colored).toList()));
        }
        player.sendActionBar(Msg.mm(text.toString()));
        if (!isElite(mob)) {
            return;
        }
        long now = Bukkit.getCurrentTick();
        Tracker tracker = bars.get(player.getUniqueId());
        if (tracker == null || !tracker.mob.equals(mob.getUniqueId())) {
            if (tracker != null) {
                player.hideBossBar(tracker.bar);
            }
            BossBar bar = BossBar.bossBar(barTitle(mob), 1f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
            tracker = new Tracker(bar, mob.getUniqueId(), now);
            bars.put(player.getUniqueId(), tracker);
            player.showBossBar(bar);
        }
        tracker.lastHit = now;
        AttributeInstance max = mob.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max == null ? 1 : max.getValue();
        float progress = (float) Math.max(0, Math.min(1, (mob.getHealth() - damage) / maxHealth));
        tracker.bar.progress(progress);
        tracker.bar.name(barTitle(mob));
    }

    private Component barTitle(LivingEntity mob) {
        return Msg.mm("<gold><bold>Elite</bold> <dark_gray>[" + levelColor(level(mob)) + "Lv." + level(mob)
                        + "<dark_gray>] <white>")
                .append(Component.translatable(mob.getType().translationKey()));
    }

    private void tickAll() {
        tick++;
        long now = Bukkit.getCurrentTick();
        Iterator<Map.Entry<UUID, Tracker>> it = bars.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Tracker> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            Tracker tracker = entry.getValue();
            if (player == null || now - tracker.lastHit > 160 || Bukkit.getEntity(tracker.mob) == null) {
                if (player != null) {
                    player.hideBossBar(tracker.bar);
                }
                it.remove();
            }
        }
    }

    @EventHandler
    public void onDeathBars(EntityDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        bars.entrySet().removeIf(entry -> {
            if (entry.getValue().mob.equals(id)) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    player.hideBossBar(entry.getValue().bar);
                }
                return true;
            }
            return false;
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();
        int level = level(mob);
        if (level == 0 || mob instanceof Player) {
            return;
        }
        ConfigurationSection c = cfg();
        boolean elite = isElite(mob);
        double xp = 1 + (level - 1) * c.getDouble("xp-per-level", 0.04);
        if (elite) {
            xp *= c.getDouble("elite.xp-multiplier", 3.0);
        }
        event.setDroppedExp((int) Math.round(event.getDroppedExp() * xp));

        Location at = mob.getLocation();
        if (traits(mob).contains(Trait.VOLATILE)) {
            at.getWorld().createExplosion(at, 1.6f, false, false);
        }
        if (elite) {
            dropEliteLoot(event, level);
        }
        Player killer = mob.getKiller();
        if (killer != null && plugin.skills() != null) {
            plugin.skills().onMobKilled(killer, mob, level, elite);
        }
    }

    private void dropEliteLoot(EntityDeathEvent event, int level) {
        ConfigurationSection c = cfg();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<ItemStack> drops = event.getDrops();
        if (random.nextDouble() < c.getDouble("elite.essence-chance", 0.6)) {
            drops.add(createEssence(1 + random.nextInt(2) + level / 25));
        }
        drops.add(new ItemStack(Material.GOLD_NUGGET, 4 + random.nextInt(8)));
        if (random.nextDouble() < 0.5) {
            drops.add(new ItemStack(Material.EMERALD, 1 + random.nextInt(3)));
        }
        if (level >= 15 && random.nextDouble() < 0.25) {
            drops.add(new ItemStack(Material.DIAMOND, 1));
        }
        if (level >= 30 && random.nextDouble() < 0.1) {
            drops.add(new ItemStack(Material.NETHERITE_SCRAP, 1));
        }
        if (random.nextDouble() < 0.15) {
            drops.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
        }
        Fx.burst(event.getEntity().getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 40, 0.6, 0.3);
        Fx.worldSound(event.getEntity().getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobHurtScale(EntityDamageEvent event) {
        // garante o nome atualizado também para dano ambiental (fogo, queda etc.)
        if (event.getEntity() instanceof LivingEntity mob && !(mob instanceof Player) && level(mob) > 0
                && !(event instanceof EntityDamageByEntityEvent)) {
            refreshLater(mob);
        }
    }

    // ── Essência Elite ─────────────────────────────────────────

    public ItemStack createEssence(int amount) {
        ItemStack item = Items.of(Material.ECHO_SHARD, "<gradient:#c4a7ff:#2cb67d><bold>Essência Elite",
                List.of("<gray>Condensada do poder de um mob Elite.",
                        "<gray>Usada nos upgrades mais altos de",
                        "<gray>fornalhas e baús."), true);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(essenceKey, PersistentDataType.BYTE, (byte) 1));
        item.setAmount(Math.max(1, Math.min(amount, 64)));
        return item;
    }

    public boolean isEssence(ItemStack item) {
        return item != null && item.getType() == Material.ECHO_SHARD && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(essenceKey, PersistentDataType.BYTE);
    }
}
