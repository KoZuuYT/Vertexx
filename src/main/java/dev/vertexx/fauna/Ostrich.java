package dev.vertexx.fauna;

import dev.vertexx.end.EndItem;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Avestruz: montaria veloz das savanas. Base Horse com a IA original (passeia, foge, pode ser montado); o plugin
 * só desenha o modelo, deixa pegadas ao correr e, de tempos em tempos, bota um ovo grande que choca em fardos de feno.
 */
final class Ostrich extends Critter {

    private int nextEgg;

    Ostrich(FaunaManager manager, Mob mob) {
        super(manager, mob);
        mob.setSilent(true);
        AttributeInstance speed = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(0.34);
        }
        AttributeInstance jump = mob.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) {
            jump.setBaseValue(0.5);
        }
        nextEgg = 6000 + ThreadLocalRandom.current().nextInt(6000);
    }

    @Override
    protected boolean wild() {
        return false;
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.OSTRICH;
    }

    @Override
    protected float modelScale() {
        return 0.85f;
    }

    @Override
    protected void think() {
        if (speed > 0.18 && age % 4 == 0 && mob.isOnGround()) {
            mob.getWorld().spawnParticle(Particle.BLOCK, mob.getLocation().add(0, 0.1, 0), 3, 0.2, 0.02, 0.2, 0.02,
                    mob.getLocation().getBlock().getRelative(0, -1, 0).getBlockData());
        }
        if (age >= nextEgg) {
            nextEgg = age + 9000 + ThreadLocalRandom.current().nextInt(9000);
            if (mob.getPassengers().isEmpty() && mob instanceof Horse horse && horse.isAdult()
                    && plugin.getConfig().getBoolean("overworld.arid.ostrich-eggs", true)) {
                ItemStack egg = EndItem.OSTRICH_EGG.create(plugin);
                mob.getWorld().dropItem(mob.getLocation(), egg);
                mob.getWorld().playSound(mob.getLocation(), org.bukkit.Sound.ENTITY_CHICKEN_EGG, 1f, 0.6f);
            }
        }
    }

    @Override
    boolean interact(Player player, ItemStack hand) {
        if (mob instanceof Horse horse && !horse.isTamed() && hand.getType() != Material.SADDLE) {
            horse.setTamed(true);                      // sem domar: monta direto e não derruba o jogador
            horse.setOwner(player);
            horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        }
        return false;
    }

    @Override
    protected void pose() {
        if (speed > 0.2) {
            model.play("run");
        } else if (speed > 0.01) {
            model.play("walk");
        } else {
            model.play("idle");
        }
        model.speed((float) Math.max(0.7, Math.min(1.6, speed * 6)));
    }
}
