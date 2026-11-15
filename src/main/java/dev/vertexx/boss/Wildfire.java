package dev.vertexx.boss;

import dev.vertexx.core.Fx;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

/**
 * Wildfire, o Blaze chefe removido da Mojang: um corpo dourado que flutua cercado por quatro escudos
 * giratórios que bloqueiam projéteis. Ataques: Onda de Choque, Chuva de Bolas de Fogo e Investida Aérea.
 */
final class Wildfire extends BossFight {

    private enum Move { NONE, SHOCKWAVE, RUSH, LUNGE }

    private static final Particle.DustOptions ORANGE = new Particle.DustOptions(Color.fromRGB(255, 140, 20), 1.4f);
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(255, 40, 20), 1.6f);

    private Move move = Move.NONE;
    private Move last = Move.NONE;
    private int moveT;
    private int cooldown = 50;

    private double hover = 1.0;
    private double hoverGoal = 1.0;
    private double hoverSpeed = 0.15;
    private float shieldAngle;
    private float shieldSpeed = 0.8f;
    private float shieldSpeedGoal = 0.8f;
    private double shieldRadius = 2.1;
    private double shieldRadiusGoal = 2.1;
    private double shieldTilt = 8;
    private double shieldTiltGoal = 8;
    private final int[] flash = new int[4];
    private final Rig.Part[][] shields = new Rig.Part[4][4];
    private Rig.Part eye;
    private Rig.Part pupil;
    private Rig.Part[] body;
    private boolean eyeOpen;

    // Onda de choque
    private final double[] waveR = {-1, -1};
    private final List<Set<UUID>> waveHit = List.of(new HashSet<>(), new HashSet<>());
    private boolean slammed;
    private int slamT;

    // Investida
    private int phase;
    private int phaseT;
    private double launchX;
    private double launchZ;
    private Vector dash = new Vector(0, 0, 1);
    private Player grabbed;
    private boolean missed;

    Wildfire(BossManager manager, Arena arena) {
        super(manager, arena);
    }

    @Override
    protected double startHeight() {
        return 1.0;
    }

    @Override
    protected double baseHealth() {
        return plugin.getConfig().getDouble("bosses.wildfire.health", 450);
    }

    @Override
    protected double bodyRadius() {
        return 1.1;
    }

    @Override
    protected double bodyHeight() {
        return 3.4;
    }

    @Override
    protected double hitboxScale() {
        return 1.2;
    }

    @Override
    protected String title() {
        return "Guardião da Fortaleza";
    }

    @Override
    protected String subtitle() {
        return "<gold>O guardião da fortaleza desperta";
    }

    @Override
    protected int wakeDuration() {
        return 55;
    }

    @Override
    List<String> moves() {
        return List.of("shockwave", "rush", "lunge");
    }

    // ── Modelo ─────────────────────────────────────────────────

    @Override
    protected void buildRig() {
        body = new Rig.Part[] {
                rig.add(Material.ORANGE_TERRACOTTA, 0, 0.6f, 0, 0.95f, 0.6f, 0.95f),
                rig.add(Material.ORANGE_CONCRETE, 0, 1.2f, 0, 0.95f, 0.6f, 0.95f),
                rig.add(Material.RAW_GOLD_BLOCK, 0, 1.8f, 0, 0.95f, 0.6f, 0.95f),
                rig.add(Material.GOLD_BLOCK, 0, 2.4f, 0, 0.95f, 0.6f, 0.95f)};
        // gotejados marrons na base, como no modelo original
        rig.add(Material.BROWN_TERRACOTTA, 0.36f, 0.2f, 0.36f, 0.22f, 0.5f, 0.22f);
        rig.add(Material.BROWN_TERRACOTTA, -0.36f, 0.15f, 0.36f, 0.22f, 0.6f, 0.22f);
        rig.add(Material.BROWN_TERRACOTTA, 0.36f, 0.12f, -0.36f, 0.22f, 0.65f, 0.22f);
        rig.add(Material.BROWN_TERRACOTTA, -0.36f, 0.2f, -0.36f, 0.22f, 0.5f, 0.22f);
        rig.add(Material.BROWN_TERRACOTTA, 0, 0.1f, 0, 0.3f, 0.45f, 0.3f);
        // cabeça: máscara escura com um olho vermelho e coroa dourada
        rig.add(Material.DARK_OAK_PLANKS, 0, 3.15f, 0, 0.95f, 0.9f, 0.95f);
        rig.add(Material.GOLD_BLOCK, 0, 3.7f, 0, 1.02f, 0.24f, 1.02f);
        rig.add(Material.RAW_GOLD_BLOCK, 0.34f, 3.95f, 0, 0.2f, 0.3f, 0.2f);
        rig.add(Material.RAW_GOLD_BLOCK, -0.34f, 3.95f, 0, 0.2f, 0.3f, 0.2f);
        rig.add(Material.BROWN_TERRACOTTA, 0, 2.82f, 0.2f, 0.7f, 0.12f, 0.6f);
        eye = rig.add(Material.BLACK_CONCRETE, 0, 3.2f, 0.49f, 0.3f, 0.3f, 0.06f);
        pupil = rig.add(Material.WHITE_CONCRETE, 0, 3.2f, 0.53f, 0.1f, 0.12f, 0.05f);
        // escudos: três placas empilhadas (amarelo → laranja) e um pingo marrom
        for (int i = 0; i < 4; i++) {
            shields[i][0] = rig.add(Material.GOLD_BLOCK, 0, 0, 0, 1.25f, 0.9f, 0.2f).world();
            shields[i][1] = rig.add(Material.RAW_GOLD_BLOCK, 0, 0, 0, 1.25f, 0.8f, 0.2f).world();
            shields[i][2] = rig.add(Material.ORANGE_CONCRETE, 0, 0, 0, 1.25f, 0.7f, 0.2f).world();
            shields[i][3] = rig.add(Material.BROWN_TERRACOTTA, 0, 0, 0, 0.5f, 0.45f, 0.2f).world();
        }
    }

    @Override
    protected void idlePose() {
        shieldSpeed = 0.8f;
        shieldSpeedGoal = 0.8f;
        hover = 1.0;
        eyeOpen = false;
        pos.setY(arena.fy + hover + Math.sin(age * 0.08) * 0.06);
    }

    private void applyHover() {
        double diff = hoverGoal - hover;
        hover += Math.max(-hoverSpeed, Math.min(hoverSpeed, diff));
        double bob = move == Move.NONE || move == Move.RUSH ? Math.sin(age * 0.12) * 0.14 : 0;
        pos.setY(arena.fy + hover + bob);
    }

    @Override
    protected void animate() {
        shieldAngle += shieldSpeed * 2;
        shieldSpeed += (shieldSpeedGoal - shieldSpeed) * 0.12f;
        shieldRadius += (shieldRadiusGoal - shieldRadius) * 0.25;
        shieldTilt += (shieldTiltGoal - shieldTilt) * 0.2;

        for (int i = 0; i < 4; i++) {
            double aw = Math.toRadians(shieldAngle + 90 * i);
            double radius = shieldRadius + (flash[i] > 0 ? 0.25 : 0);
            Quaternionf plate = new Quaternionf().rotateY((float) (Math.PI / 2 - aw))
                    .rotateX((float) Math.toRadians(shieldTilt + (state == State.DYING ? stateTicks * 1.5 : 0)));
            float cx = (float) (Math.cos(aw) * radius);
            float cz = (float) (Math.sin(aw) * radius);
            float cy = 1.75f + (float) Math.sin((age + i * 11) * 0.09) * 0.1f;
            float grow = flash[i] > 0 ? 1.12f : 1f;
            float[] offsets = {0.85f, 0f, -0.8f, -1.35f};
            float[] tangent = {0f, 0f, 0f, 0.3f};
            for (int k = 0; k < 4; k++) {
                org.joml.Vector3f local = plate.transform(new org.joml.Vector3f(tangent[k], offsets[k], 0));
                Rig.Part part = shields[i][k];
                part.pos.set(cx + local.x, cy + local.y, cz + local.z);
                part.rot.set(plate);
                part.size.set(k == 3 ? 0.5f : 1.25f, new float[] {0.9f, 0.8f, 0.7f, 0.45f}[k], 0.2f).mul(grow);
            }
            if (flash[i] > 0) {
                flash[i]--;
            }
        }
        if (eye != null) {
            eye.material(eyeOpen ? Material.RED_CONCRETE : Material.BLACK_CONCRETE);
            pupil.visible = eyeOpen;
            float pulse = eyeOpen ? 1f + (float) Math.sin(age * 0.4) * 0.12f : 1f;
            eye.size.set(0.3f * pulse, 0.3f * pulse, 0.06f);
        }
        float shake = hurtTicks > 0 ? 0.03f : 0f;
        for (Rig.Part part : body) {
            part.pos.x = (hurtTicks > 0 ? (float) (Math.random() - 0.5) * 2 * shake : 0);
        }
        // brasas flutuando ao redor do corpo
        if (age % 4 == 0) {
            Location around = pos.clone().add(Math.random() * 1.4 - 0.7, 0.4 + Math.random() * 2.8, Math.random() * 1.4 - 0.7);
            world.spawnParticle(Particle.SMALL_FLAME, around, 1, 0, 0.05, 0, 0.01);
        }
        if (age % 10 == 0 && state != State.DORMANT) {
            world.spawnParticle(Particle.LAVA, pos.clone().add(0, 0.5, 0), 1, 0.4, 0.2, 0.4, 0);
        }
    }

    // ── Despertar ──────────────────────────────────────────────

    @Override
    protected void wakeTick(int t) {
        if (t == 1) {
            Fx.worldSound(pos, Sound.ENTITY_WITHER_SPAWN, 0.9f, 1.5f);
            Fx.worldSound(pos, Sound.ENTITY_BLAZE_AMBIENT, 1.4f, 0.5f);
        }
        hover = 1.0 + 2.2 * ease(t / 42.0);
        pos.setY(arena.fy + hover);
        shieldSpeedGoal = t < 36 ? 16 : 4;
        double angle = t * 0.55;
        for (int arm = 0; arm < 3; arm++) {
            double a = angle + arm * Math.PI * 2 / 3;
            double radius = 2.8 - t * 0.04;
            world.spawnParticle(Particle.FLAME, pos.getX() + Math.cos(a) * radius, pos.getY() + 0.4 + t * 0.05,
                    pos.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0.01);
        }
        if (t == 22) {
            eyeOpen = true;
            Fx.worldSound(pos, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.6f);
            particles(Particle.FLAME, pos.clone().add(0, 3.2, 0), 40, 0.4, 0.4, 0.4, 0.08);
        }
        if (t == 48) {
            Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
            particles(Particle.EXPLOSION, pos.clone().add(0, 1.6, 0), 4, 1.2, 0.8, 1.2, 0);
            Fx.ring(pos.clone().add(0, 0.2, 0), Particle.FLAME, 5, 60);
        }
        hoverGoal = 3.2;
    }

    // ── IA ─────────────────────────────────────────────────────

    @Override
    protected void aiTick() {
        applyHover();
        interceptProjectiles();
        if (move == Move.NONE) {
            idle();
            return;
        }
        moveT++;
        switch (move) {
            case SHOCKWAVE -> shockwave();
            case RUSH -> rush();
            case LUNGE -> lunge();
            default -> { }
        }
    }

    private void idle() {
        shieldSpeedGoal = enraged ? 6f : 3.5f;
        shieldRadiusGoal = 2.1;
        shieldTiltGoal = 8;
        hoverGoal = 3.2;
        hoverSpeed = 0.15;
        Location at = aim();
        faceTowards(at, 10);
        double cx = arena.cx + 0.5;
        double cz = arena.cz + 0.5;
        double gx = cx;
        double gz = cz;
        if (target != null) {
            double dx = pos.getX() - at.getX();
            double dz = pos.getZ() - at.getZ();
            double length = Math.max(0.01, Math.hypot(dx, dz));
            double swing = Math.sin(age * 0.025) * 0.8;
            double ux = dx / length;
            double uz = dz / length;
            gx = at.getX() + (ux * Math.cos(swing) - uz * Math.sin(swing)) * 8;
            gz = at.getZ() + (ux * Math.sin(swing) + uz * Math.cos(swing)) * 8;
        }
        moveToward(gx, arena.fy + hover, gz, enraged ? 0.14 : 0.10);
        if (--cooldown <= 0 && target != null) {
            pickMove();
        }
    }

    private void pickMove() {
        double distance = Math.hypot(target.getLocation().getX() - pos.getX(), target.getLocation().getZ() - pos.getZ());
        double[] weights = {0, distance < 10 ? 3 : 1, distance > 6 ? 3 : 2, distance > 5 ? 3 : 1.5};
        weights[last.ordinal()] *= 0.15;
        double total = 0;
        for (int i = 1; i < weights.length; i++) {
            total += weights[i];
        }
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        for (int i = 1; i < weights.length; i++) {
            roll -= weights[i];
            if (roll <= 0) {
                startMove(Move.values()[i]);
                return;
            }
        }
        startMove(Move.SHOCKWAVE);
    }

    private void startMove(Move next) {
        move = next;
        last = next;
        moveT = 0;
        slammed = false;
        slamT = 0;
        waveR[0] = -1;
        waveR[1] = -1;
        waveHit.get(0).clear();
        waveHit.get(1).clear();
        phase = 0;
        phaseT = 0;
        grabbed = null;
        missed = false;
    }

    private void endMove() {
        move = Move.NONE;
        cooldown = enraged ? 35 : 60;
        hoverSpeed = 0.15;
    }

    @Override
    boolean forceMove(String name) {
        if (!active() || move != Move.NONE) {
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

    // ── Onda de choque ─────────────────────────────────────────

    private void shockwave() {
        int t = moveT;
        faceTowards(aim(), 8);
        if (t < 26) {
            hoverGoal = 5.8;
            hoverSpeed = 0.2;
            shieldSpeedGoal = 18;
            shieldRadiusGoal = 1.8;
            shieldTiltGoal = 4;
            if (t == 1) {
                Fx.worldSound(pos, Sound.ENTITY_BLAZE_AMBIENT, 1.8f, 0.5f);
                warn("<red><bold>⚠ Onda de choque!</bold> <gray>Pule quando a chama chegar.");
            }
            if (t == 14) {
                Fx.worldSound(pos, Sound.ENTITY_BLAZE_SHOOT, 1.6f, 0.7f);
            }
            double r = 3.5 * (1 - t / 26.0) + 0.8;
            for (int k = 0; k < 6; k++) {
                double a = t * 0.6 + k * Math.PI / 3;
                world.spawnParticle(Particle.FLAME, pos.getX() + Math.cos(a) * r, pos.getY() + 1.2 + Math.sin(t * 0.3 + k),
                        pos.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0.02);
            }
            if (t % 6 == 0) {
                telegraphRing(arena.half - 1.2, Particle.SMALL_FLAME);
            }
        } else if (!slammed) {
            hoverGoal = 0.5;
            hoverSpeed = 1.5;
            slamT++;
            world.spawnParticle(Particle.FLAME, pos.clone().add(0, 1.5, 0), 6, 0.3, 0.8, 0.3, 0.02);
            if (hover <= 1.0 || slamT > 8) {
                slammed = true;
                waveR[0] = 1.2;
                Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.55f);
                Fx.worldSound(pos, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.5f);
                Location ground = pos.clone();
                ground.setY(arena.fy + 0.2);
                world.spawnParticle(Particle.EXPLOSION, ground, 3, 0.8, 0.1, 0.8, 0);
                world.spawnParticle(Particle.LAVA, ground, 25, 1.2, 0.1, 1.2, 0);
                world.spawnParticle(Particle.BLOCK, ground, 40, 1.2, 0.1, 1.2, 0.1, Material.NETHERRACK.createBlockData());
            }
        } else {
            for (int w = 0; w < 2; w++) {
                if (waveR[w] >= 0) {
                    advanceWave(w);
                }
            }
            if (enraged && waveR[1] < 0 && waveR[0] > 6 && waveR[0] < 8) {
                waveR[1] = 1.2;
                Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
            }
            if (waveR[0] < 0 && waveR[1] < 0) {
                if (++phaseT > 16) {
                    hoverGoal = 3.2;
                    hoverSpeed = 0.15;
                    endMove();
                }
            }
        }
    }

    private void telegraphRing(double radius, Particle particle) {
        double cx = pos.getX();
        double cz = pos.getZ();
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            world.spawnParticle(particle, cx + Math.cos(a) * radius, arena.fy + 0.15, cz + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    private void advanceWave(int w) {
        double r = waveR[w] += 0.85;
        if (r > arena.half + 2) {
            waveR[w] = -2;
            return;
        }
        double cx = pos.getX();
        double cz = pos.getZ();
        int points = (int) Math.min(150, r * 2 * Math.PI * 2.0);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points + w;
            double x = cx + Math.cos(a) * r;
            double z = cz + Math.sin(a) * r;
            world.spawnParticle(Particle.FLAME, x, arena.fy + 0.2, z, 1, 0.05, 0.1, 0.05, 0.01);
            if (i % 2 == 0) {
                world.spawnParticle(Particle.FLAME, x, arena.fy + 0.7, z, 1, 0.05, 0.15, 0.05, 0.02);
            }
            if (i % 5 == 0) {
                world.spawnParticle(Particle.LARGE_SMOKE, x, arena.fy + 1.1, z, 1, 0.1, 0.1, 0.1, 0.01);
            }
            if (i % 9 == 0) {
                world.spawnParticle(Particle.LAVA, x, arena.fy + 0.3, z, 1, 0, 0, 0, 0);
            }
        }
        if ((int) (r / 0.85) % 3 == 0) {
            Fx.worldSound(pos, Sound.BLOCK_FIRE_AMBIENT, 1.5f, 0.6f);
        }
        for (Player player : insidePlayers()) {
            if (waveHit.get(w).contains(player.getUniqueId())) {
                continue;
            }
            double d = Math.hypot(player.getLocation().getX() - cx, player.getLocation().getZ() - cz);
            boolean grounded = player.getLocation().getY() - arena.fy < 1.0;
            if (Math.abs(d - r) < 1.4) {
                waveHit.get(w).add(player.getUniqueId());
                if (grounded) {
                    damage(player, plugin.getConfig().getDouble("bosses.wildfire.shockwave-damage", 9));
                    player.setFireTicks(Math.max(player.getFireTicks(), 100));
                    Vector push = player.getLocation().toVector().subtract(pos.toVector()).setY(0);
                    if (push.lengthSquared() > 0.01) {
                        push.normalize().multiply(0.9);
                    }
                    player.setVelocity(push.setY(0.45));
                    Fx.sound(player, Sound.ENTITY_PLAYER_HURT_ON_FIRE, 1f, 1f);
                } else {
                    player.sendActionBar(dev.vertexx.core.Msg.mm("<green>✔ Você pulou a onda!"));
                }
            }
        }
    }

    // ── Chuva de bolas de fogo ─────────────────────────────────

    private void rush() {
        int t = moveT;
        faceTowards(aim(), 14);
        hoverGoal = 4.6;
        hoverSpeed = 0.2;
        shieldSpeedGoal = 11;
        shieldRadiusGoal = 3.0;
        shieldTiltGoal = 28;
        int[] times = enraged ? new int[] {20, 34, 48, 62} : new int[] {20, 36, 52};
        if (t == 1) {
            Fx.worldSound(pos, Sound.ENTITY_BLAZE_AMBIENT, 1.6f, 0.7f);
            warn("<gold><bold>☄ Chuva de fogo!</bold> <gray>Desvie das chamas.");
        }
        // gira em volta do alvo enquanto atira
        Location at = aim();
        double a = Math.atan2(pos.getZ() - at.getZ(), pos.getX() - at.getX()) + 0.035;
        moveToward(at.getX() + Math.cos(a) * 9, arena.fy + hover, at.getZ() + Math.sin(a) * 9, 0.18);
        for (int i = 0; i < times.length; i++) {
            if (t == times[i] - 8) {
                for (int s = 0; s < 4; s++) {
                    flash[s] = 8;
                }
                Fx.worldSound(pos, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f + i * 0.15f);
            }
            if (t == times[i]) {
                fireWave(i);
            }
        }
        if (t >= times[times.length - 1] + 26) {
            endMove();
        }
    }

    private void fireWave(int index) {
        Location origin = pos.clone().add(0, 2.0, 0);
        Vector forward = aim().toVector().subtract(origin.toVector());
        if (forward.lengthSquared() < 0.01) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        double damage = plugin.getConfig().getDouble("bosses.wildfire.fireball-damage", 5);
        Fx.worldSound(pos, Sound.ENTITY_BLAZE_SHOOT, 1.8f, 0.8f);
        Fx.worldSound(pos, Sound.ENTITY_GHAST_SHOOT, 0.9f, 1.4f);
        world.spawnParticle(Particle.FLAME, origin, 30, 0.6, 0.6, 0.6, 0.06);
        world.spawnParticle(Particle.EXPLOSION, origin, 1, 0, 0, 0, 0);
        if (index % 2 == 0) {
            int count = enraged ? 9 : 7;
            for (int k = 0; k < count; k++) {
                double angle = Math.toRadians((k - (count - 1) / 2.0) * 9);
                Vector dir = forward.clone().rotateAroundY(angle);
                shoot(origin, dir, 0.85, damage);
            }
        } else {
            int count = enraged ? 16 : 12;
            for (int k = 0; k < count; k++) {
                double angle = Math.PI * 2 * k / count + index * 0.2;
                Vector dir = new Vector(Math.cos(angle), -0.14, Math.sin(angle));
                shoot(origin, dir.normalize(), 0.7, damage);
            }
        }
    }

    private void shoot(Location origin, Vector dir, double speed, double damage) {
        SmallFireball fireball = world.spawn(origin.clone().add(dir.clone().multiply(1.2)), SmallFireball.class, fb -> {
            fb.setShooter(hitbox);
            fb.setIsIncendiary(false);
            fb.setYield(0f);
            fb.setDirection(dir.clone().multiply(speed));
            fb.setVelocity(dir.clone().multiply(speed));
            fb.getPersistentDataContainer().set(manager.projectileKey(), PersistentDataType.DOUBLE, damage);
        });
        track(fireball);
    }

    // ── Investida aérea ────────────────────────────────────────

    private Location bodyCenter() {
        return pos.clone().add(0, 1.6, 0);
    }

    private void lunge() {
        phaseT++;
        switch (phase) {
            case 0 -> lungeWindup();
            case 1 -> lungeDash();
            case 2 -> lungeCarry();
            default -> lungeRecover();
        }
    }

    private void lungeWindup() {
        int t = phaseT;
        hoverGoal = 6.0;
        hoverSpeed = 0.25;
        shieldSpeedGoal = 22;
        shieldRadiusGoal = 1.5;
        shieldTiltGoal = 0;
        Location at = aim();
        if (t == 1) {
            double dx = pos.getX() - at.getX();
            double dz = pos.getZ() - at.getZ();
            double length = Math.max(0.01, Math.hypot(dx, dz));
            launchX = at.getX() + dx / length * 11;
            launchZ = at.getZ() + dz / length * 11;
            Fx.worldSound(pos, Sound.ENTITY_BLAZE_AMBIENT, 1.8f, 0.4f);
            warn("<red><bold>⚠ Investida aérea!</bold> <gray>Saia da linha de fogo.");
        }
        moveToward(launchX, arena.fy + hover, launchZ, 0.55);
        faceTowards(at, 24);
        if (t >= 12 && t < 26) {
            Location from = bodyCenter();
            Vector line = at.toVector().subtract(from.toVector());
            double length = line.length();
            if (length > 0.1) {
                line.normalize();
                for (double d = 1.5; d < Math.min(length + 4, 22); d += 0.8) {
                    Location p = from.clone().add(line.clone().multiply(d));
                    world.spawnParticle(Particle.DUST, p, 1, 0.03, 0.03, 0.03, 0, t > 20 ? RED : ORANGE);
                }
            }
            if (t == 12 && target != null) {
                target.sendActionBar(dev.vertexx.core.Msg.mm("<red><bold>⚠ Ele está mirando em você!"));
                Fx.sound(target, Sound.BLOCK_NOTE_BLOCK_BIT, 1.2f, 0.6f);
            }
        }
        if (t == 26) {
            Location from = bodyCenter();
            Vector toward = aim().toVector().subtract(from.toVector());
            dash = toward.lengthSquared() < 0.01 ? new Vector(0, 0, 1) : toward.normalize();
            phase = 1;
            phaseT = 0;
            Fx.worldSound(pos, Sound.ENTITY_BLAZE_SHOOT, 2f, 0.5f);
            Fx.worldSound(pos, Sound.ENTITY_PHANTOM_SWOOP, 1.5f, 0.6f);
            hoverSpeed = 0;
        }
    }

    private void lungeDash() {
        double nextX = pos.getX() + dash.getX() * 1.5;
        double nextY = pos.getY() + dash.getY() * 1.5;
        double nextZ = pos.getZ() + dash.getZ() * 1.5;
        boolean wall = blocked(nextX, nextY, nextZ);
        if (!wall) {
            pos.add(dash.clone().multiply(1.5));
            double wantedX = pos.getX();
            double wantedZ = pos.getZ();
            clamp();
            wall = Math.abs(wantedX - pos.getX()) > 0.01 || Math.abs(wantedZ - pos.getZ()) > 0.01;
        }
        hover = pos.getY() - arena.fy;
        hoverGoal = hover;
        faceTowards(pos.clone().add(dash), 40);
        Location center = bodyCenter();
        world.spawnParticle(Particle.FLAME, center, 14, 0.5, 0.7, 0.5, 0.03);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 3, 0.4, 0.5, 0.4, 0.01);
        world.spawnParticle(Particle.LAVA, center, 1, 0.3, 0.3, 0.3, 0);
        for (Player player : insidePlayers()) {
            Location body = player.getLocation().add(0, 0.9, 0);
            if (body.distanceSquared(center) < 2.4 * 2.4 || body.distanceSquared(center.clone().subtract(dash.clone().multiply(0.8))) < 2.0 * 2.0) {
                grabbed = player;
                phase = 2;
                phaseT = 0;
                Fx.worldSound(pos, Sound.ENTITY_BLAZE_HURT, 1.6f, 0.5f);
                Fx.worldSound(pos, Sound.ENTITY_PLAYER_HURT_ON_FIRE, 1.4f, 0.7f);
                player.setFireTicks(140);
                return;
            }
        }
        if (phaseT >= 22 || wall) {
            missed = true;
            double grazeRadius = 3.4;
            for (Player player : insidePlayers()) {
                if (player.getLocation().add(0, 0.9, 0).distanceSquared(bodyCenter()) < grazeRadius * grazeRadius) {
                    damage(player, plugin.getConfig().getDouble("bosses.wildfire.graze-damage", 3));
                    player.setFireTicks(Math.max(player.getFireTicks(), 40));
                }
            }
            exposed = true;
            exposedTicks = 70;
            phase = 3;
            phaseT = 0;
            Fx.worldSound(pos, Sound.ENTITY_BLAZE_DEATH, 1.2f, 0.7f);
            Fx.worldSound(pos, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.6f);
            world.spawnParticle(Particle.LARGE_SMOKE, bodyCenter(), 30, 0.7, 0.8, 0.7, 0.04);
            warn("<yellow><bold>✦ Wildfire está exposto!</bold> <gray>Ataque agora (+50% de dano).");
        }
    }

    private void lungeCarry() {
        if (grabbed == null || !grabbed.isOnline() || grabbed.isDead()) {
            phase = 3;
            phaseT = 0;
            return;
        }
        shieldSpeedGoal = 26;
        Location center = bodyCenter();
        Vector toBoss = center.toVector().subtract(grabbed.getLocation().add(0, 0.9, 0).toVector());
        grabbed.setVelocity(toBoss.multiply(0.55));
        grabbed.setFireTicks(140);
        pos.add(dash.clone().multiply(0.25));
        clamp();
        world.spawnParticle(Particle.FLAME, grabbed.getLocation().add(0, 1, 0), 10, 0.4, 0.6, 0.4, 0.02);
        world.spawnParticle(Particle.LAVA, grabbed.getLocation().add(0, 1, 0), 1, 0.3, 0.4, 0.3, 0);
        if (phaseT == 1) {
            grabbed.sendActionBar(dev.vertexx.core.Msg.mm("<red><bold>Você foi agarrado!"));
        }
        if (phaseT >= 14) {
            Vector back = dash.clone().multiply(-1.25).setY(0.7);
            grabbed.setVelocity(back);
            damage(grabbed, plugin.getConfig().getDouble("bosses.wildfire.lunge-damage", 8));
            grabbed.setFireTicks(Math.max(grabbed.getFireTicks(), 120));
            Fx.worldSound(grabbed.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
            world.spawnParticle(Particle.EXPLOSION, grabbed.getLocation().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0);
            world.spawnParticle(Particle.FLAME, grabbed.getLocation().add(0, 1, 0), 40, 0.5, 0.6, 0.5, 0.15);
            grabbed = null;
            phase = 3;
            phaseT = 0;
        }
    }

    private void lungeRecover() {
        if (missed) {
            hoverGoal = 1.4;
            hoverSpeed = 0.2;
            shieldSpeedGoal = 0.4f;
            shieldRadiusGoal = 3.8;
            shieldTiltGoal = 72;
            if (phaseT % 6 == 0) {
                world.spawnParticle(Particle.CRIT, pos.clone().add(0, 3.6, 0), 6, 0.4, 0.2, 0.4, 0.1);
                world.spawnParticle(Particle.SMOKE, pos.clone().add(0, 1.5, 0), 5, 0.5, 0.6, 0.5, 0.01);
            }
            if (phaseT >= 72) {
                eyeOpen = true;
                shieldTiltGoal = 8;
                shieldRadiusGoal = 2.1;
                endMove();
            }
        } else {
            hoverGoal = 3.2;
            hoverSpeed = 0.2;
            if (phaseT >= 22) {
                endMove();
            }
        }
    }

    // ── Escudos ────────────────────────────────────────────────

    private Location shieldAt(int index, double radius) {
        double aw = Math.toRadians(shieldAngle + 90 * index);
        return pos.clone().add(Math.cos(aw) * radius, 1.75, Math.sin(aw) * radius);
    }

    private void blockEffect(int index, Location at) {
        flash[index] = 8;
        Fx.worldSound(at, Sound.ITEM_SHIELD_BLOCK, 1.3f, 0.8f);
        Fx.worldSound(at, Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.6f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, at, 14, 0.3, 0.4, 0.3, 0.2);
        world.spawnParticle(Particle.FLAME, at, 8, 0.2, 0.3, 0.2, 0.05);
    }

    /** Verdadeiro se um dos escudos está no caminho de um projétil vindo de {@code from}. */
    private boolean shieldCovers(double fromX, double fromZ) {
        double theta = Math.atan2(fromZ - pos.getZ(), fromX - pos.getX());
        for (int i = 0; i < 4; i++) {
            double aw = Math.toRadians(shieldAngle + 90 * i);
            double diff = Math.atan2(Math.sin(theta - aw), Math.cos(theta - aw));
            if (Math.abs(diff) < Math.toRadians(34)) {
                blockEffect(i, shieldAt(i, shieldRadius));
                return true;
            }
        }
        return false;
    }

    @Override
    boolean blocksProjectile(Projectile projectile) {
        if (!active() || exposed || move == Move.LUNGE && phase >= 1 && phase < 3) {
            return false;
        }
        if (projectile.getShooter() instanceof Entity shooter) {
            return shieldCovers(shooter.getLocation().getX(), shooter.getLocation().getZ());
        }
        Vector v = projectile.getVelocity();
        return shieldCovers(pos.getX() - v.getX() * 8, pos.getZ() - v.getZ() * 8);
    }

    /** Projéteis lentos que tocam o anel de escudos também são barrados. */
    private void interceptProjectiles() {
        if (exposed || move == Move.LUNGE && phase >= 1 && phase < 3 || age % 2 != 0) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(pos.clone().add(0, 1.7, 0), 4.5, 3, 4.5)) {
            if (!(entity instanceof Projectile projectile) || projectile.getShooter() == hitbox
                    || projectile.getPersistentDataContainer().has(manager.projectileKey())) {
                continue;
            }
            double distance = Math.hypot(projectile.getLocation().getX() - pos.getX(),
                    projectile.getLocation().getZ() - pos.getZ());
            if (distance < shieldRadius - 1.0 || distance > shieldRadius + 1.4) {
                continue;
            }
            double theta = Math.atan2(projectile.getLocation().getZ() - pos.getZ(),
                    projectile.getLocation().getX() - pos.getX());
            for (int i = 0; i < 4; i++) {
                double aw = Math.toRadians(shieldAngle + 90 * i);
                double diff = Math.atan2(Math.sin(theta - aw), Math.cos(theta - aw));
                if (Math.abs(diff) < Math.toRadians(24)) {
                    blockEffect(i, projectile.getLocation());
                    projectile.remove();
                    break;
                }
            }
        }
    }

    // ── Fúria e morte ──────────────────────────────────────────

    @Override
    protected void onEnrage() {
        Fx.worldSound(pos, Sound.ENTITY_BLAZE_DEATH, 2f, 0.5f);
        Fx.worldSound(pos, Sound.ENTITY_WITHER_AMBIENT, 1.4f, 1.6f);
        world.spawnParticle(Particle.FLAME, pos.clone().add(0, 1.6, 0), 80, 1.2, 1.2, 1.2, 0.12);
        Fx.ring(pos.clone().add(0, 0.3, 0), Particle.FLAME, 6, 70);
        for (int i = 0; i < 4; i++) {
            flash[i] = 14;
        }
        warn("<red><bold>☄ Wildfire entrou em fúria!");
        cooldown = Math.min(cooldown, 20);
    }

    @Override
    protected void deathStart() {
        Fx.worldSound(pos, Sound.ENTITY_BLAZE_DEATH, 2f, 0.5f);
        Fx.worldSound(pos, Sound.ENTITY_WITHER_DEATH, 0.8f, 1.6f);
        shieldSpeedGoal = 30;
        shieldRadiusGoal = 4.5;
        eyeOpen = false;
    }

    @Override
    protected void deathAnimate(int t) {
        hoverGoal = 1.0;
        hoverSpeed = 0.08;
        hover += Math.max(-hoverSpeed, Math.min(hoverSpeed, hoverGoal - hover));
        pos.setY(arena.fy + hover);
        world.spawnParticle(Particle.FLAME, pos.clone().add(0, 1.6, 0), 8, 0.6, 1.0, 0.6, 0.04);
        if (t % 6 == 0) {
            world.spawnParticle(Particle.LAVA, pos.clone().add(0, 2, 0), 6, 0.6, 0.6, 0.6, 0);
            Fx.worldSound(pos, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.8f);
        }
        if (t == 46) {
            Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, pos.clone().add(0, 1.6, 0), 2, 0.5, 0.5, 0.5, 0);
            Shards.burst(plugin, pos.clone().add(0, 1.8, 0), 40, 0.8, 0.45f, Material.GOLD_BLOCK,
                    Material.RAW_GOLD_BLOCK, Material.ORANGE_CONCRETE, Material.BROWN_TERRACOTTA);
            rig.parts().forEach(part -> part.visible = false);
        }
    }

    @Override
    protected void cleanup() {
        // projéteis e displays já são removidos pela base
    }
}
