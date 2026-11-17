package dev.vertexx.boss;

import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Stray;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Root Sovereign, o guardião das Lush Caves (380 de vida) da Gruta dos Esporos Ancestrais. Fase 1 (100% a 50%): postura
 * ereta com a Casca Reforçada (braço de madeira que corta 75% do dano frontal e bloqueia projéteis; machado ou golpe pelas
 * costas a quebra) e os ataques Esfera de Esporos, Raízes Prisioneiras e Vórtice de Vinhas. Abaixo de 50% finca o cajado,
 * ruge e passa a correr de quatro, com o dobro de agilidade, ganhando Investida de Raízes, Explosão de Esporos e Chuva de
 * Vinhas. Enquanto dorme, o corredor de vinhas (cipós, tripwire e dardos) e o Salão dos Protetores (4 Sentinelas) vivem aqui.
 */
final class RootSovereign extends BossFight {

    private enum Move { NONE, SPORE, SNARE, VORTEX, DASH, FRENZY, THORNS, ROAR }

    private static final int[] COOLDOWN = {0, 140, 180, 240, 100, 200, 280, 0};
    private static final Particle.DustOptions LEAF = new Particle.DustOptions(Color.fromRGB(96, 200, 70), 1.5f);
    private static final Particle.DustOptions SPORE_DUST = new Particle.DustOptions(Color.fromRGB(150, 230, 90), 1.7f);
    private static final Particle.DustOptions PINK = new Particle.DustOptions(Color.fromRGB(240, 130, 190), 1.4f);

    private static final class Spore {
        final Location from;
        final Location to;
        final int duration;
        int t;
        BlockDisplay display;

        Spore(Location from, Location to, int duration) {
            this.from = from;
            this.to = to;
            this.duration = duration;
        }
    }

    private record Cloud(Location at, int[] ttl) {
    }

    private static final class Whip {
        final Location at;
        int t;
        BlockDisplay display;

        Whip(Location at) {
            this.at = at;
        }
    }

    private Move move = Move.NONE;
    private Move last = Move.NONE;
    private int moveT;
    private int idleTicks = 40;
    private final int[] cd = new int[8];
    private boolean walking;
    private boolean quad;

    private boolean guarding;
    private int guardT;
    private int guardCd = 80;
    private int stun;

    private Vector dash = new Vector(0, 0, 1);
    private double dashed;
    private final Set<UUID> struck = new HashSet<>();
    private final List<Spore> spores = new ArrayList<>();
    private final List<Cloud> clouds = new ArrayList<>();
    private final Map<UUID, Location> snareSpots = new HashMap<>();
    private final Map<UUID, Integer> rooted = new HashMap<>();
    private final Map<UUID, Location> rootAnchor = new HashMap<>();
    private final List<BlockDisplay> rootDisplays = new ArrayList<>();
    private int rootDisplayTicks;
    private final List<Whip> whips = new ArrayList<>();
    private int thornTicks;

    private final List<Mob> guardians = new ArrayList<>();
    private final Map<Location, Integer> webs = new HashMap<>();
    private boolean guardiansSpawned;
    private boolean gateOpened;
    private final int[] trapCd = new int[Structures.GROVE_TRAPS.length];
    private int webCd = 60;

    RootSovereign(BossManager manager, Arena arena) {
        super(manager, arena);
    }

    // ── Parâmetros ─────────────────────────────────────────────

    @Override
    protected double startHeight() {
        return 0;
    }

    @Override
    protected double baseHealth() {
        return plugin.getConfig().getDouble("bosses.root-sovereign.health", 380);
    }

    @Override
    protected double hitboxScale() {
        return 2.2;
    }

    @Override
    protected double bodyRadius() {
        return 1.5;
    }

    @Override
    protected double bodyHeight() {
        return 5.8;
    }

    @Override
    protected String modelId() {
        return "root_sovereign";
    }

    @Override
    protected String title() {
        return "O Soberano das Raízes";
    }

    @Override
    protected String subtitle() {
        return "<green>O Soberano das Raízes desperta";
    }

    @Override
    protected int wakeDuration() {
        return 60;
    }

    @Override
    List<String> moves() {
        return List.of("spore", "snare", "vortex", "dash", "frenzy", "thorns");
    }

    @Override
    protected void buildRig() {
        // o corpo vem todo do modelo; raízes, vinhas e esporos são peças criadas na hora
    }

