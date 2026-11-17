package dev.vertexx.waystone;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
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
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Criação, uso, proteção e remoção de Waystones. */
public final class WaystoneListener implements Listener {

    private final PluginMain plugin;

    public WaystoneListener(PluginMain plugin) {
        this.plugin = plugin;
    }

    private WaystoneManager waystones() {
        return plugin.waystones();
    }

    // ── Criação: Fragmento de Ametista num Lodestone ────────────

    private boolean isShard(ItemStack item) {
        return item != null && item.getType() == Material.AMETHYST_SHARD && dev.vertexx.end.EndItem.of(item) == null;
    }

    private void startCreation(PlayerInteractEvent event, Block block) {
        if (block.getType() != Material.LODESTONE || !isShard(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!WaystoneManager.hasRoomAbove(block)) {
            refuse(player, "<red>O Waystone tem quase 2 blocos de altura: deixe o bloco acima do Lodestone livre.");
            return;
        }
        if (waystones().limitReached(player)) {
            refuse(player, "<red>Você atingiu o limite de <white>"
                    + plugin.getConfig().getInt("waystones.max-per-player", 3) + "</white> Waystones.");
            return;
        }
        plugin.namePrompt().ask(player, "Qual será o nome do novo Waystone?", name -> finishCreation(player, block, name));
    }

    private void finishCreation(Player player, Block block, String name) {
        if (block.getType() != Material.LODESTONE || waystones().at(block) != null) {
            refuse(player, "<red>Este Lodestone mudou: o Waystone não foi criado.");
            return;
        }
        if (!WaystoneManager.hasRoomAbove(block)) {
            refuse(player, "<red>O bloco acima do Lodestone foi ocupado: o Waystone não foi criado.");
            return;
        }
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!creative && !isShard(held)) {
            refuse(player, "<red>Segure um <white>Fragmento de Ametista</white> para criar o Waystone.");
            return;
        }
        switch (waystones().create(player, block, name)) {
            case CREATED -> {
                if (!creative) {
                    held.setAmount(held.getAmount() - 1);
                }
                Location center = block.getLocation().add(0.5, 0.5, 0.5);
                Fx.ring(center.clone().add(0, 0.7, 0), Particle.END_ROD, 0.9, 20);
                Fx.burst(center.clone().add(0, 1, 0), Particle.ENCHANT, 50, 0.4, 1.0);
                Fx.worldSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
                Msg.send(player, "<green>Waystone <white><name></white> criado. <gray>Clique nele com o botão direito para viajar.",
                        Placeholder.unparsed("name", waystones().cleanName(name)));
            }
            case LIMIT_REACHED -> refuse(player, "<red>Você atingiu o limite de <white>"
                    + plugin.getConfig().getInt("waystones.max-per-player", 3) + "</white> Waystones.");
            case NAME_TAKEN -> refuse(player, "<red>Já existe um Waystone com esse nome. Clique no Lodestone de novo e escolha outro.");
            case INVALID_NAME -> refuse(player, "<red>Nome de Waystone inválido.");
        }
    }

    private void refuse(Player player, String message) {
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
            startCreation(event, event.getClickedBlock());
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
        Waystone route = waystones().byId(waystone.route());
        if (route != null && !player.isSneaking()) {
            Fx.click(player);
            waystones().travel(player, route); // rota fixa: viaja direto, sem menu
            return;
        }
        new WaystoneGUI(plugin, player, waystone).open();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Object holder = event.getInventory().getHolder(false);
        if (!(holder instanceof WaystoneGUI) && !(holder instanceof WaystoneConfigGUI)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (holder instanceof WaystoneGUI gui) {
            gui.handleClick(event.getSlot(), event.getClick());
        } else if (holder instanceof WaystoneConfigGUI gui) {
            gui.handleClick(event.getSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Object holder = event.getInventory().getHolder(false);
        if (holder instanceof WaystoneGUI || holder instanceof WaystoneConfigGUI) {
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
        if (event.getBlock().getType() == Material.BARRIER && waystone.pos().block() != null) {
            waystone.pos().block().setType(Material.AIR, false); // quebrou pela parte de cima (criativo)
        }
        Location center = waystone.pos().center();
        if (player.getGameMode() != GameMode.CREATIVE) {
            center.getWorld().dropItemNaturally(center, new ItemStack(Material.LODESTONE));
        }
        Fx.burst(center, Particle.REVERSE_PORTAL, 50, 0.3, 0.1);
        Fx.worldSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
        Msg.send(player, "<yellow>Waystone <white><name></white> removido.", Placeholder.unparsed("name", waystone.name()));
    }

    /** A barreira da parte alta do modelo é refeita quando o chunk carrega (ela pode ter sumido com um /fill, por ex.). */
    @EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        for (Waystone waystone : waystones().all()) {
            if (waystone.pos().world().equals(event.getWorld().getName())
                    && (waystone.pos().x() >> 4) == event.getChunk().getX()
                    && (waystone.pos().z() >> 4) == event.getChunk().getZ()) {
                Bukkit.getScheduler().runTask(plugin, () -> waystones().ensureUpper(waystone));
            }
        }
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
