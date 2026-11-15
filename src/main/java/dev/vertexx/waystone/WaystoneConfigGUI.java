package dev.vertexx.waystone;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Configuração de um Waystone pelo dono: ícone flutuante, nome sobre o modelo e rota fixa. */
public final class WaystoneConfigGUI implements InventoryHolder {

    private enum Mode {
        MAIN, ROUTE
    }

    private static final int SIZE = 54;
    private static final int NAME_SLOT = 4;
    private static final int ROUTE_SLOT = 40;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};

    private final PluginMain plugin;
    private final Player player;
    private final Waystone waystone;
    private final Inventory inventory;
    private final Map<Integer, Material> iconBySlot = new HashMap<>();
    private final Map<Integer, Waystone> routeBySlot = new HashMap<>();
    private Mode mode = Mode.MAIN;
    private int page;

    public WaystoneConfigGUI(PluginMain plugin, Player player, Waystone waystone) {
        this.plugin = plugin;
        this.player = player;
        this.waystone = waystone;
        this.inventory = Bukkit.createInventory(this, SIZE,
                Msg.mm("<gradient:#c4a7ff:#2cb67d><bold>Configurar</bold></gradient> <dark_gray>» <dark_gray><name>",
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("name", waystone.name())));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        render();
        player.openInventory(inventory);
        Fx.open(player);
    }

    public void handleClick(int slot) {
        if (!plugin.waystones().canConfigure(player, waystone) || !plugin.waystones().exists(waystone)) {
            player.closeInventory();
            return;
        }
        if (slot == CLOSE_SLOT) {
            Fx.click(player);
            player.closeInventory();
            return;
        }
        if (mode == Mode.MAIN) {
            handleMain(slot);
        } else {
            handleRoute(slot);
        }
    }

    private void handleMain(int slot) {
        Material icon = iconBySlot.get(slot);
        if (icon != null) {
            waystone.icon(icon);
            plugin.waystones().changed(waystone);
            Fx.select(player);
            render();
        } else if (slot == NAME_SLOT) {
            waystone.showName(!waystone.showName());
            plugin.waystones().changed(waystone);
            Fx.click(player);
            render();
        } else if (slot == ROUTE_SLOT) {
            Fx.click(player);
            mode = Mode.ROUTE;
            page = 0;
            render();
        } else if (slot == BACK_SLOT) {
            Fx.click(player);
            new WaystoneGUI(plugin, player, waystone).open();
        }
    }

    private void handleRoute(int slot) {
        if (slot == BACK_SLOT) {
            Fx.click(player);
            mode = Mode.MAIN;
            render();
            return;
        }
        if (slot == NEXT_SLOT) {
            Fx.open(player);
            page++;
            if (page * CONTENT_SLOTS.length >= candidates().size()) {
                page = 0;
            }
            render();
            return;
        }
        if (slot == ROUTE_SLOT) {
            waystone.route(null);
            plugin.waystones().changed(waystone);
            Fx.select(player);
            Msg.send(player, "<yellow>Rota fixa removida: o Waystone volta a abrir o menu.");
            render();
            return;
        }
        Waystone target = routeBySlot.get(slot);
        if (target != null) {
            waystone.route(target.id());
            plugin.waystones().changed(waystone);
            Fx.select(player);
            Msg.send(player, "<green>Rota fixa definida para <white>" + Msg.esc(target.name())
                    + "</white>. <gray>Quem clicar neste Waystone viaja direto; Shift + clique abre o menu.");
            mode = Mode.MAIN;
            render();
        }
    }

    private List<Waystone> candidates() {
        return plugin.waystones().destinations(player, waystone);
    }

    private void frame() {
        ItemStack border = Items.filler(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack dark = Items.filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            inventory.setItem(slot, row == 0 || row == 5 ? border : column == 0 || column == 8 ? dark : null);
        }
        inventory.setItem(CLOSE_SLOT, Items.of(Material.BARRIER, "<red>Fechar", List.of()));
        iconBySlot.clear();
        routeBySlot.clear();
    }

    private void render() {
        frame();
        if (mode == Mode.MAIN) {
            renderMain();
        } else {
            renderRoute();
        }
    }

    private void renderMain() {
        inventory.setItem(NAME_SLOT, Items.of(waystone.showName() ? Material.NAME_TAG : Material.BARRIER,
                "<gradient:#c4a7ff:#2cb67d>Nome sobre o Waystone",
                List.of("<gray>Estado: " + (waystone.showName() ? "<green>visível" : "<red>oculto"),
                        "",
                        "<yellow>▶ Clique para alternar")));
        List<Material> icons = WaystoneIcons.ALL;
        for (int i = 0; i < icons.size() && i < CONTENT_SLOTS.length; i++) {
            Material icon = icons.get(i);
            boolean selected = icon == waystone.icon();
            inventory.setItem(CONTENT_SLOTS[i], Items.of(icon,
                    (selected ? "<green>✔ " : "<white>") + "<lang:" + icon.translationKey() + ">",
                    List.of(selected ? "<gray>Ícone atual." : "<yellow>▶ Clique para usar este ícone"), selected));
            iconBySlot.put(CONTENT_SLOTS[i], icon);
        }
        Waystone route = plugin.waystones().byId(waystone.route());
        inventory.setItem(ROUTE_SLOT, Items.of(Material.RECOVERY_COMPASS, "<gradient:#c4a7ff:#2cb67d>Rota fixa",
                List.of(route == null ? "<gray>Nenhuma: o clique abre o menu de destinos."
                                : "<gray>Destino: <white>" + Msg.esc(route.name()),
                        "<dark_gray>Com rota, o clique direito viaja direto.",
                        "<dark_gray>Shift + clique direito ainda abre o menu.",
                        "",
                        "<yellow>▶ Clique para escolher")));
        inventory.setItem(BACK_SLOT, Items.of(Material.ARROW, "<yellow>◀ Voltar ao menu", List.of()));
    }

    private void renderRoute() {
        inventory.setItem(NAME_SLOT, Items.of(Material.RECOVERY_COMPASS, "<gradient:#c4a7ff:#2cb67d>Escolha o destino fixo",
                List.of("<gray>Só aparecem Waystones já descobertos por você.")));
        List<Waystone> all = candidates();
        int from = page * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length && from + i < all.size(); i++) {
            Waystone target = all.get(from + i);
            boolean selected = target.id().equals(waystone.route());
            inventory.setItem(CONTENT_SLOTS[i], Items.of(target.icon(),
                    (selected ? "<green>✔ " : "<white>") + Msg.esc(target.name()),
                    List.of("<gray>Dono: <white>" + target.ownerName(),
                            "<gray>Local: <white>" + target.pos().coords(),
                            "",
                            selected ? "<gray>Destino atual." : "<yellow>▶ Clique para definir"), selected));
            routeBySlot.put(CONTENT_SLOTS[i], target);
        }
        if (all.isEmpty()) {
            inventory.setItem(22, Items.of(Material.COMPASS, "<gray>Nenhum outro Waystone descoberto", List.of()));
        }
        inventory.setItem(ROUTE_SLOT, Items.of(Material.BARRIER, "<red>Remover rota fixa",
                List.of("<gray>O clique volta a abrir o menu.")));
        inventory.setItem(BACK_SLOT, Items.of(Material.ARROW, "<yellow>◀ Voltar", List.of()));
        if (all.size() > CONTENT_SLOTS.length) {
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>Página " + (page + 1) + " de " + ((all.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length));
            inventory.setItem(NEXT_SLOT, Items.of(Material.ARROW, "<yellow>Próxima página ▶", lore));
        }
    }
}
