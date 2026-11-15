package dev.vertexx.fauna;

import dev.vertexx.boss.Rig;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;

/**
 * Coguvaca amarela coberta de botões-de-ouro. Mantém a IA e os sons da vaca: só o desenho muda. Deixa
 * um rastro de flores por onde anda, entrega néctar às abelhas por perto e perde as flores quando tosquiada.
 */
final class Moobloom extends Critter {

    private static final int FLOWERS = 4;

    private final Rig.Part[] legs = new Rig.Part[4];
    private final Rig.Part[] hooves = new Rig.Part[4];
    private final Rig.Part[] stems = new Rig.Part[FLOWERS];
    private final Rig.Part[][] blooms = new Rig.Part[FLOWERS][2];
    private Rig.Part head;
    private Rig.Part snout;
    private Rig.Part tail;
    private long lastTrail;

    Moobloom(FaunaManager manager, Mob mob) {
        super(manager, mob);
    }

    @Override
    protected boolean wild() {
        return false;
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.MOOBLOOM;
    }

    @Override
    protected void build() {
        Material wool = Material.YELLOW_WOOL;
        rig.add(wool, 0, 1.0f, 0, 0.74f, 0.64f, 1.12f);
        // manchas cremes no dorso e nas laterais, levemente salientes
        rig.add(Material.WHITE_WOOL, 0.1f, 1.325f, 0.22f, 0.36f, 0.03f, 0.42f);
        rig.add(Material.WHITE_WOOL, -0.17f, 1.325f, -0.28f, 0.3f, 0.03f, 0.34f);
        rig.add(Material.WHITE_WOOL, 0.375f, 1.0f, -0.12f, 0.03f, 0.34f, 0.38f);
        rig.add(Material.WHITE_WOOL, 0.375f, 0.86f, 0.3f, 0.03f, 0.2f, 0.2f);
        rig.add(Material.WHITE_WOOL, -0.375f, 1.05f, 0.18f, 0.03f, 0.3f, 0.3f);
        rig.add(Material.WHITE_WOOL, -0.375f, 0.9f, -0.34f, 0.03f, 0.22f, 0.24f);
        rig.add(Material.GOLD_BLOCK, 0, 0.7f, 0, 0.7f, 0.04f, 1.08f); // barriga mais escura
        head = rig.add(wool, 0, 1.12f, 0.72f, 0.52f, 0.5f, 0.44f);
        snout = rig.add(Material.LIGHT_GRAY_CONCRETE, 0, 1.0f, 0.96f, 0.36f, 0.2f, 0.08f);
        rig.add(Material.BLACK_CONCRETE, 0.08f, 1.0f, 1.0f, 0.06f, 0.06f, 0.02f);
        rig.add(Material.BLACK_CONCRETE, -0.08f, 1.0f, 1.0f, 0.06f, 0.06f, 0.02f);
        rig.add(Material.BLACK_CONCRETE, 0.2f, 1.22f, 0.945f, 0.09f, 0.11f, 0.02f);
        rig.add(Material.BLACK_CONCRETE, -0.2f, 1.22f, 0.945f, 0.09f, 0.11f, 0.02f);
        rig.add(Material.PINK_CONCRETE, 0.285f, 1.13f, 0.7f, 0.04f, 0.16f, 0.2f); // orelhas
        rig.add(Material.PINK_CONCRETE, -0.285f, 1.13f, 0.7f, 0.04f, 0.16f, 0.2f);
        rig.add(Material.LIGHT_GRAY_CONCRETE, 0.22f, 1.42f, 0.7f, 0.08f, 0.16f, 0.08f); // chifres
        rig.add(Material.LIGHT_GRAY_CONCRETE, -0.22f, 1.42f, 0.7f, 0.08f, 0.16f, 0.08f);
        tail = rig.add(wool, 0, 1.22f, -0.6f, 0.08f, 0.5f, 0.08f);
        float[] lx = {0.23f, -0.23f, 0.23f, -0.23f};
        float[] lz = {0.4f, 0.4f, -0.4f, -0.4f};
        for (int i = 0; i < 4; i++) {
            legs[i] = rig.add(wool, lx[i], 0.4f, lz[i], 0.2f, 0.55f, 0.2f);
            hooves[i] = rig.add(Material.BLACK_CONCRETE, lx[i], 0.06f, lz[i], 0.21f, 0.12f, 0.21f);
        }
        float[] fx = {0.2f, -0.22f, 0.16f, -0.16f};
        float[] fz = {0.34f, 0.08f, -0.2f, -0.38f};
        float[] fh = {0.3f, 0.24f, 0.34f, 0.26f};
        for (int i = 0; i < FLOWERS; i++) {
            stems[i] = rig.add(Material.GREEN_CONCRETE, fx[i], 1.32f + fh[i] / 2, fz[i], 0.05f, fh[i], 0.05f);
            blooms[i][0] = rig.add(Material.GOLD_BLOCK, fx[i], 1.32f + fh[i] + 0.06f, fz[i], 0.22f, 0.2f, 0.22f);
            blooms[i][1] = rig.add(Material.ORANGE_CONCRETE, fx[i], 1.32f + fh[i] + 0.12f, fz[i], 0.12f, 0.2f, 0.12f);
        }
    }

