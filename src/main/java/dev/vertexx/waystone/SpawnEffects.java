package dev.vertexx.waystone;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/** Efeitos de brilho e ondas de impacto que acompanham a montagem de um Waystone. */
final class SpawnEffects {

    private static final Color GREEN = Color.fromRGB(120, 255, 90);
    private static final Color VIOLET = Color.fromRGB(170, 90, 255);

    private SpawnEffects() {
    }

    private static Particle.DustOptions dust(Color color, float size) {
        return new Particle.DustOptions(color, size);
    }

    private static Color mix(Color from, Color to, double amount) {
        return Color.fromRGB(
                (int) (from.getRed() + (to.getRed() - from.getRed()) * amount),
                (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
                (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }

    /** O golpe inicial: explosão de detritos do bloco, ondas de choque no chão e uma coluna de luz. */
    static void impact(PluginMain plugin, Location base, Material debris) {
        World world = base.getWorld();
        Fx.worldSound(base, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0f, 0.6f);
        Fx.worldSound(base, Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 1.7f);
        world.spawnParticle(Particle.EXPLOSION, base.clone().add(0, 0.25, 0), 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.BLOCK, base.clone().add(0, 0.3, 0), 70, 0.6, 0.12, 0.6, 0.12,
                debris.createBlockData());
        world.spawnParticle(Particle.CLOUD, base.clone().add(0, 0.15, 0), 24, 0.5, 0.05, 0.5, 0.08);

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick > 22) {
                    cancel();
                    return;
                }
                if (tick <= 14) {
                    // onda de choque: dois anéis concêntricos que se expandem e mudam de cor
                    double amount = tick / 14.0;
                    ring(world, base, 0.4 + tick * 0.24, 0.1 + tick * 0.015, 14 + tick * 3,
                            dust(mix(GREEN, VIOLET, amount), 1.3f));
                    if (tick >= 3) {
                        ring(world, base, 0.2 + (tick - 3) * 0.2, 0.12, 12 + tick * 2, Particle.END_ROD);
                    }
                    if (tick % 4 == 0) {
                        ring(world, base, 0.5 + tick * 0.24, 0.05, 20, Particle.CLOUD);
                    }
                }
                // espiral ascendente
                double y = tick * 0.11;
                for (int arm = 0; arm < 2; arm++) {
                    double angle = tick * 0.55 + arm * Math.PI;
                    double radius = 0.75 - tick * 0.012;
                    world.spawnParticle(Particle.END_ROD, base.getX() + Math.cos(angle) * radius,
                            base.getY() + y, base.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
                }
                if (tick == 18) {
                    // a coluna de luz sobe de uma vez
                    for (int i = 0; i < 20; i++) {
                        double angle = Math.PI * 2 * i / 20;
                        world.spawnParticle(Particle.END_ROD, base.getX() + Math.cos(angle) * 0.45,
                                base.getY() + 0.1, base.getZ() + Math.sin(angle) * 0.45, 0, 0, 1, 0, 0.28);
                    }
                    Fx.worldSound(base, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, 1.4f);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** O brilho final, quando o olho flutuante é ativado no topo. */
    static void flash(PluginMain plugin, Location base, double top) {
        World world = base.getWorld();
        Location crown = base.clone().add(0, top, 0);
        Fx.worldSound(crown, Sound.ENTITY_ENDER_EYE_DEATH, 1.0f, 0.8f);
        Fx.worldSound(crown, Sound.ITEM_TOTEM_USE, 0.25f, 1.9f);
        world.spawnParticle(Particle.FIREWORK, crown, 45, 0.3, 0.3, 0.3, 0.18);
        world.spawnParticle(Particle.ELECTRIC_SPARK, crown, 40, 0.4, 0.4, 0.4, 0.4);
        world.spawnParticle(Particle.GLOW, crown, 30, 0.45, 0.45, 0.45, 0.05);
        world.spawnParticle(Particle.END_ROD, crown, 30, 0.1, 0.1, 0.1, 0.3);

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick > 12) {
                    cancel();
                    return;
                }
                double amount = tick / 12.0;
                ring(world, crown, 0.3 + tick * 0.2, 0, 16 + tick * 2, dust(mix(VIOLET, GREEN, amount), 1.2f));
                // onda que desce pelo corpo do pilar
                double y = Math.max(0.1, top - tick * 0.16);
                ring(world, base.clone().add(0, y, 0), 0.62, 0, 14, Particle.END_ROD);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Pulso discreto quando a skin ou o ícone é trocado. */
    static void pulse(PluginMain plugin, Location base) {
        World world = base.getWorld();
        Fx.worldSound(base.clone().add(0, 1, 0), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick > 7) {
                    cancel();
                    return;
                }
                ring(world, base.clone().add(0, 0.1 + tick * 0.1, 0), 0.7 + tick * 0.12, 0, 18,
                        dust(mix(GREEN, VIOLET, tick / 7.0), 1.0f));
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static void ring(World world, Location center, double radius, double lift, int points, Object particle) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            double x = center.getX() + Math.cos(angle) * radius;
            double y = center.getY() + lift;
            double z = center.getZ() + Math.sin(angle) * radius;
            if (particle instanceof Particle.DustOptions options) {
                world.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, options);
            } else {
                world.spawnParticle((Particle) particle, x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }
}
