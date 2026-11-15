package dev.vertexx.panel;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Msg;
import dev.vertexx.core.YamlStore;
import dev.vertexx.kingdom.Kingdom;
import dev.vertexx.kingdom.KingdomPanelGUI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Painel geral do plugin: aberto por administradores com {@code /vertexx} e por qualquer jogador
 * com o Guia (livro entregue no primeiro login). O conteúdo administrativo só aparece a quem tem OP.
 */
public final class PanelManager implements Listener {

    private final PluginMain plugin;
    private final YamlStore store;
    private final NamespacedKey guideKey;
    private final Set<UUID> received = new HashSet<>();

    public PanelManager(PluginMain plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "panel.yml");
        this.guideKey = new NamespacedKey(plugin, "guide");
    }

    // ── Persistência ────────────────────────────────────────────

    public YamlConfiguration read() {
        return store.load();
    }

    public void apply(YamlConfiguration data) {
        received.clear();
        for (String id : data.getStringList("book-given")) {
            try {
                received.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
                // entrada inválida: ignora
            }
        }
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("book-given", received.stream().map(UUID::toString).toList());
        return data;
    }

    public void save() {
        store.saveAsync(snapshot());
    }

    public void saveNow() {
        store.saveNow(snapshot());
    }

    // ── Livro ───────────────────────────────────────────────────

    public ItemStack createGuide() {
        ItemStack book = Items.of(Material.BOOK, "<gradient:#c4a7ff:#2cb67d><bold>Guia do Vertexx",
                List.of("<gray>Reinos, Waystones, túmulos e honra",
                        "<gray>reunidos em um só lugar.",
                        "",
                        "<yellow>▶ Clique com o botão direito para abrir"), true);
        book.editMeta(meta -> meta.getPersistentDataContainer().set(guideKey, PersistentDataType.BYTE, (byte) 1));
        return book;
    }

    public boolean isGuide(ItemStack item) {
        return item != null && item.getType() == Material.BOOK && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(guideKey, PersistentDataType.BYTE);
    }

    public void giveGuide(Player player) {
        player.getInventory().addItem(createGuide()).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        Fx.success(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> showNotice(player), 60L);
        if (received.contains(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !received.add(player.getUniqueId())) {
                return;
            }
            giveGuide(player);
            save();
            Msg.send(player, "<gray>Você recebeu o <white>Guia do Vertexx</white>. "
                    + "Clique com o botão direito nele para abrir o painel.");
        }, 30L);
    }

    /** Mostra o mural do reino ao entrar, se o líder tiver fixado um aviso. */
    private void showNotice(Player player) {
        if (!player.isOnline()) {
            return;
        }
        Kingdom kingdom = plugin.kingdoms().kingdomOf(player.getUniqueId());
        String notice = kingdom == null ? null : plugin.kingdoms().notice(kingdom);
        if (notice != null) {
            Msg.send(player, kingdom.styled() + " <gray>— mural: <white>" + Msg.esc(notice));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!isGuide(event.getItem())) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (plugin.onboarding().isPending(event.getPlayer())) {
            return;
        }
        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
            open(event.getPlayer());
        }
    }

    public void open(Player player) {
        new PanelGUI(plugin, player).open();
    }

    // ── Menus ───────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Object holder = event.getInventory().getHolder(false);
        if (!(holder instanceof PanelGUI) && !(holder instanceof KingdomPanelGUI)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (holder instanceof PanelGUI gui) {
            gui.handleClick(event.getSlot(), event.getClick());
        } else if (holder instanceof KingdomPanelGUI gui) {
            gui.handleClick(event.getSlot(), event.getClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Object holder = event.getInventory().getHolder(false);
        if (holder instanceof PanelGUI || holder instanceof KingdomPanelGUI) {
            event.setCancelled(true);
        }
    }
}
