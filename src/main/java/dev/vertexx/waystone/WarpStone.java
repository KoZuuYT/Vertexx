package dev.vertexx.waystone;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Ui;
import dev.vertexx.end.EndItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.persistence.PersistentDataType;

/**
 * Pedra de Warp: abre a lista de todos os Waystones que o jogador já conheceu e leva até um deles sem gastar
 * pérolas. Em troca, tem poucas cargas ({@code waystones.warp-stone.charges}); ao zerar, a pedra se desfaz.
 */
public final class WarpStone implements Listener {

    private final PluginMain plugin;
    private final NamespacedKey chargesKey;

    public WarpStone(PluginMain plugin) {
        this.plugin = plugin;
        this.chargesKey = new NamespacedKey(plugin, "warp_charges");
    }

    // ── Receita e item ──────────────────────────────────────────

    public void start() {
        NamespacedKey key = new NamespacedKey(plugin, "warp_stone");
        Bukkit.removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, create(maxCharges()));
        recipe.shape("AAA", "AEA", "AAA");
        recipe.setIngredient('A', new RecipeChoice.ExactChoice(new ItemStack(Material.AMETHYST_SHARD)));
        recipe.setIngredient('E', Material.ENDER_EYE);
        Bukkit.addRecipe(recipe);
    }

    public int maxCharges() {
        return Math.max(1, plugin.getConfig().getInt("waystones.warp-stone.charges", 5));
    }

    public ItemStack create(int charges) {
        ItemStack item = EndItem.WARP_STONE.create(plugin, List.of(
                Msg.item("<gray>Cargas: <light_purple>" + charges + "<dark_gray>/<white>" + maxCharges()),
                Msg.item("<yellow>Clique direito para escolher o destino.")));
        item.editMeta(meta -> meta.getPersistentDataContainer().set(chargesKey, PersistentDataType.INTEGER, charges));
        return item;
    }

    public boolean isStone(ItemStack item) {
        return EndItem.of(item) == EndItem.WARP_STONE;
    }

    public int charges(ItemStack item) {
        if (!isStone(item)) {
            return 0;
        }
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(chargesKey, PersistentDataType.INTEGER, 0);
    }

    private ItemStack firstStone(Player player) {
        for (ItemStack stack : new ItemStack[]{player.getInventory().getItemInMainHand(), player.getInventory().getItemInOffHand()}) {
            if (charges(stack) > 0) {
                return stack;
            }
        }
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (charges(stack) > 0) {
                return stack;
            }
        }
        return null;
    }

    boolean hasCharge(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || firstStone(player) != null;
    }

    /** Gasta uma carga depois da viagem; na última, a pedra se estilhaça. */
    void spend(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack stone = firstStone(player);
        if (stone == null) {
            return;
        }
        int left = charges(stone) - 1;
        if (left <= 0) {
            stone.setAmount(stone.getAmount() - 1);
            Msg.send(player, "<light_purple>A Pedra de Warp se esvaiu: acabaram as cargas.");
            Fx.sound(player, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 0.8f);
        } else {
            stone.setItemMeta(create(left).getItemMeta());
            Msg.actionBar(player, "<light_purple>Pedra de Warp: <white>" + left + "<gray> cargas restantes");
        }
    }

    // ── Uso ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !isStone(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (plugin.waystones().isTraveling(player)) {
            return;
        }
        if (charges(event.getItem()) <= 0 && player.getGameMode() != GameMode.CREATIVE) {
            Msg.actionBar(player, "<red>Esta Pedra de Warp está sem cargas.");
            return;
        }
        new Menu(player).open();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder(false) instanceof Menu menu) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                menu.handleClick(event.getSlot());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }

    // ── Menu ────────────────────────────────────────────────────

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private final class Menu implements InventoryHolder {

        private final Player player;
        private final Inventory inventory;
        private final boolean pack;
        private final List<Waystone> list;
        private final Map<Integer, Waystone> bySlot = new HashMap<>();
        private int page;

        Menu(Player player) {
            this.player = player;
            this.pack = plugin.resourcePack().isLoaded(player);
            this.list = new ArrayList<>(plugin.waystones().destinations(player, null));
            this.inventory = Bukkit.createInventory(this, 54, Ui.title(pack, Ui.Theme.VERTEXX,
                    "<gradient:#c4a7ff:#2cb67d><bold>Pedra de Warp</bold></gradient>"));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void open() {
            render();
            player.openInventory(inventory);
            Fx.sound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        }

        private int pages() {
            return Math.max(1, (list.size() + SLOTS.length - 1) / SLOTS.length);
        }

        void handleClick(int slot) {
            Waystone destination = bySlot.get(slot);
            if (destination != null) {
                Fx.click(player);
                plugin.waystones().travel(player, destination, TravelCost.FREE, p -> hasCharge(p), p -> spend(p));
                return;
            }
            if (slot == 45 && page > 0) {
                page--;
                render();
            } else if (slot == 53 && page < pages() - 1) {
                page++;
                render();
            } else if (slot == 49) {
                player.closeInventory();
            }
        }

        private void render() {
            bySlot.clear();
            ItemStack theme = Items.filler(Material.PURPLE_STAINED_GLASS_PANE);
            ItemStack dark = Items.filler(Material.BLACK_STAINED_GLASS_PANE);
            for (int slot = 0; slot < 54; slot++) {
                int row = slot / 9;
                int column = slot % 9;
                ItemStack item = null;
                if (row == 0 || row == 5) {
                    item = column == 0 || column == 8 ? theme : dark;
                } else if (column == 0 || column == 8) {
                    item = dark;
                }
                inventory.setItem(slot, item);
            }
            ItemStack held = firstStone(player);
            String charges = player.getGameMode() == GameMode.CREATIVE ? "infinitas"
                    : held == null ? "0" : String.valueOf(charges(held));
            inventory.setItem(4, Items.of(Material.AMETHYST_CLUSTER, "<gradient:#c4a7ff:#2cb67d><bold>Pedra de Warp",
                    List.of("<gray>Cargas: <light_purple>" + charges, "<gray>Conhecidos: <white>" + list.size(),
                            "<dark_gray>Sem custo de pérolas."), true));
            inventory.setItem(49, Items.of(Material.BARRIER, "<red>Fechar", List.of()));
            if (page > 0) {
                inventory.setItem(45, Items.of(Material.ARROW, "<yellow>◀ Página anterior", List.of()));
            }
            if (page < pages() - 1) {
                inventory.setItem(53, Items.of(Material.ARROW, "<yellow>Próxima página ▶", List.of()));
            }
            Ui.skin(pack, inventory);
            if (list.isEmpty()) {
                inventory.setItem(22, Items.of(Material.COMPASS, "<gray>Nenhum Waystone conhecido",
                        List.of("<dark_gray>Clique em Waystones para registrá-los.")));
                return;
            }
            int from = page * SLOTS.length;
            for (int i = 0; i < SLOTS.length && from + i < list.size(); i++) {
                Waystone waystone = list.get(from + i);
                World world = waystone.pos().bukkitWorld();
                String dimension = world == null ? "Mundo desconhecido" : switch (world.getEnvironment()) {
                    case NETHER -> "Nether";
                    case THE_END -> "The End";
                    default -> "Superfície";
                };
                inventory.setItem(SLOTS[i], Items.of(waystone.skin().pillar(),
                        "<gradient:#c4a7ff:#2cb67d>" + Msg.esc(waystone.name()),
                        List.of("<gray>Dono: <white>" + waystone.ownerName(),
                                "<gray>Local: <white>" + dimension + " <dark_gray>" + waystone.pos().coords(),
                                "", "<yellow>▶ Clique para viajar <dark_gray>(1 carga)")));
                bySlot.put(SLOTS[i], waystone);
            }
        }
    }
}
