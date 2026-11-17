package dev.vertexx.boss;

import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Gloom Stalker, o predador do Deep Dark (420 de vida) do Covil Ressonante. Fase 1 (100% a 50%): caçador furtivo. Com
 * nível de luz abaixo de 7 no corpo, recebe 60% menos dano e bloqueia projéteis; luz forte (tochas, lanternas) ou flechas
 * espectrais dissipam o Manto de Escuridão por 6 s. Ataca com Emboscada das Sombras, Rugido Sônico e Pulso de Sensores.
 * Abaixo de 50% ruge, perde o manto, ganha +40% de velocidade e passa a usar Salto Predatório Duplo, Frenesi de Garras
 * Abissais e a Detonação do Núcleo de Alma. Enquanto dorme, a Galeria dos Sensores (corre sem agachar: dardos e piso que
 * some) e a Câmara de Incubação (3 Catalisadores + 5 Rastejadores + 2 Sentinelas) vivem aqui.
 */
final class GloomStalker extends BossFight {

    private enum Move { NONE, POUNCE, SONIC, SNARE, LEAP, SHRED, OVERLOAD, ROAR }

    private static final int[] COOLDOWN = {0, 160, 180, 240, 140, 120, 260, 0};
    private static final Particle.DustOptions CYAN = new Particle.DustOptions(Color.fromRGB(70, 220, 255), 1.6f);
    private static final Particle.DustOptions DEEP = new Particle.DustOptions(Color.fromRGB(20, 60, 80), 1.8f);

    private Move move = Move.NONE;
    private Move last = Move.NONE;
    private int moveT;
    private int idleTicks = 40;
    private final int[] cd = new int[8];
    private boolean walking;
    private int lightExposed;
    private int strafe = 1;

    private Vector dash = new Vector(0, 0, 1);
    private Location retreat;
    private Location leapFrom;
    private Location leapTo;
    private final Set<UUID> struck = new HashSet<>();
    private boolean hidden;

    private final List<Mob> guardians = new ArrayList<>();
    private final Map<Location, Material> floorBack = new HashMap<>();
    private final Map<Location, Integer> floorTicks = new HashMap<>();
    private boolean guardiansSpawned;
    private boolean gateOpened;
    private int trapCd;

    GloomStalker(BossManager manager, Arena arena) {
        super(manager, arena);
    }

    // ── Parâmetros ─────────────────────────────────────────────

    @Override
    protected double startHeight() {
        return 0;
    }

    @Override
    protected double baseHealth() {
        return plugin.getConfig().getDouble("bosses.gloom-stalker.health", 420);
    }

    @Override
    protected double hitboxScale() {
        return 1.5;
    }

    @Override
    protected double bodyRadius() {
        return 1.7;
    }

    @Override
    protected double bodyHeight() {
        return 3.4;
    }

    @Override
    protected String modelId() {
        return "gloom_stalker";
    }

    @Override
    protected String title() {
        return "O Espreitador das Sombras";
    }

    @Override
    protected String subtitle() {
        return "<dark_aqua>O Espreitador das Sombras desperta";
    }

    @Override
    protected int wakeDuration() {
        return 60;
    }

    @Override
    List<String> moves() {
        return List.of("pounce", "sonic", "snare", "leap", "shred", "overload");
    }

    @Override
    protected void buildRig() {
        // o corpo vem todo do modelo
    }

