package dev.vertexx.boss;

import dev.vertexx.PluginMain;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Estilhaços: pedaços de bloco que voam, giram, caem e somem (quebra de gelo, explosões). */
final class Shards {

    private Shards() {
    }

    static void burst(PluginMain plugin, Location at, int count, double speed, float size, Material... materials) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<BlockDisplay> pieces = new ArrayList<>();
        List<Vector> velocities = new ArrayList<>();
        List<Float> sizes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Material material = materials[random.nextInt(materials.length)];
            float s = size * (0.5f + random.nextFloat());
            BlockDisplay display = at.getWorld().spawn(at, BlockDisplay.class, entity -> {
                entity.setBlock(material.createBlockData());
                entity.setPersistent(false);
                entity.setViewRange(1.0f);
                entity.setBrightness(new Display.Brightness(12, 15));
                entity.setTeleportDuration(1);
                entity.setInterpolationDuration(2);
                entity.setTransformation(new Transformation(new Vector3f(-s / 2, -s / 2, -s / 2), new Quaternionf(),
                        new Vector3f(s), new Quaternionf()));
            });
            pieces.add(display);
            sizes.add(s);
            velocities.add(new Vector(random.nextDouble(-1, 1), random.nextDouble(0.2, 1.1), random.nextDouble(-1, 1))
                    .normalize().multiply(speed * random.nextDouble(0.5, 1.2)));
        }
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                for (int i = 0; i < pieces.size(); i++) {
                    BlockDisplay display = pieces.get(i);
                    Vector v = velocities.get(i);
                    v.setY(v.getY() - 0.07);
                    display.teleport(display.getLocation().add(v));
                    if (t % 2 == 0) {
                        float s = sizes.get(i);
                        Quaternionf q = new Quaternionf().rotateXYZ(t * 0.3f + i, t * 0.2f, t * 0.25f);
                        Vector3f offset = q.transform(new Vector3f(-s / 2, -s / 2, -s / 2));
                        display.setInterpolationDelay(0);
                        display.setInterpolationDuration(2);
                        display.setTransformation(new Transformation(offset, q, new Vector3f(s), new Quaternionf()));
                    }
                }
                if (t >= 22) {
                    pieces.forEach(BlockDisplay::remove);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
