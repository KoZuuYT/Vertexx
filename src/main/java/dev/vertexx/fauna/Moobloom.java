package dev.vertexx.fauna;

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
        model.play(speed > 0.02 ? "walk" : "idle");
        model.speed((float) Math.min(2.2, 0.55 + speed * 7));
        boolean flowers = !shorn();
        for (int i = 0; i < FLOWERS; i++) {
            model.hide("flower" + i, !flowers);
        }
    }
}
