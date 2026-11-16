package dev.vertexx.fauna;

import dev.vertexx.core.Msg;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Pterodáctilo de Caverna: planador ressuscitado. Com a mão vazia, o jogador sobe nele e plana para onde olha pelas
 * cavernas; agachado e com a mão vazia, ele solta um grito ultrassônico que faz os minérios num raio de 24 blocos
 * brilharem (visíveis através das paredes) por 8 segundos (recarga de 20 s).
 */
final class CaveGlider extends Critter {

    private Vector heading = new Vector();
    private int wander;
    private float facing;
    private int screamReady;

    CaveGlider(FaunaManager manager, Mob mob) {
        super(manager, mob);
        mob.setSilent(true);
        mob.setAware(false);
        mob.setGravity(false);
        facing = mob.getLocation().getYaw();
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.CAVE_GLIDER;
    }

    @Override
    protected float modelScale() {
        return 1.1f;
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
        if (rider != null) {
            Vector look = rider.getLocation().getDirection();
            Vector flat = new Vector(look.getX(), 0, look.getZ());
            flat = flat.lengthSquared() < 1e-4 ? new Vector() : flat.normalize().multiply(0.26);
            double rise = Math.max(-0.14, Math.min(0.14, -look.getY() * 0.3)) - 0.01;
            velocity = flat.setY(rise);
        } else {
            if (wander-- <= 0) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                double a = r.nextDouble() * Math.PI * 2;
                heading = r.nextInt(3) == 0 ? new Vector()
                        : new Vector(Math.cos(a), r.nextDouble(-0.2, 0.2), Math.sin(a)).multiply(0.07);
                wander = 50 + r.nextInt(100);
            }
            velocity = heading.clone();
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
        if (!hand.getType().isAir()) {
            return false;
        }
        if (player.isSneaking()) {
            if (age < screamReady) {
                Msg.actionBar(player, "<gray>O grito ainda não voltou (" + (screamReady - age) / 20 + "s).");
                return true;
            }
            screamReady = age + 400;
            scream(player);
            return true;
        }
        if (mob.getPassengers().isEmpty()) {
            mob.addPassenger(player);
            return true;
        }
        return false;
    }

    private void scream(Player player) {
        model.restart("scream");
        Location me = mob.getLocation();
        me.getWorld().playSound(me, Sound.ENTITY_BAT_AMBIENT, 2f, 0.5f);
        me.getWorld().playSound(me, Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.5f, 0.6f);
        int found = 0;
        for (int x = -24; x <= 24 && found < 40; x++) {
            for (int y = -16; y <= 16 && found < 40; y++) {
                for (int z = -24; z <= 24 && found < 40; z++) {
                    Block b = me.getBlock().getRelative(x, y, z);
                    Material m = b.getType();
                    if (!(m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS)) {
                        continue;
                    }
                    found++;
                    BlockDisplay glow = b.getWorld().spawn(b.getLocation(), BlockDisplay.class, d -> {
                        d.setBlock(b.getBlockData());
                        d.setGlowing(true);
                        d.setGlowColorOverride(Color.fromRGB(255, 214, 64));
                        d.setPersistent(false);
                        d.setTransformation(new Transformation(new Vector3f(-0.003f, -0.003f, -0.003f),
                                new AxisAngle4f(0, 0, 0, 1), new Vector3f(1.006f, 1.006f, 1.006f), new AxisAngle4f(0, 0, 0, 1)));
                    });
                    Bukkit.getScheduler().runTaskLater(plugin, glow::remove, 160L);
                }
            }
        }
        Msg.actionBar(player, found > 0 ? "<yellow>O grito revelou <white>" + found + "<yellow> minérios." : "<gray>Nenhum minério por perto.");
    }

    @Override
    protected float yaw() {
        return facing;
    }

    @Override
    protected void pose() {
        if ("scream".equals(model.playing()) && !model.finished()) {
            return;
        }
        model.play(mob.getVelocity().lengthSquared() > 0.0004 || rider() != null ? "fly" : "glide");
        model.speed(1f);
    }
}