    private boolean shorn() {
        Long until = mob.getPersistentDataContainer().get(manager.shornKey(), PersistentDataType.LONG);
        return until != null && until > mob.getWorld().getFullTime();
    }

    @Override
    protected void think() {
        if (age % 10 == 0 && speed > 0.02 && !shorn()) {
            trail();
        }
        if (age % 60 == 0 && !shorn()) {
            pollinate();
        }
    }

    /** Deixa um botão-de-ouro (dente-de-leão) na grama, no máximo um a cada 3 segundos. */
    private void trail() {
        long now = mob.getWorld().getFullTime();
        if (now - lastTrail < 60 || ThreadLocalRandom.current().nextDouble() > 0.35) {
            return;
        }
        Block at = mob.getLocation().getBlock();
        Block below = at.getRelative(0, -1, 0);
        if (at.getType() == Material.AIR && (below.getType() == Material.GRASS_BLOCK || below.getType() == Material.DIRT
                || below.getType() == Material.PODZOL)) {
            at.setType(Material.DANDELION, false);
            lastTrail = now;
            mob.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.getLocation().add(0.5, 0.3, 0.5), 4, 0.2, 0.2, 0.2, 0);
        }
    }

    /** As abelhas por perto ganham néctar, como se tivessem polinizado as flores da coguvaca. */
    private void pollinate() {
        for (Entity entity : mob.getNearbyEntities(5, 3, 5)) {
            if (entity instanceof Bee bee && !bee.hasNectar()) {
                bee.setHasNectar(true);
                mob.getWorld().spawnParticle(Particle.FALLING_NECTAR, bee.getLocation().add(0, 0.4, 0), 6, 0.2, 0.2, 0.2, 0);
                mob.getWorld().playSound(bee.getLocation(), Sound.BLOCK_BEEHIVE_ENTER, 0.4f, 1.6f);
            }
        }
    }

    @Override
    protected void pose() {
        boolean flowers = !shorn();
        float swing = (float) Math.sin(phase) * 0.5f * gait();
        float[] sign = {1, -1, -1, 1};
        float[] hipZ = {0.4f, 0.4f, -0.4f, -0.4f};
        float[] hipX = {0.23f, -0.23f, 0.23f, -0.23f};
        for (int i = 0; i < 4; i++) {
            float angle = swing * sign[i];
            swing(legs[i], hipX[i], 0.68f, hipZ[i], 0.55f, angle);
            hooves[i].at(hipX[i], 0.68f - (float) Math.cos(angle) * 0.62f, hipZ[i] - (float) Math.sin(angle) * 0.62f);
            hooves[i].rot.identity().rotateX(angle);
        }
        // cabeça balança de leve; rabo acompanha o passo
        head.rot.identity().rotateX((float) Math.sin(age * 0.07) * 0.04f);
        snout.rot.identity().rotateX((float) Math.sin(age * 0.07) * 0.04f);
        tail.rot.identity().rotateX(-0.2f + (float) Math.sin(age * 0.2) * 0.2f * (0.4f + gait()));
        for (int i = 0; i < FLOWERS; i++) {
            float sway = (float) Math.sin(age * 0.09 + i * 1.7) * 0.12f + (float) Math.sin(phase + i) * 0.08f * gait();
            stems[i].visible = flowers;
            blooms[i][0].visible = flowers;
            blooms[i][1].visible = flowers;
            stems[i].rot.identity().rotateZ(sway);
            blooms[i][0].rot.identity().rotateZ(sway).rotateY(i * 0.7f);
            blooms[i][1].rot.identity().rotateZ(sway);
        }
    }
}
