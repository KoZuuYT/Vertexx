package dev.vertexx.rpg;

import dev.vertexx.PluginMain;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;

/** Fontes de XP dos status: mineração, colheita, pesca, quedas, dano sofrido e projéteis. */
public final class ActivityListener implements Listener {

    private static final int PLACED_LIMIT = 60_000;

    private final PluginMain plugin;
    /** Blocos colocados por jogadores (em memória): quebrá-los não rende XP, evitando farm de colocar e quebrar. */
    private final Set<Long> placed = Collections.newSetFromMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Long, Boolean> eldest) {
            return size() > PLACED_LIMIT;
        }
    });

    public ActivityListener(PluginMain plugin) {
        this.plugin = plugin;
    }

    private RpgManager rpg() {
        return plugin.rpg();
    }

    private static long key(Block block) {
        long position = ((block.getX() & 0x3FFFFFFL) << 38) | ((block.getZ() & 0x3FFFFFFL) << 12)
                | ((block.getY() + 2048) & 0xFFFL);
        return position ^ ((long) block.getWorld().getUID().hashCode() * 0x9E3779B97F4A7C15L);
    }

    public boolean wasPlaced(Block block) {
        return placed.contains(key(block));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        placed.add(key(event.getBlockPlaced()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!rpg().enabled() || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        Block block = event.getBlock();
        Material type = block.getType();
        boolean wasPlaced = placed.remove(key(block));

        if (block.getBlockData() instanceof Ageable age && Ores.cropXp(type) > 0) {
            if (age.getAge() >= age.getMaximumAge()) {
                rpg().addXp(player, Stat.FARMING, Ores.cropXp(type));
            }
            return;
        }
        if (Ores.cropXp(type) > 0) { // melão e abóbora
            if (!wasPlaced) {
                rpg().addXp(player, Stat.FARMING, Ores.cropXp(type));
            }
            return;
        }
        if (wasPlaced) {
            return;
        }
        if (Tag.LOGS.isTagged(type)) {
            rpg().addXp(player, Stat.WOODCUTTING, 3.2);
        } else if (Ores.isOre(type)) {
            rpg().addXp(player, Stat.MINING, Ores.oreXp(type));
        } else if (Tag.MINEABLE_PICKAXE.isTagged(type)) {
            rpg().addXp(player, Stat.MINING, 0.8);
        } else if (Tag.MINEABLE_SHOVEL.isTagged(type)) {
            rpg().addXp(player, Stat.EXCAVATION, 0.8);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            rpg().addXp(event.getPlayer(), Stat.FISHING, 14);
        }
    }

    /** Pés Leves reduz a queda; queda e dano sofrido rendem Acrobacia e Resistência. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallReduce(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player
                && rpg().enabled()) {
            int rank = rpg().data(player).rank(Skill.FEATHER);
            if (rank > 0) {
                event.setDamage(event.getDamage() * (1 - 0.2 * rank));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !rpg().enabled()) {
            return;
        }
        double damage = event.getFinalDamage();
        if (damage <= 0) {
            return;
        }
        switch (event.getCause()) {
            case FALL -> rpg().addXp(player, Stat.ACROBATICS, Math.max(2, damage * 3));
            case VOID, SUICIDE, KILL, STARVATION -> {
            }
            default -> rpg().addXp(player, Stat.ENDURANCE, Math.min(20, damage * 1.5));
        }
    }

    /** Arquearia aumenta o dano de projéteis contra mobs. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectile(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter
                && !(event.getEntity() instanceof Player) && rpg().enabled()) {
            int level = rpg().data(shooter).level(Stat.ARCHERY);
            if (level > 1) {
                event.setDamage(event.getDamage() * (1 + (level - 1) * 0.005));
            }
        }
    }
}
