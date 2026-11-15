package dev.vertexx.panel;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Ui;
import dev.vertexx.grave.Grave;
import dev.vertexx.grave.GraveManager;
import dev.vertexx.waystone.Waystone;
import dev.vertexx.waystone.WaystoneConfigGUI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Painel do plugin. Páginas administrativas só existem para quem tem {@code vertexx.admin}. */
public final class PanelGUI implements InventoryHolder {

    private enum Page {
        HOME("Painel"), WAYSTONES("Waystones"), GRAVES("Túmulos"),
        GUIDE("Comandos"), ADMIN("Administração"), ADMIN_WAYSTONES("Todos os Waystones");

        private final String title;

        Page(String title) {
            this.title = title;
        }
    }

    private static final int SIZE = 54;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final int[] LIST_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private final PluginMain plugin;
    private final Player player;
    private final Inventory inventory;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
    private final boolean pack;
    private Page page = Page.HOME;
    private int listPage;

    public PanelGUI(PluginMain plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.pack = plugin.resourcePack().isLoaded(player);
        this.inventory = Bukkit.createInventory(this, SIZE, Ui.title(pack, Ui.Theme.VERTEXX,
                "<gradient:#7f5af0:#2cb67d><bold>Vertexx</bold></gradient> <dark_gray>» <gray>Painel"));
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

    private boolean admin() {
        return player.hasPermission("vertexx.admin");
    }

    public void handleClick(int slot, ClickType click) {
        Consumer<ClickType> action = actions.get(slot);
        if (action != null) {
            Fx.click(player);
            action.accept(click);
        }
    }

    private void go(Page target) {
        if ((target == Page.ADMIN || target == Page.ADMIN_WAYSTONES) && !admin()) {
            Fx.deny(player);
            return;
        }
        page = target;
        listPage = 0;
        render();
    }

    // ── Renderização ───────────────────────────────────────────

    private void set(int slot, ItemStack item, Consumer<ClickType> action) {
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    private void frame() {
        actions.clear();
        ItemStack border = Items.filler(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack dark = Items.filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            inventory.setItem(slot, row == 0 || row == 5 ? border : column == 0 || column == 8 ? dark : null);
        }
        set(CLOSE_SLOT, Items.of(Material.BARRIER, "<red>Fechar", List.of()), c -> player.closeInventory());
        if (page != Page.HOME) {
            Page back = page == Page.ADMIN_WAYSTONES ? Page.ADMIN : Page.HOME;
            set(BACK_SLOT, Items.of(Material.ARROW, "<yellow>◀ Voltar", List.of()), c -> go(back));
        }
        set(4, Items.of(Material.BOOK, "<gradient:#c4a7ff:#2cb67d><bold>" + page.title,
                List.of("<dark_gray>Vertexx " + plugin.getPluginMeta().getVersion())), null);
    }

    private void render() {
        frame();
        switch (page) {
            case HOME -> home();
            case WAYSTONES -> waystones();
            case GRAVES -> graves();
            case GUIDE -> guide();
            case ADMIN -> admin_();
            case ADMIN_WAYSTONES -> adminWaystones();
        }
        Ui.skin(pack, inventory);
    }

    private void home() {
        set(13, Items.of(Material.PLAYER_HEAD, "<white>" + Msg.esc(player.getName()),
                List.of("<gray>Waystones: <white>" + plugin.waystones().ownedBy(player.getUniqueId()),
                        "<gray>Túmulos pendentes: <white>" + plugin.graves().ownedBy(player.getUniqueId()).size())), null);

        set(20, Items.of(Material.LODESTONE, "<gradient:#c4a7ff:#2cb67d>Waystones",
                List.of("<gray>Como criar, pagamento e seus pontos.", "", "<yellow>▶ Clique para abrir")),
                c -> go(Page.WAYSTONES));
        set(22, Items.of(Material.SKELETON_SKULL, "<gradient:#c4a7ff:#2cb67d>Túmulos",
                List.of("<gray>Seus túmulos pendentes.", "", "<yellow>▶ Clique para abrir")),
                c -> go(Page.GRAVES));
        set(24, Items.of(Material.WRITABLE_BOOK, "<gradient:#c4a7ff:#2cb67d>Comandos",
                List.of("<gray>Lista de comandos e dicas.", "", "<yellow>▶ Clique para abrir")),
                c -> go(Page.GUIDE));

        set(31, Items.of(Material.NETHER_STAR, "<gradient:#c4a7ff:#2cb67d>Status e Habilidades",
                List.of("<gray>Níveis, árvore de habilidades e", "<gray>informações do sistema.", "",
                        "<yellow>▶ Clique para abrir")),
                c -> dev.vertexx.rpg.StatsGUI.open(plugin, player, dev.vertexx.rpg.StatsGUI.Page.STATS));

        if (admin()) {
            set(40, Items.of(Material.COMMAND_BLOCK, "<red><bold>Administração",
                    List.of("<gray>Status, atualização, configurações", "<gray>e todos os Waystones.", "",
                            "<dark_gray>Visível apenas para administradores.", "<yellow>▶ Clique para abrir")),
                    c -> go(Page.ADMIN));
        }
    }

    private void waystones() {
        var manager = plugin.waystones();
        int limit = plugin.getConfig().getInt("waystones.max-per-player", 3);
        int owned = manager.ownedBy(player.getUniqueId());
        set(20, Items.of(Material.LODESTONE, "<gradient:#c4a7ff:#2cb67d>Como criar um Waystone",
                List.of("<gray>1. Renomeie um <white>Lodestone</white> na bigorna.",
                        "<gray>2. Coloque-o no chão.",
                        "<gray>3. Assista à montagem do modelo.",
                        "",
                        "<gray>Seus Waystones: <white>" + owned + (limit < 0 ? "" : "<dark_gray>/<white>" + limit))), null);
        Material paying = manager.chosenPayment(player);
        set(22, Items.of(paying, "<gradient:#c4a7ff:#2cb67d>Pagamento da viagem",
                List.of("<gray>Você paga com: " + manager.costFor(player).describe(),
                        "",
                        "<yellow>▶ Clique para alternar <dark_gray>(ametista, pérola ou resina)")),
                c -> {
                    manager.cyclePayment(player);
                    Fx.select(player);
                    render();
                });
        set(24, Items.of(Material.COMPARATOR, "<gradient:#c4a7ff:#2cb67d>Personalização",
                List.of("<gray>No menu do seu Waystone, abra <white>Configurar</white>:",
                        "<gray>• ícone flutuante sobre o modelo",
                        "<gray>• mostrar ou ocultar o nome",
                        "<gray>• rota fixa, sem menu de destinos",
                        "",
                        "<dark_gray>Shift + clique direito abre o menu mesmo com rota.")), null);

        List<Waystone> mine = manager.ownedList(player.getUniqueId());
        for (int i = 0; i < mine.size() && i < 14; i++) {
            Waystone waystone = mine.get(i);
            set(LIST_SLOTS[i + 7], Items.of(waystone.icon(), "<white>" + Msg.esc(waystone.name()),
                    List.of("<gray>Local: <white>" + waystone.pos().world() + " <dark_gray>" + waystone.pos().coords(),
                            "",
                            "<yellow>▶ Clique para configurar")),
                    c -> new WaystoneConfigGUI(plugin, player, waystone).open());
        }
    }

    private void graves() {
        GraveManager manager = plugin.graves();
        List<Grave> graves = manager.ownedBy(player.getUniqueId());
        if (graves.isEmpty()) {
            set(22, Items.of(Material.SKELETON_SKULL, "<gray>Nenhum túmulo pendente",
                    List.of("<dark_gray>Ao morrer, seus itens e XP ficam", "<dark_gray>guardados em um túmulo.")), null);
            return;
        }
        for (int i = 0; i < graves.size() && i < LIST_SLOTS.length; i++) {
            Grave grave = graves.get(i);
            long left = manager.protectionLeft(grave);
            set(LIST_SLOTS[i], Items.of(Material.PLAYER_HEAD, "<white>Túmulo " + (i + 1),
                    List.of("<gray>Local: <white>" + grave.pos().world() + " <dark_gray>" + grave.pos().coords(),
                            "<gray>XP guardado: <white>" + grave.xp(),
                            left > 0 ? "<gray>Só você abre por mais <white>" + GraveManager.formatTime(left)
                                    : "<yellow>Protegido apenas pelo local")), null);
        }
    }

    private void guide() {
        set(20, Items.of(Material.SKELETON_SKULL, "<white>/tumulo", List.of("<gray>Lista seus túmulos pendentes.")), null);
        set(22, Items.of(Material.PAINTING, "<white>/pacote", List.of("<gray>Aceita ou recusa o pacote visual opcional.")), null);
        set(24, Items.of(Material.BOOK, "<white>/vertexx livro", List.of("<gray>Recebe outro Guia do Vertexx.")), null);
        set(29, Items.of(Material.NETHER_STAR, "<white>/vstats", List.of("<gray>Status, habilidades e informações.")), null);
        set(31, Items.of(Material.LODESTONE, "<white>Waystones",
                List.of("<gray>Clique direito para viajar.", "<gray>Clique em um novo Waystone para descobri-lo.")), null);
    }

    // ── Administração ──────────────────────────────────────────

    private void admin_() {
        if (!admin()) {
            go(Page.HOME);
            return;
        }
        int[] models = plugin.models().stats();
        String pending = plugin.updater().pendingVersion();
        set(19, Items.of(Material.COMMAND_BLOCK, "<white>Servidor",
                List.of("<gray>Vertexx: <white>" + plugin.getPluginMeta().getVersion(),
                        "<gray>Jogadores: <white>" + Bukkit.getOnlinePlayers().size() + "<dark_gray>/" + Bukkit.getMaxPlayers(),
                        "<gray>Túmulos ativos: <white>" + plugin.graves().count())), null);
        set(20, Items.of(Material.LODESTONE, "<white>Waystones e modelos",
                List.of("<gray>Registrados: <white>" + models[0],
                        "<gray>Renderizados agora: <white>" + models[1],
                        "<gray>Observadores: <white>" + models[2],
                        "",
                        "<yellow>▶ Clique para ver todos")), c -> go(Page.ADMIN_WAYSTONES));
        set(21, Items.of(Material.HOPPER, "<white>Atualização pelo GitHub",
                List.of(pending == null ? "<gray>Nenhuma versão pendente." : "<yellow>Aguardando reinício: <white>" + pending,
                        "",
                        "<yellow>▶ Clique para checar agora")),
                c -> plugin.updater().checkNow(player));
        set(22, Items.of(Material.REDSTONE_TORCH, "<white>Recarregar config.yml",
                List.of("<gray>Aplica mudanças feitas no arquivo.", "", "<yellow>▶ Clique para recarregar")),
                c -> {
                    plugin.reloadConfig();
                    Msg.send(player, "<green>config.yml recarregado.");
                    render();
                });
        toggle(29, "waystones.ambient-particles", Material.ENCHANTED_BOOK, "Partículas dos Waystones", true);
        toggle(30, "waystones.model.enabled", Material.LODESTONE, "Modelos 3D dos Waystones", true);
        toggle(31, "waystones.model.fov-culling", Material.SPYGLASS, "Ocultar fora do campo de visão", true);
        toggle(32, "waystones.model.occlusion-culling", Material.STONE_BRICKS, "Ocultar atrás de paredes", true);
        toggle(33, "waystones.model.assembly-animation", Material.ANVIL, "Animação de montagem", true);
        toggle(34, "graves.enabled", Material.SKELETON_SKULL, "Túmulos", true);
    }

    private void toggle(int slot, String path, Material icon, String label, boolean def) {
        boolean on = plugin.getConfig().getBoolean(path, def);
        set(slot, Items.of(icon, "<white>" + label,
                List.of("<gray>Estado: " + (on ? "<green>ligado" : "<red>desligado"),
                        "<dark_gray>" + path,
                        "",
                        "<yellow>▶ Clique para alternar")),
                c -> {
                    if (!admin()) {
                        return;
                    }
                    plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path, def));
                    plugin.saveConfig();
                    render();
                });
    }

