package dev.vertexx.fauna;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * The Monster of the Ocean Depths / Barnacle (Mob Vote 2017): lula gigante ancorada no fundo dos oceanos profundos.
 * Em ciclos, escancara a bocarra de três mandíbulas e cria um redemoinho que puxa para baixo quem estiver nadando
 * por perto e arrasta os barcos para o centro (onde eles podem quebrar). Nada devagar atrás de quem entra no seu
 * território e morde quem chega perto da bocarra (além do afogamento do redemoinho).
 */
final class Barnacle extends Critter {

    private static final int REST = 160;
    private static final int WHIRL = 100;

    private int cycle = ThreadLocalRandom.current().nextInt(REST);
    private float facing;
    private int biteCooldown;
    private int wander;
    private Vector heading = new Vector();
    private Entity grudge;
    private int grudgeUntil;

    Barnacle(FaunaManager manager, Mob mob) {
        super(manager, mob);
        mob.setSilent(true);
        mob.setGravity(false);
        AttributeInstance hp = mob.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null && hp.getBaseValue() < 60) {
            hp.setBaseValue(60);
            mob.setHealth(60);
        }
        facing = mob.getLocation().getYaw();
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.BARNACLE;
    }

    private boolean whirling() {
        return cycle >= REST;
    }

    @Override
    protected void think() {
        cycle = (cycle + 2) % (REST + WHIRL);
        if (biteCooldown > 0) {
            biteCooldown -= 2;
        }
        double radius = plugin.getConfig().getDouble("fauna.barnacle.radius", 10);
        World world = mob.getWorld();
        Location mouth = mob.getLocation().add(mob.getLocation().getDirection().setY(0).normalize().multiply(1.5)).add(0, 1, 0);
        Entity target = null;
        double best = radius * radius;
        for (Entity e : mob.getNearbyEntities(radius, 24, radius)) {
            boolean swimmer = e instanceof Player p && p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE
                    && p.isInWater();
            boolean boat = e instanceof Boat;
            if (!swimmer && !boat) {
                continue;
            }
            double dx = e.getX() - mob.getX();
            double dz = e.getZ() - mob.getZ();
            double d2 = dx * dx + dz * dz;
            if (d2 < best) {
                best = d2;
                target = e;
            }
            if (!whirling()) {
                continue;
            }
            double d = Math.sqrt(d2);
            double pull = 0.05 + 0.07 * (1 - d / radius);
            Vector to = mouth.toVector().subtract(e.getLocation().toVector());
            to.setY(0);
            Vector v = e.getVelocity();
            if (to.lengthSquared() > 0.01) {
                to.normalize().multiply(pull);
                // tangente: o redemoinho gira
                Vector swirl = new Vector(-to.getZ(), 0, to.getX()).multiply(0.6);
                v.add(to).add(swirl);
            }
            if (swimmer) {
                v.setY(Math.max(v.getY() - 0.045, -0.35));
            }
            e.setVelocity(v);
            if (boat && d < 2.2 && plugin.getConfig().getBoolean("fauna.barnacle.break-boats", true)) {
                swallowBoat((Boat) e);
            }
        }
        swim(target);
        bite(mouth);
        if (target != null) {      // vira devagar para o alvo mais próximo
            float want = (float) Math.toDegrees(Math.atan2(-(target.getX() - mob.getX()), target.getZ() - mob.getZ()));
            float diff = ((want - facing) % 360 + 540) % 360 - 180;
            facing += Math.max(-6, Math.min(6, diff));
        }
        if (whirling() && near()) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < 6; i++) {
                double a = (age * 0.25 + i * Math.PI / 3);
                double rr = 1 + r.nextDouble() * radius * 0.8;
                Location p = mouth.clone().add(Math.cos(a) * rr, r.nextDouble() * 6, Math.sin(a) * rr);
                world.spawnParticle(Particle.BUBBLE, p, 2, 0.1, 0.1, 0.1, 0);
                world.spawnParticle(Particle.CURRENT_DOWN, p, 1, 0.2, 0.2, 0.2, 0);
            }
            if (age % 20 == 0) {
                world.playSound(mouth, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.2f, 0.6f);
            }
        }
    }

    private boolean water(org.bukkit.Location l) {
        return l.getBlock().getType() == Material.WATER || l.getBlock().getType() == Material.BUBBLE_COLUMN
                || l.getBlock().getType() == Material.KELP_PLANT || l.getBlock().getType() == Material.SEAGRASS
                || l.getBlock().getType() == Material.TALL_SEAGRASS || l.getBlock().getType() == Material.KELP;
    }

    /** Nada devagar: atrás de quem estiver no território (ou de quem o feriu), ou à deriva pelo fundo. */
    private void swim(Entity target) {
        double base = plugin.getConfig().getDouble("fauna.barnacle.swim-speed", 0.075);
        Entity chase = grudge != null && grudge.isValid() && age < grudgeUntil && grudge.getWorld() == mob.getWorld() ? grudge : target;
        Vector move = new Vector();
        if (chase != null) {
            Vector to = chase.getLocation().add(0, 0.6, 0).toVector().subtract(mob.getLocation().toVector());
            if (to.lengthSquared() > 3.0 * 3.0) {          // chega perto, mas não passa por cima
                move = to.normalize().multiply(base);
            }
            if (!whirling()) {
                wander = 0;
            }
        } else {
            if (wander-- <= 0) {
                double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
                heading = ThreadLocalRandom.current().nextInt(3) == 0 ? new Vector()
                        : new Vector(Math.cos(a), ThreadLocalRandom.current().nextDouble(-0.1, 0.1), Math.sin(a)).multiply(base * 0.4);
                wander = 40 + ThreadLocalRandom.current().nextInt(80);
            }
            move = heading.clone();
        }
        if (move.lengthSquared() > 0) {                      // só avança onde há água por cima e à frente
            Location ahead = mob.getLocation().add(move.clone().multiply(8));
            if (!water(ahead) || !water(ahead.clone().add(0, 3, 0)) || !water(mob.getLocation().add(0, 3, 0))) {
                move = new Vector();
                heading = new Vector();
            }
        }
        mob.setVelocity(move);
        if (move.lengthSquared() > 1e-4 && chase == null) {
            facing = (float) Math.toDegrees(Math.atan2(-move.getX(), move.getZ()));
        }
    }

    /** Morde quem chega perto da bocarra: dano, recuo para dentro e animação. */
    private void bite(Location mouth) {
        if (biteCooldown > 0) {
            return;
        }
        double damage = plugin.getConfig().getDouble("fauna.barnacle.bite-damage", 6);
        for (Entity e : mob.getNearbyEntities(5, 4, 5)) {
            if (!(e instanceof Player p) || p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE
                    || !(p.isInWater() || p.getVehicle() instanceof Boat)) {
                continue;
            }
            if (p.getLocation().add(0, 0.9, 0).distanceSquared(mouth) > 3.6 * 3.6) {
                continue;
            }
            biteCooldown = 36;
            model.restart("bite");
            mob.getWorld().playSound(mouth, Sound.ENTITY_GUARDIAN_ATTACK, 1.2f, 0.5f);
            mob.getWorld().playSound(mouth, Sound.ENTITY_ELDER_GUARDIAN_HURT, 0.8f, 0.6f);
            mob.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, p.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0);
            p.damage(damage, mob);
            p.setVelocity(p.getVelocity().add(mouth.toVector().subtract(p.getLocation().toVector()).setY(-0.1).normalize().multiply(0.35)));
            return;
        }
    }

    /** Atacado, abre a bocarra na hora, vira para o agressor e passa a persegui-lo por uns segundos. */
    @Override
    protected void onHurt(Entity attacker) {
        if (!whirling()) {
            cycle = REST;
        }
        if (attacker != null) {
            grudge = attacker;
            grudgeUntil = age + 200;
            facing = (float) Math.toDegrees(Math.atan2(-(attacker.getX() - mob.getX()), attacker.getZ() - mob.getZ()));
        }
        model.restart("bite");
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_HURT, 1f, 0.6f);
    }

    private void swallowBoat(Boat boat) {
        World world = boat.getWorld();
        Location at = boat.getLocation();
        for (Entity passenger : boat.getPassengers()) {
            boat.removePassenger(passenger);
            passenger.setVelocity(new Vector(0, -0.3, 0));
        }
        ItemStack item = boat.getPickItemStack();
        boat.remove();
        if (item != null && item.getType() != Material.AIR) {
            world.dropItemNaturally(at, item);
        }
        world.playSound(at, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1f, 0.6f);
        world.playSound(at, Sound.BLOCK_WOOD_BREAK, 1f, 0.7f);
        world.spawnParticle(Particle.SPLASH, at, 40, 0.8, 0.2, 0.8, 0.1);
        model.restart("bite");
    }

    @Override
    protected float yaw() {
        return facing;
    }

    @Override
    protected void pose() {
        if ("bite".equals(model.playing()) && !model.finished()) {
            return;
        }
        model.play(whirling() ? "whirl" : "idle");
        model.speed(1f);
    }
}
