package dev.vertexx.grave;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Criação de túmulos na morte, coleta por clique/quebra e proteção do bloco. */
public final class GraveListener implements Listener {

    private final PluginMain plugin;

    public GraveListener(PluginMain plugin) {
        this.plugin = plugin;
    }

    private GraveManager graves() {
        return plugin.graves();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("graves.enabled", true)) {
            return;
        }
        Player player = event.getEntity();
        List<ItemStack> drops = new ArrayList<>();
        if (!event.getKeepInventory()) {
            for (ItemStack drop : event.getDrops()) {
                if (drop != null && !drop.isEmpty()) {
                    drops.add(drop.clone());
                }
            }
        }
        int xp = 0;
        if (!event.getKeepLevel()) {
            int percent = Math.max(0, Math.min(100, plugin.getConfig().getInt("graves.xp-percent", 100)));
            xp = (int) (player.calculateTotalExperiencePoints() * (percent / 100.0));
        }
        if (drops.isEmpty() && xp <= 0) {
            return;
        }
        Grave grave = graves().create(player, drops, xp);
        if (grave == null) {
            return; // sem espaço para o túmulo: a morte segue o comportamento vanilla
        }
        event.getDrops().clear();
        if (!event.getKeepLevel()) {
            event.setDroppedExp(0);
        }
        long minutes = plugin.getConfig().getLong("graves.protection-minutes", 30);
        Msg.send(player, "<gray>Seu túmulo está em <white>" + grave.pos().coords() + "</white> <dark_gray>("
                + grave.pos().world() + ")</dark_gray><gray>. Protegido por <white>" + minutes + " min</white>.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null || !graves().hasGraves()) {
            return;
        }
        Grave grave = graves().at(event.getClickedBlock());
        if (grave == null) {
            return;
        }
        event.setCancelled(true);
        if (adminOpen(event.getPlayer(), grave)) {
            return;
        }
        graves().tryCollect(event.getPlayer(), grave);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Grave grave = graves().at(event.getBlock());
        if (grave == null) {
            return;
        }
        // quebrar equivale a tentar abrir: o bloco nunca dropa como cabeça
        event.setCancelled(true);
        if (adminOpen(event.getPlayer(), grave)) {
            return;
        }
        graves().tryCollect(event.getPlayer(), grave);
    }

    /** Administrador agachado abre o menu de gerenciamento do túmulo em vez de coletá-lo. */
    private boolean adminOpen(Player player, Grave grave) {
        if (!player.isSneaking() || !player.hasPermission("vertexx.admin")) {
            return false;
        }
        new GraveMenu.GraveDetailMenu(plugin, player, grave.id(), 0).open();
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (graves().hasGraves()) {
            event.blockList().removeIf(block -> graves().at(block) != null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (graves().hasGraves()) {
            event.blockList().removeIf(block -> graves().at(block) != null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (touches(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (touches(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFluid(BlockFromToEvent event) {
        if (graves().hasGraves() && graves().at(event.getToBlock()) != null) {
            event.setCancelled(true);
        }
    }

    private boolean touches(List<Block> blocks) {
        if (!graves().hasGraves()) {
            return false;
        }
        for (Block block : blocks) {
            if (graves().at(block) != null) {
                return true;
            }
        }
        return false;
    }
}
