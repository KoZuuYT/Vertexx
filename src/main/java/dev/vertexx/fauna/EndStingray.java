package dev.vertexx.fauna;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Raia do Fim (End Stingray): raia de obsidiana com veios de ametista que plana em círculos largos sobre as ilhas.
 * Passiva e inofensiva. Base Allay sem IA (o cérebro do Allay brigaria com a nossa direção): o movimento é por
 * velocidade, como na água-viva. Aproxima-se de quem segura Fruta do Coro.
 */
final class EndStingray extends Critter {

    private double angle;
    private double turn;
    private double climb;
    private int mood;
    private float facing;

    EndStingray(FaunaManager manager, Mob mob) {
        super(manager, mob);
        mob.setSilent(true);
        mob.setAware(false);
        mob.setGravity(false);
        angle = Math.toRadians(mob.getLocation().getYaw() + 90);
        facing = mob.getLocation().getYaw();
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.END_STINGRAY;
    }

    private static boolean free(Location at) {
        return !at.getBlock().getType().isSolid();
    }

    @Override
    protected void think() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Player lure = null;
        double best = 24 * 24;
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
            Vector to = lure.getEyeLocation().add(0, 2.5, 0).toVector().subtract(mob.getLocation().toVector());
            double want = Math.atan2(to.getZ(), to.getX());
            double diff = Math.atan2(Math.sin(want - angle), Math.cos(want - angle));
            turn = Math.max(-0.05, Math.min(0.05, diff * 0.2));
            climb = Math.max(-0.03, Math.min(0.03, to.getY() * 0.01));
        } else if (mood-- <= 0) {
            // um arco largo para um lado, por 4 a 9 segundos, com subidas e descidas suaves
            turn = r.nextDouble(-0.035, 0.035);
            climb = r.nextDouble(-0.02, 0.02);
            mood = 80 + r.nextInt(100);
        }
        angle += turn;
        Vector velocity = new Vector(Math.cos(angle) * 0.085, climb, Math.sin(angle) * 0.085);
        if (!free(mob.getLocation().add(velocity.clone().multiply(10)))) {
            angle += Math.PI / 2;          // parede ou ilha à frente: faz a curva
            velocity = new Vector(Math.cos(angle) * 0.085, Math.abs(climb) + 0.02, Math.sin(angle) * 0.085);
        }
        mob.setVelocity(velocity);
        facing = (float) Math.toDegrees(Math.atan2(-velocity.getX(), velocity.getZ()));
    }

    @Override
    protected float yaw() {
        return facing;
    }

    @Override
    protected void pose() {
        model.play("fly");
        model.speed(1f);
    }
}
