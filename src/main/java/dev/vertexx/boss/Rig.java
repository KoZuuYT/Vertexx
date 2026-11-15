package dev.vertexx.boss;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Modelo montado com várias BlockDisplays. Cada peça tem posição, tamanho e rotação no espaço do modelo;
 * a cada atualização o conjunto é girado para a base atual (rotação em Y do boss ou orientação livre) e
 * enviado ao cliente com interpolação, então o movimento fica suave mesmo atualizando a cada 2 ticks.
 */
public final class Rig {

    /** Uma peça do modelo. Os campos são alterados livremente pela animação. */
    public static final class Part {
        final BlockDisplay display;
        public final Vector3f pos = new Vector3f();
        public final Vector3f size = new Vector3f(1, 1, 1);
        public final Quaternionf rot = new Quaternionf();
        /** Peças em espaço do mundo não giram junto com o boss (escudos, auras). */
        public boolean world;
        public boolean visible = true;

        private Part(BlockDisplay display) {
            this.display = display;
        }

        public Part at(float x, float y, float z) {
            pos.set(x, y, z);
            return this;
        }

        public Part size(float x, float y, float z) {
            size.set(x, y, z);
            return this;
        }

        public Part spin(float yawDeg, float pitchDeg, float rollDeg) {
            rot.identity().rotateY((float) Math.toRadians(yawDeg)).rotateX((float) Math.toRadians(pitchDeg))
                    .rotateZ((float) Math.toRadians(rollDeg));
            return this;
        }

        public Part world() {
            world = true;
            return this;
        }

        public void material(Material material) {
            display.setBlock(material.createBlockData());
        }
    }

    private final NamespacedKey tag;
    private final Location base;
    private final List<Part> parts = new ArrayList<>();
    private float scale = 1f;
    private boolean placed;

    public Rig(NamespacedKey tag, Location base) {
        this.tag = tag;
        this.base = base.clone();
        this.base.setYaw(0);
        this.base.setPitch(0);
    }

    public Part add(Material material, float x, float y, float z, float sx, float sy, float sz) {
        BlockDisplay display = base.getWorld().spawn(base, BlockDisplay.class, entity -> {
            entity.setBlock(material.createBlockData());
            entity.setPersistent(false);
            entity.setViewRange(1.6f);
            entity.setBrightness(new Display.Brightness(13, 15));
            entity.setTeleportDuration(2);
            entity.setInterpolationDuration(2);
            entity.setShadowRadius(0f);
            entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.001f),
                    new Quaternionf()));
            entity.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        });
        Part part = new Part(display);
        part.at(x, y, z).size(sx, sy, sz);
        parts.add(part);
        return part;
    }

    public void scale(float value) {
        this.scale = value;
    }

    public List<Part> parts() {
        return parts;
    }

    public static Quaternionf yaw(float yawDegrees) {
        return new Quaternionf().rotateY((float) Math.toRadians(-yawDegrees));
    }

    /** Envia a pose atual: {@code at} é a origem do modelo e {@code basis} a orientação das peças presas ao boss. */
    public void update(Location at, Quaternionf basis, int duration) {
        boolean moved = !placed || at.getX() != base.getX() || at.getY() != base.getY() || at.getZ() != base.getZ();
        if (moved) {
            base.setX(at.getX());
            base.setY(at.getY());
            base.setZ(at.getZ());
            placed = true;
        }
        for (Part part : parts) {
            BlockDisplay display = part.display;
            if (!display.isValid()) {
                continue;
            }
            if (moved) {
                display.teleport(base);
            }
            Vector3f size = new Vector3f(part.size).mul(part.visible ? scale : 0.0005f);
            Quaternionf basisQ = part.world ? new Quaternionf() : basis;
            Vector3f center = basisQ.transform(new Vector3f(part.pos).mul(scale));
            Quaternionf rotation = new Quaternionf(basisQ).mul(part.rot);
            Vector3f half = rotation.transform(new Vector3f(size).mul(0.5f));
            Vector3f translation = center.sub(half);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(duration);
            display.setTeleportDuration(Math.min(duration, 59));
            display.setTransformation(new Transformation(translation, rotation, size, new Quaternionf()));
        }
    }

    public void remove() {
        for (Part part : parts) {
            part.display.remove();
        }
        parts.clear();
    }
}
