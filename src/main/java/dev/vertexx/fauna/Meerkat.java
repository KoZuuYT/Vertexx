package dev.vertexx.fauna;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrushableBlock;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

/**
 * Suricato: pequeno e curioso do deserto. Passeia e, de vez em quando, para numa areia próxima, cava e revela
 * um item enterrado ou um bloco de areia suspeita. Foge quando machucado.
 */
final class Meerkat extends Critter {

    private int wander;
    private int nextDig;
    private Block digging;
    private int digUntil;

    Meerkat(FaunaManager manager, Mob mob) {
        super(manager, mob);
        mob.setSilent(true);
        nextDig = 600 + ThreadLocalRandom.current().nextInt(1200);
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.MEERKAT;
    }

    private Block findSand() {
        Location me = mob.getLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 12; i++) {
            Block b = me.getBlock().getRelative(r.nextInt(-5, 6), -1, r.nextInt(-5, 6));
            if ((b.getType() == Material.SAND || b.getType() == Material.RED_SAND)
                    && b.getRelative(0, 1, 0).getType().isAir()) {
                return b;
            }
        }
        return null;
    }

    @Override
    protected void think() {
        if (digging != null) {
            mob.getPathfinder().stopPathfinding();
            if (age >= digUntil) {
                reveal(digging);
                digging = null;
                nextDig = age + 1200 + ThreadLocalRandom.current().nextInt(2400);
            }
            return;
        }
        if (age >= nextDig) {
            Block sand = findSand();
            nextDig = age + 200;
            if (sand != null) {
                Location at = sand.getLocation().add(0.5, 1, 0.5);
                if (at.distanceSquared(mob.getLocation()) < 2.5) {
                    digging = sand;
                    digUntil = age + 28;
                    model.restart("dig");
                    mob.getWorld().playSound(at, Sound.BLOCK_SAND_BREAK, 0.8f, 1.2f);
                } else {
                    mob.getPathfinder().moveTo(at, 1.0);
                    nextDig = age + 20;
                }
            }
            return;
        }
        if (wander-- <= 0) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            wander = 50 + r.nextInt(100);
            if (r.nextInt(3) != 0) {
                mob.getPathfinder().moveTo(mob.getLocation().add(r.nextDouble(-6, 6), 0, r.nextDouble(-6, 6)), 0.9);
            }
        }
    }

    private void reveal(Block sand) {
        if (!sand.getType().name().endsWith("SAND")) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location above = sand.getLocation().add(0.5, 1.1, 0.5);
        ItemStack loot = switch (r.nextInt(7)) {
            case 0 -> new ItemStack(Material.BONE);
            case 1 -> new ItemStack(Material.GOLD_NUGGET, 1 + r.nextInt(3));
            case 2 -> new ItemStack(Material.STICK, 2);
            case 3 -> new ItemStack(Material.CLAY_BALL, 2);
            case 4 -> new ItemStack(Material.EMERALD);
            case 5 -> new ItemStack(Material.BRICK);
            default -> new ItemStack(Material.DEAD_BUSH);
        };
        if (r.nextInt(100) < 35) {
            sand.setType(Material.SUSPICIOUS_SAND, false);
            BlockState state = sand.getState();
            if (state instanceof BrushableBlock brushable) {
                brushable.setItem(loot);
                brushable.update(true, false);
            }
        } else {
            mob.getWorld().dropItem(above, loot);
        }
        mob.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, above, 14, 0.25, 0.1, 0.25, 0.05, sand.getBlockData());
    }

    @Override
    protected void pose() {
        if ("dig".equals(model.playing()) && !model.finished()) {
            return;
        }
        model.play(speed > 0.01 ? "walk" : "idle");
        model.speed(speed > 0.01 ? (float) Math.max(0.7, Math.min(1.5, speed * 8)) : 1f);
    }
}