    private void adminWaystones() {
        if (!admin()) {
            go(Page.HOME);
            return;
        }
        List<Waystone> all = new ArrayList<>(plugin.waystones().all());
        int perPage = LIST_SLOTS.length;
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        listPage = Math.min(listPage, pages - 1);
        for (int i = 0; i < perPage && listPage * perPage + i < all.size(); i++) {
            Waystone waystone = all.get(listPage * perPage + i);
            set(LIST_SLOTS[i], Items.of(waystone.icon(), "<white>" + Msg.esc(waystone.name()),
                    List.of("<gray>Dono: <white>" + waystone.ownerName(),
                            "<gray>Local: <white>" + waystone.pos().world() + " <dark_gray>" + waystone.pos().coords(),
                            "",
                            "<yellow>▶ Clique esquerdo: teleportar",
                            "<yellow>▶ Clique direito: configurar",
                            "<red>▶ Shift + direito: remover registro <dark_gray>(o bloco permanece)")),
                    click -> adminWaystoneClick(waystone, click));
        }
        if (all.isEmpty()) {
            set(22, Items.of(Material.COMPASS, "<gray>Nenhum Waystone registrado", List.of()), null);
        }
        if (pages > 1) {
            set(NEXT_SLOT, Items.of(Material.ARROW, "<yellow>Próxima página ▶",
                    List.of("<dark_gray>Página " + (listPage + 1) + " de " + pages)), c -> {
                listPage = (listPage + 1) % pages;
                render();
            });
        }
    }

    private void adminWaystoneClick(Waystone waystone, ClickType click) {
        if (!admin()) {
            return;
        }
        if (click == ClickType.SHIFT_RIGHT) {
            plugin.waystones().remove(waystone);
            Msg.send(player, "<yellow>Registro do Waystone <white>" + Msg.esc(waystone.name()) + "</white> removido.");
            render();
        } else if (click.isRightClick()) {
            new WaystoneConfigGUI(plugin, player, waystone).open();
        } else {
            World world = waystone.pos().bukkitWorld();
            if (world == null) {
                Fx.deny(player);
                return;
            }
            Location target = waystone.pos().center().add(0, 1.5, 0);
            player.closeInventory();
            player.teleportAsync(target);
            Msg.send(player, "<gray>Teleportado para <white>" + Msg.esc(waystone.name()) + "</white>.");
        }
    }
}
