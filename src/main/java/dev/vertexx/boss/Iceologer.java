package dev.vertexx.boss;

import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

/**
 * Iceologer, o saqueador do gelo removido da Mojang: um ilusionista de manto azul que invoca espinhos
 * de gelo (lançados um a um) e arranca um iceberg do chão para arremessá-lo no jogador.
 */
final class Iceologer extends BossFight {

    private enum Move { NONE, SPIKES, ICEBERG }

    private static final Particle.DustOptions CYAN = new Particle.DustOptions(Color.fromRGB(120, 220, 255), 1.3f);
    private static final Particle.DustOptions MARK = new Particle.DustOptions(Color.fromRGB(255, 70, 70), 1.5f);

    private Move move = Move.NONE;
    private Move last = Move.NONE;
    private int moveT;
    private int cooldown = 60;
    private double cast;
    private double castGoal;

    private Rig.Part casing;
    private Rig.Part casingBase;
    private final Rig.Part[] circle = new Rig.Part[2];
    private final Rig.Part[] orbit = new Rig.Part[4];
    private double lastX;
    private double lastZ;

    // Espinhos
    private final List<Spike> spikes = new ArrayList<>();
    private int spikeCount;
    private int summoned;
    private int spikesLaunched;

    // Iceberg
    private Iceberg iceberg;
    private double groundX;
    private double groundZ;
    private double lockX;
    private double lockZ;
    private boolean locked;
    private int impactT = -1;
    private int chargeEnd;

    Iceologer(BossManager manager, Arena arena) {
        super(manager, arena);
    }

    @Override
    protected double startHeight() {
        return 0.1;
    }

    @Override
    protected double baseHealth() {
        return plugin.getConfig().getDouble("bosses.iceologer.health", 380);
    }

    @Override
    protected double bodyRadius() {
        return 0.7;
    }

    @Override
    protected double bodyHeight() {
        return 2.7;
    }

    @Override
    protected double hitboxScale() {
        return 1.15;
    }

    @Override
    protected String title() {
        return "Senhor do Castelo de Gelo";
    }

    @Override
    protected String subtitle() {
        return "<aqua>O senhor do castelo de gelo desperta";
    }

    @Override
    protected int wakeDuration() {
        return 62;
    }

    @Override
    List<String> moves() {
        return List.of("spikes", "iceberg");
    }

    // ── Modelo ─────────────────────────────────────────────────

    @Override
    protected String modelId() {
        return "iceologer";
    }

    @Override
    protected void buildRig() {
        // fragmentos de gelo orbitando
        for (int i = 0; i < orbit.length; i++) {
            orbit[i] = rig.add(i % 2 == 0 ? Material.BLUE_ICE : Material.PACKED_ICE, 0, 0, 0, 0.2f, 0.34f, 0.2f).world();
        }
        // círculo mágico no chão
        circle[0] = rig.add(Material.LIGHT_BLUE_STAINED_GLASS, 0, 0.04f, 0, 3.6f, 0.02f, 3.6f);
        circle[1] = rig.add(Material.CYAN_STAINED_GLASS, 0, 0.05f, 0, 3.6f, 0.02f, 3.6f);
        // bloco de gelo que prende o boss enquanto ele dorme
        casing = rig.add(Material.ICE, 0, 1.75f, 0, 1.9f, 3.6f, 1.7f);
        casingBase = rig.add(Material.PACKED_ICE, 0, 0.2f, 0, 2.2f, 0.5f, 2.0f);
    }

    @Override
    protected void idlePose() {
        cast = 0;
        castGoal = 0;
        casing.visible = true;
        casingBase.visible = true;
        pos.setY(arena.fy + 0.1);
    }

