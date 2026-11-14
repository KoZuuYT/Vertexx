package dev.vertexx.waystone;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Criação, uso, proteção e remoção de Waystones. */
public final class WaystoneListener implements Listener {

    private final PluginMain plugin;

    public WaystoneListener(PluginMain plugin) {
        this.plugin = plugin;
    }

    private WaystoneManager waystones() {
        return plugin.waystones();
    }

    // ── Criação: colocar um Lodestone renomeado ─────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.LODESTONE) {
            return;
        }
        ItemMeta meta = event.getItemInHand().getItemMeta();
        if (meta == null || !meta.hasCustomName()) {
            return; // Lodestone comum continua 100% vanilla
        }
        Player player = event.getPlayer();
        String name = Msg.plain(meta.customName());
        switch (waystones().create(player, event.getBlockPlaced(), name)) {
            case CREATED -> {
                Location center = event.getBlockPlaced().getLocation().add(0.5, 0.5, 0.5);
                Fx.ring(center.clone().add(0, 0.7, 0), Particle.END_ROD, 0.9, 20);
                Fx.burst(center.clone().add(0, 1, 0), Particle.ENCHANT, 50, 0.4, 1.0);
                Fx.worldSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
                Msg.send(player, "<green>Waystone <white><name></white> criado. <gray>Clique nele com o botão direito para viajar.",
                        Placeholder.unparsed("name", name.strip()));
            }
            case LIMIT_REACHED -> refuse(event, player, "<red>Você atingiu o limite de <white>"
                    + plugin.getConfig().getInt("waystones.max-per-player", 3) + "</white> Waystones.");
            case NAME_TAKEN -> refuse(event, player, "<red>Já existe um Waystone com esse nome. Renomeie o Lodestone na bigorna.");
            case INVALID_NAME -> refuse(event, player, "<red>Nome de Waystone inválido.");
        }
    }

    private void refuse(BlockPlaceEvent event, Player player, String message) {
        event.setCancelled(true);
        Msg.send(player, message);
        Fx.deny(player);
    }

    // ── Uso: clique direito abre o menu de viagem ───────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Waystone waystone = waystones().at(event.getClickedBlock());
        if (waystone == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        if (held != null && (held.getType() == Material.COMPASS || (player.isSneaking() && held.getType().isBlock()))) {
            return; // preserva a bússola de magnetita e a construção ao redor
        }
        event.setCancelled(true);
        if (waystones().isTraveling(player)) {
            return;
        }
        if (waystones().discover(player, waystone)) {
            Location center = waystone.pos().center();
            Fx.burst(center.clone().add(0, 0.8, 0), Particle.ENCHANT, 40, 0.4, 1.0);
            Fx.sound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9f, 1.3f);
            Msg.send(player, "<light_purple>Waystone descoberto: <white><name></white>.",
                    Placeholder.unparsed("name", waystone.name()));
        }
        new WaystoneGUI(plugin, player, waystone).open();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof WaystoneGUI gui)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            gui.handleClick(event.getSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof WaystoneGUI) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof WaystoneGUI gui) {
            gui.close();
        }
    }

    // ── Remoção e proteção ──────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Waystone waystone = waystones().at(event.getBlock());
        if (waystone == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!waystone.owner().equals(player.getUniqueId()) && !player.hasPermission("vertexx.admin")) {
            event.setCancelled(true);
            Msg.send(player, "<red>Apenas <white>" + waystone.ownerName() + "</white> pode remover este Waystone.");
            Fx.deny(player);
            return;
        }
        waystones().remove(waystone);
        event.setDropItems(false);
        Location center = waystone.pos().center();
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack drop = new ItemStack(Material.LODESTONE);
            drop.editMeta(meta -> meta.customName(net.kyori.adventure.text.Component.text(waystone.name())));
            center.getWorld().dropItemNaturally(center, drop);
        }
        Fx.burst(center, Particle.REVERSE_PORTAL, 50, 0.3, 0.1);
        Fx.worldSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
        Msg.send(player, "<yellow>Waystone <white><name></white> removido.", Placeholder.unparsed("name", waystone.name()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> waystones().at(block) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> waystones().at(block) != null);
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

    private boolean touches(List<Block> blocks) {
        for (Block block : blocks) {
            if (waystones().at(block) != null) {
                return true;
            }
        }
        return false;
    }
}
