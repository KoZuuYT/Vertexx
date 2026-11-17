package dev.vertexx.boss;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pequeno tornado de areia: pilha de BlockDisplays girando em eixo Y (cada camada num ritmo) mais anéis de
 * partículas de areia caindo. Usado pelo Parched Titan (Vórtice de Tempestade) e pela Grande Espada do Deserto.
 */
public final class SandTornado {

    private static final float[] WIDTH = {1.0f, 1.7f, 2.4f, 3.0f};
    private static final float HEIGHT = 0.95f;

    private final World world;
    private final boolean fire;
    private final List<BlockDisplay> layers = new ArrayList<>();
    private Location at;

    public SandTornado(Location at, boolean fire, org.bukkit.NamespacedKey partKey) {
        this.world = at.getWorld();
        this.at = at.clone();
        this.fire = fire;
        for (int i = 0; i < WIDTH.length; i++) {
            final int index = i;
            Material material = fire ? (i % 2 == 0 ? Material.MAGMA_BLOCK : Material.RED_SAND)
                    : (i % 2 == 0 ? Material.SAND : Material.SANDSTONE);
            Location spot = at.clone().add(0, i * HEIGHT, 0);
            BlockDisplay display = world.spawn(spot, BlockDisplay.class, d -> {
                d.setBlock(material.createBlockData());
                d.setPersistent(false);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setInterpolationDuration(2);
                d.setTeleportDuration(2);
                d.setViewRange(1.6f);
                d.setTransformation(transform(index, 0));
                if (partKey != null) {
                    d.getPersistentDataContainer().set(partKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                }
            });
            layers.add(display);
        }
    }

    private static Transformation transform(int layer, int age) {
        float w = WIDTH[layer];
        float angle = (float) Math.toRadians(age * (layer % 2 == 0 ? 38 : -52));
        Quaternionf rotation = new Quaternionf(new AxisAngle4f(angle, 0, 1, 0));
        Vector3f shift = rotation.transform(new Vector3f(w / 2, 0, w / 2)).negate();
        return new Transformation(shift, rotation, new Vector3f(w, HEIGHT, w), new Quaternionf());
    }

    public Location location() {
        return at.clone();
    }

    /** Move o pé do tornado para o ponto dado. */
    public void moveTo(Location to) {
        at = to.clone();
        for (int i = 0; i < layers.size(); i++) {
            BlockDisplay display = layers.get(i);
            if (display.isValid()) {
                display.teleport(at.clone().add(0, i * HEIGHT, 0));
            }
        }
    }

    /** Gira as camadas e solta os anéis de partículas; chamar uma vez por tick. */
    public void tick(int age) {
        if (age % 2 == 0) {
            for (int i = 0; i < layers.size(); i++) {
                BlockDisplay display = layers.get(i);
                if (display.isValid()) {
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(2);
                    display.setTransformation(transform(i, age));
                }
            }
        }
        if (age % 2 == 0) {
            Material dust = fire ? Material.RED_SAND : Material.SAND;
            for (int ring = 0; ring < 6; ring++) {
                double y = ring * 0.7;
                double radius = 0.5 + ring * 0.32;
                double angle = Math.toRadians(age * 31 + ring * 70);
                world.spawnParticle(Particle.FALLING_DUST, at.getX() + Math.cos(angle) * radius, at.getY() + y,
                        at.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0, dust.createBlockData());
                if (fire && ring % 2 == 0) {
                    world.spawnParticle(Particle.FLAME, at.getX() - Math.cos(angle) * radius, at.getY() + y,
                            at.getZ() - Math.sin(angle) * radius, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }
        }
    }

    public void remove() {
        for (BlockDisplay display : layers) {
            display.remove();
        }
        layers.clear();
    }
}