    @Override
    protected void animate() {
        cast += (castGoal - cast) * 0.22;
        boolean sleeping = state == State.DORMANT || state == State.WAKING && stateTicks < 42;
        casing.visible = sleeping;
        casingBase.visible = sleeping || state == State.WAKING;
        double moved = Math.hypot(pos.getX() - lastX, pos.getZ() - lastZ);
        lastX = pos.getX();
        lastZ = pos.getZ();
        if (state == State.DYING) {
            model.play("death");
        } else if (cast > 0.35 && state != State.DORMANT) {
            model.play("cast");
        } else if (moved > 0.02 && state == State.ACTIVE) {
            model.play("walk");
            model.speed((float) Math.min(2.0, 0.7 + moved * 5));
        } else {
            model.play("idle");
            model.speed(1f);
        }
        for (int i = 0; i < orbit.length; i++) {
            double a = age * 0.06 + i * Math.PI / 2;
            double r = 1.35 + 0.1 * Math.sin(age * 0.1 + i);
            Rig.Part part = orbit[i];
            part.pos.set((float) (Math.cos(a) * r), (float) (1.2 + i * 0.35 + Math.sin(age * 0.08 + i) * 0.12),
                    (float) (Math.sin(a) * r));
            part.rot.identity().rotateXYZ(age * 0.05f + i, age * 0.07f, age * 0.04f);
            part.visible = state != State.DYING;
        }
        boolean casting = cast > 0.35 && state != State.DORMANT;
        for (int i = 0; i < 2; i++) {
            circle[i].visible = casting;
            circle[i].rot.identity().rotateY((float) Math.toRadians(age * (i == 0 ? 4 : -5) + i * 45));
        }
        if (casting && age % 2 == 0) {
            double a = age * 0.25;
            for (int k = 0; k < 3; k++) {
                double ang = a + k * Math.PI * 2 / 3;
                world.spawnParticle(Particle.SNOWFLAKE, pos.getX() + Math.cos(ang) * 1.7, pos.getY() + 0.1,
                        pos.getZ() + Math.sin(ang) * 1.7, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.DUST, pos.getX() + Math.cos(-ang) * 1.2, pos.getY() + 0.12,
                        pos.getZ() + Math.sin(-ang) * 1.2, 1, 0, 0, 0, 0, CYAN);
            }
        }
        model.offset("body", hurtTicks > 0 ? (float) (Math.random() - 0.5) * 1.2f : 0f, 0, 0);
        if (age % 8 == 0 && state != State.DORMANT) {
            world.spawnParticle(Particle.SNOWFLAKE, pos.clone().add(0, 1.4, 0), 3, 0.6, 0.8, 0.6, 0.01);
        }
    }

    // ── Despertar ──────────────────────────────────────────────

    @Override
    protected void wakeTick(int t) {
        if (t == 1) {
            Fx.worldSound(pos, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.5f);
            Fx.worldSound(pos, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1.4f, 0.6f);
        }
        if (t >= 8 && t < 42) {
            if (t % 6 == 0) {
                Fx.worldSound(pos, Sound.BLOCK_GLASS_BREAK, 0.9f, 0.6f + t * 0.02f);
                world.spawnParticle(Particle.BLOCK, pos.clone().add(0, 1.8, 0), 14, 0.8, 1.2, 0.8, 0.1,
                        Material.PACKED_ICE.createBlockData());
            }
            world.spawnParticle(Particle.SNOWFLAKE, pos.clone().add(0, 1.8, 0), 4, 0.8, 1.3, 0.8, 0.02);
        }
        if (t == 42) {
            Fx.worldSound(pos, Sound.BLOCK_GLASS_BREAK, 2f, 0.55f);
            Fx.worldSound(pos, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.5f, 0.7f);
            Shards.burst(plugin, pos.clone().add(0, 1.8, 0), 38, 0.8, 0.5f, Material.ICE, Material.PACKED_ICE,
                    Material.BLUE_ICE);
            world.spawnParticle(Particle.SNOWFLAKE, pos.clone().add(0, 1.8, 0), 100, 1, 1.4, 1, 0.25);
            castGoal = 1;
        }
        if (t == 56) {
            castGoal = 0;
        }
    }

    // ── IA ─────────────────────────────────────────────────────

