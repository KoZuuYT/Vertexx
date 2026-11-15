package dev.vertexx.panel;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Ui;
import dev.vertexx.grave.Grave;
import dev.vertexx.grave.GraveManager;
import dev.vertexx.honor.HonorCommand;
import dev.vertexx.honor.HonorManager;
import dev.vertexx.kingdom.Kingdom;
import dev.vertexx.kingdom.KingdomManager;
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
        HOME("Painel"), KINGDOM("Reinos"), WAYSTONES("Waystones"), GRAVES("Túmulos"), HONOR("Honra"),
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
            case KINGDOM -> kingdoms();
            case WAYSTONES -> waystones();
            case GRAVES -> graves();
            case HONOR -> honor();
            case GUIDE -> guide();
            case ADMIN -> admin_();
            case ADMIN_WAYSTONES -> adminWaystones();
        }
        Ui.skin(pack, inventory);
    }

    private void home() {
        KingdomManager.Member member = plugin.kingdoms().member(player.getUniqueId());
        int honor = plugin.honor().get(player.getUniqueId());
        boolean outlaw = plugin.mercenaries().isOutlaw(player.getUniqueId());
        List<String> profile = new ArrayList<>();
        profile.add("<gray>Reino: " + (member != null ? member.kingdom().styled()
                : outlaw ? "<red>fora da lei" : "<red>nenhum"));
        if (member != null) {
            profile.add("<gray>Cargo: <white>" + member.rank().displayName());
        }
        profile.add("<gray>Honra: " + HonorCommand.bar(honor) + " <white>" + honor
                + " <dark_gray>(" + HonorManager.Tier.of(honor).styled() + "<dark_gray>)");
        int rep = plugin.mercenaries().rep(player.getUniqueId());
        if (outlaw || rep > 0) {
            profile.add("<gray>Reputação: <white>" + rep + " <dark_gray>("
                    + plugin.mercenaries().tier(player.getUniqueId()).styled() + "<dark_gray>)");
        }
        set(13, Items.of(Material.PLAYER_HEAD, "<white>" + Msg.esc(player.getName()), profile), null);

        set(29, Ui.model(pack, Items.of(Material.SKELETON_SKULL, "<red><bold>Mercenários e recompensas",
                List.of("<gray>Quadro de procurados, preço nas cabeças",
                        "<gray>e a reputação dos foras da lei.", "", "<yellow>▶ Clique para abrir")), "ui_skull"),
                c -> new dev.vertexx.mercenary.BountyGUI(plugin, player).open());
        if (member != null || outlaw) {
            set(30, Ui.model(pack, Items.of(Material.IRON_DOOR, "<gradient:#c4a7ff:#2cb67d>" + (outlaw ? "Voltar a um reino" : "Deixar o reino"),
                    List.of("<gray>" + (outlaw ? "Entre em um dos três reinos." : "Aliança, traição ou vida de fora da lei."), "",
                            "<yellow>▶ Clique para abrir")), "ui_daggers"),
                    c -> new dev.vertexx.kingdom.KingdomSwitchGUI(plugin, player).open());
        }

        set(20, Items.of(member == null ? Material.WHITE_BANNER : member.kingdom().icon(),
                "<gradient:#c4a7ff:#2cb67d>Reinos",
                List.of("<gray>Os três reinos, membros e alianças.", "", "<yellow>▶ Clique para abrir")),
                c -> go(Page.KINGDOM));
        set(21, Items.of(Material.LODESTONE, "<gradient:#c4a7ff:#2cb67d>Waystones",
                List.of("<gray>Como criar, pagamento e seus pontos.", "", "<yellow>▶ Clique para abrir")),
                c -> go(Page.WAYSTONES));
        set(22, Items.of(Material.SKELETON_SKULL, "<gradient:#c4a7ff:#2cb67d>Túmulos",
                List.of("<gray>Seus túmulos pendentes.", "", "<yellow>▶ Clique para abrir")),
                c -> go(Page.GRAVES));
        set(23, Items.of(Material.GOLDEN_HELMET, "<gradient:#c4a7ff:#2cb67d>Honra",
                List.of("<gray>Níveis de honra e o que muda neles.", "", "<yellow>▶ Clique para abrir")),
                c -> go(Page.HONOR));
        set(24, Items.of(Material.WRITABLE_BOOK, "<gradient:#c4a7ff:#2cb67d>Comandos",
                List.of("<gray>Lista de comandos e dicas.", "", "<yellow>▶ Clique para abrir")),
                c -> go(Page.GUIDE));

        if (admin()) {
            set(40, Items.of(Material.COMMAND_BLOCK, "<red><bold>Administração",
                    List.of("<gray>Status, atualização, configurações", "<gray>e todos os Waystones.", "",
                            "<dark_gray>Visível apenas para administradores.", "<yellow>▶ Clique para abrir")),
                    c -> go(Page.ADMIN));
        }
    }

    private void kingdoms() {
        KingdomManager kingdoms = plugin.kingdoms();
        Kingdom own = kingdoms.kingdomOf(player.getUniqueId());
        int[] slots = {20, 22, 24};
        int index = 0;
        for (Kingdom kingdom : Kingdom.values()) {
            List<String> lore = new ArrayList<>(kingdom.description());
            lore.add("");
            lore.add("<gray>Membros: <white>" + kingdoms.count(kingdom));
            List<String> allies = new ArrayList<>();
            for (Kingdom ally : kingdoms.alliesOf(kingdom)) {
                allies.add(ally.styled());
            }
            lore.add("<gray>Aliados: " + (allies.isEmpty() ? "<dark_gray>nenhum" : String.join("<gray>, ", allies)));
            if (kingdom == own) {
                lore.add("");
                lore.add("<green>✔ Seu reino");
            }
            lore.add(1, "<gray>Honra do reino: " + dev.vertexx.kingdom.KingdomManager.honorLabel(kingdoms.honor(kingdom)));
            set(slots[index++], Ui.emblem(pack, Items.of(kingdom.icon(), "<bold>" + kingdom.styled(), lore, kingdom == own), kingdom), null);
        }
        if (own != null) {
            set(40, Items.of(own.icon(), "<gradient:#c4a7ff:#2cb67d><bold>Painel do meu reino",
                    List.of("<gray>Status detalhado, membros, mural", "<gray>e diplomacia de " + own.styled() + "<gray>.", "",
                            "<yellow>▶ Clique para abrir")),
                    c -> new dev.vertexx.kingdom.KingdomPanelGUI(plugin, player, own).open());
        }
        boolean friendly = plugin.getConfig().getBoolean("combat.friendly-fire", false);
        set(31, Items.of(Material.IRON_SWORD, "<gradient:#c4a7ff:#2cb67d>Regras de combate",
                List.of("<gray>Fogo amigo entre aliados: " + (friendly ? "<red>ligado" : "<green>desligado"),
                        "<gray>Membros do mesmo reino e de reinos",
                        "<gray>aliados não se ferem.",
                        "",
                        "<dark_gray>Alianças: <white>/reino aliar <reino></white>",
                        "<dark_gray>Romper: <white>/reino romper <reino></white> <dark_gray>(custa honra)")), null);
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

    private void honor() {
        int honor = plugin.honor().get(player.getUniqueId());
        set(13, Items.of(Material.NETHER_STAR, "<white>Sua honra: " + honor,
                List.of(HonorCommand.bar(honor), "<gray>Nível: " + HonorManager.Tier.of(honor).styled(),
                        "", "<dark_gray>Vai de -100 a 100.")), null);
        set(20, Items.of(Material.WITHER_SKELETON_SKULL, "<red>Desonrado <dark_gray>(-30 ou menos)",
                List.of("<gray>Paga o dobro nos Waystones.", "<gray>Pode ser caçado sem punição.")), null);
        set(21, Items.of(Material.IRON_INGOT, "<gray>Neutro <dark_gray>(-29 a 29)",
                List.of("<gray>Sem bônus nem penalidades.")), null);
        set(23, Items.of(Material.EMERALD, "<green>Honrado <dark_gray>(30+)",
                List.of("<gray>Reconhecido pelos reinos.")), null);
        set(24, Items.of(Material.GOLDEN_APPLE, "<gold>Glorioso <dark_gray>(70+)",
                List.of("<gray>Viaja de graça pelos Waystones.")), null);
        var config = plugin.getConfig();
        set(31, Items.of(Material.PAPER, "<gradient:#c4a7ff:#2cb67d>O que muda a honra",
                List.of("<gray>Tempo ativo: <green>+" + config.getInt("honor.activity.amount", 1)
                                + " <dark_gray>a cada " + config.getInt("honor.activity.interval-minutes", 10) + " min",
                        "<gray>Defender aliado: <green>+" + config.getInt("honor.defense-kill", 3),
                        "<gray>Matar desonrado: <green>+" + config.getInt("honor.dishonored-kill", 5),
                        "<gray>Ataque injustificado: <red>" + config.getInt("honor.unjustified-attack", -1),
                        "<gray>Morte injustificada: <red>" + config.getInt("honor.unjustified-kill", -10),
                        "<gray>Traição: <red>" + config.getInt("honor.betrayal-kill", -25),
                        "<gray>Romper aliança: <red>" + config.getInt("honor.alliance-break", -20))), null);
    }

    private void guide() {
        set(20, Items.of(Material.WHITE_BANNER, "<white>/reino", List.of("<gray>Informações do seu reino.",
                "<dark_gray>/reino membros, aliar, romper")), null);
        set(21, Items.of(Material.SKELETON_SKULL, "<white>/tumulo", List.of("<gray>Lista seus túmulos pendentes.")), null);
        set(22, Items.of(Material.GOLDEN_HELMET, "<white>/honra [jogador]", List.of("<gray>Mostra a honra de alguém.")), null);
        set(23, Items.of(Material.PAINTING, "<white>/pacote", List.of("<gray>Aceita ou recusa o pacote visual opcional.")), null);
        set(24, Items.of(Material.BOOK, "<white>/vertexx livro", List.of("<gray>Recebe outro Guia do Vertexx.")), null);
        set(29, Items.of(Material.WRITABLE_BOOK, "<white>/reino anuncio <texto>",
                List.of("<gray>Líder: fixa o mural do reino.", "<dark_gray>/reino promover|rebaixar <jogador>")), null);
        set(32, Items.of(Material.IRON_DOOR, "<white>/reino sair | entrar",
                List.of("<gray>Troca de reino ou vida de fora da lei.", "<dark_gray>Aliança, traição ou mercenário.")), null);
        set(33, Items.of(Material.SKELETON_SKULL, "<white>/recompensa | /mercenario",
                List.of("<gray>Quadro de procurados e reputação.", "<dark_gray>/recompensa colocar <jogador> <quantia>")), null);
        set(31, Items.of(Material.LODESTONE, "<white>Waystones",
                List.of("<gray>Clique direito para viajar.", "<gray>Clique em um novo Waystone para descobri-lo.",
                        "<gray>Em combate você não pode viajar.")), null);
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
                        "<gray>Túmulos ativos: <white>" + plugin.graves().count(),
                        "<gray>Reinos: <white>" + membersLine())), null);
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

        toggle(28, "combat.friendly-fire", Material.IRON_SWORD, "Fogo amigo", false);
        toggle(29, "waystones.ambient-particles", Material.ENCHANTED_BOOK, "Partículas dos Waystones", true);
        toggle(30, "waystones.model.enabled", Material.LODESTONE, "Modelos 3D dos Waystones", true);
        toggle(31, "waystones.model.fov-culling", Material.SPYGLASS, "Ocultar fora do campo de visão", true);
        toggle(32, "waystones.model.occlusion-culling", Material.STONE_BRICKS, "Ocultar atrás de paredes", true);
        toggle(33, "waystones.model.assembly-animation", Material.ANVIL, "Animação de montagem", true);
        toggle(34, "graves.enabled", Material.SKELETON_SKULL, "Túmulos", true);
    }

    private String membersLine() {
        List<String> parts = new ArrayList<>();
        for (Kingdom kingdom : Kingdom.values()) {
            parts.add(String.valueOf(plugin.kingdoms().count(kingdom)));
        }
        return String.join("<dark_gray>/<white>", parts);
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
