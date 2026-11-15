package dev.vertexx.waystone;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Msg;
import dev.vertexx.kingdom.Kingdom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Configuração de um Waystone pelo dono: um painel central com a prévia e as opções, e subpáginas
 * para escolher a skin, o ícone flutuante e a rota fixa.
 */
public final class WaystoneConfigGUI implements InventoryHolder {

    private enum Mode {
        HUB, SKIN, ICON, ROUTE
    }

    private static final int SIZE = 54;
    private static final int PREVIEW_SLOT = 13;
    private static final int SKIN_SLOT = 20;
    private static final int ICON_SLOT = 22;
    private static final int NAME_SLOT = 24;
    private static final int ROUTE_SLOT = 31;
    private static final int TITLE_SLOT = 4;
    private static final int ACTION_SLOT = 40;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final int[] SKIN_SLOTS = {19, 21, 23, 25, 28, 30, 32, 34};
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};

    private final PluginMain plugin;
    private final Player player;
    private final Waystone waystone;
    private final Inventory inventory;
    private final Map<Integer, Material> iconBySlot = new HashMap<>();
    private final Map<Integer, WaystoneSkin> skinBySlot = new HashMap<>();
    private final Map<Integer, Waystone> routeBySlot = new HashMap<>();
    private Mode mode = Mode.HUB;
    private int page;

    public WaystoneConfigGUI(PluginMain plugin, Player player, Waystone waystone) {
        this.plugin = plugin;
        this.player = player;
        this.waystone = waystone;
        this.inventory = Bukkit.createInventory(this, SIZE,
                Msg.mm("<gradient:#c4a7ff:#2cb67d><bold>Configurar</bold></gradient> <dark_gray>» <dark_gray><name>",
                        Placeholder.unparsed("name", waystone.name())));
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
        switch (mode) {
            case HUB -> handleHub(slot);
            case SKIN -> handleSkin(slot);
            case ICON -> handleIcon(slot);
            case ROUTE -> handleRoute(slot);
        }
    }

    private void go(Mode target) {
        Fx.click(player);
        mode = target;
        page = 0;
        render();
    }

    private void handleHub(int slot) {
        switch (slot) {
            case SKIN_SLOT -> go(Mode.SKIN);
            case ICON_SLOT -> go(Mode.ICON);
            case ROUTE_SLOT -> go(Mode.ROUTE);
            case NAME_SLOT -> {
                waystone.showName(!waystone.showName());
                plugin.waystones().changed(waystone);
                Fx.click(player);
                render();
            }
            case BACK_SLOT -> {
                Fx.click(player);
                new WaystoneGUI(plugin, player, waystone).open();
            }
            default -> {
            }
        }
    }

    private void handleSkin(int slot) {
        if (slot == BACK_SLOT) {
            go(Mode.HUB);
            return;
        }
        WaystoneSkin skin = skinBySlot.get(slot);
        if (skin != null && skin != waystone.skin()) {
            waystone.skin(skin);
            plugin.waystones().changed(waystone);
            Fx.select(player);
            render();
        }
    }

    private void handleIcon(int slot) {
        if (slot == BACK_SLOT) {
            go(Mode.HUB);
            return;
        }
        Material icon = iconBySlot.get(slot);
        if (icon != null && icon != waystone.icon()) {
            waystone.icon(icon);
            plugin.waystones().changed(waystone);
            Fx.select(player);
            render();
        }
    }

    private void handleRoute(int slot) {
        if (slot == BACK_SLOT) {
            go(Mode.HUB);
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
        if (slot == ACTION_SLOT) {
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
            mode = Mode.HUB;
            render();
        }
    }

    private List<Waystone> candidates() {
        return plugin.waystones().destinations(player, waystone);
    }

    // ── Renderização ───────────────────────────────────────────

    private void frame() {
        Kingdom owner = plugin.kingdoms().kingdomOf(waystone.owner());
        ItemStack theme = Items.filler(owner == null ? Material.PURPLE_STAINED_GLASS_PANE : owner.pane());
        ItemStack dark = Items.filler(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack soft = Items.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            ItemStack item = null;
            if (row == 0 || row == 5) {
                item = column == 0 || column == 8 ? theme : column % 2 == 1 ? soft : dark;
            } else if (column == 0 || column == 8) {
                item = row == 2 || row == 3 ? theme : dark;
            }
            inventory.setItem(slot, item);
        }
        inventory.setItem(CLOSE_SLOT, Items.of(Material.BARRIER, "<red>Fechar", List.of()));
        iconBySlot.clear();
        skinBySlot.clear();
        routeBySlot.clear();
    }

    private void render() {
        frame();
        switch (mode) {
            case HUB -> renderHub();
            case SKIN -> renderSkin();
            case ICON -> renderIcon();
            case ROUTE -> renderRoute();
        }
    }

    private void title(Material icon, String name, List<String> lore) {
        inventory.setItem(TITLE_SLOT, Items.of(icon, "<gradient:#c4a7ff:#2cb67d><bold>" + name, lore, true));
    }

    private void renderHub() {
        title(Material.COMPARATOR, "Configurar Waystone", List.of("<gray>" + Msg.esc(waystone.name()),
                "<dark_gray>Tudo o que muda aqui aparece no modelo."));
        inventory.setItem(PREVIEW_SLOT, Items.of(waystone.skin().pillar(),
                "<gradient:#c4a7ff:#2cb67d><bold>" + Msg.esc(waystone.name()),
                List.of("<gray>Skin: <white>" + waystone.skin().label(),
                        "<gray>Ícone: <white><lang:" + waystone.icon().translationKey() + ">",
                        "<gray>Nome flutuante: " + (waystone.showName() ? "<green>visível" : "<red>oculto"),
                        "<gray>Viagens até aqui: <white>" + waystone.uses()), true));
        inventory.setItem(SKIN_SLOT, Items.of(waystone.skin().pillar(), "<gradient:#c4a7ff:#2cb67d>Skin do modelo",
                List.of("<gray>Atual: <white>" + waystone.skin().label(),
                        "<dark_gray>Oito conjuntos de blocos.", "", "<yellow>▶ Clique para escolher")));
        inventory.setItem(ICON_SLOT, Items.of(waystone.icon(), "<gradient:#c4a7ff:#2cb67d>Ícone flutuante",
                List.of("<gray>Atual: <white><lang:" + waystone.icon().translationKey() + ">",
                        "<dark_gray>Gira sobre o topo do modelo.", "", "<yellow>▶ Clique para escolher")));
        inventory.setItem(NAME_SLOT, Items.of(waystone.showName() ? Material.NAME_TAG : Material.BARRIER,
                "<gradient:#c4a7ff:#2cb67d>Nome sobre o Waystone",
                List.of("<gray>Estado: " + (waystone.showName() ? "<green>visível" : "<red>oculto"),
                        "", "<yellow>▶ Clique para alternar")));
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

    private void renderSkin() {
        title(waystone.skin().pillar(), "Skin do modelo", List.of("<gray>Os blocos do corpo do Waystone."));
        WaystoneSkin[] skins = WaystoneSkin.values();
        for (int i = 0; i < skins.length && i < SKIN_SLOTS.length; i++) {
            WaystoneSkin skin = skins[i];
            boolean selected = skin == waystone.skin();
            inventory.setItem(SKIN_SLOTS[i], Items.of(skin.pillar(),
                    (selected ? "<green>✔ " : "<white>") + skin.label(),
                    List.of("<gray>Pilar: <white><lang:" + skin.pillar().translationKey() + ">",
                            "<gray>Beiral: <white><lang:" + skin.ledge().translationKey() + ">",
                            "",
                            selected ? "<gray>Skin atual." : "<yellow>▶ Clique para aplicar"), selected));
            skinBySlot.put(SKIN_SLOTS[i], skin);
        }
        inventory.setItem(BACK_SLOT, Items.of(Material.ARROW, "<yellow>◀ Voltar", List.of()));
    }

    private void renderIcon() {
        title(waystone.icon(), "Ícone flutuante", List.of("<gray>O item que gira sobre o topo."));
        List<Material> icons = WaystoneIcons.ALL;
        for (int i = 0; i < icons.size() && i < CONTENT_SLOTS.length; i++) {
            Material icon = icons.get(i);
            boolean selected = icon == waystone.icon();
            inventory.setItem(CONTENT_SLOTS[i], Items.of(icon,
                    (selected ? "<green>✔ " : "<white>") + "<lang:" + icon.translationKey() + ">",
                    List.of(selected ? "<gray>Ícone atual." : "<yellow>▶ Clique para usar este ícone"), selected));
            iconBySlot.put(CONTENT_SLOTS[i], icon);
        }
        inventory.setItem(BACK_SLOT, Items.of(Material.ARROW, "<yellow>◀ Voltar", List.of()));
    }

    private void renderRoute() {
        title(Material.RECOVERY_COMPASS, "Escolha o destino fixo",
                List.of("<gray>Só aparecem Waystones já descobertos por você."));
        List<Waystone> all = candidates();
        int from = page * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length && from + i < all.size(); i++) {
            Waystone target = all.get(from + i);
            boolean selected = target.id().equals(waystone.route());
            inventory.setItem(CONTENT_SLOTS[i], Items.of(target.skin().pillar(),
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
        inventory.setItem(ACTION_SLOT, Items.of(Material.BARRIER, "<red>Remover rota fixa",
                List.of("<gray>O clique volta a abrir o menu.")));
        inventory.setItem(BACK_SLOT, Items.of(Material.ARROW, "<yellow>◀ Voltar", List.of()));
        if (all.size() > CONTENT_SLOTS.length) {
            inventory.setItem(NEXT_SLOT, Items.of(Material.ARROW, "<yellow>Próxima página ▶",
                    List.of("<dark_gray>Página " + (page + 1) + " de "
                            + ((all.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length))));
        }
    }
}