    @Override
    protected void aiTick() {
        pos.setY(arena.fy + 0.1 + Math.sin(age * 0.1) * 0.07);
        tickSpikes();
        if (move == Move.NONE) {
            idle();
            return;
        }
        moveT++;
        switch (move) {
            case SPIKES -> spikesMove();
            case ICEBERG -> icebergMove();
            default -> { }
        }
    }

    private void idle() {
        castGoal = 0;
        Location at = aim();
        faceTowards(at, 10);
        if (target != null) {
            double dx = pos.getX() - at.getX();
            double dz = pos.getZ() - at.getZ();
            double length = Math.max(0.01, Math.hypot(dx, dz));
            double swing = Math.sin(age * 0.03) * 0.9;
            double ux = dx / length;
            double uz = dz / length;
            moveToward(at.getX() + (ux * Math.cos(swing) - uz * Math.sin(swing)) * 8.5, pos.getY(),
                    at.getZ() + (ux * Math.sin(swing) + uz * Math.cos(swing)) * 8.5, enraged ? 0.12 : 0.085);
        } else {
            moveToward(arena.cx + 0.5, pos.getY(), arena.cz + 0.5, 0.05);
        }
        if (--cooldown <= 0 && target != null) {
            Move next = last == Move.SPIKES ? Move.ICEBERG : last == Move.ICEBERG ? Move.SPIKES
                    : ThreadLocalRandom.current().nextBoolean() ? Move.SPIKES : Move.ICEBERG;
            startMove(next);
        }
    }

    private void startMove(Move next) {
        move = next;
        last = next;
        moveT = 0;
        spikeCount = enraged ? 8 : 5;
        spikesLaunched = 0;
        summoned = 0;
        locked = false;
        impactT = -1;
    }

