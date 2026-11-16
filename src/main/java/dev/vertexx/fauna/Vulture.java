package dev.vertexx.fauna;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

/**
 * Abutre: ave passiva que circula alto no céu sobre itens largados no chão (restos de combate, mortes, loot),
 * mostrando aos jogadores onde há algo. Sem itens por perto, plana em círculos largos.
 */
final class Vulture extends Critter {

    private Vector heading = new Vector(1, 0, 0);
    private float facing;
    private double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
    private Location home;

    Vulture(FaunaManager manager, Mob mob) {
        super(manager, mob);
        mob.setSilent(true);
        mob.setAware(false);
        mob.setGravity(false);
        home = mob.getLocation().clone();
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.VULTURE;
    }

    @Override
    protected float modelScale() {
        return 1.2f;
    }

    @Override
    protected void think() {
        Location focus = manager.hotspot(mob.getLocation(), 64);
        if (focus == null) {
            focus = home;
        } else {
            home = focus.clone();
        }
        angle += 0.045;
        double radius = focus == home ? 14 : 9;
        double targetY = focus.getY() + 15;
        Location target = new Location(focus.getWorld(), focus.getX() + Math.cos(angle) * radius, targetY,
                focus.getZ() + Math.sin(angle) * radius);
        Vector to = target.toVector().subtract(mob.getLocation().toVector());
        Vector velocity = to.lengthSquared() > 0.04 ? to.normalize().multiply(Math.min(0.32, to.length() * 0.25)) : new Vector();
        mob.setVelocity(velocity);
        if (velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ() > 1e-4) {
            facing = (float) Math.toDegrees(Math.atan2(-velocity.getX(), velocity.getZ()));
        }
        heading = velocity;
    }

    @Override
    protected float yaw() {
        return facing;
    }

    @Override
    protected void pose() {
        model.play(heading.getY() > 0.06 ? "fly" : "glide");
        model.speed(1f);
    }
}