    // ── Geometria local da gruta ───────────────────────────────

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
        return quad ? 1.8 : 1.0;
    }

    private Location ground(Location at) {
        return new Location(world, at.getX(), arena.fy, at.getZ());
    }

    // ── Antes do chefe: labirinto, salão dos protetores e portão ──

    @Override
    protected void dormantTick() {
        if (age % 5 != 0) {
            return;
        }
        boolean inHall = false;
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL && player.getGameMode() != org.bukkit.GameMode.ADVENTURE) {
                continue;
            }
            Location at = player.getLocation();
            if (Math.abs(at.getY() - arena.fy) > 10) {
                continue;
            }
            double x = lx(at);
            double z = lz(at);
            if (z >= 24 && z <= 43 && Math.abs(x) <= 6.5) {
                inHall = true;
            }
            vineZone(player, x, z);
            tripwire(player, x, z);
        }
        for (int i = 0; i < trapCd.length; i++) {
            if (trapCd[i] > 0) {
                trapCd[i] -= 5;
            }
        }
        if (inHall && !guardiansSpawned) {
            spawnGuardians();
        }
        if (guardiansSpawned && !gateOpened) {
            guardians.removeIf(mob -> !mob.isValid());
            mossWebs();
            if (guardians.isEmpty()) {
                gateOpened = true;
                clearWebs();
                manager.setGate(arena, false);
                for (Player player : world.getPlayers()) {
                    if (arena.distanceSquared(player.getLocation()) < 70 * 70) {
                        Msg.actionBar(player, "<green>As Sentinelas caíram: o portão de raízes se abre.");
                    }
                }
                Fx.worldSound(local(0, arena.fy + 2, 21), Sound.BLOCK_ROOTED_DIRT_BREAK, 2f, 0.5f);
                Fx.worldSound(local(0, arena.fy + 2, 21), Sound.BLOCK_MOSS_BREAK, 2f, 0.6f);
            }
        }
        tickWebs(5);
    }

    /** Cipós densos: lentidão de 45% e veneno leve para quem atravessa. */
    private void vineZone(Player player, double x, double z) {
        for (int[] zone : Structures.GROVE_VINES) {
            if (Math.abs(x - zone[0]) <= 1.9 && Math.abs(z - zone[1]) <= 2.9) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 2, false, false));
                if (age % 20 == 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 50, 0));
                    Msg.actionBar(player, "<green>Cipós espinhosos! <gray>Eles te prendem e envenenam.");
                }
                world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 3, 0.4, 0.6, 0.4, 0, LEAF);
                return;
            }
        }
    }

    /** Tripwire: pisar no fio dispara os dispensers das paredes (dardos de lentidão). */
    private void tripwire(Player player, double x, double z) {
        for (int i = 0; i < Structures.GROVE_TRAPS.length; i++) {
            int[] trap = Structures.GROVE_TRAPS[i];
            if (trapCd[i] > 0 || Math.abs(x - trap[0]) > 1.6 || Math.abs(z - trap[1]) > 0.9) {
                continue;
            }
            trapCd[i] = 200;
            Fx.worldSound(player.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1.4f, 0.8f);
            for (int side : new int[] {-2, 2}) {
                Location from = local(trap[0] + side * 0.7, arena.fy + 1.5, trap[1]);
                Vector aim = player.getLocation().add(0, 1.0, 0).toVector().subtract(from.toVector()).normalize().multiply(1.6);
                Fx.worldSound(from, Sound.BLOCK_DISPENSER_LAUNCH, 1.2f, 1.2f);
                Arrow arrow = world.spawn(from, Arrow.class, a -> {
                    a.setVelocity(aim);
                    a.setPersistent(false);
                    a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                    a.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1), true);
                    a.getPersistentDataContainer().set(manager.projectileKey(), PersistentDataType.DOUBLE, 3.0);
                    a.getPersistentDataContainer().set(manager.partKey(), PersistentDataType.BYTE, (byte) 1);
                });
                track(arrow);
            }
            Msg.actionBar(player, "<green>Um fio de tripwire! <gray>Dardos de lentidão!");
        }
    }

    /** 4 Sentinelas de Vinhas Rastejantes (Strays com carapaça vegetal) guardam o salão. */
    private void spawnGuardians() {
        guardiansSpawned = true;
        double[][] spots = {{-4, 30}, {4, 30}, {-4, 38}, {4, 38}};
        for (double[] spot : spots) {
            Location at = local(spot[0], arena.fy, spot[1]);
            Stray stray = world.spawn(at, Stray.class, s -> {
                s.setPersistent(false);
                s.setRemoveWhenFarAway(false);
                s.setCanPickupItems(false);
                s.getPersistentDataContainer().set(manager.partKey(), PersistentDataType.BYTE, (byte) 1);
                s.customName(Msg.mm("<green>Sentinela de Vinhas"));
                s.setCustomNameVisible(false);
                AttributeInstance max = s.getAttribute(Attribute.MAX_HEALTH);
                if (max != null) {
                    max.setBaseValue(36);
                }
                s.setHealth(36);
                AttributeInstance follow = s.getAttribute(Attribute.FOLLOW_RANGE);
                if (follow != null) {
                    follow.setBaseValue(40);
                }
                AttributeInstance knock = s.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
                if (knock != null) {
                    knock.setBaseValue(0.6);
                }
            });
            stray.getEquipment().setHelmet(new ItemStack(Material.MOSS_BLOCK));
            stray.getEquipment().setChestplate(leaf(Material.LEATHER_CHESTPLATE));
            stray.getEquipment().setLeggings(leaf(Material.LEATHER_LEGGINGS));
            stray.getEquipment().setBoots(leaf(Material.LEATHER_BOOTS));
            stray.getEquipment().setHelmetDropChance(0f);
            stray.getEquipment().setChestplateDropChance(0f);
            stray.getEquipment().setLeggingsDropChance(0f);
            stray.getEquipment().setBootsDropChance(0f);
            guardians.add(stray);
            track(stray);
        }
        for (Player player : world.getPlayers()) {
            if (arena.distanceSquared(player.getLocation()) < 70 * 70) {
                Msg.actionBar(player, "<green>As Sentinelas de Vinhas despertam!");
            }
        }
        Fx.worldSound(local(0, arena.fy + 2, 34), Sound.BLOCK_AZALEA_LEAVES_BREAK, 2f, 0.5f);
        Fx.worldSound(local(0, arena.fy + 2, 34), Sound.ENTITY_STRAY_AMBIENT, 2f, 0.5f);
    }

    private ItemStack leaf(Material material) {
        ItemStack item = new ItemStack(material);
        item.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(58, 120, 44)));
        return item;
    }

    /** As Sentinelas semeiam teias de musgo sob os pés dos jogadores (somem em 8 s). */
    private void mossWebs() {
        webCd -= 5;
        if (webCd > 0) {
            return;
        }
        webCd = 50;
        for (Mob guardian : guardians) {
            Player best = null;
            double bestDistance = 14 * 14;
            for (Player player : world.getPlayers()) {
                double d = player.getLocation().distanceSquared(guardian.getLocation());
                if (d < bestDistance && (player.getGameMode() == org.bukkit.GameMode.SURVIVAL
                        || player.getGameMode() == org.bukkit.GameMode.ADVENTURE)) {
                    bestDistance = d;
                    best = player;
                }
            }
            if (best == null) {
                continue;
            }
            org.bukkit.block.Block block = best.getLocation().getBlock();
            if (block.getType() == Material.AIR || block.getType() == Material.MOSS_CARPET) {
                webs.put(block.getLocation(), 160);
                block.setType(Material.COBWEB, false);
                world.spawnParticle(Particle.DUST, block.getLocation().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, 0, LEAF);
            }
        }
    }

    private void tickWebs(int dt) {
        if (webs.isEmpty()) {
            return;
        }
        webs.replaceAll((block, ttl) -> ttl - dt);
        webs.entrySet().removeIf(entry -> {
            if (entry.getValue() <= 0) {
                if (entry.getKey().getBlock().getType() == Material.COBWEB) {
                    entry.getKey().getBlock().setType(Material.AIR, false);
                }
                return true;
            }
            return false;
        });
    }

    private void clearWebs() {
        for (Location block : webs.keySet()) {
            if (block.getBlock().getType() == Material.COBWEB) {
                block.getBlock().setType(Material.AIR, false);
            }
        }
        webs.clear();
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
            world.spawnParticle(Particle.COMPOSTER, projectile.getLocation(), 8, 0.2, 0.2, 0.2, 0.3);
            Fx.worldSound(projectile.getLocation(), Sound.BLOCK_WOOD_HIT, 1.4f, 0.6f);
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
        world.spawnParticle(Particle.BLOCK, at(1.8, 3.4), 10, 0.6, 0.8, 0.6, 0.2, Material.MANGROVE_LOG.createBlockData());
        Fx.worldSound(at(1.8, 3.4), Sound.BLOCK_WOOD_BREAK, 1.4f, 0.6f);
        return base * 0.25;
    }

    @Override
    double damageTaken(double base) {
        hurtTicks = 6;
        return base;
    }

    private void breakGuard() {
        guarding = false;
        stun = 30;
        endMove();
        Fx.worldSound(at(1.8, 3), Sound.ITEM_SHIELD_BREAK, 1.6f, 0.6f);
        world.spawnParticle(Particle.BLOCK, at(1.8, 3), 40, 0.8, 1.2, 0.8, 0.2, Material.MANGROVE_LOG.createBlockData());
        warn("<green><bold>Casca rompida!</bold> <gray>O Soberano está atordoado: ataque!");
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
            Fx.worldSound(pos, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.6f);
            Fx.worldSound(pos, Sound.BLOCK_AZALEA_LEAVES_BREAK, 2f, 0.5f);
        }
        if (t % 5 == 0) {
            world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, at(0, 3), 40, 3, 2, 3, 0);
            world.spawnParticle(Particle.DUST, at(0, 3), 20, 2.5, 2, 2.5, 0, SPORE_DUST);
        }
        if (t == 30) {
            Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.6f);
            world.spawnParticle(Particle.EXPLOSION, at(0, 3), 3, 2, 1, 2, 0);
        }
    }

    @Override
    protected void onEnrage() {
        guarding = false;
        stun = 0;
        endMove();
        startMove(Move.ROAR);
        warn("<green><bold>O Soberano finca o cajado e corre de quatro!</bold> <gray>Fúria Silvestre.");
        for (int i = 0; i < cd.length; i++) {
            cd[i] = Math.min(cd[i], 40);
        }
    }

    @Override
    protected void deathStart() {
        endMove();
        clearHazards();
        Fx.worldSound(pos, Sound.ENTITY_RAVAGER_DEATH, 2f, 0.5f);
    }

    @Override
    protected void deathAnimate(int t) {
        model.play("stagger");
        if (t % 3 == 0) {
            world.spawnParticle(Particle.BLOCK, at(0, 3), 40, 2, 2.4, 2, 0.3, Material.MANGROVE_LOG.createBlockData());
            world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, at(0, 3), 30, 2, 2, 2, 0);
            world.spawnParticle(Particle.DUST, at(0, 3), 12, 1.6, 1.6, 1.6, 0, PINK);
        }
        if (t == 48) {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, at(0, 3), 2, 1, 1, 1, 0);
            Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
        }
    }

    @Override
    protected void cleanup() {
        clearHazards();
        clearWebs();
        for (Mob guardian : guardians) {
            guardian.remove();
        }
        guardians.clear();
    }

    private void clearHazards() {
        for (Spore spore : spores) {
            if (spore.display != null) {
                spore.display.remove();
            }
        }
        spores.clear();
        clouds.clear();
        for (Whip whip : whips) {
            if (whip.display != null) {
                whip.display.remove();
            }
        }
        whips.clear();
        thornTicks = 0;
        for (BlockDisplay display : rootDisplays) {
            display.remove();
        }
        rootDisplays.clear();
        rooted.clear();
        rootAnchor.clear();
        snareSpots.clear();
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
                case SPORE -> quad ? "q_cast" : "spore";
                case SNARE -> quad ? "q_cast" : "snare";
                case VORTEX -> quad ? "q_cast" : "vortex";
                case DASH -> moveT < 18 ? "q_idle" : "q_dash";
                case FRENZY -> "q_frenzy";
                case THORNS -> "q_thorns";
                case ROAR -> "roar";
                default -> quad ? (walking ? "q_walk" : "q_idle") : walking ? "walk" : "idle";
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
        tickSpores();
        tickClouds();
        tickRoots();
        tickWhips();
        if (stun > 0) {
            stun--;
            walking = false;
            if (stun % 8 == 0) {
                world.spawnParticle(Particle.CRIT, at(0, 6), 6, 0.8, 0.3, 0.8, 0.1);
            }
            return;
        }
        if (guarding) {
            guardT--;
            walking = false;
            faceTowards(aim(), 5);
            if (guardT <= 0) {
                guarding = false;
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
            case SPORE -> spore();
            case SNARE -> snare();
            case VORTEX -> vortex();
            case DASH -> dash();
            case FRENZY -> frenzy();
            case THORNS -> thorns();
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
        if (target != null && distance > (quad ? 4.5 : 12)) {
            moveToward(at.getX(), arena.fy, at.getZ(), step);
            walking = true;
        } else if (target != null && !quad && distance < 6) {
            moveToward(pos.getX() - dx, arena.fy, pos.getZ() - dz, step * 0.7);
            walking = true;
        }
        if (--idleTicks <= 0 && target != null) {
            if (!quad && guardCd == 0 && ThreadLocalRandom.current().nextDouble() < 0.35) {
                startGuard();
            } else {
                pickMove(distance);
            }
        }
    }

    private void startGuard() {
        guarding = true;
        guardT = 50;
        model.restart("guard");
        Fx.worldSound(pos, Sound.BLOCK_WOOD_PLACE, 1.5f, 0.5f);
    }

    private void pickMove(double distance) {
        double[] w = new double[8];
        w[Move.SPORE.ordinal()] = cd[1] == 0 && distance >= 4 && distance <= 20 ? (quad ? 1.5 : 3) : 0;
        w[Move.SNARE.ordinal()] = cd[2] == 0 && distance <= 14 ? (quad ? 1.5 : 3) : 0;
        w[Move.VORTEX.ordinal()] = cd[3] == 0 && distance <= 12 ? (quad ? 1.5 : 2.5) : 0;
        w[Move.DASH.ordinal()] = quad && cd[4] == 0 && distance >= 4 && distance <= 18 ? 4 : 0;
        w[Move.FRENZY.ordinal()] = quad && cd[5] == 0 && distance <= 9 ? 3.5 : 0;
        w[Move.THORNS.ordinal()] = quad && cd[6] == 0 ? 2.5 : 0;
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
        String clip = switch (next) {
            case SPORE -> quad ? "q_cast" : "spore";
            case SNARE -> quad ? "q_cast" : "snare";
            case VORTEX -> quad ? "q_cast" : "vortex";
            case DASH -> "q_idle";
            case FRENZY -> "q_frenzy";
            case THORNS -> "q_thorns";
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
        move = Move.NONE;
        idleTicks = quad ? 14 : 40;
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

    // ── 1. Esfera de Esporos Tóxicos ───────────────────────────

    private void spore() {
        if (moveT < 18) {
            faceTowards(aim(), 14);
            if (moveT % 2 == 0) {
                world.spawnParticle(Particle.DUST, at(1.8, 5.6), 4, 0.35, 0.35, 0.35, 0, SPORE_DUST);
                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, at(1.8, 5.6), 6, 0.4, 0.4, 0.4, 0);
            }
            if (moveT == 1) {
                Fx.worldSound(pos, Sound.BLOCK_BIG_DRIPLEAF_TILT_DOWN, 1.6f, 0.5f);
            }
            return;
        }
        if (moveT == 18) {
            Location from = at(1.8, 5.2);
            Location want = aim();
            Vector delta = new Vector(want.getX() - from.getX(), 0, want.getZ() - from.getZ());
            double d = delta.length();
            if (d > 20) {
                delta.multiply(20 / d);
                d = 20;
            }
            Location to = new Location(world, from.getX() + delta.getX(), arena.fy, from.getZ() + delta.getZ());
            Spore ball = new Spore(from, to, (int) Math.max(14, d * 1.1));
            ball.display = world.spawn(from, BlockDisplay.class, display -> {
                display.setBlock(Material.MOSS_BLOCK.createBlockData());
                display.setPersistent(false);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setTeleportDuration(1);
                display.setTransformation(new Transformation(new Vector3f(-0.6f, -0.6f, -0.6f), new Quaternionf(),
                        new Vector3f(1.2f, 1.2f, 1.2f), new Quaternionf()));
                display.getPersistentDataContainer().set(manager.partKey(), PersistentDataType.BYTE, (byte) 1);
            });
            track(ball.display);
            spores.add(ball);
            Fx.worldSound(from, Sound.ENTITY_SLIME_JUMP, 1.6f, 0.5f);
            Fx.worldSound(from, Sound.ENTITY_BREEZE_SHOOT, 1.2f, 0.7f);
        }
        if (moveT >= 30) {
            endMove();
        }
    }

    private void tickSpores() {
        for (Spore ball : new ArrayList<>(spores)) {
            ball.t++;
            double k = Math.min(1.0, ball.t / (double) ball.duration);
            Location at = ball.from.clone().add(ball.to.clone().subtract(ball.from).toVector().multiply(k));
            at.setY(ball.from.getY() + (ball.to.getY() - ball.from.getY()) * k + 4.5 * Math.sin(Math.PI * k));
            if (ball.display != null && ball.display.isValid()) {
                ball.display.teleport(at);
            }
            world.spawnParticle(Particle.DUST, at, 3, 0.3, 0.3, 0.3, 0, SPORE_DUST);
            world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, at, 3, 0.3, 0.3, 0.3, 0);
            if (k >= 1.0) {
                explodeSpore(ball);
                spores.remove(ball);
            }
        }
    }

    private void explodeSpore(Spore ball) {
        if (ball.display != null) {
            ball.display.remove();
        }
        Location impact = ball.to.clone();
        Fx.worldSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 1.2f);
        Fx.worldSound(impact, Sound.BLOCK_MOSS_BREAK, 2f, 0.5f);
        world.spawnParticle(Particle.DUST, impact.clone().add(0, 0.6, 0), 80, 2.4, 0.8, 2.4, 0, SPORE_DUST);
        world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, impact.clone().add(0, 1, 0), 60, 2.5, 1, 2.5, 0);
        world.spawnParticle(Particle.EXPLOSION, impact.clone().add(0, 0.5, 0), 2, 1, 0.2, 1, 0);
        for (Player player : insidePlayers()) {
            Location at = player.getLocation();
            if (Math.hypot(at.getX() - impact.getX(), at.getZ() - impact.getZ()) <= 4 && at.getY() - impact.getY() < 3.5) {
                damage(player, 8);
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1));
            }
        }
        clouds.add(new Cloud(impact, new int[] {80}));
    }

    private void tickClouds() {
        if (clouds.isEmpty()) {
            return;
        }
        clouds.removeIf(cloud -> --cloud.ttl()[0] <= 0);
        for (Cloud cloud : clouds) {
            if (age % 4 == 0) {
                world.spawnParticle(Particle.DUST, cloud.at().clone().add(0, 0.6, 0), 6, 2.4, 0.5, 2.4, 0, SPORE_DUST);
                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, cloud.at().clone().add(0, 1, 0), 6, 2.4, 0.6, 2.4, 0);
            }
            if (age % 10 == 0) {
                for (Player player : insidePlayers()) {
                    Location at = player.getLocation();
                    if (Math.hypot(at.getX() - cloud.at().getX(), at.getZ() - cloud.at().getZ()) <= 4
                            && at.getY() - cloud.at().getY() < 3.5) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1, false, true));
                    }
                }
            }
        }
    }

    // ── 2. Raízes Prisioneiras ─────────────────────────────────

    private void snare() {
        faceTowards(aim(), 12);
        if (moveT == 1) {
            Fx.worldSound(pos, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.8f, 0.5f);
        }
        if (moveT == 4) {
            snareSpots.clear();
            for (Player player : insidePlayers()) {
                if (player.getLocation().distanceSquared(pos) <= 12 * 12) {
                    snareSpots.put(player.getUniqueId(), ground(player.getLocation()));
                }
            }
        }
        if (moveT >= 4 && moveT < 22) {
            if (moveT % 2 == 0) {
                for (Location spot : snareSpots.values()) {
                    for (int i = 0; i < 18; i++) {
                        double a = Math.PI * 2 * i / 18;
                        world.spawnParticle(Particle.DUST, spot.getX() + Math.cos(a) * 1.9, spot.getY() + 0.15,
                                spot.getZ() + Math.sin(a) * 1.9, 1, 0, 0, 0, 0, LEAF);
                    }
                    world.spawnParticle(Particle.COMPOSTER, spot.clone().add(0, 0.2, 0), 3, 1, 0.1, 1, 0);
                }
            }
            return;
        }
        if (moveT == 22) {
            Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
            for (Location spot : snareSpots.values()) {
                world.spawnParticle(Particle.BLOCK, spot, 40, 1.2, 0.3, 1.2, 0.2, Material.MANGROVE_ROOTS.createBlockData());
                Fx.worldSound(spot, Sound.BLOCK_MANGROVE_ROOTS_BREAK, 1.8f, 0.6f);
                for (int i = 0; i < 4; i++) {
                    double a = Math.PI / 2 * i + 0.5;
                    Location at = spot.clone().add(Math.cos(a) * 0.9, 0, Math.sin(a) * 0.9);
                    BlockDisplay root = world.spawn(at, BlockDisplay.class, d -> {
                        d.setBlock(Material.MANGROVE_ROOTS.createBlockData());
                        d.setPersistent(false);
                        d.setTransformation(new Transformation(new Vector3f(-0.5f, 0, -0.5f), new Quaternionf(),
                                new Vector3f(1f, 1.8f, 1f), new Quaternionf()));
                        d.getPersistentDataContainer().set(manager.partKey(), PersistentDataType.BYTE, (byte) 1);
                    });
                    rootDisplays.add(root);
                    track(root);
                }
            }
            rootDisplayTicks = 50;
            for (Player player : insidePlayers()) {
                Location spot = snareSpots.get(player.getUniqueId());
                Location at = player.getLocation();
                if (spot != null && Math.hypot(at.getX() - spot.getX(), at.getZ() - spot.getZ()) <= 2.0
                        && at.getY() - spot.getY() < 2.5) {
                    damage(player, 6);
                    rooted.put(player.getUniqueId(), 50);
                    rootAnchor.put(player.getUniqueId(), player.getLocation());
                    Msg.actionBar(player, "<green>Raízes prisioneiras! <gray>Você está imobilizado.");
                }
            }
        }
        if (moveT >= 34) {
            endMove();
        }
    }

    /** Mantém presos os jogadores enredados (2,5 s): sem andar, sem pular. */
    private void tickRoots() {
        if (rootDisplayTicks > 0 && --rootDisplayTicks == 0) {
            for (BlockDisplay display : rootDisplays) {
                display.remove();
            }
            rootDisplays.clear();
        }
        if (rooted.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Integer> entry : new ArrayList<>(rooted.entrySet())) {
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            int left = entry.getValue() - 1;
            if (player == null || !player.isOnline() || player.isDead() || left <= 0) {
                rooted.remove(entry.getKey());
                rootAnchor.remove(entry.getKey());
                continue;
            }
            rooted.put(entry.getKey(), left);
            Location anchor = rootAnchor.get(entry.getKey());
            Location at = player.getLocation();
            if (anchor != null && Math.hypot(at.getX() - anchor.getX(), at.getZ() - anchor.getZ()) > 0.2) {
                Location back = anchor.clone();
                back.setYaw(at.getYaw());
                back.setPitch(at.getPitch());
                player.teleport(back);
            }
            player.setVelocity(new Vector(0, Math.min(0, player.getVelocity().getY()), 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 6, 6, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 6, 200, false, false));
        }
    }

    // ── 3. Vórtice de Vinhas ───────────────────────────────────

    private void vortex() {
        if (moveT < 38) {
            faceTowards(aim(), 12);
        }
        if (moveT == 1) {
            Fx.worldSound(pos, Sound.BLOCK_BIG_DRIPLEAF_TILT_DOWN, 1.8f, 0.5f);
        }
        if (moveT >= 2 && moveT < 38) {
            if (moveT % 2 == 0) {
                for (int i = 0; i < 40; i++) {
                    double a = Math.PI * 2 * i / 40 + moveT * 0.06;
                    world.spawnParticle(Particle.DUST, pos.getX() + Math.cos(a) * 10, pos.getY() + 0.3,
                            pos.getZ() + Math.sin(a) * 10, 1, 0, 0.3, 0, 0, LEAF);
                    if (i % 4 == 0) {
                        world.spawnParticle(Particle.BLOCK, pos.getX() + Math.cos(a) * 10, pos.getY() + 0.8,
                                pos.getZ() + Math.sin(a) * 10, 2, 0.1, 0.5, 0.1, 0, Material.MANGROVE_ROOTS.createBlockData());
                    }
                }
            }
            for (Player player : insidePlayers()) {
                Location at = player.getLocation();
                double dx = pos.getX() - at.getX();
                double dz = pos.getZ() - at.getZ();
                double d = Math.hypot(dx, dz);
                if (d < 10 && d > 3.0) {
                    Vector pull = new Vector(dx / d, 0, dz / d).multiply(0.10);
                    player.setVelocity(player.getVelocity().add(pull));
                }
            }
        }
        if (moveT == 38) {
            Vector f = facing();
            Location blow = at(2.8, 0.4);
            Fx.worldSound(blow, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.8f, 0.5f);
            Fx.worldSound(blow, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.8f);
            world.spawnParticle(Particle.BLOCK, blow, 50, 1.4, 0.3, 1.4, 0.2, Material.MANGROVE_LOG.createBlockData());
            world.spawnParticle(Particle.SWEEP_ATTACK, at(2.4, 2.8), 4, 1.2, 0.4, 1.2, 0);
            for (Player player : insidePlayers()) {
                Location at = player.getLocation();
                double dx = at.getX() - pos.getX();
                double dz = at.getZ() - pos.getZ();
                double distance = Math.hypot(dx, dz);
                if (distance <= 5.2 && (distance < 1 || (dx * f.getX() + dz * f.getZ()) / distance > -0.2)) {
                    damage(player, 12);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
                    Vector away = distance < 0.1 ? f.clone() : new Vector(dx, 0, dz).normalize();
                    player.setVelocity(away.multiply(1.1).setY(0.5));
                }
            }
        }
        if (moveT >= 50) {
            endMove();
        }
    }

    // ── 4. Investida de Raízes Iradas ──────────────────────────

    private void dash() {
        if (moveT < 18) {
            faceTowards(aim(), 20);
            if (moveT % 2 == 0) {
                world.spawnParticle(Particle.DUST, at(1.6, 1.2), 5, 0.8, 0.3, 0.8, 0, LEAF);
                world.spawnParticle(Particle.BLOCK, at(0, 0.3), 8, 1.4, 0.1, 1.4, 0.1, Material.MANGROVE_ROOTS.createBlockData());
            }
            if (moveT == 1) {
                Fx.worldSound(pos, Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.7f);
            }
            if (moveT == 17) {
                dash = facing();
            }
            return;
        }
        double step = 1.5;
        double nx = pos.getX() + dash.getX() * step;
        double nz = pos.getZ() + dash.getZ() * step;
        boolean wall = blocked(nx, pos.getY(), nz);
        if (!wall && dashed < 15) {
            pos.setX(nx);
            pos.setZ(nz);
            clamp();
            dashed += step;
            world.spawnParticle(Particle.BLOCK, pos.clone().add(0, 0.4, 0), 8, 0.8, 0.2, 0.8, 0.1, Material.MOSS_BLOCK.createBlockData());
            if (age % 3 == 0) {
                Fx.worldSound(pos, Sound.BLOCK_ROOTED_DIRT_STEP, 1.8f, 0.5f);
            }
        }
        Location head = at(2.4, 1.8);
        for (Player player : insidePlayers()) {
            if (struck.contains(player.getUniqueId())) {
                continue;
            }
            if (player.getLocation().add(0, 1, 0).distanceSquared(head) < 3.0 * 3.0) {
                struck.add(player.getUniqueId());
                damage(player, 14);
                player.setVelocity(new Vector(dash.getX() * 2.4, 0.8, dash.getZ() * 2.4));
                Fx.worldSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.6f, 0.5f);
                world.spawnParticle(Particle.BLOCK, player.getLocation().add(0, 1, 0), 24, 0.4, 0.5, 0.4, 0.2,
                        Material.MANGROVE_ROOTS.createBlockData());
            }
        }
        if (wall || dashed >= 15) {
            if (wall) {
                Fx.worldSound(head, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
                world.spawnParticle(Particle.BLOCK, head, 30, 1.2, 0.5, 1.2, 0.2, Material.MOSS_BLOCK.createBlockData());
            }
            if (moveT > 18 + (int) (dashed / step) + 12) {
                endMove();
            }
        }
    }

    // ── 5. Explosão de Esporos Natais ──────────────────────────

    private void frenzy() {
        if (moveT == 1) {
            Fx.worldSound(pos, Sound.ENTITY_RAVAGER_ROAR, 1.6f, 0.8f);
        }
        if (moveT < 12) {
            if (moveT % 3 == 0) {
                for (int i = 0; i < 36; i++) {
                    double a = Math.PI * 2 * i / 36;
                    world.spawnParticle(Particle.DUST, pos.getX() + Math.cos(a) * 8, pos.getY() + 0.2,
                            pos.getZ() + Math.sin(a) * 8, 1, 0, 0, 0, 0, SPORE_DUST);
                }
            }
            return;
        }
        for (int wave = 0; wave < 2; wave++) {
            int start = 12 + wave * 14;
            if (moveT >= start && moveT < start + 8) {
                double r = 2 + (moveT - start) * 0.9;
                for (int i = 0; i < 28; i++) {
                    double a = Math.PI * 2 * i / 28;
                    world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, pos.getX() + Math.cos(a) * r, pos.getY() + 1.2,
                            pos.getZ() + Math.sin(a) * r, 1, 0.1, 0.4, 0.1, 0);
                    if (i % 3 == 0) {
                        world.spawnParticle(Particle.DUST, pos.getX() + Math.cos(a) * r, pos.getY() + 1,
                                pos.getZ() + Math.sin(a) * r, 1, 0.1, 0.3, 0.1, 0, PINK);
                    }
                }
            }
            if (moveT == start + 1) {
                Fx.worldSound(pos, Sound.ENTITY_BREEZE_WIND_BURST, 1.8f, 0.6f);
                Fx.worldSound(pos, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, 0.5f);
                world.spawnParticle(Particle.SWEEP_ATTACK, at(0, 2.4), 8, 3, 0.4, 3, 0);
            }
        }
        if (moveT == 20) {                                              // o segundo giro é o que acerta (aviso de 12 ticks)
            for (Player player : insidePlayers()) {
                Location at = player.getLocation();
                double dx = at.getX() - pos.getX();
                double dz = at.getZ() - pos.getZ();
                double distance = Math.hypot(dx, dz);
                if (distance <= 8 && at.getY() - pos.getY() < 4) {
                    damage(player, 12);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                    Vector away = distance < 0.1 ? new Vector(0, 0, 0) : new Vector(dx, 0, dz).normalize().multiply(0.9);
                    player.setVelocity(away.setY(0.5));
                }
            }
        }
        if (moveT >= 42) {
            endMove();
        }
    }

    // ── 6. Chuva de Vinhas Cortantes ───────────────────────────

    private void thorns() {
        if (moveT == 1) {
            Fx.worldSound(pos, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.5f);
            Fx.worldSound(pos, Sound.BLOCK_AZALEA_LEAVES_BREAK, 2f, 0.5f);
            thornTicks = 120;
        }
        if (moveT >= 14 && moveT < 14 + 120 && moveT % 3 == 0 && whips.size() < 16) {
            Location spot;
            List<Player> players = insidePlayers();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (!players.isEmpty() && random.nextDouble() < 0.55) {
                Player pick = players.get(random.nextInt(players.size()));
                spot = ground(pick.getLocation());
                spot.add((random.nextDouble() - 0.5) * 2.5, 0, (random.nextDouble() - 0.5) * 2.5);
            } else {
                double a = random.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(random.nextDouble()) * (arena.half - 3);
                spot = new Location(world, arena.cx + 0.5 + Math.cos(a) * r, arena.fy, arena.cz + 0.5 + Math.sin(a) * r);
            }
            whips.add(new Whip(spot));
        }
        if (moveT >= 14 + 120 + 22) {
            endMove();
        }
    }

    private void tickWhips() {
        if (whips.isEmpty()) {
            return;
        }
        for (Whip whip : new ArrayList<>(whips)) {
            whip.t++;
            if (whip.t < 14) {
                if (whip.t % 2 == 0) {
                    for (int i = 0; i < 10; i++) {
                        double a = Math.PI * 2 * i / 10;
                        world.spawnParticle(Particle.DUST, whip.at.getX() + Math.cos(a) * 1.3, whip.at.getY() + 0.15,
                                whip.at.getZ() + Math.sin(a) * 1.3, 1, 0, 0, 0, 0, LEAF);
                    }
                    world.spawnParticle(Particle.COMPOSTER, whip.at.clone().add(0, 4 + whip.t * 0.5, 0), 2, 0.2, 1.5, 0.2, 0);
                }
            } else if (whip.t == 14) {
                Fx.worldSound(whip.at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
                Fx.worldSound(whip.at, Sound.BLOCK_BIG_DRIPLEAF_TILT_UP, 1.4f, 0.6f);
                world.spawnParticle(Particle.BLOCK, whip.at.clone().add(0, 0.4, 0), 20, 0.5, 0.3, 0.5, 0.2, Material.MOSS_BLOCK.createBlockData());
                double height = Math.max(6, arena.fy + arena.height - 1 - whip.at.getY());
                whip.display = world.spawn(whip.at.clone().add(0, 0, 0), BlockDisplay.class, d -> {
                    d.setBlock(Material.CAVE_VINES_PLANT.createBlockData());
                    d.setPersistent(false);
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setTransformation(new Transformation(new Vector3f(-0.9f, 0, -0.9f), new Quaternionf(),
                            new Vector3f(1.8f, (float) height, 1.8f), new Quaternionf()));
                    d.getPersistentDataContainer().set(manager.partKey(), PersistentDataType.BYTE, (byte) 1);
                });
                track(whip.display);
                for (Player player : insidePlayers()) {
                    Location at = player.getLocation();
                    if (Math.hypot(at.getX() - whip.at.getX(), at.getZ() - whip.at.getZ()) <= 1.7 && at.getY() - whip.at.getY() < 3.5) {
                        damage(player, 10);
                        player.setVelocity(player.getVelocity().add(new Vector(0, 0.4, 0)));
                    }
                }
            } else if (whip.t >= 20) {
                if (whip.display != null) {
                    whip.display.remove();
                }
                whips.remove(whip);
            }
        }
    }

    // ── Fúria ──────────────────────────────────────────────────

    private void roar() {
        if (moveT == 1) {
            Fx.worldSound(pos, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.4f);
        }
        if (moveT == 14) {
            quad = true;
            Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
            world.spawnParticle(Particle.BLOCK, at(0, 4), 120, 2, 2.6, 2, 0.4, Material.MANGROVE_LOG.createBlockData());
            world.spawnParticle(Particle.DUST, at(0, 4), 60, 3, 2.6, 3, 0, PINK);
            world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, at(0, 3), 80, 4, 2, 4, 0);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, at(0, 4), 1, 0, 0, 0, 0);
        }
        if (moveT >= 48) {
            endMove();
        }
    }
}