    private void endMove() {
        move = Move.NONE;
        castGoal = 0;
        cooldown = enraged ? 40 : 70;
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

    // ── Espinhos de gelo ───────────────────────────────────────

    private void spikesMove() {
        int t = moveT;
        faceTowards(aim(), 12);
        castGoal = 1;
        if (t == 1) {
            Fx.worldSound(pos, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.6f, 0.8f);
            warn("<aqua><bold>❄ Espinhos de gelo!</bold> <gray>Desvie de cada um.");
        }
        int gap = enraged ? 3 : 4;
        int summonStart = 10;
        int launchStart = summonStart + gap * spikeCount + 14;
        int launchGap = enraged ? 4 : 6;
        if (t >= summonStart && (t - summonStart) % gap == 0 && summoned < spikeCount && t < launchStart) {
            summonSpike(summoned++);
        }
        if (t >= launchStart && (t - launchStart) % launchGap == 0 && spikesLaunched < spikeCount) {
            launchNext();
        }
        if (spikesLaunched >= spikeCount && spikes.isEmpty() && t > launchStart) {
            castGoal = 0;
            if (t > launchStart + launchGap * spikeCount + 16) {
                endMove();
            }
        }
        if (t > launchStart + launchGap * spikeCount + 90) {
            endMove(); // segurança
        }
    }

    private void summonSpike(int index) {
        Location at = pos.clone();
        Spike spike = new Spike(at, index, spikeCount);
        spikes.add(spike);
        double angle = spike.angle;
        Location floor = pos.clone().add(Math.cos(angle) * 2.6, 0, Math.sin(angle) * 2.6);
        floor.setY(arena.fy + 0.1);
        Fx.worldSound(floor, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 1.2f + index * 0.05f);
        Fx.worldSound(floor, Sound.BLOCK_GLASS_BREAK, 0.6f, 1.7f);
        world.spawnParticle(Particle.BLOCK, floor, 14, 0.3, 0.1, 0.3, 0.1, Material.PACKED_ICE.createBlockData());
        world.spawnParticle(Particle.SNOWFLAKE, floor, 10, 0.3, 0.3, 0.3, 0.05);
    }

    private void launchNext() {
        for (Spike spike : spikes) {
            if (!spike.flying && spike.life > 8) {
                Location from = spike.p;
                Location at = aim();
                if (target != null) {
                    at = at.add(target.getVelocity().multiply(3));
                }
                Vector dir = at.toVector().subtract(from.toVector());
                if (dir.lengthSquared() < 0.01) {
                    dir = new Vector(0, 0, 1);
                }
                spike.dir = dir.normalize();
                spike.flying = true;
                spike.life = 0;
                spikesLaunched++;
                Fx.worldSound(from, Sound.ENTITY_EVOKER_CAST_SPELL, 1.2f, 1.7f);
                Fx.worldSound(from, Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.6f);
                world.spawnParticle(Particle.SNOWFLAKE, from, 12, 0.2, 0.2, 0.2, 0.1);
                return;
            }
        }
    }

    private void tickSpikes() {
        Iterator<Spike> it = spikes.iterator();
        while (it.hasNext()) {
            Spike spike = it.next();
            if (spike.tick()) {
                spike.rig.remove();
                it.remove();
            }
        }
    }

    private final class Spike {
        final Rig rig;
        final Location p;
        final double angle;
        Vector dir = new Vector(0, 1, 0);
        boolean flying;
        int life;

        Spike(Location start, int index, int total) {
            this.p = start.clone();
            this.angle = Math.PI * 2 * index / total + yaw * 0.0 + 0.3;
            this.p.add(Math.cos(angle) * 2.6, -1.4, Math.sin(angle) * 2.6);
            this.p.setY(arena.fy - 1.4);
            rig = new Rig(manager.partKey(), p);
            rig.add(Material.BLUE_ICE, 0, 0.2f, 0, 0.46f, 0.45f, 0.46f);
            rig.add(Material.PACKED_ICE, 0, 0.6f, 0, 0.36f, 0.45f, 0.36f).spin(45, 0, 0);
            rig.add(Material.BLUE_ICE, 0, 0.98f, 0, 0.26f, 0.4f, 0.26f);
            rig.add(Material.PACKED_ICE, 0, 1.3f, 0, 0.15f, 0.34f, 0.15f).spin(45, 0, 0);
            update();
        }

        void update() {
            rig.update(p, new Quaternionf().rotationTo(0f, 1f, 0f, (float) dir.getX(), (float) dir.getY(),
                    (float) dir.getZ()), 2);
        }

        /** Devolve verdadeiro quando o espinho acabou. */
        boolean tick() {
            life++;
            if (!flying) {
                double rise = life <= 8 ? ease(life / 8.0) : 1;
                double a = angle + Math.max(0, life - 8) * 0.045;
                p.setX(pos.getX() + Math.cos(a) * 2.6);
                p.setZ(pos.getZ() + Math.sin(a) * 2.6);
                p.setY(arena.fy - 1.4 + (1.6 + 1.4 + Math.sin(life * 0.15 + angle) * 0.2) * rise);
                if (life > 8) {
                    Vector aimDir = aim().toVector().subtract(p.toVector());
                    if (aimDir.lengthSquared() > 0.01) {
                        dir = aimDir.normalize();
                    }
                }
                if (life % 2 == 0) {
                    update();
                }
                if (life > 8 && life % 5 == 0) {
                    world.spawnParticle(Particle.SNOWFLAKE, p, 1, 0.1, 0.1, 0.1, 0.01);
                }
                return state == State.DYING || !valid();
            }
            Location before = p.clone();
            double speed = 1.45;
            RayTraceResult blockHit = world.rayTraceBlocks(before, dir, speed, FluidCollisionMode.NEVER, true);
            if (blockHit != null) {
                shatter(blockHit.getHitPosition().toLocation(world));
                return true;
            }
            for (Player player : insidePlayers()) {
                BoundingBox box = player.getBoundingBox().expand(0.35);
                if (box.contains(before.toVector()) || box.rayTrace(before.toVector(), dir, speed) != null) {
                    hit(player);
                    shatter(p.clone());
                    return true;
                }
            }
            p.add(dir.clone().multiply(speed));
            world.spawnParticle(Particle.SNOWFLAKE, p, 3, 0.08, 0.08, 0.08, 0.02);
            if (life % 2 == 0) {
                world.spawnParticle(Particle.DUST, p, 1, 0.05, 0.05, 0.05, 0, CYAN);
            }
            update();
            if (life > 60 || !arena.contains(p, 2)) {
                shatter(p.clone());
                return true;
            }
            return false;
        }

        private void hit(Player player) {
            damage(player, plugin.getConfig().getDouble("bosses.iceologer.spike-damage", 6));
            player.setFreezeTicks(Math.min(player.getMaxFreezeTicks() + 60, player.getFreezeTicks() + 140));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 1));
            Fx.sound(player, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1f);
        }

