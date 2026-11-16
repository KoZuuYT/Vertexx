package dev.vertexx.core;

import dev.vertexx.PluginMain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Base dos menus de 6 linhas: moldura padrão, botões com ação, e o visual com/sem resource pack
 * (ver {@link Ui}). Os cliques são roteados por {@link MenuListener}.
 */
public abstract class Menu implements InventoryHolder {

    protected static final int SIZE = 54;
    protected static final int HEADER_SLOT = 4;
    protected static final int BACK_SLOT = 45;
    protected static final int CLOSE_SLOT = 49;
    protected static final int NEXT_SLOT = 53;
    protected static final int[] LIST_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    protected final PluginMain plugin;
    protected final Player player;
    protected final Inventory inventory;
    protected final boolean pack;
    private final Ui.Theme theme;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();

    protected Menu(PluginMain plugin, Player player, Ui.Theme theme, String title) {
        this.plugin = plugin;
        this.player = player;
        this.theme = theme;
        this.pack = plugin.resourcePack().isLoaded(player);
        Component component = Ui.title(pack, theme, title);
        this.inventory = Bukkit.createInventory(this, SIZE, component);
    }

    /** Menu com título pronto (painel largo desenhado por {@link Canvas}). */
    protected Menu(PluginMain plugin, Player player, Ui.Theme theme, Component title) {
        this.plugin = plugin;
        this.player = player;
        this.theme = theme;
        this.pack = plugin.resourcePack().isLoaded(player);
        this.inventory = Bukkit.createInventory(this, SIZE, title);
    }

    /** O jogador tem o resource pack? (para montar o título antes do construtor). */
    public static boolean packed(PluginMain plugin, Player player) {
        return plugin.resourcePack().isLoaded(player);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Monta o conteúdo da página atual (chame {@link #frame()} primeiro). */
    protected abstract void render();

    public final void open() {
        refresh();
        player.openInventory(inventory);
        Fx.open(player);
    }

    /** Redesenha o menu inteiro. */
    protected final void refresh() {
        actions.clear();
        render();
        Ui.skin(pack, inventory);
    }

    public final void handleClick(int slot, ClickType click) {
        Consumer<ClickType> action = actions.get(slot);
        if (action != null) {
            Fx.click(player);
            action.accept(click);
        }
    }

    protected final void set(int slot, ItemStack item, Consumer<ClickType> action) {
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    protected final void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    /** Moldura: anel decorativo com o tema, botão de fechar e (opcional) de voltar. */
    protected final void frame(Runnable back) {
        ItemStack accent = Items.filler(theme.pane());
        ItemStack dark = Items.filler(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack soft = Items.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            ItemStack item = null;
            if (row == 0 || row == 5) {
                item = column == 0 || column == 8 ? accent : column % 2 == 1 ? soft : dark;
            } else if (column == 0 || column == 8) {
                item = row == 2 || row == 3 ? accent : dark;
            }
            inventory.setItem(slot, item);
        }
        set(CLOSE_SLOT, Items.of(Material.BARRIER, "<red>Fechar", List.of()), c -> player.closeInventory());
        if (back != null) {
            set(BACK_SLOT, Items.of(Material.ARROW, "<yellow>◀ Voltar", List.of()), c -> back.run());
        }
    }

    protected final ItemStack icon(Material material, String model, String name, List<String> lore, boolean glint) {
        return Ui.model(pack, Items.of(material, name, lore, glint), model);
    }
}