    // ── Geometria local do covil ───────────────────────────────

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
        return enraged ? 1.4 : 1.0;
    }

    // ── Antes do chefe: galeria, incubação e portão ────────────

    @Override
    protected void dormantTick() {
        if (age % 5 != 0) {
            return;
        }
        boolean inHatch = false;
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
            if (z >= 25 && z <= 46 && Math.abs(x) <= 8.5) {
                inHatch = true;
            }
            if (z >= Structures.LAIR_GALLERY_FROM && z <= Structures.LAIR_GALLERY_TO + 2 && Math.abs(x) <= 2.6) {
                gallery(player, x, z);
            }
        }
        if (trapCd > 0) {
            trapCd -= 5;
        }
        tickFloors(5);
        if (inHatch && !guardiansSpawned) {
            spawnGuardians();
        }
        if (guardiansSpawned && !gateOpened) {
            guardians.removeIf(mob -> !mob.isValid());
            boolean catalysts = false;
            for (int[] spot : Structures.LAIR_CATALYSTS) {
                if (local(spot[0], arena.fy, spot[1]).getBlock().getType() == Material.SCULK_CATALYST) {
                    catalysts = true;
                }
            }
            if (guardians.isEmpty() && !catalysts) {
                gateOpened = true;
                manager.setGate(arena, false);
                for (Player player : world.getPlayers()) {
                    if (arena.distanceSquared(player.getLocation()) < 70 * 70) {
                        Msg.actionBar(player, "<dark_aqua>Os Catalisadores caíram: o portão do abismo se abre.");
                    }
                }
                Fx.worldSound(local(0, arena.fy + 2, 22), Sound.BLOCK_SCULK_CATALYST_BLOOM, 2f, 0.5f);
                Fx.worldSound(local(0, arena.fy + 2, 22), Sound.BLOCK_DEEPSLATE_BREAK, 2f, 0.6f);
            }
        }
    }

    /** Sensores ocultos: correr sem se agachar dispara dardos de Darkness e faz o piso da ponte recuar. */
    private void gallery(Player player, double x, double z) {
        if (trapCd > 0 || !player.isSprinting() || player.isSneaking()) {
            return;
        }
        trapCd = 60;
        Fx.worldSound(player.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.6f, 0.6f);
        Fx.worldSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1.2f, 0.7f);
        Msg.actionBar(player, "<dark_aqua>Os sensores ouviram você correr! <gray>Agache-se para passar em silêncio.");
        for (int side : new int[] {-3, 3}) {
            Location from = local(side * 0.8, arena.fy + 1.5, z + 1);
            Vector aim = player.getLocation().add(0, 1.0, 0).toVector().subtract(from.toVector()).normalize().multiply(1.5);
            Arrow arrow = world.spawn(from, Arrow.class, a -> {
                a.setVelocity(aim);
                a.setPersistent(false);
                a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                a.addCustomEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0), true);
                a.getPersistentDataContainer().set(manager.projectileKey(), PersistentDataType.DOUBLE, 2.0);
                a.getPersistentDataContainer().set(manager.partKey(), PersistentDataType.BYTE, (byte) 1);
            });
            track(arrow);
        }
        for (int[] bridge : Structures.LAIR_BRIDGES) {          // pistões retraem o piso da ponte logo à frente
            if (z >= bridge[0] - 3 && z <= bridge[1]) {
                for (int dz = 2; dz <= 3; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        double tz = z + dz;
                        if (tz < bridge[0] || tz > bridge[1]) {
                            continue;
                        }
                        Location spot = local(Math.round(x) + dx, arena.fy - 1, Math.round(tz)).getBlock().getLocation();
                        if (spot.getBlock().getType() == Material.DEEPSLATE_BRICKS && !floorBack.containsKey(spot)) {
                            floorBack.put(spot, Material.DEEPSLATE_BRICKS);
                            floorTicks.put(spot, 80);
                            spot.getBlock().setType(Material.AIR, false);
                        }
                    }
                }
                break;
            }
        }
    }

    private void tickFloors(int dt) {
        if (floorTicks.isEmpty()) {
            return;
        }
        for (Map.Entry<Location, Integer> entry : new ArrayList<>(floorTicks.entrySet())) {
            int left = entry.getValue() - dt;
            if (left <= 0) {
                entry.getKey().getBlock().setType(floorBack.getOrDefault(entry.getKey(), Material.DEEPSLATE_BRICKS), false);
                floorTicks.remove(entry.getKey());
                floorBack.remove(entry.getKey());
            } else {
                floorTicks.put(entry.getKey(), left);
            }
        }
    }

    private void restoreFloors() {
        for (Map.Entry<Location, Material> entry : floorBack.entrySet()) {
            entry.getKey().getBlock().setType(entry.getValue(), false);
        }
        floorBack.clear();
        floorTicks.clear();
    }

    /** 5 Rastejadores de Ardósia (Silverfish) e 2 Sentinelas de Alma (Skeletons de deepslate). */
    private void spawnGuardians() {
        guardiansSpawned = true;
        double[][] crawlers = {{-6, 30}, {6, 30}, {-2, 36}, {2, 36}, {0, 43}};
        for (double[] spot : crawlers) {
            Silverfish fish = world.spawn(local(spot[0], arena.fy, spot[1]), Silverfish.class, s -> {
                setup(s, 16, 4);
                AttributeInstance speed = s.getAttribute(Attribute.MOVEMENT_SPEED);
                if (speed != null) {
                    speed.setBaseValue(0.42);
                }
                s.customName(Msg.mm("<dark_aqua>Rastejador de Ardósia"));
            });
            guardians.add(fish);
            track(fish);
        }
        double[][] sentinels = {{-6, 27}, {6, 42}};
        for (double[] spot : sentinels) {
            Skeleton skeleton = world.spawn(local(spot[0], arena.fy, spot[1]), Skeleton.class, s -> {
                setup(s, 30, 3);
                s.customName(Msg.mm("<dark_aqua>Sentinela de Alma"));
                s.getPersistentDataContainer().set(manager.partKey(), PersistentDataType.BYTE, (byte) 3);
                AttributeInstance follow = s.getAttribute(Attribute.FOLLOW_RANGE);
                if (follow != null) {
                    follow.setBaseValue(16);
                }
            });
            skeleton.getEquipment().setHelmet(new ItemStack(Material.POLISHED_DEEPSLATE));
            skeleton.getEquipment().setChestplate(dyed(Material.LEATHER_CHESTPLATE));
            skeleton.getEquipment().setLeggings(dyed(Material.LEATHER_LEGGINGS));
            skeleton.getEquipment().setBoots(dyed(Material.LEATHER_BOOTS));
            skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
            skeleton.getEquipment().setHelmetDropChance(0f);
            skeleton.getEquipment().setChestplateDropChance(0f);
            skeleton.getEquipment().setLeggingsDropChance(0f);
            skeleton.getEquipment().setBootsDropChance(0f);
            skeleton.getEquipment().setItemInMainHandDropChance(0f);
            guardians.add(skeleton);
            track(skeleton);
        }
        for (Player player : world.getPlayers()) {
            if (arena.distanceSquared(player.getLocation()) < 70 * 70) {
                Msg.actionBar(player, "<dark_aqua>Destrua os 3 Catalisadores e vença os guardiões!");
            }
        }
        Fx.worldSound(local(0, arena.fy + 2, 34), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 2f, 0.5f);
    }

    private void setup(Mob mob, double health, double damage) {
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);
        mob.setCanPickupItems(false);
        mob.getPersistentDataContainer().set(manager.partKey(), PersistentDataType.BYTE, (byte) 1);
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

    private ItemStack dyed(Material material) {
        ItemStack item = new ItemStack(material);
        item.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(44, 52, 62)));
        return item;
    }

    // ── Manto de Escuridão ─────────────────────────────────────

    private boolean cloaked() {
        return !enraged && lightExposed <= 0;
    }

    private int lightAtBody() {
        int a = pos.getBlock().getLightFromBlocks();
        int b = pos.clone().add(0, 1.5, 0).getBlock().getLightFromBlocks();
        return Math.max(a, b);
    }

    private void expose() {
        if (lightExposed <= 0 && !enraged) {
            warn("<aqua><bold>O manto se dissipa!</bold> <gray>O Soul-Heart brilha: ataque!");
            Fx.worldSound(pos, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.4f, 1.4f);
        }
        lightExposed = 120;
    }

    @Override
    boolean blocksProjectile(Projectile projectile) {
        if (projectile instanceof SpectralArrow) {
            expose();
            return false;
        }
        if (cloaked()) {
            world.spawnParticle(Particle.SCULK_SOUL, projectile.getLocation(), 8, 0.2, 0.2, 0.2, 0.05);
            Fx.worldSound(projectile.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.2f, 0.7f);
            return true;
        }
        return false;
    }

    @Override
    double damageTaken(EntityDamageEvent event, double base) {
        hurtTicks = 6;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof SpectralArrow) {
                expose();
            }
        }
        if (!enraged && lightAtBody() >= 7) {
            expose();
        }
        return cloaked() ? base * 0.4 : base;
    }

    @Override
    double damageTaken(double base) {
        hurtTicks = 6;
        return cloaked() ? base * 0.4 : base;
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
            Fx.worldSound(pos, Sound.ENTITY_WARDEN_EMERGE, 2f, 0.6f);
            Fx.worldSound(pos, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 2f, 0.5f);
        }
        if (t % 5 == 0) {
            world.spawnParticle(Particle.SCULK_SOUL, at(0, 1.4), 20, 2.5, 1, 2.5, 0.03);
            world.spawnParticle(Particle.DUST, at(0, 1.4), 12, 2, 1, 2, 0, CYAN);
        }
    }

    @Override
    protected void onEnrage() {
        endMove();
        showBody();
        startMove(Move.ROAR);
        warn("<dark_aqua><bold>O Espreitador abandona a furtividade!</bold> <gray>Predador Desenfreado.");
        for (int i = 0; i < cd.length; i++) {
            cd[i] = Math.min(cd[i], 40);
        }
    }

    private void showBody() {
        if (hidden) {
            hidden = false;
            model.hideAll(false);
        }
    }

    @Override
    protected void deathStart() {
        endMove();
        showBody();
        Fx.worldSound(pos, Sound.ENTITY_WARDEN_DEATH, 2f, 0.6f);
    }

    @Override
    protected void deathAnimate(int t) {
        model.play("stagger");
        if (t % 3 == 0) {
            world.spawnParticle(Particle.SCULK_SOUL, at(0, 1.6), 30, 1.6, 1, 1.6, 0.05);
            world.spawnParticle(Particle.DUST, at(0, 1.6), 20, 1.6, 1, 1.6, 0, CYAN);
        }
        if (t == 48) {
            world.spawnParticle(Particle.SONIC_BOOM, at(0, 1.6), 1, 0, 0, 0, 0);
            Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
        }
    }

    @Override
    protected void cleanup() {
        showBodyQuiet();
        restoreFloors();
        for (Mob guardian : guardians) {
            guardian.remove();
        }
        guardians.clear();
    }

    private void showBodyQuiet() {
        hidden = false;
        if (model != null) {
            model.hideAll(false);
        }
    }

    // ── Animação ───────────────────────────────────────────────

    @Override
    protected void animate() {
        if (state == State.DYING || state == State.DORMANT || state == State.WAKING) {
            return;
        }
        String clip = switch (move) {
            case POUNCE -> "pounce";
            case SONIC -> "sonic";
            case SNARE -> "snare";
            case LEAP -> "leap";
            case SHRED -> "shred";
            case OVERLOAD -> "overload";
            case ROAR -> "roar";
            default -> walking ? (enraged ? "run" : "stalk") : "idle";
        };
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
        if (lightExposed > 0) {
            lightExposed--;
            if (age % 6 == 0) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, at(-0.4, 2.0), 3, 0.3, 0.3, 0.3, 0.01);
            }
        } else if (!enraged && age % 5 == 0 && lightAtBody() >= 7) {
            expose();
        }
        if (cloaked() && age % 8 == 0) {
            world.spawnParticle(Particle.SCULK_SOUL, at(0, 1.6), 2, 0.8, 0.5, 0.8, 0.01);
        }
        if (move == Move.NONE) {
            idle();
            return;
        }
        moveT++;
        switch (move) {
            case POUNCE -> pounce();
            case SONIC -> sonic();
            case SNARE -> snare();
            case LEAP -> leap();
            case SHRED -> shred();
            case OVERLOAD -> overload();
            case ROAR -> roar();
            default -> { }
        }
    }

    private void idle() {
        Location at = aim();
        faceTowards(at, 7 * (float) speed());
        double dx = at.getX() - pos.getX();
        double dz = at.getZ() - pos.getZ();
        double distance = Math.hypot(dx, dz);
        walking = false;
        double step = 0.17 * speed();
        if (target != null) {
            if (enraged) {
                if (distance > 3.5) {
                    moveToward(at.getX(), arena.fy, at.getZ(), step * 1.2);
                    walking = true;
                }
            } else if (distance > 14) {
                moveToward(at.getX(), arena.fy, at.getZ(), step);
                walking = true;
            } else if (distance < 8) {
                moveToward(pos.getX() - dx, arena.fy, pos.getZ() - dz, step * 0.9);
                walking = true;
            } else {
                if (age % 80 == 0) {
                    strafe = -strafe;
                }
                double nx = pos.getX() + (-dz / Math.max(0.1, distance)) * strafe * step * 0.6;
                double nz = pos.getZ() + (dx / Math.max(0.1, distance)) * strafe * step * 0.6;
                moveToward(nx, arena.fy, nz, step);
                walking = true;
            }
        }
        if (--idleTicks <= 0 && target != null) {
            pickMove(distance);
        }
    }

    private void pickMove(double distance) {
        double[] w = new double[8];
        w[Move.POUNCE.ordinal()] = !enraged && cd[1] == 0 && distance >= 6 && distance <= 18 ? 3 : 0;
        w[Move.SONIC.ordinal()] = cd[2] == 0 && distance <= 15 && distance >= 3 ? (enraged ? 1.5 : 3) : 0;
        w[Move.SNARE.ordinal()] = cd[3] == 0 && distance <= 11 ? (enraged ? 1.2 : 2.5) : 0;
        w[Move.LEAP.ordinal()] = enraged && cd[4] == 0 && distance >= 5 && distance <= 20 ? 4 : 0;
        w[Move.SHRED.ordinal()] = enraged && cd[5] == 0 && distance <= 7 ? 4 : 0;
        w[Move.OVERLOAD.ordinal()] = enraged && cd[6] == 0 ? 2.5 : 0;
        w[last.ordinal()] *= 0.3;
        double total = 0;
        for (double v : w) {
            total += v;
        }
        if (total <= 0) {
            idleTicks = 14;
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
        walking = false;
        String clip = switch (next) {
            case POUNCE -> "pounce";
            case SONIC -> "sonic";
            case SNARE -> "snare";
            case LEAP -> "leap";
            case SHRED -> "shred";
            case OVERLOAD -> "overload";
            case ROAR -> "roar";
            default -> null;
        };
        if (clip != null) {
            model.restart(clip);
        }
    }

    private void endMove() {
        if (move != Move.NONE) {
            cd[move.ordinal()] = COOLDOWN[move.ordinal()];
        }
        showBody();
        move = Move.NONE;
        idleTicks = enraged ? 12 : 36;
    }

    @Override
    boolean forceMove(String name) {
        if (!active() || move != Move.NONE) {
            return false;
        }
        for (Move candidate : Move.values()) {
            if (candidate != Move.NONE && candidate != Move.ROAR && candidate.name().equalsIgnoreCase(name)) {
                startMove(candidate);
                return true;
            }
        }
        return false;
    }

    // ── Dano utilitário ────────────────────────────────────────

    /** Dano sônico: atravessa escudos (derruba a guarda antes de acertar). */
    private void sonicHit(Player player, double amount) {
        if (player.isBlocking()) {
            player.clearActiveItem();
        }
        DamageSource source = DamageSource.builder(DamageType.SONIC_BOOM).withCausingEntity(hitbox).withDirectEntity(hitbox).build();
        player.damage(amount * manager.damageMultiplier(), source);
    }

    /** Corte que ignora 30% da armadura: 70% pelo caminho normal e 30% como dano mágico. */
    private void claw(Player player, double amount) {
        damage(player, amount * 0.7);
        player.setNoDamageTicks(0);
        DamageSource source = DamageSource.builder(DamageType.MAGIC).withCausingEntity(hitbox).withDirectEntity(hitbox).build();
        player.damage(amount * 0.3 * manager.damageMultiplier(), source);
    }

    private void slash(Location from, Vector f) {
        world.spawnParticle(Particle.SWEEP_ATTACK, from.clone().add(f.clone().multiply(1.2)).add(0, 1.2, 0), 2, 0.5, 0.3, 0.5, 0);
        world.spawnParticle(Particle.DUST, from.clone().add(f.clone().multiply(1.6)).add(0, 1.2, 0), 10, 0.5, 0.4, 0.5, 0, CYAN);
    }

    // ── 1. Emboscada das Sombras ───────────────────────────────

    private void pounce() {
        if (moveT == 1) {
            double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            retreat = new Location(world, arena.cx + 0.5 + Math.cos(a) * 12, arena.fy, arena.cz + 0.5 + Math.sin(a) * 12);
            Fx.worldSound(pos, Sound.ENTITY_WARDEN_STEP, 1.5f, 0.6f);
        }
        if (moveT <= 12) {
            faceTowards(retreat, 25);
            moveToward(retreat.getX(), arena.fy, retreat.getZ(), 0.8);
            return;
        }
        if (moveT == 13) {
            hidden = true;
            model.hideAll(true);
            world.spawnParticle(Particle.SCULK_SOUL, at(0, 1.4), 40, 1, 0.8, 1, 0.05);
            Fx.worldSound(pos, Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.4f, 0.5f);
        }
        if (moveT < 37) {                                              // 1,2 s semitransparente, a rondar
            if (moveT % 4 == 0 && target != null) {
                world.spawnParticle(Particle.SCULK_SOUL, pos.clone().add(0, 0.8, 0), 3, 0.6, 0.4, 0.6, 0.01);
            }
            return;
        }
        if (moveT == 37) {                                             // escolhe o ponto às costas do alvo
            leapFrom = pos.clone();
            Location t = aim();
            Vector look = target != null ? target.getLocation().getDirection().setY(0) : new Vector(0, 0, 1);
            if (look.lengthSquared() < 1.0E-4) {
                look = new Vector(0, 0, 1);
            }
            look.normalize();
            leapTo = new Location(world, t.getX() - look.getX() * 2.2, arena.fy, t.getZ() - look.getZ() * 2.2);
            double dx = leapTo.getX() - (arena.cx + 0.5);
            double dz = leapTo.getZ() - (arena.cz + 0.5);
            double d = Math.hypot(dx, dz);
            if (d > arena.half - 2.8) {
                leapTo = new Location(world, arena.cx + 0.5 + dx / d * (arena.half - 2.8), arena.fy,
                        arena.cz + 0.5 + dz / d * (arena.half - 2.8));
            }
            showBody();
        }
        int t = moveT - 37;
        if (t <= 10 && leapTo != null) {
            double k = t / 10.0;
            pos.setX(leapFrom.getX() + (leapTo.getX() - leapFrom.getX()) * k);
            pos.setZ(leapFrom.getZ() + (leapTo.getZ() - leapFrom.getZ()) * k);
            pos.setY(arena.fy + 2.4 * Math.sin(Math.PI * k));
            faceTowards(aim(), 40);
            if (t == 10) {
                pos.setY(arena.fy);
                Location blow = at(2.2, 0.6);
                Fx.worldSound(blow, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, 0.5f);
                Fx.worldSound(blow, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.2f, 0.9f);
                slash(pos, facing());
                for (Player player : insidePlayers()) {
                    Location at = player.getLocation();
                    if (Math.hypot(at.getX() - blow.getX(), at.getZ() - blow.getZ()) <= 2.8 && Math.abs(at.getY() - pos.getY()) < 3) {
                        damage(player, 13);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
                    }
                }
            }
        }
        if (moveT >= 37 + 10 + 14) {
            endMove();
        }
    }

    // ── 2. Rugido Sônico Direcional ────────────────────────────

    private void sonic() {
        if (moveT < 22) {
            faceTowards(aim(), 12);
            if (moveT == 1) {
                Fx.worldSound(pos, Sound.ENTITY_WARDEN_ROAR, 1.6f, 0.9f);
            }
            if (moveT % 4 == 0) {
                world.spawnParticle(Particle.DUST, at(1.6, 1.6), 6, 0.4, 0.4, 0.4, 0, CYAN);
            }
            return;
        }
        if (moveT == 22) {
            Vector f = facing();
            Fx.worldSound(pos, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.9f);
            for (int i = 2; i <= 14; i += 2) {
                Location p = pos.clone().add(f.getX() * i, 1.4, f.getZ() * i);
                world.spawnParticle(Particle.SONIC_BOOM, p, 1, 0, 0, 0, 0);
                snuff(p);
            }
            for (Player player : insidePlayers()) {
                Location at = player.getLocation();
                double dx = at.getX() - pos.getX();
                double dz = at.getZ() - pos.getZ();
                double distance = Math.hypot(dx, dz);
                if (distance <= 14 && distance > 0.1 && (dx * f.getX() + dz * f.getZ()) / distance > 0.8) {
                    sonicHit(player, 11);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0));
                    player.setVelocity(new Vector(dx / distance, 0, dz / distance).multiply(1.0).setY(0.35));
                }
            }
        }
        if (moveT >= 34) {
            endMove();
        }
    }

    /** O rugido apaga tochas e lanternas no trajeto. */
    private void snuff(Location center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    org.bukkit.block.Block block = center.clone().add(dx, dy, dz).getBlock();
                    String name = block.getType().name();
                    if (name.contains("TORCH") || name.contains("LANTERN") || name.equals("CAMPFIRE") || name.equals("SOUL_CAMPFIRE")) {
                        block.breakNaturally();
                    }
                }
            }
        }
    }

    // ── 3. Pulso de Sensores Dorsais ───────────────────────────

    private void snare() {
        if (moveT == 1) {
            Fx.worldSound(pos, Sound.ENTITY_WARDEN_DIG, 1.8f, 0.6f);
        }
        if (moveT < 18) {
            faceTowards(aim(), 8);
            if (moveT % 3 == 0) {
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16;
                    world.spawnParticle(Particle.DUST, pos.getX() + Math.cos(a) * 1.8, pos.getY() + 0.2,
                            pos.getZ() + Math.sin(a) * 1.8, 1, 0, 0, 0, 0, CYAN);
                }
            }
            return;
        }
        if (moveT == 18) {
            Fx.worldSound(pos, Sound.BLOCK_SCULK_SENSOR_CLICKING, 2f, 0.5f);
            Fx.worldSound(pos, Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.7f);
            for (Player player : insidePlayers()) {
                Location at = player.getLocation();
                if (Math.hypot(at.getX() - pos.getX(), at.getZ() - pos.getZ()) > 9) {
                    continue;
                }
                boolean moving = player.isSprinting() || !player.isOnGround() || player.getVelocity().getY() > 0.1;
                if (moving && !player.isSneaking()) {
                    damage(player, 9);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                    Msg.actionBar(player, "<dark_aqua>Os sensores sentiram você! <gray>Fique parado ou agache-se.");
                }
            }
        }
        if (moveT >= 18 && moveT < 30) {
            double r = (moveT - 18) * 0.8;
            for (int i = 0; i < 30; i++) {
                double a = Math.PI * 2 * i / 30;
                world.spawnParticle(Particle.DUST, pos.getX() + Math.cos(a) * r, pos.getY() + 0.2,
                        pos.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, CYAN);
            }
            world.spawnParticle(Particle.SCULK_CHARGE_POP, pos.clone().add(Math.cos(moveT) * r, 0.1, Math.sin(moveT) * r), 2, 0.2, 0.05, 0.2, 0);
        }
        if (moveT >= 36) {
            endMove();
        }
    }

    // ── 4. Salto Predatório Duplo ──────────────────────────────

    private void leap() {
        if (moveT < 8) {
            faceTowards(aim(), 20);
            return;
        }
        int n = (moveT - 8) / 22;
        int t = (moveT - 8) % 22;
        if (n >= 2) {
            if (moveT >= 8 + 44 + 8) {
                endMove();
            }
            return;
        }
        if (t == 0) {
            leapFrom = pos.clone();
            Location tg = aim();
            double dx = tg.getX() - (arena.cx + 0.5);
            double dz = tg.getZ() - (arena.cz + 0.5);
            double d = Math.hypot(dx, dz);
            double limit = arena.half - 2.8;
            leapTo = d > limit ? new Location(world, arena.cx + 0.5 + dx / d * limit, arena.fy, arena.cz + 0.5 + dz / d * limit)
                    : new Location(world, tg.getX(), arena.fy, tg.getZ());
            Fx.worldSound(pos, Sound.ENTITY_WARDEN_ROAR, 1.2f, 1.1f);
        }
        if (t <= 14 && leapTo != null) {
            double k = t / 14.0;
            pos.setX(leapFrom.getX() + (leapTo.getX() - leapFrom.getX()) * k);
            pos.setZ(leapFrom.getZ() + (leapTo.getZ() - leapFrom.getZ()) * k);
            pos.setY(arena.fy + 4.0 * Math.sin(Math.PI * k));
            faceTowards(leapTo, 30);
            if (t == 14) {
                pos.setY(arena.fy);
                land();
            }
        }
    }

    private void land() {
        Location impact = pos.clone();
        Fx.worldSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.6f);
        Fx.worldSound(impact, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.6f, 0.7f);
        world.spawnParticle(Particle.EXPLOSION, impact.clone().add(0, 0.5, 0), 4, 1.5, 0.3, 1.5, 0);
        for (double r = 1; r <= 5; r += 1.2) {
            for (int i = 0; i < r * 7; i++) {
                double a = Math.PI * 2 * i / (r * 7);
                world.spawnParticle(Particle.DUST, impact.getX() + Math.cos(a) * r, impact.getY() + 0.2,
                        impact.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, CYAN);
            }
        }
        for (Player player : insidePlayers()) {
            Location at = player.getLocation();
            double dx = at.getX() - impact.getX();
            double dz = at.getZ() - impact.getZ();
            double distance = Math.hypot(dx, dz);
            if (distance <= 4.5 && at.getY() - impact.getY() < 3.5) {
                damage(player, 15);
                Vector away = distance < 0.1 ? new Vector(0, 0, 0) : new Vector(dx, 0, dz).normalize().multiply(1.1);
                player.setVelocity(away.setY(0.6));
            }
        }
    }

    // ── 5. Frenesi de Garras Abissais ──────────────────────────

    private void shred() {
        if (moveT <= 6) {
            faceTowards(aim(), 25);
            Location at = aim();
            if (Math.hypot(at.getX() - pos.getX(), at.getZ() - pos.getZ()) > 2.2) {
                moveToward(at.getX(), arena.fy, at.getZ(), 0.8);
            }
            return;
        }
        if (moveT == 8 || moveT == 14 || moveT == 20) {
            faceTowards(aim(), 40);
            Vector f = facing();
            Fx.worldSound(pos, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, moveT == 14 ? 0.5f : 0.7f);
            Fx.worldSound(pos, Sound.BLOCK_DEEPSLATE_BREAK, 1.4f, 0.8f);
            slash(pos, f);
            for (Player player : insidePlayers()) {
                Location at = player.getLocation();
                double dx = at.getX() - pos.getX();
                double dz = at.getZ() - pos.getZ();
                double distance = Math.hypot(dx, dz);
                if (distance <= 4.5 && (distance < 1 || (dx * f.getX() + dz * f.getZ()) / distance > 0.1)) {
                    claw(player, 6);
                    player.setVelocity(player.getVelocity().add(f.clone().multiply(0.25)));
                }
            }
        }
        if (moveT >= 30) {
            endMove();
        }
    }

    // ── 6. Detonação do Núcleo de Alma ─────────────────────────

    private void overload() {
        if (moveT < 30) {
            if (moveT == 1) {
                Fx.worldSound(pos, Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.5f);
            }
            if (moveT % 3 == 0) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, at(-0.4, 2.0), 8, 0.4, 0.4, 0.4, 0.04);
                world.spawnParticle(Particle.DUST, at(-0.4, 2.0), 8, 0.6, 0.6, 0.6, 0, CYAN);
            }
            if (moveT % 6 == 0) {
                double r = 12.0 * moveT / 30.0;
                for (int i = 0; i < 32; i++) {
                    double a = Math.PI * 2 * i / 32;
                    world.spawnParticle(Particle.DUST, pos.getX() + Math.cos(a) * r, pos.getY() + 0.2,
                            pos.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, DEEP);
                }
            }
            return;
        }
        if (moveT == 30) {
            Fx.worldSound(pos, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.6f);
            Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
            world.spawnParticle(Particle.SONIC_BOOM, at(0, 1.6), 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, at(0, 1.4), 1, 0, 0, 0, 0);
            for (double r = 1; r <= 12; r += 1.5) {
                for (int i = 0; i < r * 6; i++) {
                    double a = Math.PI * 2 * i / (r * 6);
                    world.spawnParticle(Particle.SCULK_SOUL, pos.getX() + Math.cos(a) * r, pos.getY() + 0.3,
                            pos.getZ() + Math.sin(a) * r, 1, 0, 0.1, 0, 0.02);
                }
            }
            for (Player player : insidePlayers()) {
                Location at = player.getLocation();
                double distance = Math.hypot(at.getX() - pos.getX(), at.getZ() - pos.getZ());
                // a explosão corre pelo chão: quem está numa plataforma elevada (+2,5 blocos) escapa
                if (distance <= 12 && at.getY() - arena.fy < 2.5) {
                    sonicHit(player, 16);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0));
                    Vector away = distance < 0.1 ? new Vector(0, 0, 0) : new Vector(at.getX() - pos.getX(), 0, at.getZ() - pos.getZ()).normalize().multiply(0.9);
                    player.setVelocity(away.setY(0.45));
                }
            }
        }
        if (moveT >= 46) {
            endMove();
        }
    }

    // ── Fúria ──────────────────────────────────────────────────

    private void roar() {
        if (moveT == 1) {
            Fx.worldSound(pos, Sound.ENTITY_WARDEN_ROAR, 2f, 0.4f);
            Fx.worldSound(pos, Sound.ENTITY_WARDEN_ANGRY, 2f, 0.5f);
        }
        if (moveT == 14) {
            Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
            Fx.worldSound(pos, Sound.BLOCK_DEEPSLATE_BREAK, 2f, 0.4f);
            world.spawnParticle(Particle.SONIC_BOOM, at(0, 1.6), 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.SCULK_SOUL, at(0, 1.6), 100, 2.4, 1.2, 2.4, 0.08);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, at(-0.4, 2.0), 60, 0.8, 0.8, 0.8, 0.1);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, at(0, 1.4), 1, 0, 0, 0, 0);
        }
        if (moveT >= 48) {
            endMove();
        }
    }
}
