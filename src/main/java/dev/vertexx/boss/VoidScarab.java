package dev.vertexx.boss;

import dev.vertexx.core.Fx;
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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Void-Scarab, a Matriarca de Quitina: escaravelho colossal (600 de vida) do Pináculo de Quitina. A carapaça reduz 75% do
 * dano comum; o Núcleo de Energia, cristal flutuante sobre o tórax, leva 200% e, ao acumular 100 de dano, provoca a
 * Sobrecarga (o chefe cai por 6 s e perde a resistência). Ataques: Investida de Mandíbulas, Rajada de Espinhos de Coro,
 * Pulso Gravitacional do Ferrão e, abaixo de 50% de vida, o Enxame da Colmeia.
 */
final class VoidScarab extends BossFight {

    /** Peças que recebem golpes (hitboxes Interaction sincronizadas ao modelo). */
    enum Part { BODY, HEAD, CORE }

    private enum Move { NONE, CLAMP, VOLLEY, STINGER, SWARM }

    private static final double K = 3.4 / 16.0;                 // blocos por pixel do modelo
    private static final Particle.DustOptions PURPLE = new Particle.DustOptions(Color.fromRGB(176, 80, 255), 1.5f);
    private static final Particle.DustOptions PINK = new Particle.DustOptions(Color.fromRGB(255, 90, 230), 1.7f);
    private static final int[] COOLDOWN = {0, 160, 200, 280, 440};

    private Move move = Move.NONE;
    private Move last = Move.NONE;
    private int moveT;
    private int idleTicks = 50;
    private final int[] cd = new int[5];

    private Part pending;
    private double coreCharge;
    private int stagger;
    private boolean walking;

    private final Interaction[] parts = new Interaction[4];
    private final Part[] partKind = {Part.HEAD, Part.BODY, Part.BODY, Part.CORE};
    private final Set<UUID> struck = new HashSet<>();
    private final List<Entity> bullets = new ArrayList<>();
    private final List<Mob> mites = new ArrayList<>();
    private Vector dash = new Vector(0, 0, 1);
    private double dashed;

    VoidScarab(BossManager manager, Arena arena) {
        super(manager, arena);
        // o ninho com o altar fica no centro; o chefe dorme atrás dele, de frente para a entrada
        pos.add(-arena.fx * 11, 0, -arena.fz * 11);
    }

    // ── Parâmetros ─────────────────────────────────────────────

    @Override
    protected double startHeight() {
        return 0;
    }

    @Override
    protected double baseHealth() {
        return plugin.getConfig().getDouble("bosses.void-scarab.health", 600);
    }

    @Override
    protected double hitboxScale() {
        return 2.0;
    }

    @Override
    protected double bodyRadius() {
        return 2.3;
    }

    @Override
    protected double bodyHeight() {
        return 4.5;
    }

    @Override
    protected String modelId() {
        return "void_scarab";
    }

    @Override
    protected String title() {
        return "Matriarca de Quitina";
    }

    @Override
    protected String subtitle() {
        return "<dark_purple>A Matriarca de Quitina desperta";
    }

    @Override
    protected int wakeDuration() {
        return 70;
    }

    @Override
    List<String> moves() {
        return List.of("clamp", "volley", "stinger", "swarm");
    }

    @Override
    protected void buildRig() {
        double[][] offsets = {{0, 3.9, -6.0, 3.4, 3.4}, {0, 3.6, 0, 5.2, 4.8}, {0, 3.4, 3.6, 4.6, 4.2}, {0, 7.3, 1.3, 2.2, 2.2}};
        for (int i = 0; i < parts.length; i++) {
            final int index = i;
            Location at = pos.clone();
            parts[i] = world.spawn(at, Interaction.class, interaction -> {
                interaction.setInteractionWidth((float) offsets[index][3]);
                interaction.setInteractionHeight((float) offsets[index][4]);
                interaction.setResponsive(true);
                interaction.setPersistent(false);
                interaction.getPersistentDataContainer().set(manager.partKey(), PersistentDataType.BYTE, (byte) 1);
                interaction.getPersistentDataContainer().set(manager.hitKey(), PersistentDataType.STRING,
                        arena.id + ":" + partKind[index].name());
            });
            track(parts[i]);
        }
    }

