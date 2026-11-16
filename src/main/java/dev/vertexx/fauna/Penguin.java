package dev.vertexx.fauna;

import org.bukkit.Particle;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

/**
 * Pinguim das praias rochosas (Mob Vote 2023). Desajeitado em terra (anda gingando, devagar como a tartaruga-base)
 * e ótimo nadador: barcos com um pinguim nadando por perto ficam mais rápidos, como a Graça do Golfinho faz com
 * quem nada.
 */
final class Penguin extends Critter {

    Penguin(FaunaManager manager, Mob mob) {
        super(manager, mob);
        mob.setSilent(true);
    }

    @Override
    protected boolean wild() {
        return false;         // IA anfíbia da tartaruga-base: anda em terra e nada
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.PENGUIN;
    }

    @Override
    protected void think() {
        if (age % 10 != 0 || !mob.isInWater()) {
            return;
        }
        double boost = plugin.getConfig().getDouble("fauna.penguin.boat-boost", 1.12);
        for (Entity entity : mob.getNearbyEntities(6, 3, 6)) {
            if (!(entity instanceof Boat boat) || boat.getPassengers().isEmpty()) {
                continue;
            }
            Vector v = boat.getVelocity();
            double h = Math.hypot(v.getX(), v.getZ());
            if (h < 0.05 || h > 0.9) {
                continue;
            }
            boat.setVelocity(new Vector(v.getX() * boost, v.getY(), v.getZ() * boost));
            boat.getWorld().spawnParticle(Particle.DOLPHIN, boat.getLocation().add(0, 0.2, 0), 6, 0.6, 0.1, 0.6, 0);
            // o pinguim acompanha o barco
            mob.getPathfinder().moveTo(boat.getLocation(), 1.6);
        }
    }

    @Override
    protected void pose() {
        if (mob.isInWater()) {
            model.play("swim");
            model.speed((float) Math.min(2.0, 0.8 + speed * 6));
        } else if (speed > 0.012) {
            model.play("walk");
            model.speed((float) Math.min(2.0, 0.7 + speed * 10));
        } else {
            model.play("idle");
            model.speed(1f);
        }
    }
}
