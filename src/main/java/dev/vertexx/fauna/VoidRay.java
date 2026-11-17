package dev.vertexx.fauna;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Água-viva do Fim (End Jellyfish): água-viva voadora e passiva das copas do Coro. Paira entre as ilhas, segue quem segura Fruta do
 * Coro e, com uma sela ou uma guia na mão, deixa o jogador pendurar-se nela e planar devagar (transporte pré-Elytra).
 * Base Allay sem IA (o cérebro do Allay brigaria com a nossa direção): o movimento é por velocidade.
 */
final class VoidRay extends Critter {

    private Vector heading = new Vector();
    private int wander;
    private float facing;

    VoidRay(FaunaManager manager, Mob mob) {
        super(manager, mob);
        mob.setSilent(true);
        mob.setAware(false);
        mob.setGravity(false);
        facing = mob.getLocation().getYaw();
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.VOID_RAY;
    }

    @Override
    protected String modelId() {
        return "end_jellyfish";
    }

    private Player rider() {
        for (var passenger : mob.getPassengers()) {
            if (passenger instanceof Player p) {
                return p;
            }
        }
        return null;
    }

    private boolean free(Location at) {
        return !at.getBlock().getType().isSolid();
    }

    @Override
    protected void think() {
        Vector velocity;
        Player rider = rider();
        if (rider != null) {                     // planando com um passageiro: segue para onde ele olha
            Vector look = rider.getLocation().getDirection();
            Vector flat = new Vector(look.getX(), 0, look.getZ());
            flat = flat.lengthSquared() < 1e-4 ? new Vector() : flat.normalize().multiply(0.2);
            double rise = Math.max(-0.1, Math.min(0.1, -look.getY() * 0.2)) - 0.025;
            velocity = flat.setY(rise);
        } else {
            Player lure = null;
            double best = 18 * 18;
            for (Player p : mob.getWorld().getPlayers()) {
                boolean fruit = p.getInventory().getItemInMainHand().getType() == Material.CHORUS_FRUIT
                        || p.getInventory().getItemInOffHand().getType() == Material.CHORUS_FRUIT;
                double d = p.getLocation().distanceSquared(mob.getLocation());
                if (fruit && d < best) {
                    best = d;
                    lure = p;
                }
            }
            if (lure != null) {
                Vector to = lure.getEyeLocation().add(0, 1.2, 0).toVector().subtract(mob.getLocation().toVector());
                velocity = to.lengthSquared() > 9 ? to.normalize().multiply(0.1) : new Vector();
                wander = 0;
            } else {
                if (wander-- <= 0) {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    double a = r.nextDouble() * Math.PI * 2;
                    heading = r.nextInt(3) == 0 ? new Vector()
                            : new Vector(Math.cos(a), r.nextDouble(-0.25, 0.25), Math.sin(a)).multiply(0.045);
                    wander = 50 + r.nextInt(100);
                }
                velocity = heading.clone();
            }
        }
        if (velocity.lengthSquared() > 0 && !free(mob.getLocation().add(velocity.clone().multiply(8)))) {
            velocity = new Vector();
            heading = new Vector();
        }
        mob.setVelocity(velocity);
        if (velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ() > 1e-4) {
            facing = (float) Math.toDegrees(Math.atan2(-velocity.getX(), velocity.getZ()));
        }
    }

    @Override
    boolean interact(Player player, ItemStack hand) {
        Material m = hand.getType();
        if ((m == Material.SADDLE || m == Material.LEAD) && mob.getPassengers().isEmpty()) {
            mob.addPassenger(player);
            return true;
        }
        return false;
    }

    @Override
    protected float yaw() {
        return facing;
    }

    @Override
    protected void pose() {
        model.play(mob.getVelocity().lengthSquared() > 0.0004 || rider() != null ? "fly" : "idle");
        model.speed(1f);
    }
}