        private void shatter(Location at) {
            Fx.worldSound(at, Sound.BLOCK_GLASS_BREAK, 1.2f, 1.2f);
            world.spawnParticle(Particle.BLOCK, at, 22, 0.25, 0.25, 0.25, 0.1, Material.PACKED_ICE.createBlockData());
            world.spawnParticle(Particle.SNOWFLAKE, at, 16, 0.25, 0.25, 0.25, 0.08);
            Shards.burst(plugin, at, 4, 0.35, 0.2f, Material.ICE, Material.PACKED_ICE);
        }
    }

    // ── Iceberg ────────────────────────────────────────────────

    private final class Iceberg {
        final Rig rig;
        final Location p;
        float spin;

        Iceberg(double x, double y, double z) {
            p = new Location(world, x, y, z);
            rig = new Rig(manager.partKey(), p);
            rig.add(Material.BLUE_ICE, 0, 1.15f, 0, 1.7f, 2.3f, 1.5f).spin(20, 4, 0);
            rig.add(Material.PACKED_ICE, 0.75f, 0.85f, 0.4f, 1.2f, 1.7f, 1.2f).spin(-25, 0, 10);
            rig.add(Material.PACKED_ICE, -0.8f, 0.8f, -0.2f, 1.1f, 1.5f, 1.1f).spin(35, 0, -12);
            rig.add(Material.BLUE_ICE, 0.1f, 2.45f, 0.1f, 0.95f, 1.6f, 0.95f).spin(45, 0, 8);
            rig.add(Material.ICE, -0.35f, 2.65f, -0.4f, 0.5f, 1.4f, 0.5f).spin(0, 0, -20);
            rig.add(Material.ICE, 0.85f, 1.95f, -0.5f, 0.45f, 1.2f, 0.45f).spin(10, 0, 25);
            rig.add(Material.SNOW_BLOCK, 0, 2.3f, 0, 1.35f, 0.28f, 1.25f).spin(20, 4, 0);
            rig.add(Material.PACKED_ICE, -0.2f, 0.25f, 0.85f, 0.8f, 0.7f, 0.8f).spin(15, 0, 0);
            rig.add(Material.BLUE_ICE, 0.35f, 0.2f, -0.85f, 0.7f, 0.6f, 0.7f).spin(-30, 0, 0);
            update();
        }

        void update() {
            rig.update(p, Rig.yaw(spin), 2);
        }
    }

    private void icebergMove() {
        int t = moveT;
        faceTowards(aim(), 12);
        castGoal = 1;
        int charge = enraged ? 34 : 44;
        if (t == 1) {
            Location at = aim();
            double dx = at.getX() - pos.getX();
            double dz = at.getZ() - pos.getZ();
            double length = Math.max(0.01, Math.hypot(dx, dz));
            double limit = arena.half - 3.5;
            groundX = Math.max(arena.cx + 0.5 - limit, Math.min(arena.cx + 0.5 + limit, pos.getX() + dx / length * 4.5));
            groundZ = Math.max(arena.cz + 0.5 - limit, Math.min(arena.cz + 0.5 + limit, pos.getZ() + dz / length * 4.5));
            iceberg = new Iceberg(groundX, arena.fy - 3.4, groundZ);
            Fx.worldSound(pos, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.6f, 0.6f);
            warn("<aqua><bold>❄ Iceberg!</bold> <gray>Ele vai arrancar o chão.");
        }
        if (iceberg == null && impactT < 0) {
            endMove();
            return;
        }
        if (t <= 30) {
            double f = ease(t / 30.0);
            iceberg.p.set(groundX + shake(), arena.fy - 3.4 + 3.3 * f, groundZ + shake());
            if (t % 2 == 0) {
                Location floor = new Location(world, groundX, arena.fy + 0.1, groundZ);
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2 * i / 6 + t * 0.3;
                    world.spawnParticle(Particle.BLOCK, floor.getX() + Math.cos(a) * 1.7, floor.getY(),
                            floor.getZ() + Math.sin(a) * 1.7, 4, 0.2, 0.05, 0.2, 0.1,
                            Material.PACKED_ICE.createBlockData());
                }
                world.spawnParticle(Particle.SNOWFLAKE, floor, 6, 1.2, 0.1, 1.2, 0.03);
            }
            if (t % 8 == 1) {
                Fx.worldSound(iceberg.p, Sound.BLOCK_GLASS_BREAK, 1.3f, 0.5f + t * 0.01f);
                Fx.worldSound(iceberg.p, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.8f, 0.5f);
            }
            iceberg.spin += 2;
        } else if (t <= 58) {
            double f = ease((t - 30) / 28.0);
            double ty = pos.getY() + 5.4;
            iceberg.p.set(groundX + (pos.getX() - groundX) * f, arena.fy - 0.1 + (ty - (arena.fy - 0.1)) * f,
                    groundZ + (pos.getZ() - groundZ) * f);
            iceberg.spin += 4;
            if (t == 31) {
                Fx.worldSound(pos, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1.4f, 0.7f);
                Fx.worldSound(pos, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.4f, 0.5f);
            }
            world.spawnParticle(Particle.SNOWFLAKE, iceberg.p.clone().add(0, 1.2, 0), 6, 0.9, 1.0, 0.9, 0.02);
            world.spawnParticle(Particle.BLOCK, iceberg.p.clone().add(0, 0.3, 0), 3, 0.8, 0.2, 0.8, 0.1,
                    Material.PACKED_ICE.createBlockData());
        } else if (t <= 58 + charge) {
            chargeEnd = 58 + charge;
            double wobble = (t - 58) / (double) charge;
            iceberg.p.set(pos.getX() + shake() * wobble * 2, pos.getY() + 5.4 + Math.sin(age * 0.15) * 0.15,
                    pos.getZ() + shake() * wobble * 2);
            iceberg.spin += 3 + wobble * 8;
            if (t % 3 == 0) {
                double a = t * 0.5;
                for (int k = 0; k < 3; k++) {
                    double ang = a + k * Math.PI * 2 / 3;
                    world.spawnParticle(Particle.SNOWFLAKE, pos.getX() + Math.cos(ang) * 2.2,
                            pos.getY() + 3.5 + Math.sin(t * 0.2) * 1.5, pos.getZ() + Math.sin(ang) * 2.2, 1, 0, 0, 0, 0);
                }
            }
            if (t % 10 == 0) {
                Fx.worldSound(pos, Sound.BLOCK_BEACON_AMBIENT, 1.4f, 1.0f + (float) wobble);
            }
            trackMarker(t);
        } else if (t <= chargeEnd + 16) {
            if (t == chargeEnd + 1) {
                Fx.worldSound(pos, Sound.ENTITY_WITHER_SHOOT, 1.4f, 0.5f);
                Fx.worldSound(pos, Sound.ENTITY_EVOKER_CAST_SPELL, 1.4f, 0.6f);
            }
            double f = (t - chargeEnd) / 16.0;
            double start = pos.getY() + 5.4;
            double sx = pos.getX();
            double sz = pos.getZ();
            double e = Math.pow(f, 1.7);
            iceberg.p.set(sx + (lockX - sx) * e, start + (arena.fy + 0.2 - start) * e + Math.sin(f * Math.PI) * 2.2,
                    sz + (lockZ - sz) * e);
            iceberg.spin += 18;
            world.spawnParticle(Particle.SNOWFLAKE, iceberg.p.clone().add(0, 1.2, 0), 8, 0.7, 0.8, 0.7, 0.03);
            world.spawnParticle(Particle.CLOUD, iceberg.p.clone().add(0, 1.2, 0), 3, 0.5, 0.5, 0.5, 0.02);
            drawMarker(true);
            if (t == chargeEnd + 16) {
                impact();
            }
        } else {
            castGoal = 0;
            if (impactT >= 0) {
                impactT++;
                double r = impactT * 0.9;
                if (r <= 6) {
                    for (int i = 0; i < 40; i++) {
                        double a = Math.PI * 2 * i / 40;
                        world.spawnParticle(Particle.CLOUD, lockX + Math.cos(a) * r, arena.fy + 0.15,
                                lockZ + Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0.01);
                        if (i % 3 == 0) {
                            world.spawnParticle(Particle.SNOWFLAKE, lockX + Math.cos(a) * r, arena.fy + 0.3,
                                    lockZ + Math.sin(a) * r, 1, 0.1, 0.1, 0.1, 0.02);
                        }
                    }
                }
            }
            if (t > chargeEnd + 16 + 34) {
                endMove();
            }
        }
        if (iceberg != null) {
            iceberg.update();
        }
    }

    private double shake() {
        return (Math.random() - 0.5) * 0.12;
    }

    /** Marca no chão: acompanha o alvo e trava um instante antes do arremesso. */
    private void trackMarker(int t) {
        int lockAt = chargeEnd - 14;
        if (t == 60 && target != null) {
            target.sendActionBar(Msg.mm("<aqua><bold>❄ Iceberg!</bold> <gray>Saia da marca no chão."));
            Fx.sound(target, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.2f, 0.7f);
        }
        if (t < lockAt) {
            Location at = aim();
            lockX = at.getX();
            lockZ = at.getZ();
            locked = false;
        } else if (!locked) {
            locked = true;
            Fx.worldSound(new Location(world, lockX, arena.fy, lockZ), Sound.BLOCK_NOTE_BLOCK_BIT, 1.6f, 0.5f);
            Fx.worldSound(new Location(world, lockX, arena.fy, lockZ), Sound.BLOCK_ANVIL_LAND, 0.8f, 1.8f);
            if (target != null) {
                target.sendActionBar(Msg.mm("<red><bold>⚠ Marca travada!"));
            }
        }
        drawMarker(locked);
    }

    private void drawMarker(boolean lockedColor) {
        if (age % 2 != 0) {
            return;
        }
        Particle.DustOptions color = lockedColor ? MARK : CYAN;
        double radius = 4.2;
        for (int i = 0; i < 36; i++) {
            double a = Math.PI * 2 * i / 36;
            world.spawnParticle(Particle.DUST, lockX + Math.cos(a) * radius, arena.fy + 0.12, lockZ + Math.sin(a) * radius,
                    1, 0, 0, 0, 0, color);
        }
        for (int i = -3; i <= 3; i++) {
            world.spawnParticle(Particle.DUST, lockX + i * 1.0, arena.fy + 0.12, lockZ, 1, 0, 0, 0, 0, color);
            world.spawnParticle(Particle.DUST, lockX, arena.fy + 0.12, lockZ + i * 1.0, 1, 0, 0, 0, 0, color);
        }
        if (lockedColor) {
            world.spawnParticle(Particle.CRIT, lockX, arena.fy + 0.3, lockZ, 6, 1.5, 0.1, 1.5, 0.2);
        }
    }

    private void impact() {
        Location at = new Location(world, lockX, arena.fy + 0.3, lockZ);
        Fx.worldSound(at, Sound.BLOCK_GLASS_BREAK, 2f, 0.55f);
        Fx.worldSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.3f);
        Fx.worldSound(at, Sound.BLOCK_POWDER_SNOW_BREAK, 2f, 0.6f);
        world.spawnParticle(Particle.EXPLOSION, at, 2, 0.8, 0.2, 0.8, 0);
        world.spawnParticle(Particle.BLOCK, at, 110, 1.8, 0.4, 1.8, 0.2, Material.PACKED_ICE.createBlockData());
        world.spawnParticle(Particle.BLOCK, at, 50, 1.4, 0.4, 1.4, 0.2, Material.BLUE_ICE.createBlockData());
        world.spawnParticle(Particle.SNOWFLAKE, at, 120, 2, 0.6, 2, 0.3);
        Shards.burst(plugin, at.clone().add(0, 1.0, 0), 26, 0.9, 0.55f, Material.PACKED_ICE, Material.BLUE_ICE,
                Material.ICE, Material.SNOW_BLOCK);
        double maxDamage = plugin.getConfig().getDouble("bosses.iceologer.iceberg-damage", 11);
        for (Player player : insidePlayers()) {
            double d = Math.hypot(player.getLocation().getX() - lockX, player.getLocation().getZ() - lockZ);
            if (d <= 4.2 && player.getLocation().getY() < arena.fy + 3.5) {
                damage(player, maxDamage * (1 - 0.45 * d / 4.2));
                player.setFreezeTicks(Math.min(player.getMaxFreezeTicks() + 80, player.getFreezeTicks() + 200));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 2));
                Vector push = player.getLocation().toVector().subtract(at.toVector()).setY(0);
                if (push.lengthSquared() > 0.01) {
                    push.normalize().multiply(0.8);
                }
                player.setVelocity(push.setY(0.5));
                Fx.sound(player, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.2f, 0.8f);
            }
        }
        if (iceberg != null) {
            iceberg.rig.remove();
            iceberg = null;
        }
        impactT = 0;
    }

    // ── Projéteis, fúria e morte ───────────────────────────────

    @Override
    boolean blocksProjectile(Projectile projectile) {
        return false;
    }

    @Override
    protected void onEnrage() {
        Fx.worldSound(pos, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2f, 0.5f);
        Fx.worldSound(pos, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.5f);
        world.spawnParticle(Particle.SNOWFLAKE, pos.clone().add(0, 1.5, 0), 120, 1.2, 1.4, 1.2, 0.2);
        Fx.ring(pos.clone().add(0, 0.3, 0), Particle.CLOUD, 5, 60);
        castGoal = 1;
        warn("<aqua><bold>❄ O Iceologer entrou em fúria!");
        cooldown = Math.min(cooldown, 20);
    }

    @Override
    protected void deathStart() {
        Fx.worldSound(pos, Sound.ENTITY_EVOKER_DEATH, 2f, 0.6f);
        cleanup();
        castGoal = 1;
    }

    @Override
    protected void deathAnimate(int t) {
        if (t % 5 == 0) {
            Fx.worldSound(pos, Sound.BLOCK_GLASS_BREAK, 1f, 0.6f + t * 0.01f);
            world.spawnParticle(Particle.SNOWFLAKE, pos.clone().add(0, 1.6, 0), 14, 0.6, 1.0, 0.6, 0.05);
        }
        if (t == 44) {
            Fx.worldSound(pos, Sound.BLOCK_GLASS_BREAK, 2f, 0.5f);
            Fx.worldSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.4f);
            world.spawnParticle(Particle.SNOWFLAKE, pos.clone().add(0, 1.6, 0), 140, 1.2, 1.4, 1.2, 0.25);
            Shards.burst(plugin, pos.clone().add(0, 1.8, 0), 40, 0.8, 0.45f, Material.LAPIS_BLOCK, Material.BLUE_CONCRETE,
                    Material.PACKED_ICE, Material.BLUE_ICE, Material.LIGHT_GRAY_CONCRETE);
            rig.parts().forEach(part -> part.visible = false);
            model.hideAll(true);
        }
    }

    @Override
    protected void cleanup() {
        for (Spike spike : spikes) {
            spike.rig.remove();
        }
        spikes.clear();
        if (iceberg != null) {
            iceberg.rig.remove();
            iceberg = null;
        }
    }
}