    /** Posição de uma peça no mundo: {@code forward} blocos à frente do corpo e {@code y} de altura. */
    private Location at(double forward, double y) {
        double rad = Math.toRadians(yaw);
        return pos.clone().add(-Math.sin(rad) * forward, y, Math.cos(rad) * forward);
    }

    private void syncParts() {
        double[][] offsets = {{6.0, 0}, {0, 0}, {-3.6, 0}, {-1.3, 3.6}};
        double[] floor = {0, 0, 0, 3.7};
        for (int i = 0; i < parts.length; i++) {
            Interaction part = parts[i];
            if (part == null || !part.isValid()) {
                continue;
            }
            Location target = at(offsets[i][0], floor[i]);
            if (part.getLocation().distanceSquared(target) > 0.01) {
                part.teleport(target);
            }
        }
    }

    // ── Dano ───────────────────────────────────────────────────

    void hitPart(Part part) {
        pending = part;
    }

    @Override
    boolean vulnerable() {
        return state == State.ACTIVE;
    }

    @Override
    double damageTaken(double base) {
        hurtTicks = 6;
        Part part = pending;
        pending = null;
        Location at = at(0, 4);
        if (stagger > 0) {
            return base;
        }
        if (part == Part.CORE) {
            double dealt = base * 2.0;
            coreCharge += dealt;
            Fx.worldSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.4f, 0.6f);
            world.spawnParticle(Particle.DUST, at(-1.3, 7.3), 14, 0.6, 0.6, 0.6, 0.02, PINK);
            if (coreCharge >= 100) {
                startStagger();
            }
            return dealt;
        }
        Fx.worldSound(at, Sound.ENTITY_IRON_GOLEM_HURT, 1.2f, 1.6f);
        world.spawnParticle(Particle.CRIT, at(part == Part.HEAD ? 6 : 0, 3.6), 6, 0.6, 0.5, 0.6, 0.2);
        return base * 0.25;
    }

    private void startStagger() {
        coreCharge = 0;
        stagger = 120;
        endMove();
        exposed = true;
        Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
        Fx.worldSound(pos, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.6f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, at(-1.3, 7.3), 60, 1, 1, 1, 0.4);
        warn("<light_purple><bold>Sobrecarga!</bold> <gray>A carapaça cedeu: ataque com tudo!");
    }

    @Override
    boolean blocksProjectile(Projectile projectile) {
        return false;
    }

    // ── Despertar e morte ──────────────────────────────────────

    @Override
    protected void idlePose() {
        pos.setY(arena.fy);
        model.play("dormant");
    }

    @Override
    protected void wakeTick(int t) {
        if (t == 1) {
            model.restart("roar");
            Fx.worldSound(pos, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.5f);
            Fx.worldSound(pos, Sound.ENTITY_WARDEN_EMERGE, 1.5f, 0.8f);
        }
        if (t % 6 == 0) {
            Fx.ring(at(0, 0.3), Particle.PORTAL, 2 + t / 6.0, 36);
        }
        if (t == 40) {
            Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.6f);
            world.spawnParticle(Particle.EXPLOSION, at(0, 3), 6, 2, 1, 2, 0);
        }
    }

    @Override
    protected void onEnrage() {
        warn("<dark_purple><bold>A Matriarca clama pela colmeia!");
        Fx.worldSound(pos, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.4f);
        cd[Move.SWARM.ordinal()] = 20;
    }

    @Override
    protected void deathStart() {
        endMove();
        Fx.worldSound(pos, Sound.ENTITY_ENDER_DRAGON_DEATH, 2f, 0.7f);
        for (Mob mite : mites) {
            mite.remove();
        }
        mites.clear();
    }

    @Override
    protected void deathAnimate(int t) {
        model.play("stagger");
        if (t % 3 == 0) {
            world.spawnParticle(Particle.PORTAL, at(0, 3.5), 40, 3, 1.8, 3, 0.8);
            world.spawnParticle(Particle.REVERSE_PORTAL, at(-1.3, 7.3), 20, 1, 1, 1, 0.3);
        }
        if (t == 48) {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, at(0, 3), 2, 1, 1, 1, 0);
            Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
        }
    }

    @Override
    protected void cleanup() {
        for (Entity bullet : bullets) {
            bullet.remove();
        }
        for (Mob mite : mites) {
            mite.remove();
        }
        bullets.clear();
        mites.clear();
    }

    // ── Animação ───────────────────────────────────────────────

    @Override
    protected void animate() {
        syncParts();
        if (state == State.DYING || state == State.DORMANT) {
            return;
        }
        String clip;
        if (stagger > 0) {
            clip = "stagger";
        } else if (move == Move.CLAMP) {
            clip = moveT < 26 ? "bite" : "charge";
        } else if (move == Move.VOLLEY) {
            clip = "volley";
        } else if (move == Move.STINGER) {
            clip = "slam";
        } else if (move == Move.SWARM) {
            clip = "roar";
        } else {
            clip = walking ? "walk" : "idle";
        }
        if (state == State.WAKING) {
            return;
        }
        model.play(clip);
        if (age % 3 == 0 && state == State.ACTIVE) {
            world.spawnParticle(Particle.DUST, at(-1.3, 7.3), 1, 0.4, 0.5, 0.4, 0, stagger > 0 ? PINK : PURPLE);
        }
        model.offset("core_glow", hurtTicks > 0 ? (float) (Math.random() - 0.5) * 1.6f : 0f, 0, 0);
    }

    // ── IA ─────────────────────────────────────────────────────

    @Override
    protected void aiTick() {
        for (int i = 1; i < cd.length; i++) {
            if (cd[i] > 0) {
                cd[i]--;
            }
        }
        acidPools();
        tickBullets();
        mites.removeIf(mite -> !mite.isValid());
        if (stagger > 0) {
            stagger--;
            if (stagger % 10 == 0) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, at(-1.3, 7.3), 14, 0.8, 0.8, 0.8, 0.3);
                Fx.worldSound(at(-1.3, 7.3), Sound.BLOCK_BEACON_AMBIENT, 1f, 1.8f);
            }
            if (stagger == 0) {
                exposed = false;
                Fx.worldSound(pos, Sound.ENTITY_RAVAGER_STEP, 1.5f, 0.6f);
            }
            walking = false;
            return;
        }
        if (move == Move.NONE) {
            idle();
            return;
        }
        moveT++;
        switch (move) {
            case CLAMP -> clampAttack();
            case VOLLEY -> volley();
            case STINGER -> stinger();
            case SWARM -> swarm();
            default -> { }
        }
    }

    private void idle() {
        Location at = aim();
        faceTowards(at, 6);
        double dx = at.getX() - pos.getX();
        double dz = at.getZ() - pos.getZ();
        double distance = Math.hypot(dx, dz);
        walking = false;
        if (target != null && distance > 9) {
            moveToward(at.getX(), arena.fy, at.getZ(), enraged ? 0.22 : 0.17);
            walking = true;
        } else if (target != null && distance < 5) {
            moveToward(pos.getX() - dx, arena.fy, pos.getZ() - dz, 0.1);
            walking = true;
        }
        if (--idleTicks <= 0 && target != null) {
            pickMove(distance);
        }
    }

    private void pickMove(double distance) {
        double[] weights = new double[5];
        weights[Move.CLAMP.ordinal()] = cd[1] == 0 ? (distance > 6 ? 3 : 2) : 0;
        weights[Move.VOLLEY.ordinal()] = cd[2] == 0 ? (distance > 10 ? 3 : 1.5) : 0;
        weights[Move.STINGER.ordinal()] = cd[3] == 0 ? (distance < 12 ? 3 : 1) : 0;
        weights[Move.SWARM.ordinal()] = enraged && cd[4] == 0 && mites.size() < 4 ? 4 : 0;
        weights[last.ordinal()] *= 0.25;
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        if (total <= 0) {
            idleTicks = 20;
            return;
        }
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        for (int i = 1; i < weights.length; i++) {
            roll -= weights[i];
            if (roll <= 0) {
                startMove(Move.values()[i]);
                return;
            }
        }
    }

    private void startMove(Move next) {
        move = next;
        last = next;
        moveT = 0;
        struck.clear();
        dashed = 0;
        walking = false;
        if (next == Move.CLAMP) {
            model.restart("bite");
        } else if (next == Move.VOLLEY) {
            model.restart("volley");
        } else if (next == Move.STINGER) {
            model.restart("slam");
        } else if (next == Move.SWARM) {
            model.restart("roar");
        }
    }

    private void endMove() {
        if (move != Move.NONE) {
            cd[move.ordinal()] = (int) (COOLDOWN[move.ordinal()] * (enraged ? 0.7 : 1.0));
        }
        move = Move.NONE;
        idleTicks = enraged ? 30 : 50;
    }

    @Override
    boolean forceMove(String name) {
        if (!active() || move != Move.NONE || stagger > 0) {
            return false;
        }
        for (Move candidate : Move.values()) {
            if (candidate != Move.NONE && candidate.name().equalsIgnoreCase(name)) {
                startMove(candidate);
                return true;
            }
        }
        return false;
    }

    // ── Poças de ácido ─────────────────────────────────────────

    private void acidPools() {
        if (age % 10 != 0) {
            return;
        }
        for (Player player : insidePlayers()) {
            Material under = player.getLocation().getBlock().getType();
            Material below = player.getLocation().subtract(0, 0.2, 0).getBlock().getType();
            if (under != Material.LIME_CARPET && below != Material.LIME_CARPET && below != Material.LIME_CONCRETE_POWDER) {
                continue;
            }
            ItemStack boots = player.getInventory().getBoots();
            if (boots != null && !boots.getEnchantments().isEmpty()) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
        }
    }

    // ── 1. Investida de Mandíbulas ─────────────────────────────

    private void clampAttack() {
        if (moveT < 26) {                                       // 1,2 s de aviso: duas mordidas com faíscas roxas
            faceTowards(aim(), 14);
            Location jaw = at(8.4, 3.4);
            if (moveT % 3 == 0) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, jaw, 10, 0.5, 0.4, 0.5, 0.3);
                world.spawnParticle(Particle.DUST, jaw, 4, 0.5, 0.3, 0.5, 0, PURPLE);
            }
            if (moveT == 4 || moveT == 14) {
                Fx.worldSound(jaw, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.6f, 0.5f);
            }
            if (moveT == 25) {
                double rad = Math.toRadians(yaw);
                dash = new Vector(-Math.sin(rad), 0, Math.cos(rad));
                Fx.worldSound(pos, Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.8f);
            }
            return;
        }
        double step = 0.95;
        double nx = pos.getX() + dash.getX() * step;
        double nz = pos.getZ() + dash.getZ() * step;
        boolean wall = blocked(nx, pos.getY(), nz);
        if (!wall && dashed < 16) {
            pos.setX(nx);
            pos.setZ(nz);
            clamp();
            dashed += step;
            world.spawnParticle(Particle.DUST, at(0, 0.4), 4, 1.6, 0.1, 1.6, 0, PURPLE);
            if (age % 4 == 0) {
                Fx.worldSound(pos, Sound.ENTITY_RAVAGER_STEP, 1.2f, 0.7f);
            }
        }
        Location head = at(6.5, 1.5);
        for (Player player : insidePlayers()) {
            if (struck.contains(player.getUniqueId())) {
                continue;
            }
            if (player.getLocation().add(0, 1, 0).distanceSquared(head) < 3.4 * 3.4) {
                struck.add(player.getUniqueId());
                damage(player, 14);
                player.setVelocity(new Vector(dash.getX() * 0.9, 1.1, dash.getZ() * 0.9));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                Fx.worldSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.4f, 0.6f);
                world.spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 24, 0.4, 0.5, 0.4, 0.5);
            }
        }
        if (wall || dashed >= 16) {
            if (wall) {
                Fx.worldSound(head, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
                world.spawnParticle(Particle.EXPLOSION, head, 2, 0.5, 0.5, 0.5, 0);
            }
            if (moveT > 26 + (int) (dashed / step) + 14) {
                endMove();
            } else if (moveT > 26 + 30) {
                endMove();
            }
        }
    }

    // ── 2. Rajada de Espinhos de Coro ──────────────────────────

    private void volley() {
        faceTowards(aim(), 8);
        if (moveT == 14) {
            Fx.worldSound(pos, Sound.ENTITY_SHULKER_SHOOT, 2f, 0.6f);
            List<Player> inside = insidePlayers();
            Location origin = at(-1, 5.8);
            for (int i = 0; i < 6; i++) {
                double fan = (i - 2.5) * 0.28;
                double rad = Math.toRadians(yaw) + fan;
                Vector dir = new Vector(-Math.sin(rad), 0.25, Math.cos(rad)).normalize();
                Player victim = inside.isEmpty() ? null : inside.get(i % inside.size());
                int index = i;
                ShulkerBullet bullet = world.spawn(origin.clone().add(dir.clone().multiply(1.5)), ShulkerBullet.class, b -> {
                    b.setPersistent(false);
                    b.setSilent(true);
                    b.setTarget(victim);
                    b.setVelocity(dir.clone().multiply(0.5));
                    b.getPersistentDataContainer().set(manager.projectileKey(), PersistentDataType.DOUBLE, 8.0);
                    b.getPersistentDataContainer().set(manager.partKey(), PersistentDataType.BYTE, (byte) 1);
                });
                bullets.add(bullet);
                track(bullet);
            }
        }
        if (moveT >= 34) {
            endMove();
        }
    }

    private void tickBullets() {
        if (bullets.isEmpty()) {
            return;
        }
        bullets.removeIf(b -> {
            if (!b.isValid()) {
                return true;
            }
            world.spawnParticle(Particle.DUST, b.getLocation(), 2, 0.1, 0.1, 0.1, 0, PURPLE);
            if (b.getTicksLived() > 160) {
                b.remove();
                return true;
            }
            return false;
        });
    }

    private void dustRing(Location center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            world.spawnParticle(Particle.DUST, center.getX() + Math.cos(angle) * radius, center.getY(),
                    center.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0, PURPLE);
        }
    }

    // ── 3. Pulso Gravitacional do Ferrão ───────────────────────

    private void stinger() {
        Location base = at(-9.5, 0.2);
        if (moveT < 36) {                                       // anéis roxos concêntricos fecham sobre o ferrão
            if (moveT % 3 == 0) {
                double r = 10 - (moveT / 36.0) * 9;
                dustRing(base, r, 48);
                Fx.ring(base, Particle.PORTAL, Math.max(0.5, r - 0.6), 24);
            }
            world.spawnParticle(Particle.DUST, base.clone().add(0, 0.4, 0), 3, 0.5, 0.2, 0.5, 0, PINK);
            if (moveT == 1) {
                Fx.worldSound(base, Sound.BLOCK_BEACON_POWER_SELECT, 2f, 0.5f);
            }
            warn("<light_purple>O ferrão carrega... <gray>salte no último instante!");
            return;
        }
        if (moveT == 36) {
            Fx.worldSound(base, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
            Fx.worldSound(base, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.6f);
            for (double r = 1; r <= 14; r += 1.5) {
                Fx.ring(base, Particle.EXPLOSION, r, (int) (r * 5));
                Fx.ring(base, Particle.WITCH, r, (int) (r * 6));
            }
            for (Player player : insidePlayers()) {
                double dx = player.getLocation().getX() - base.getX();
                double dz = player.getLocation().getZ() - base.getZ();
                if (Math.hypot(dx, dz) <= 14 && player.isOnGround()) {
                    damage(player, 16);
                    Vector away = new Vector(dx, 0, dz).normalize().multiply(0.6).setY(0.5);
                    player.setVelocity(away);
                }
            }
        }
        if (moveT >= 50) {
            endMove();
        }
    }

    // ── 4. Enxame da Colmeia ───────────────────────────────────

    private void swarm() {
        if (moveT == 1) {
            Fx.worldSound(pos, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.35f);
        }
        if (moveT == 20) {
            int count = 4 + ThreadLocalRandom.current().nextInt(3);
            for (int i = 0; i < count && mites.size() < 8; i++) {
                double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
                double radius = 3 + ThreadLocalRandom.current().nextDouble() * (arena.half - 6);
                Location from = arena.center().add(Math.cos(angle) * radius, arena.height - 5, Math.sin(angle) * radius);
                world.spawnParticle(Particle.DUST, from, 12, 0.4, 0.4, 0.4, 0, PURPLE);
                boolean mite = i % 2 == 0;
                Mob spawned = mite ? world.spawn(from, Endermite.class, e -> setupMite(e)) : world.spawn(from, Silverfish.class,
                        e -> setupMite(e));
                mites.add(spawned);
                track(spawned);
            }
        }
        if (moveT >= 50) {
            endMove();
        }
    }

    private void setupMite(Mob mob) {
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);
        mob.customName(dev.vertexx.core.Msg.mm("<dark_purple>Ácaro do Vazio"));
        AttributeInstance damage = mob.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(4);
        }
        AttributeInstance speed = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * 1.6);
        }
        if (target != null) {
            mob.setTarget(target);
        }
    }
}
