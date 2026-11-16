package dev.vertexx.fauna;

import dev.vertexx.core.Msg;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Anquilossauro de Ardósia: dinossauro blindado ressuscitado. Alimentado com Glow Berries, passa a seguir o jogador;
 * com a mão vazia, o dono o faz bater a cauda no chão, quebrando pedra e ardósia numa área de 3x3x3 à frente sem
 * gastar picareta (recarga de 15 s).
 */
final class Ankylo extends Critter {

    private UUID owner;
    private int wander;
    private int slamReady;

    Ankylo(FaunaManager manager, Mob mob) {
        super(manager, mob);
        mob.setSilent(true);
        String raw = mob.getPersistentDataContainer().get(manager.ownerKey(), PersistentDataType.STRING);
        if (raw != null) {
            try {
                owner = UUID.fromString(raw);
            } catch (IllegalArgumentException ignored) {
                owner = null;
            }
        }
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.DEEPSLATE_ANKYLO;
    }

    @Override
    protected float modelScale() {
        return 1.1f;
    }

    private Player ownerPlayer() {
        return owner == null ? null : Bukkit.getPlayer(owner);
    }

    @Override
    protected void think() {
        Player p = ownerPlayer();
        if (p != null && p.getWorld() == mob.getWorld()) {
            double d2 = p.getLocation().distanceSquared(mob.getLocation());
            if (d2 > 24 * 24) {
                mob.teleport(p.getLocation());
            } else if (d2 > 4 * 4) {
                mob.getPathfinder().moveTo(p.getLocation(), 1.25);
            } else {
                mob.getPathfinder().stopPathfinding();
            }
            return;
        }
        if (wander-- <= 0) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            wander = 60 + r.nextInt(120);
            if (r.nextInt(3) != 0) {
                mob.getPathfinder().moveTo(mob.getLocation().add(r.nextDouble(-5, 5), 0, r.nextDouble(-5, 5)), 0.8);
            }
        }
    }

    private static boolean breakable(Material m) {
        return Tag.BASE_STONE_OVERWORLD.isTagged(m) || m == Material.DEEPSLATE || m == Material.COBBLED_DEEPSLATE
                || m == Material.COBBLESTONE || m == Material.TUFF || m == Material.CALCITE || m == Material.DRIPSTONE_BLOCK
                || m == Material.SMOOTH_BASALT;
    }

    @Override
    boolean interact(Player player, ItemStack hand) {
        if (hand.getType() == Material.GLOW_BERRIES) {
            hand.setAmount(hand.getAmount() - 1);
            owner = player.getUniqueId();
            mob.getPersistentDataContainer().set(manager.ownerKey(), PersistentDataType.STRING, owner.toString());
            mob.getWorld().spawnParticle(Particle.HEART, mob.getLocation().add(0, 1, 0), 5, 0.4, 0.3, 0.4);
            Msg.actionBar(player, "<green>O anquilossauro agora te segue. <gray>Mão vazia: bater a cauda.");
            return true;
        }
        if (hand.getType().isAir() && player.getUniqueId().equals(owner)) {
            if (age < slamReady) {
                Msg.actionBar(player, "<gray>Ele ainda está descansando (" + (slamReady - age) / 20 + "s).");
                return true;
            }
            slamReady = age + 300;
            slam(player);
            return true;
        }
        return false;
    }

    private void slam(Player player) {
        Vector dir = player.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize();
        Location center = mob.getLocation().add(dir.clone().multiply(2.2)).add(0, 1, 0);
        mob.setRotation((float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ())), 0);
        model.restart("slam");
        int broken = 0;
        ItemStack tool = new ItemStack(Material.IRON_PICKAXE);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block b = center.clone().add(x, y, z).getBlock();
                    if (breakable(b.getType()) && b.breakNaturally(tool)) {
                        broken++;
                    }
                }
            }
        }
        mob.getWorld().playSound(center, Sound.BLOCK_DEEPSLATE_BREAK, 1.4f, 0.6f);
        mob.getWorld().spawnParticle(Particle.EXPLOSION, center, 1);
        Msg.actionBar(player, broken > 0 ? "<gray>Quebrou <white>" + broken + "<gray> blocos." : "<gray>Nada para quebrar ali.");
    }

    @Override
    protected void pose() {
        if ("slam".equals(model.playing()) && !model.finished()) {
            return;
        }
        model.play(speed > 0.01 ? "walk" : "idle");
        model.speed(speed > 0.01 ? (float) Math.max(0.7, Math.min(1.5, speed * 7)) : 1f);
    }
}
