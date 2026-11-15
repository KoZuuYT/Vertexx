package dev.vertexx.fauna;

import dev.vertexx.core.Msg;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Criatura de folhagem das cavernas exuberantes. Procura pontos totalmente escuros (onde monstros podem
 * nascer), paira sobre eles ranzinza e se acalma quando alguém acende uma luz. Com bagas brilhantes,
 * passa a acompanhar o jogador e a vigiar o escuro ao redor dele.
 */
final class Glare extends Critter {

    private Location spot;
    private boolean angry;
    private int calmUntil;
    private Location wander;

    Glare(FaunaManager manager, Mob mob) {
        super(manager, mob);
        mob.setGravity(false);
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.GLARE;
    }

    UUID owner() {
        String raw = mob.getPersistentDataContainer().get(manager.ownerKey(), PersistentDataType.STRING);
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    protected float modelScale() {
        return 0.9f;
    }

    // ── Comportamento ──────────────────────────────────────────

    private Player ownerPlayer() {
        UUID id = owner();
        return id == null ? null : plugin.getServer().getPlayer(id);
    }

    @Override
    protected void think() {
        World world = mob.getWorld();
        Player owner = ownerPlayer();
        if (age % 20 == 0) {
            if (spot != null && !dark(spot)) {
                calm();
            }
            if (spot == null && age >= calmUntil) {
                Location center = owner != null && owner.getWorld().equals(world) ? owner.getLocation() : mob.getLocation();
                spot = findDark(center);
            }
        }
        if (spot != null) {
            angry = true;
            Location hover = spot.clone().add(0, 1.6, 0);
            if (mob.getLocation().distanceSquared(hover) > 1.2) {
                mob.getPathfinder().moveTo(hover, 1.2);
            }
            if (age % 8 == 0) {
                world.spawnParticle(Particle.ANGRY_VILLAGER, mob.getLocation().add(0, 1.9, 0), 1, 0.25, 0.1, 0.25, 0);
                world.spawnParticle(Particle.DUST, spot.clone().add(0, 0.15, 0), 5, 0.4, 0.02, 0.4, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 40, 40), 1.2f));
            }
            if (age % 90 == 0) {
                world.playSound(mob.getLocation(), Sound.ENTITY_ALLAY_HURT, 0.5f, 0.5f);
            }
            return;
        }
        angry = false;
        if (owner != null && owner.getWorld().equals(world)) {
            double d = owner.getLocation().distanceSquared(mob.getLocation());
            if (d > 36) {
                mob.getPathfinder().moveTo(owner.getLocation().add(0, 2.0, 0), 1.3);
            } else if (d > 4 && age % 40 == 0) {
                mob.getPathfinder().moveTo(owner.getLocation().add(Math.random() * 3 - 1.5, 1.8, Math.random() * 3 - 1.5), 0.8);
            }
            if (d > 48 * 48) {
                mob.teleport(owner.getLocation().add(0, 2, 0));
            }
        } else if (age % 100 == 0) {
            wander = mob.getLocation().add(Math.random() * 8 - 4, Math.random() * 2 - 0.5, Math.random() * 8 - 4);
            if (wander.getBlock().isPassable()) {
                mob.getPathfinder().moveTo(wander, 0.5);
            }
        }
    }

    private void calm() {
        World world = mob.getWorld();
        world.spawnParticle(Particle.HAPPY_VILLAGER, mob.getLocation().add(0, 1.7, 0), 10, 0.4, 0.3, 0.4, 0);
        world.playSound(mob.getLocation(), Sound.ENTITY_ALLAY_ITEM_GIVEN, 0.7f, 1.4f);
        spot = null;
        calmUntil = age + 100; // respira um pouco antes de procurar outro escuro
    }

    private static boolean dark(Location at) {
        Block block = at.getBlock();
        return block.getLightLevel() == 0;
    }

    private Location findDark(Location center) {
        World world = center.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 16; i++) {
            int x = center.getBlockX() + random.nextInt(-14, 15);
            int z = center.getBlockZ() + random.nextInt(-14, 15);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            for (int dy = 3; dy >= -4; dy--) {
                Block b = world.getBlockAt(x, center.getBlockY() + dy, z);
                if (b.isPassable() && !b.isLiquid() && !b.getRelative(0, -1, 0).isPassable()
                        && b.getRelative(0, 1, 0).isPassable() && b.getLightLevel() == 0) {
                    return b.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return null;
    }

    @Override
    boolean interact(Player player, ItemStack hand) {
        if (hand == null || hand.getType() != Material.GLOW_BERRIES) {
            return false;
        }
        UUID current = owner();
        if (player.getUniqueId().equals(current)) {
            player.sendActionBar(Msg.mm("<green>O Glare já acompanha você."));
            return true;
        }
        mob.getPersistentDataContainer().set(manager.ownerKey(), PersistentDataType.STRING, player.getUniqueId().toString());
        mob.setRemoveWhenFarAway(false);
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
        mob.getWorld().spawnParticle(Particle.HEART, mob.getLocation().add(0, 1.8, 0), 6, 0.4, 0.3, 0.4, 0);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ALLAY_ITEM_GIVEN, 1f, 1.2f);
        player.sendActionBar(Msg.mm("<green>O Glare agora acompanha você e vigia o escuro ao seu redor."));
        spot = null;
        return true;
    }

    // ── Desenho ────────────────────────────────────────────────

    @Override
    protected void pose() {
        model.play(angry ? "angry" : "idle");
        model.speed(angry ? 1f : 0.8f + (float) Math.min(1.2, speed * 6));
    }
}
