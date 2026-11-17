package dev.vertexx.boss;

import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Parched;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Parched Titan, o colosso do Deserto (360 de vida) da Cidadela Soterrada. Fase 1 (100% a 50%): postura calculista com
 * Guarda de Arenito (bloqueia projéteis frontais, corta 80% do dano de frente; machado ou golpe pelas costas quebra a guarda
 * e atordoa por 1,5 s) e os ataques Contra-Ataque de Escudo, Corte de Onda Sísmica e Prisão de Areia Movediça. Abaixo de 50%
 * ruge, perde a guarda, anda 35% mais rápido, ataca com metade das esperas e ganha Salto Sísmico, Investida Implacável e
 * Vórtice de Tempestade. Enquanto dorme, a fita antes da sala do chefe também vive aqui: dardos e rajadas no átrio e a onda
 * de guardiões mumificados (6 Husks e 2 Parched) que mantém o portão fechado.
 */
final class ParchedTitan extends BossFight {

    private enum Move { NONE, PARRY, CLEAVE, QUICKSAND, LEAP, RUSH, SPIN, ROAR }

    private static final int[] COOLDOWN = {0, 120, 160, 240, 180, 140, 260, 0};
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 196, 60), 1.6f);
    private static final Particle.DustOptions EMBER = new Particle.DustOptions(Color.fromRGB(255, 110, 20), 1.5f);
    private static final Material[] PILLAR_BLOCKS = {Material.CUT_SANDSTONE, Material.CHISELED_SANDSTONE,
            Material.SMOOTH_SANDSTONE};

    private record Trail(Location at, int[] ttl) {
    }

    private static final class Cyclone {
        final SandTornado tornado;
        final double phase;
        final double dir;
        int age;

        Cyclone(SandTornado tornado, double phase, double dir) {
            this.tornado = tornado;
            this.phase = phase;
            this.dir = dir;
        }
    }

    private Move move = Move.NONE;
    private Move last = Move.NONE;
    private int moveT;
    private int idleTicks = 40;
    private final int[] cd = new int[8];
    private boolean walking;

    private boolean guarding;
    private int guardT;
    private int guardCd = 80;
    private int stun;
    private int parryHits;
    private int parryWindow;

    private Vector dash = new Vector(0, 0, 1);
    private double dashed;
    private final Set<UUID> struck = new HashSet<>();
    private Location pitCenter;
    private int pitTicks;
    private BlockDisplay pitDisplay;
    private Location leapFrom;
    private Location leapTo;
    private final List<Trail> trails = new ArrayList<>();
    private final List<Cyclone> cyclones = new ArrayList<>();

    private final List<Mob> guardians = new ArrayList<>();
    private boolean guardiansSpawned;
    private boolean gateOpened;
    private int dartCd = 60;
    private int gustCd = 120;

    ParchedTitan(BossManager manager, Arena arena) {
        super(manager, arena);
    }

    // ── Parâmetros ─────────────────────────────────────────────

    @Override
    protected double startHeight() {
        return 0;
    }

    @Override
    protected double baseHealth() {
        return plugin.getConfig().getDouble("bosses.parched-titan.health", 360);
    }

    @Override
    protected double hitboxScale() {
        return 2.4;
    }

    @Override
    protected double bodyRadius() {
        return 1.7;
    }

    @Override
    protected double bodyHeight() {
        return 6.5;
    }

    @Override
    protected String modelId() {
        return "parched_titan";
    }

    @Override
    protected String title() {
        return "O Colosso Ressecado";
    }

    @Override
    protected String subtitle() {
        return "<gold>O Colosso Ressecado desperta";
    }

    @Override
    protected int wakeDuration() {
        return 60;
    }

    @Override
    List<String> moves() {
        return List.of("parry", "cleave", "quicksand", "leap", "rush", "spin");
    }

    @Override
    protected void buildRig() {
        // o corpo vem todo do modelo; só a poça de areia movediça usa uma peça extra, criada na hora
    }

    // ── Geometria local da cidadela ────────────────────────────

    /** Coordenada lateral (lx) e de profundidade (lz) de um ponto, no referencial da estrutura (entrada em +lz). */
    private double lx(Location at) {
        return (at.getX() - (arena.cx + 0.5)) * arena.fz - (at.getZ() - (arena.cz + 0.5)) * arena.fx;
    }

    private double lz(Location at) {
        return (at.getX() - (arena.cx + 0.5)) * arena.fx + (at.getZ() - (arena.cz + 0.5)) * arena.fz;
    }

    private Location local(double lx, double y, double lz) {
        return new Location(world, arena.cx + 0.5 + arena.fx * lz + arena.fz * lx, y,
                arena.cz + 0.5 + arena.fz * lz - arena.fx * lx);
    }

    private Location at(double forward, double y) {
        double rad = Math.toRadians(yaw);
        return pos.clone().add(-Math.sin(rad) * forward, y, Math.cos(rad) * forward);
    }

    private Vector facing() {
        double rad = Math.toRadians(yaw);
        return new Vector(-Math.sin(rad), 0, Math.cos(rad));
    }

    private double speed() {
        return enraged ? 1.35 : 1.0;
    }

    // ── Antes do chefe: átrio, guardiões e portão ──────────────

    @Override
    protected void dormantTick() {
        if (age % 10 != 0) {
            return;
        }
        List<Player> atrium = new ArrayList<>();
        boolean inHall = false;
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL && player.getGameMode() != org.bukkit.GameMode.ADVENTURE) {
                continue;
            }
            Location at = player.getLocation();
            if (Math.abs(at.getY() - arena.fy) > 12) {
                continue;
            }
            double x = lx(at);
            double z = lz(at);
            if (z >= 46 && z <= 72 && Math.abs(x) <= 3.6) {
                atrium.add(player);
            }
            if (z >= 23 && z <= 45 && Math.abs(x) <= 8) {
                inHall = true;
            }
        }
        if (!atrium.isEmpty()) {
            atriumTraps(atrium);
        }
        if (inHall && !guardiansSpawned) {
            spawnGuardians();
        }
        if (guardiansSpawned && !gateOpened) {
            guardians.removeIf(mob -> !mob.isValid());
            if (guardians.isEmpty()) {
                gateOpened = true;
                manager.setGate(arena, false);
                for (Player player : world.getPlayers()) {
                    if (arena.distanceSquared(player.getLocation()) < 70 * 70) {
                        Msg.actionBar(player, "<gold>Os guardiões caíram: o portão de pedra se abre.");
                    }
                }
                Fx.worldSound(local(0, arena.fy + 2, 22), Sound.BLOCK_PISTON_EXTEND, 2f, 0.5f);
                Fx.worldSound(local(0, arena.fy + 2, 22), Sound.BLOCK_STONE_BREAK, 2f, 0.6f);
            }
        }
    }

    /** Dardos de dispensers escondidos nos relevos e rajadas de areia que empurram para os fossos. */
    private void atriumTraps(List<Player> players) {
        dartCd -= 10;
        gustCd -= 10;
        if (dartCd <= 0) {
            dartCd = 40 + ThreadLocalRandom.current().nextInt(30);
            Player victim = players.get(ThreadLocalRandom.current().nextInt(players.size()));
            double side = ThreadLocalRandom.current().nextBoolean() ? 3.6 : -3.6;
            Location from = local(side, arena.fy + 1.6, lz(victim.getLocation()) + (ThreadLocalRandom.current().nextDouble() - 0.5) * 6);
            Vector aim = victim.getLocation().add(0, 1.2, 0).toVector().subtract(from.toVector()).normalize().multiply(1.5);
            Fx.worldSound(from, Sound.BLOCK_DISPENSER_LAUNCH, 1.2f, 1.2f);
            Arrow arrow = world.spawn(from, Arrow.class, a -> {
                a.setVelocity(aim);
                a.setPersistent(false);
                a.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
                a.addCustomEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 0), true);
                a.getPersistentDataContainer().set(manager.projectileKey(), PersistentDataType.DOUBLE, 3.0);
                a.getPersistentDataContainer().set(manager.partKey(), PersistentDataType.BYTE, (byte) 1);
            });
            track(arrow);
        }
        if (gustCd <= 0) {
            gustCd = 90 + ThreadLocalRandom.current().nextInt(60);
            double side = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
            for (Player player : players) {
                Vector push = new Vector(arena.fz * side, 0, -arena.fx * side).multiply(0.85).setY(0.2);
                player.setVelocity(player.getVelocity().add(push));
                world.spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 18, 0.4, 0.5, 0.4, 0.2);
                world.spawnParticle(Particle.FALLING_DUST, player.getLocation().add(0, 1, 0), 24, 1.0, 0.6, 1.0, 0.1,
                        Material.SAND.createBlockData());
            }
            Fx.worldSound(players.get(0).getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.4f, 0.6f);
            Msg.actionBar(players.get(0), "<gold>Uma rajada de areia varre o corredor!");
        }
    }

    /** 6 Husks blindados no chão e 2 Parched nas varandas, aparecem quando alguém entra no salão. */
    private void spawnGuardians() {
        guardiansSpawned = true;
        double[][] husks = {{-4, 30}, {0, 33}, {4, 30}, {-5, 38}, {0, 40}, {5, 38}};
        for (double[] spot : husks) {
            Location at = local(spot[0], arena.fy, spot[1]);
            Husk husk = world.spawn(at, Husk.class, h -> setupGuardian(h, 30, 5));
            husk.getEquipment().setHelmet(new ItemStack(Material.COPPER_HELMET));
            husk.getEquipment().setChestplate(new ItemStack(Material.COPPER_CHESTPLATE));
            husk.getEquipment().setLeggings(new ItemStack(Material.GOLDEN_LEGGINGS));
            husk.getEquipment().setBoots(new ItemStack(Material.COPPER_BOOTS));
            husk.getEquipment().setHelmetDropChance(0f);
            husk.getEquipment().setChestplateDropChance(0f);
            husk.getEquipment().setLeggingsDropChance(0f);
            husk.getEquipment().setBootsDropChance(0f);
            AttributeInstance knock = husk.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (knock != null) {
                knock.setBaseValue(0.7);
            }
            guardians.add(husk);
            track(husk);
        }
        double[][] archers = {{-6.5, 29}, {6.5, 37}};
        for (double[] spot : archers) {
            Location at = local(spot[0], arena.fy + 5, spot[1]);
            Parched parched = world.spawn(at, Parched.class, p -> setupGuardian(p, 24, 3));
            parched.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
            parched.getEquipment().setItemInMainHandDropChance(0f);
            parched.getPersistentDataContainer().set(manager.partKey(), PersistentDataType.BYTE, (byte) 2);
            guardians.add(parched);
            track(parched);
        }
        for (Player player : world.getPlayers()) {
            if (arena.distanceSquared(player.getLocation()) < 70 * 70) {
                Msg.actionBar(player, "<gold>Os Guardiões Mumificados despertam!");
            }
        }
        Fx.worldSound(local(0, arena.fy + 2, 34), Sound.ENTITY_HUSK_AMBIENT, 2f, 0.5f);
    }

    private void setupGuardian(Mob mob, double health, double damage) {
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);
        mob.setCanPickupItems(false);
        mob.getPersistentDataContainer().set(manager.partKey(), PersistentDataType.BYTE, (byte) 1);
        mob.customName(Msg.mm("<gold>Guardião Mumificado"));
        mob.setCustomNameVisible(false);
        AttributeInstance max = mob.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(health);
        }
        mob.setHealth(health);
        AttributeInstance dmg = mob.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(damage);
        }
        AttributeInstance follow = mob.getAttribute(Attribute.FOLLOW_RANGE);
        if (follow != null) {
            follow.setBaseValue(40);
        }
    }

    // ── Dano e guarda ──────────────────────────────────────────

    private boolean frontal(Location from) {
        double dx = from.getX() - pos.getX();
        double dz = from.getZ() - pos.getZ();
        double length = Math.hypot(dx, dz);
        if (length < 1.0E-4) {
            return true;
        }
        Vector f = facing();
        return (dx * f.getX() + dz * f.getZ()) / length > 0.4;
    }

    @Override
    boolean blocksProjectile(Projectile projectile) {
        if (guarding && frontal(projectile.getLocation())) {
            world.spawnParticle(Particle.CRIT, projectile.getLocation(), 8, 0.2, 0.2, 0.2, 0.4);
            Fx.worldSound(projectile.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.2f, 0.7f);
            return true;
        }
        return false;
    }

    @Override
    double damageTaken(EntityDamageEvent event, double base) {
        hurtTicks = 6;
        if (!guarding || !(event instanceof EntityDamageByEntityEvent byEntity)) {
            return base;
        }
        Entity damager = byEntity.getDamager();
        Player player = damager instanceof Player p ? p
                : damager instanceof Projectile pr && pr.getShooter() instanceof Player s ? s : null;
        boolean axe = player != null && Tag.ITEMS_AXES.isTagged(player.getInventory().getItemInMainHand().getType());
        Location from = player != null ? player.getLocation() : damager.getLocation();
        if (axe || !frontal(from)) {
            breakGuard();
            return base;
        }
        parryHits++;
        parryWindow = 40;
        world.spawnParticle(Particle.CRIT, at(2.5, 3), 8, 0.8, 0.8, 0.8, 0.3);
        Fx.worldSound(at(2.5, 3), Sound.ITEM_SHIELD_BLOCK, 1.4f, 0.6f);
        return base * 0.2;
    }

    @Override
    double damageTaken(double base) {
        hurtTicks = 6;
        return base;
    }

    private void breakGuard() {
        guarding = false;
        stun = 30;
        parryHits = 0;
        endMove();
        Fx.worldSound(at(2, 3), Sound.ITEM_SHIELD_BREAK, 1.6f, 0.6f);
        world.spawnParticle(Particle.BLOCK, at(2, 3), 40, 1, 1.2, 1, 0.2, Material.SANDSTONE.createBlockData());
        warn("<gold><bold>Guarda quebrada!</bold> <gray>O Titã está atordoado: ataque!");
        model.restart("stagger");
    }

    // ── Despertar, fúria e morte ───────────────────────────────

    @Override
    protected void idlePose() {
        pos.setY(arena.fy);
        model.play("dormant");
    }

    @Override
    protected void wakeTick(int t) {
        if (t == 1) {
            model.restart("roar");
            Fx.worldSound(pos, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.4f);
            Fx.worldSound(pos, Sound.ENTITY_WARDEN_EMERGE, 1.5f, 0.7f);
        }
        if (t % 5 == 0) {
            world.spawnParticle(Particle.BLOCK, at(0, 1), 30, 3, 1.5, 3, 0.2, Material.SAND.createBlockData());
        }
        if (t == 30) {
            Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.5f);
            world.spawnParticle(Particle.EXPLOSION, at(0, 3), 4, 2, 1, 2, 0);
        }
    }

    @Override
    protected void onEnrage() {
        guarding = false;
        stun = 0;
        endMove();
        startMove(Move.ROAR);
        warn("<gold><bold>O Titã estilhaça a armadura de pedra!</bold> <gray>Fúria do Deserto.");
        for (int i = 0; i < cd.length; i++) {
            cd[i] = Math.min(cd[i], 40);
        }
    }

    @Override
    protected void deathStart() {
        endMove();
        clearHazards();
        Fx.worldSound(pos, Sound.ENTITY_RAVAGER_DEATH, 2f, 0.4f);
    }

    @Override
    protected void deathAnimate(int t) {
        model.play("stagger");
        if (t % 3 == 0) {
            world.spawnParticle(Particle.BLOCK, at(0, 3.5), 50, 2, 2.4, 2, 0.3, Material.SANDSTONE.createBlockData());
            world.spawnParticle(Particle.LAVA, at(0, 3.5), 6, 1.2, 1.2, 1.2, 0);
        }
        if (t == 48) {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, at(0, 3), 2, 1, 1, 1, 0);
            Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
        }
    }

    @Override
    protected void cleanup() {
        clearHazards();
        for (Mob guardian : guardians) {
            guardian.remove();
        }
        guardians.clear();
    }

    private void clearHazards() {
        for (Cyclone cyclone : cyclones) {
            cyclone.tornado.remove();
        }
        cyclones.clear();
        trails.clear();
        pitTicks = 0;
        if (pitDisplay != null) {
            pitDisplay.remove();
            pitDisplay = null;
        }
    }

    // ── Animação ───────────────────────────────────────────────

    @Override
    protected void animate() {
        if (state == State.DYING || state == State.DORMANT || state == State.WAKING) {
            return;
        }
        String clip;
        if (stun > 0) {
            clip = "stagger";
        } else if (guarding) {
            clip = "guard";
        } else {
            clip = switch (move) {
                case PARRY -> "parry";
                case CLEAVE -> "cleave";
                case QUICKSAND -> "quicksand";
                case LEAP -> "leap";
                case RUSH -> moveT < 18 ? "idle" : "rush";
                case SPIN -> "spin";
                case ROAR -> "roar";
                default -> walking ? "walk" : "idle";
            };
        }
        model.play(clip);
    }

    // ── IA ─────────────────────────────────────────────────────

    @Override
    protected void aiTick() {
        for (int i = 1; i < cd.length; i++) {
            if (cd[i] > 0) {
                cd[i]--;
            }
        }
        if (guardCd > 0) {
            guardCd--;
        }
        if (parryWindow > 0 && --parryWindow == 0) {
            parryHits = 0;
        }
        tickPit();
        tickCyclones();
        tickTrails();
        if (stun > 0) {
            stun--;
            walking = false;
            if (stun % 8 == 0) {
                world.spawnParticle(Particle.CRIT, at(0, 7), 6, 0.8, 0.3, 0.8, 0.1);
            }
            return;
        }
        if (guarding) {
            guardT--;
            walking = false;
            faceTowards(aim(), 5);
            if (parryHits >= 2 && cd[Move.PARRY.ordinal()] == 0) {
                guarding = false;
                parryHits = 0;
                startMove(Move.PARRY);
                return;
            }
            if (guardT <= 0) {
                guarding = false;
                parryHits = 0;
                guardCd = 60;
                idleTicks = 20;
            }
            return;
        }
        if (move == Move.NONE) {
            idle();
            return;
        }
        moveT++;
        switch (move) {
            case PARRY -> parry();
            case CLEAVE -> cleave();
            case QUICKSAND -> quicksand();
            case LEAP -> leap();
            case RUSH -> rush();
            case SPIN -> spin();
            case ROAR -> roar();
            default -> { }
        }
    }

    private void idle() {
        Location at = aim();
        faceTowards(at, 6 * (float) speed());
        double dx = at.getX() - pos.getX();
        double dz = at.getZ() - pos.getZ();
        double distance = Math.hypot(dx, dz);
        walking = false;
        double step = 0.16 * speed();
        if (target != null && distance > (enraged ? 6 : 10)) {
            moveToward(at.getX(), arena.fy, at.getZ(), step);
            walking = true;
        } else if (target != null && !enraged && distance < 5) {
            moveToward(pos.getX() - dx, arena.fy, pos.getZ() - dz, step * 0.7);
            walking = true;
        }
        if (--idleTicks <= 0 && target != null) {
            if (!enraged && guardCd == 0 && ThreadLocalRandom.current().nextDouble() < 0.35) {
                startGuard();
            } else {
                pickMove(distance);
            }
        }
    }

    private void startGuard() {
        guarding = true;
        guardT = 50;
        parryHits = 0;
        model.restart("guard");
        Fx.worldSound(pos, Sound.ITEM_ARMOR_EQUIP_IRON, 1.5f, 0.5f);
    }

    private void pickMove(double distance) {
        double[] w = new double[8];
        w[Move.PARRY.ordinal()] = cd[1] == 0 && distance <= 5.5 ? 4 : 0;
        w[Move.CLEAVE.ordinal()] = cd[2] == 0 && distance <= 13 && distance > 2.5 ? (enraged ? 1.5 : 3) : 0;
        w[Move.QUICKSAND.ordinal()] = cd[3] == 0 && distance <= 18 ? (enraged ? 1 : 2.5) : 0;
        w[Move.LEAP.ordinal()] = enraged && cd[4] == 0 && distance >= 5 ? 4 : 0;
        w[Move.RUSH.ordinal()] = enraged && cd[5] == 0 && distance >= 5 && distance <= 18 ? 3 : 0;
        w[Move.SPIN.ordinal()] = enraged && cd[6] == 0 ? 2.5 : 0;
        w[last.ordinal()] *= 0.3;
        double total = 0;
        for (double v : w) {
            total += v;
        }
        if (total <= 0) {
            idleTicks = 16;
            return;
        }
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        for (int i = 1; i < w.length; i++) {
            roll -= w[i];
            if (roll <= 0) {
                startMove(Move.values()[i]);
                return;
            }
        }
    }

    private void startMove(Move next) {
        move = next;
        last = next == Move.ROAR ? last : next;
        moveT = 0;
        struck.clear();
        dashed = 0;
        walking = false;
        switch (next) {
            case PARRY -> model.restart("parry");
            case CLEAVE -> model.restart("cleave");
            case QUICKSAND -> model.restart("quicksand");
            case LEAP -> model.restart("leap");
            case RUSH -> model.restart("idle");
            case SPIN -> model.restart("spin");
            case ROAR -> model.restart("roar");
            default -> { }
        }
    }

    private void endMove() {
        if (move != Move.NONE) {
            cd[move.ordinal()] = (int) (COOLDOWN[move.ordinal()] * (enraged ? 0.5 : 1.0));
        }
        move = Move.NONE;
        idleTicks = enraged ? 18 : 40;
    }

    @Override
    boolean forceMove(String name) {
        if (!active() || move != Move.NONE || stun > 0) {
            return false;
        }
        guarding = false;
        for (Move candidate : Move.values()) {
            if (candidate != Move.NONE && candidate != Move.ROAR && candidate.name().equalsIgnoreCase(name)) {
                startMove(candidate);
                return true;
            }
        }
        return false;
    }

    // ── Utilidades ─────────────────────────────────────────────

    private void shake(Location at, int blocks) {
        world.spawnParticle(Particle.BLOCK, at, blocks, 1.4, 0.3, 1.4, 0.2, Material.SANDSTONE.createBlockData());
        world.spawnParticle(Particle.CLOUD, at, 8, 1.2, 0.2, 1.2, 0.05);
    }

    // ── 1. Contra-Ataque de Escudo ─────────────────────────────

    private void parry() {
        if (moveT < 9) {
            faceTowards(aim(), 14);
        }
        if (moveT == 10) {
            Location fist = at(3.6, 0.3);
            Fx.worldSound(fist, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.8f, 0.4f);
            Fx.worldSound(fist, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.7f);
            shake(fist, 50);
            Vector f = facing();
            for (Player player : insidePlayers()) {
                Location at = player.getLocation();
                double dx = at.getX() - pos.getX();
                double dz = at.getZ() - pos.getZ();
                double distance = Math.hypot(dx, dz);
                if (distance <= 5.5 && (distance < 1 || (dx * f.getX() + dz * f.getZ()) / distance > 0.2)) {
                    damage(player, 10);
                    Vector away = distance < 0.1 ? f.clone() : new Vector(dx, 0, dz).normalize();
                    player.setVelocity(away.multiply(1.6).setY(0.55));
                }
            }
        }
        if (moveT >= 18) {
            endMove();
        }
    }

    // ── 2. Corte de Onda Sísmica ───────────────────────────────

    private void cleave() {
        if (moveT < 22) {
            faceTowards(aim(), 10);
            if (moveT % 3 == 0) {
                Vector f = facing();
                for (int i = 2; i <= 12; i += 2) {
                    world.spawnParticle(Particle.DUST, pos.getX() + f.getX() * i, pos.getY() + 0.3, pos.getZ() + f.getZ() * i,
                            2, 0.2, 0.05, 0.2, 0, EMBER);
                }
            }
            if (moveT == 1) {
                Fx.worldSound(pos, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.7f);
            }
            if (moveT == 21) {
                dash = facing();
            }
            return;
        }
        int step = moveT - 22;
        if (step == 0) {
            Fx.worldSound(at(3, 1), Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.6f);
            Fx.worldSound(at(3, 1), Sound.BLOCK_GRAVEL_BREAK, 2f, 0.5f);
        }
        if (step <= 11) {
            double d = 1.5 + step;
            Location spot = pos.clone().add(dash.getX() * d, 0.4, dash.getZ() * d);
            world.spawnParticle(Particle.BLOCK, spot, 26, 0.5, 0.5, 0.5, 0.3, Material.SAND.createBlockData());
            world.spawnParticle(Particle.BLOCK, spot, 10, 0.4, 0.2, 0.4, 0.4, Material.SANDSTONE.createBlockData());
            world.spawnParticle(Particle.CLOUD, spot, 3, 0.4, 0.1, 0.4, 0.05);
            for (Player player : insidePlayers()) {
                if (struck.contains(player.getUniqueId())) {
                    continue;
                }
                Location at = player.getLocation();
                if (Math.hypot(at.getX() - spot.getX(), at.getZ() - spot.getZ()) < 1.7 && Math.abs(at.getY() - spot.getY()) < 3) {
                    struck.add(player.getUniqueId());
                    damage(player, 12);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                    player.setVelocity(player.getVelocity().add(new Vector(0, 0.5, 0)));
                }
            }
        }
        if (moveT >= 46) {
            endMove();
        }
    }

    // ── 3. Prisão de Areia Movediça ────────────────────────────

    private void quicksand() {
        if (moveT == 1) {
            Location at = aim();
            double dx = at.getX() - pos.getX();
            double dz = at.getZ() - pos.getZ();
            double d = Math.hypot(dx, dz);
            if (d > 18) {
                at = new Location(world, pos.getX() + dx / d * 18, arena.fy, pos.getZ() + dz / d * 18);
            }
            pitCenter = new Location(world, at.getX(), arena.fy, at.getZ());
            Fx.worldSound(pos, Sound.ITEM_TRIDENT_HIT_GROUND, 1.8f, 0.5f);
        }
        faceTowards(pitCenter, 12);
        if (moveT < 26) {
            if (moveT % 2 == 0) {
                for (int i = 0; i < 28; i++) {
                    double a = Math.PI * 2 * i / 28;
                    world.spawnParticle(Particle.DUST, pitCenter.getX() + Math.cos(a) * 3, pitCenter.getY() + 0.15,
                            pitCenter.getZ() + Math.sin(a) * 3, 1, 0, 0, 0, 0, GOLD);
                }
                world.spawnParticle(Particle.END_ROD, pitCenter.clone().add(0, 0.2, 0), 2, 1.2, 0.1, 1.2, 0.01);
            }
            return;
        }
        if (moveT == 26) {
            pitTicks = 100;
            Fx.worldSound(pitCenter, Sound.BLOCK_SAND_BREAK, 2f, 0.4f);
            Fx.worldSound(pitCenter, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
            world.spawnParticle(Particle.BLOCK, pitCenter, 60, 2.4, 0.2, 2.4, 0.2, Material.SAND.createBlockData());
            pitDisplay = world.spawn(pitCenter.clone().add(0, 0.02, 0), BlockDisplay.class, d -> {
                d.setBlock(Material.SAND.createBlockData());
                d.setPersistent(false);
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                d.setTransformation(new Transformation(new Vector3f(-3, -0.05f, -3), new Quaternionf(),
                        new Vector3f(6, 0.07f, 6), new Quaternionf()));
                d.getPersistentDataContainer().set(manager.partKey(), PersistentDataType.BYTE, (byte) 1);
            });
            track(pitDisplay);
            for (Player player : insidePlayers()) {
                if (inPit(player)) {
                    damage(player, 4);
                }
            }
        }
        if (moveT >= 40) {
            endMove();
        }
    }

    private boolean inPit(Player player) {
        return pitCenter != null && Math.hypot(player.getLocation().getX() - pitCenter.getX(),
                player.getLocation().getZ() - pitCenter.getZ()) <= 3.0 && player.getLocation().getY() - pitCenter.getY() < 2.5;
    }

    private void tickPit() {
        if (pitTicks <= 0) {
            return;
        }
        pitTicks--;
        if (pitTicks % 6 == 0) {
            world.spawnParticle(Particle.FALLING_DUST, pitCenter.clone().add(0, 0.3, 0), 6, 2.2, 0.1, 2.2, 0,
                    Material.SAND.createBlockData());
        }
        for (Player player : insidePlayers()) {
            if (inPit(player)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 14, 4, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 14, 200, false, false));
                if (pitTicks % 20 == 0) {
                    Msg.actionBar(player, "<gold>Areia movediça! <gray>Saia do círculo, ela te prende.");
                }
            }
        }
        if (pitTicks == 0 && pitDisplay != null) {
            pitDisplay.remove();
            pitDisplay = null;
            world.spawnParticle(Particle.CLOUD, pitCenter.clone().add(0, 0.3, 0), 30, 2, 0.2, 2, 0.05);
        }
    }

    // ── 4. Salto Sísmico ───────────────────────────────────────

    private void leap() {
        if (moveT < 18) {
            faceTowards(aim(), 14);
            if (moveT % 4 == 0) {
                world.spawnParticle(Particle.BLOCK, at(0, 0.3), 20, 1.6, 0.1, 1.6, 0.1, Material.SAND.createBlockData());
            }
            if (moveT == 17) {
                leapFrom = pos.clone();
                Location t = aim();
                double dx = t.getX() - (arena.cx + 0.5);
                double dz = t.getZ() - (arena.cz + 0.5);
                double d = Math.hypot(dx, dz);
                double limit = arena.half - 2.8;
                leapTo = d > limit ? new Location(world, arena.cx + 0.5 + dx / d * limit, arena.fy, arena.cz + 0.5 + dz / d * limit)
                        : new Location(world, t.getX(), arena.fy, t.getZ());
                Fx.worldSound(pos, Sound.ENTITY_RAVAGER_ROAR, 1.6f, 0.6f);
            }
            return;
        }
        int t = moveT - 18;
        if (t <= 22 && leapTo != null) {
            double k = t / 22.0;
            pos.setX(leapFrom.getX() + (leapTo.getX() - leapFrom.getX()) * k);
            pos.setZ(leapFrom.getZ() + (leapTo.getZ() - leapFrom.getZ()) * k);
            pos.setY(arena.fy + 6.0 * Math.sin(Math.PI * k));
            faceTowards(leapTo, 20);
            if (t == 1) {
                Fx.worldSound(pos, Sound.ENTITY_IRON_GOLEM_STEP, 2f, 0.4f);
                shake(at(0, 0.2), 40);
            }
            if (t == 22) {
                pos.setY(arena.fy);
                land();
            }
        }
        if (moveT >= 18 + 22 + 12) {
            endMove();
        }
    }

    private void land() {
        Location impact = pos.clone();
        Fx.worldSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
        Fx.worldSound(impact, Sound.ENTITY_IRON_GOLEM_DAMAGE, 2f, 0.4f);
        world.spawnParticle(Particle.EXPLOSION, impact.clone().add(0, 0.5, 0), 6, 2, 0.3, 2, 0);
        for (double r = 1; r <= 7; r += 1.5) {
            for (int i = 0; i < r * 6; i++) {
                double a = Math.PI * 2 * i / (r * 6);
                world.spawnParticle(Particle.BLOCK, impact.getX() + Math.cos(a) * r, impact.getY() + 0.2,
                        impact.getZ() + Math.sin(a) * r, 2, 0.1, 0.1, 0.1, 0.1, Material.SANDSTONE.createBlockData());
            }
        }
        for (Player player : insidePlayers()) {
            Location at = player.getLocation();
            double dx = at.getX() - impact.getX();
            double dz = at.getZ() - impact.getZ();
            double distance = Math.hypot(dx, dz);
            if (distance <= 5 && at.getY() - impact.getY() < 3.5) {
                damage(player, 15);
                Vector away = distance < 0.1 ? new Vector(0, 0, 0) : new Vector(dx, 0, dz).normalize().multiply(0.7);
                player.setVelocity(away.setY(1.15));
            }
        }
        shatterPillars(impact, 6.0);
    }

    /** O impacto estilhaça os pilares de cobertura próximos. */
    private void shatterPillars(Location impact, double radius) {
        int broken = 0;
        for (int[] c : Structures.pillarCenters(arena.cx, arena.cz, arena.fx, arena.fz)) {
            if (Math.hypot(c[0] - impact.getX(), c[1] - impact.getZ()) > radius + 1.5) {
                continue;
            }
            for (int x = c[0] - 1; x <= c[0] + 1; x++) {
                for (int z = c[1] - 1; z <= c[1] + 1; z++) {
                    for (int y = arena.fy; y <= arena.fy + 15; y++) {
                        org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                        Material type = block.getType();
                        for (Material pillar : PILLAR_BLOCKS) {
                            if (type == pillar) {
                                block.setType(Material.AIR, false);
                                if (broken++ % 3 == 0) {
                                    world.spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 8, 0.4, 0.4,
                                            0.4, 0.1, type.createBlockData());
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (broken > 0) {
            Fx.worldSound(impact, Sound.BLOCK_STONE_BREAK, 2f, 0.5f);
        }
    }

    // ── 5. Investida Implacável ────────────────────────────────

    private void rush() {
        if (moveT < 18) {
            faceTowards(aim(), 20);
            if (moveT % 2 == 0) {
                world.spawnParticle(Particle.FLAME, at(3.5, 4.5), 6, 0.5, 0.4, 0.5, 0.02);
                world.spawnParticle(Particle.FALLING_DUST, at(3.5, 4.2), 8, 0.5, 0.3, 0.5, 0, Material.SAND.createBlockData());
            }
            if (moveT == 1) {
                Fx.worldSound(pos, Sound.ENTITY_RAVAGER_ROAR, 1.6f, 0.5f);
            }
            if (moveT == 17) {
                dash = facing();
            }
            return;
        }
        double step = 1.1;
        double nx = pos.getX() + dash.getX() * step;
        double nz = pos.getZ() + dash.getZ() * step;
        boolean wall = blocked(nx, pos.getY(), nz);
        if (!wall && dashed < 16) {
            pos.setX(nx);
            pos.setZ(nz);
            clamp();
            dashed += step;
            trails.add(new Trail(pos.clone().add(-dash.getX() * 1.5, 0, -dash.getZ() * 1.5), new int[] {90}));
            if (age % 3 == 0) {
                Fx.worldSound(pos, Sound.ENTITY_IRON_GOLEM_STEP, 1.8f, 0.5f);
            }
        }
        Location head = at(2.8, 2.0);
        for (Player player : insidePlayers()) {
            if (struck.contains(player.getUniqueId())) {
                continue;
            }
            if (player.getLocation().add(0, 1, 0).distanceSquared(head) < 3.2 * 3.2) {
                struck.add(player.getUniqueId());
                damage(player, 14);
                player.setVelocity(new Vector(dash.getX() * 2.0, 0.7, dash.getZ() * 2.0));
                player.setFireTicks(60);
                Fx.worldSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.6f, 0.5f);
                world.spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 24, 0.4, 0.5, 0.4, 0.1);
            }
        }
        if (wall || dashed >= 16) {
            if (wall) {
                Fx.worldSound(head, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
                shake(head, 30);
            }
            if (moveT > 18 + (int) (dashed / step) + 12) {
                endMove();
            }
        }
    }

    private void tickTrails() {
        if (trails.isEmpty()) {
            return;
        }
        trails.removeIf(trail -> --trail.ttl()[0] <= 0);
        if (age % 4 == 0) {
            for (Trail trail : trails) {
                world.spawnParticle(Particle.FLAME, trail.at(), 3, 0.5, 0.1, 0.5, 0.01);
                world.spawnParticle(Particle.SMOKE, trail.at().clone().add(0, 0.4, 0), 1, 0.4, 0.2, 0.4, 0.01);
            }
        }
        if (age % 10 == 0) {
            for (Player player : insidePlayers()) {
                for (Trail trail : trails) {
                    Location at = player.getLocation();
                    if (Math.hypot(at.getX() - trail.at().getX(), at.getZ() - trail.at().getZ()) < 1.4
                            && Math.abs(at.getY() - trail.at().getY()) < 2.2) {
                        player.setFireTicks(40);
                        damage(player, 2);
                        break;
                    }
                }
            }
        }
    }

    // ── 6. Vórtice de Tempestade ───────────────────────────────

    private void spin() {
        if (moveT == 1) {
            Fx.worldSound(pos, Sound.ENTITY_RAVAGER_ROAR, 1.6f, 0.9f);
        }
        if (moveT >= 6 && moveT <= 30 && moveT % 3 == 0) {
            double a = Math.toRadians(yaw + (moveT - 6) * 18);
            world.spawnParticle(Particle.SWEEP_ATTACK, at(0, 3).add(Math.cos(a) * 3.6, 0, Math.sin(a) * 3.6), 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.CLOUD, at(0, 1.6), 10, 3, 0.4, 3, 0.2);
        }
        if (moveT == 14) {
            Fx.worldSound(pos, Sound.ENTITY_BREEZE_WIND_BURST, 2f, 0.5f);
            for (int i = 0; i < 2; i++) {
                double phase = i * Math.PI;
                Location from = arena.center().add(Math.cos(phase) * 5, 0, Math.sin(phase) * 5);
                cyclones.add(new Cyclone(new SandTornado(from, false, manager.partKey()), phase, i == 0 ? 1 : -1));
            }
        }
        if (moveT >= 36) {
            endMove();
        }
    }

    private void tickCyclones() {
        if (cyclones.isEmpty()) {
            return;
        }
        for (Cyclone c : new ArrayList<>(cyclones)) {
            c.age++;
            double t = c.age / 120.0;
            double radius = 4 + 8.5 * Math.sin(Math.PI * Math.min(1, t) * 2.0) * Math.sin(Math.PI * Math.min(1, t) * 2.0) + 2;
            double angle = c.phase + c.dir * c.age * 0.07;
            Location at = new Location(world, arena.cx + 0.5 + Math.cos(angle) * radius, arena.fy, arena.cz + 0.5 + Math.sin(angle) * radius);
            c.tornado.moveTo(at);
            c.tornado.tick(c.age);
            for (Player player : insidePlayers()) {
                Location p = player.getLocation();
                double dx = at.getX() - p.getX();
                double dz = at.getZ() - p.getZ();
                double d = Math.hypot(dx, dz);
                if (d < 10 && d > 0.1) {
                    Vector pull = new Vector(dx / d, 0, dz / d).multiply(Math.min(0.11, 0.025 * (11 - d)));
                    player.setVelocity(player.getVelocity().add(pull));
                }
                if (d < 1.9 && c.age % 10 == 0) {
                    damage(player, 3);                              // 6 de dano por segundo no vórtice
                    player.setVelocity(new Vector(0, 0.55, 0));       // ergue o alvo ~2 blocos
                    player.setRotation(p.getYaw() + 22, p.getPitch());
                }
            }
            if (c.age >= 120) {
                c.tornado.remove();
                cyclones.remove(c);
            }
        }
    }

    // ── Fúria ──────────────────────────────────────────────────

    private void roar() {
        if (moveT == 1) {
            Fx.worldSound(pos, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.35f);
        }
        if (moveT == 14) {
            Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
            world.spawnParticle(Particle.BLOCK, at(0, 4), 160, 2, 2.6, 2, 0.4, Material.SANDSTONE.createBlockData());
            world.spawnParticle(Particle.LAVA, at(0, 4), 20, 1.4, 1.4, 1.4, 0);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, at(0, 4), 1, 0, 0, 0, 0);
        }
        if (moveT >= 48) {
            endMove();
        }
    }
}
