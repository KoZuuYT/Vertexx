package dev.vertexx.fauna;

import dev.vertexx.core.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Golem de Ametista: pequeno autômato musical (1 bloco de ametista + 1 de calcita + abóbora entalhada). Fica perto de
 * onde foi invocado, ouve sons (blocos de nota, sinos, passos, pistões...) e gira para o Sculk Sensor Calibrado ou
 * Observer mais próximo. Sintonizado com um Fragmento de Ametista numa nota, emite um pulso de Redstone de força 15
 * no bloco abaixo dele por 10 ticks sempre que ouve essa nota.
 */
final class AmethystGolem extends Critter {

    static final String[] NOTES = {"Fá#", "Sol", "Sol#", "Lá", "Lá#", "Si", "Dó", "Dó#", "Ré", "Ré#", "Mi", "Fá"};

    private final NamespacedKey noteKey;
    private final Location home;
    private float facing;
    private int listenUntil;
    private int note = -1;

    AmethystGolem(FaunaManager manager, Mob mob) {
        super(manager, mob);
        noteKey = new NamespacedKey(plugin, "golem_note");
        Integer saved = mob.getPersistentDataContainer().get(noteKey, PersistentDataType.INTEGER);
        note = saved == null ? -1 : saved;
        home = mob.getLocation().clone();
        mob.setSilent(true);
        mob.setAware(false);
        mob.setGravity(false);
        facing = mob.getLocation().getYaw();
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.AMETHYST_GOLEM;
    }

    static String noteName(int n) {
        return NOTES[n % 12] + (n >= 12 ? " agudo" : "");
    }

    @Override
    protected void think() {
        Vector back = home.toVector().subtract(mob.getLocation().toVector());
        mob.setVelocity(back.lengthSquared() > 4 ? back.normalize().multiply(0.1) : new Vector());
    }

    /** Ouviu um som em {@code at}; {@code heard} é a nota (0-24) ou -1 para sons sem altura. */
    void hear(Location at, int heard) {
        Location me = mob.getLocation();
        if (!at.getWorld().equals(me.getWorld()) || at.distanceSquared(me) > 16 * 16) {
            return;
        }
        Location target = nearestReceiver(me);
        Location look = target != null ? target : at;
        Vector dir = look.toVector().subtract(me.toVector());
        if (dir.getX() * dir.getX() + dir.getZ() * dir.getZ() > 1e-4) {
            facing = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        }
        listenUntil = age + 24;
        me.getWorld().spawnParticle(Particle.NOTE, me.clone().add(0, 1.4, 0), 1, 0.2, 0.1, 0.2, heard < 0 ? 0.5 : heard / 24.0);
        if (heard >= 0 && heard == note) {
            pulse();
        }
    }

    private Location nearestReceiver(Location me) {
        Location best = null;
        double bestD = 12 * 12;
        int r = 12;
        for (int x = -r; x <= r; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -r; z <= r; z++) {
                    Block b = me.getBlock().getRelative(x, y, z);
                    Material m = b.getType();
                    if (m == Material.OBSERVER || m == Material.CALIBRATED_SCULK_SENSOR) {
                        double d = b.getLocation().add(0.5, 0.5, 0.5).distanceSquared(me);
                        if (d < bestD) {
                            bestD = d;
                            best = b.getLocation().add(0.5, 0.5, 0.5);
                        }
                    }
                }
            }
        }
        return best;
    }

    private void pulse() {
        Block below = home.getBlock().getRelative(0, -1, 0);
        for (int i = 0; i < 3 && below.getType().isAir(); i++) {
            below = below.getRelative(0, -1, 0);
        }
        if (!below.getType().isSolid() || below.getState() instanceof org.bukkit.block.TileState
                || manager.pulses().containsKey(below)) {
            return;
        }
        BlockData original = below.getBlockData();
        manager.pulses().put(below, original);
        below.setType(Material.REDSTONE_BLOCK, false);
        Block target = below;
        mob.getWorld().playSound(mob.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.4f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (manager.pulses().remove(target) != null) {
                target.setBlockData(original, false);
            }
        }, 10L);
    }

    @Override
    boolean interact(Player player, ItemStack hand) {
        if (hand.getType() != Material.AMETHYST_SHARD) {
            return false;
        }
        note = (note + 1) % 25;
        mob.getPersistentDataContainer().set(noteKey, PersistentDataType.INTEGER, note);
        float pitch = (float) Math.pow(2.0, (note - 12) / 12.0);
        player.playSound(mob.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, pitch);
        Msg.actionBar(player, "<light_purple>Golem sintonizado em <white>" + noteName(note) + " <dark_gray>(" + note + "/24)");
        return true;
    }

    @Override
    protected float yaw() {
        return facing;
    }

    @Override
    protected void pose() {
        model.play(age < listenUntil ? "listen" : "idle");
        model.speed(1f);
    }
}
