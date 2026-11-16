package dev.vertexx.panel;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Ui;
import dev.vertexx.grave.Grave;
import dev.vertexx.boss.BossKind;
import dev.vertexx.grave.GraveManager;
import dev.vertexx.mob.MobManager;
import dev.vertexx.mob.Trait;
import dev.vertexx.rpg.Stat;
import dev.vertexx.waystone.Waystone;
import dev.vertexx.waystone.WaystoneConfigGUI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Painel do plugin. Páginas administrativas só existem para quem tem {@code vertexx.admin}. */
public final class PanelGUI implements InventoryHolder {

    private enum Page {
        HOME("Painel"), WAYSTONES("Waystones"), GRAVES("Túmulos"),
        GUIDE("Comandos"), ADMIN("Administração"), ADMIN_WAYSTONES("Todos os Waystones"),
        ADMIN_MOBS("Criar mobs"), ADMIN_TRAITS("Traits do mob"), ADMIN_XP("Conceder XP"), ADMIN_BOSSES("Bosses"), ADMIN_EGGS("Ovos e itens"),
        WIKI("Wiki");

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
    private EntityType mobType = EntityType.ZOMBIE;
    private int mobLevel = 10;
    private boolean mobElite;
    private final EnumSet<Trait> mobTraits = EnumSet.noneOf(Trait.class);
    private java.util.UUID xpTarget;
    private Stat xpStat = Stat.COMBAT;
    private int xpAmount = 100;
    private int wikiTab;

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
        if (target.name().startsWith("ADMIN") && !admin()) {
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
            Page back = switch (page) {
                case ADMIN_WAYSTONES, ADMIN_MOBS, ADMIN_XP, ADMIN_BOSSES, ADMIN_EGGS -> Page.ADMIN;
                case ADMIN_TRAITS -> Page.ADMIN_MOBS;
                default -> Page.HOME;
            };
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
            case ADMIN_MOBS -> adminMobs();
            case ADMIN_TRAITS -> adminTraits();
            case ADMIN_XP -> adminXp();
            case ADMIN_BOSSES -> adminBosses();
            case ADMIN_EGGS -> adminEggs();
            case WIKI -> wiki();
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

        set(33, Items.of(Material.ENCHANTED_BOOK, "<gradient:#c4a7ff:#2cb67d>Wiki: Traits, Elites e Bosses",
                List.of("<gray>Todos os traits, mobs Elite", "<gray>e os bosses do Vertexx.", "",
                        "<yellow>▶ Clique para abrir")),
                c -> go(Page.WIKI));

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
        set(33, Items.of(Material.ENCHANTED_BOOK, "<white>Wiki", List.of("<gray>Traits, mobs Elite e bosses",
                "<gray>estão na Wiki, aberta pela tela inicial.")), c -> go(Page.WIKI));
        set(38, Items.of(Material.CHEST, "<white>/mochila",
                List.of("<gray>Abre a mochila equipada no peito.", "<gray>Botão direito com ela na mão também abre.",
                        "<gray>Botão do meio num baú ou mochila: ordena.")), null);
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
        toggle(28, "mobs.enabled", Material.ZOMBIE_HEAD, "Mobs nivelados", true);
        toggle(35, "bosses.enabled", Material.WITHER_SKELETON_SKULL, "Bosses", true);
        set(38, Items.of(Material.ZOMBIE_SPAWN_EGG, "<white>Criar mobs",
                List.of("<gray>Escolha tipo, nível, elite e traits", "<gray>e crie um mob à sua frente.",
                        "<dark_gray>Equivale a /vertexx mob", "", "<yellow>▶ Clique para abrir")),
                c -> go(Page.ADMIN_MOBS));
        set(39, Items.of(Material.EXPERIENCE_BOTTLE, "<white>Conceder XP",
                List.of("<gray>Dá XP de status a um jogador.", "<dark_gray>Equivale a /vertexx xp", "",
                        "<yellow>▶ Clique para abrir")), c -> go(Page.ADMIN_XP));
        set(40, Items.of(Material.WITHER_SKELETON_SKULL, "<white>Bosses",
                List.of("<gray>Salas, testes e controle dos bosses.", "<dark_gray>Equivale a /vertexx boss", "",
                        "<yellow>▶ Clique para abrir")), c -> go(Page.ADMIN_BOSSES));
        set(41, Items.of(Material.ALLAY_SPAWN_EGG, "<white>Ovos e itens",
                List.of("<gray>Ovos das criaturas customizadas e", "<gray>itens da End Update (mochilas...).", "",
                        "<yellow>▶ Clique para abrir")), c -> go(Page.ADMIN_EGGS));
    }

    private void adminEggs() {
        if (!admin()) {
            go(Page.HOME);
            return;
        }
        dev.vertexx.fauna.FaunaKind[] kinds = dev.vertexx.fauna.FaunaKind.values();
        for (int i = 0; i < kinds.length && i < LIST_SLOTS.length; i++) {
            dev.vertexx.fauna.FaunaKind kind = kinds[i];
            ItemStack egg = plugin.fauna().egg(kind);
            egg.editMeta(meta -> meta.lore(List.of(
                    Msg.item("<gray>Clique: <white>1 ovo"),
                    Msg.item("<gray>Shift + clique: <white>uma pilha de 16"),
                    Msg.item("<dark_gray>Use num bloco para criar a criatura."))));
            set(LIST_SLOTS[i], egg, c -> {
                ItemStack give = plugin.fauna().egg(kind);
                give.setAmount(c.isShiftClick() ? 16 : 1);
                player.getInventory().addItem(give).values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
                Fx.success(player);
                Msg.actionBar(player, "<green>Ovo de <white>" + kind.display() + "<green> entregue.");
            });
        }
        dev.vertexx.end.EndItem[] items = dev.vertexx.end.EndItem.values();
        for (int i = 0; i < items.length && kinds.length + i < LIST_SLOTS.length; i++) {
            dev.vertexx.end.EndItem item = items[i];
            ItemStack icon = item.tier() > 0 ? plugin.backpacks().create(item.tier()) : item.create(plugin);
            icon.editMeta(meta -> {
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
                lore.add(Msg.item(""));
                lore.add(Msg.item("<yellow>▶ Clique: 1 unidade"));
                if (item.tier() == 0) {
                    lore.add(Msg.item("<yellow>▶ Shift + clique: 16"));
                }
                meta.lore(lore);
            });
            set(LIST_SLOTS[kinds.length + i], icon, c -> {
                ItemStack give = item.tier() > 0 ? plugin.backpacks().create(item.tier()) : item.create(plugin);
                give.setAmount(item.tier() > 0 ? 1 : c.isShiftClick() ? 16 : 1);
                player.getInventory().addItem(give).values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
                Fx.success(player);
                Msg.actionBar(player, "<green>" + item.display() + " entregue.");
            });
        }
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

    // ── Administração: mobs, XP e bosses ───────────────────────

    private static Material egg(EntityType type) {
        Material material = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        return material == null ? Material.ZOMBIE_SPAWN_EGG : material;
    }

    private static String typeName(EntityType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    private String mobCommand() {
        StringBuilder text = new StringBuilder("/vertexx mob " + typeName(mobType) + " " + mobLevel);
        if (mobElite) {
            text.append(" elite");
        }
        for (Trait trait : mobTraits) {
            text.append(' ').append(trait.name().toLowerCase(Locale.ROOT));
        }
        return text.toString();
    }

    private void adminMobs() {
        if (!admin()) {
            go(Page.HOME);
            return;
        }
        List<EntityType> types = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            if (MobManager.accepts(type)) {
                types.add(type);
            }
        }
        int perPage = 14;
        int pages = Math.max(1, (types.size() + perPage - 1) / perPage);
        listPage = Math.min(listPage, pages - 1);
        for (int i = 0; i < perPage && listPage * perPage + i < types.size(); i++) {
            EntityType type = types.get(listPage * perPage + i);
            boolean chosen = type == mobType;
            set(LIST_SLOTS[i], Items.of(egg(type), (chosen ? "<green>✔ " : "<white>") + typeName(type),
                    List.of(chosen ? "<gray>Selecionado." : "<yellow>▶ Clique para selecionar"), chosen), c -> {
                mobType = type;
                render();
            });
        }
        if (pages > 1) {
            set(NEXT_SLOT, Items.of(Material.ARROW, "<yellow>Mais mobs ▶",
                    List.of("<dark_gray>Página " + (listPage + 1) + " de " + pages)), c -> {
                listPage = (listPage + 1) % pages;
                render();
            });
        }
        set(28, Items.of(Material.RED_STAINED_GLASS_PANE, "<red>Nível −10",
                List.of("<gray>Clique esquerdo: −10", "<gray>Clique direito: −1")), c -> {
            mobLevel = Math.max(1, mobLevel - (c.isRightClick() ? 1 : 10));
            render();
        });
        set(29, Items.of(Material.RED_STAINED_GLASS_PANE, "<red>Nível −1", List.of("<gray>Clique para diminuir.")), c -> {
            mobLevel = Math.max(1, mobLevel - 1);
            render();
        });
        set(30, Items.of(Material.EXPERIENCE_BOTTLE, "<white>Nível: " + MobManager.levelColor(mobLevel) + mobLevel,
                List.of("<gray>O nível do mob criado.")), null);
        set(31, Items.of(Material.LIME_STAINED_GLASS_PANE, "<green>Nível +1", List.of("<gray>Clique para aumentar.")), c -> {
            mobLevel = Math.min(200, mobLevel + 1);
            render();
        });
        set(32, Items.of(Material.LIME_STAINED_GLASS_PANE, "<green>Nível +10",
                List.of("<gray>Clique esquerdo: +10", "<gray>Clique direito: +1")), c -> {
            mobLevel = Math.min(200, mobLevel + (c.isRightClick() ? 1 : 10));
            render();
        });
        set(33, Items.of(mobElite ? Material.ENCHANTED_GOLDEN_APPLE : Material.GOLDEN_APPLE,
                "<gold>Elite: " + (mobElite ? "<green>ligado" : "<red>desligado"),
                List.of("<gray>Escala maior, mais vida, traits melhores", "<gray>e drops especiais.", "",
                        "<yellow>▶ Clique para alternar"), mobElite), c -> {
            mobElite = !mobElite;
            render();
        });
        List<String> traitLore = new ArrayList<>();
        traitLore.add(mobTraits.isEmpty() ? "<gray>Nenhum: o jogo sorteia pelas regras normais." : "<gray>Selecionados:");
        for (Trait trait : mobTraits) {
            traitLore.add(" " + trait.color() + trait.symbol() + " " + trait.display());
        }
        traitLore.add("");
        traitLore.add("<yellow>▶ Clique para escolher");
        set(34, Items.of(Material.NETHER_STAR, "<light_purple>Traits", traitLore, !mobTraits.isEmpty()),
                c -> go(Page.ADMIN_TRAITS));
        set(40, Items.of(Material.EMERALD, "<green><bold>Criar mob",
                List.of("<dark_gray>" + mobCommand(), "", "<yellow>▶ Clique para criar à sua frente"), true), c -> {
            var at = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(3));
            boolean elite = mobElite || mobTraits.stream().anyMatch(Trait::elite);
            var mob = plugin.mobs().spawnTest(at, mobType, mobLevel, elite, new ArrayList<>(mobTraits));
            if (mob == null) {
                Fx.deny(player);
                Msg.actionBar(player, "<red>Não foi possível criar o mob.");
            } else {
                Fx.success(player);
                Msg.actionBar(player, "<green>Mob criado: <white>" + typeName(mobType));
            }
        });
    }

    private void adminTraits() {
        if (!admin()) {
            go(Page.HOME);
            return;
        }
        int[] normalSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32};
        int[] eliteSlots = {38, 39, 40, 41, 42};
        int n = 0;
        int e = 0;
        for (Trait trait : Trait.values()) {
            int slot = trait.elite() ? eliteSlots[e++] : normalSlots[n++];
            boolean chosen = mobTraits.contains(trait);
            set(slot, traitItem(trait, chosen, chosen ? "<green>✔ Selecionado <dark_gray>(clique para remover)"
                    : "<yellow>▶ Clique para selecionar"), c -> {
                if (!mobTraits.remove(trait)) {
                    mobTraits.add(trait);
                }
                render();
            });
        }
        set(10, Items.of(Material.PAPER, "<white>Traits comuns", List.of("<gray>Podem nascer em qualquer mob.")), null);
        set(37, Items.of(Material.PAPER, "<gold>Traits de Elite",
                List.of("<gray>Só em mobs Elite (o mob vira Elite", "<gray>automaticamente ao escolher um).")), null);
        set(34, Items.of(Material.BARRIER, "<red>Limpar seleção", List.of("<gray>Volta ao sorteio normal.")), c -> {
            mobTraits.clear();
            render();
        });
    }

    private static Material traitIcon(Trait trait) {
        return switch (trait) {
            case SWIFT -> Material.FEATHER;
            case TOUGH -> Material.APPLE;
            case BRUTE -> Material.IRON_AXE;
            case ARMORED -> Material.IRON_CHESTPLATE;
            case VAMPIRIC -> Material.REDSTONE;
            case THORNY -> Material.CACTUS;
            case VENOMOUS -> Material.SPIDER_EYE;
            case FROSTY -> Material.SNOWBALL;
            case BLAZING -> Material.BLAZE_POWDER;
            case REGENERATING -> Material.GHAST_TEAR;
            case VOLATILE -> Material.TNT;
            case BLINKER -> Material.ENDER_PEARL;
            case TITAN -> Material.IRON_BLOCK;
            case FURIOUS -> Material.BLAZE_ROD;
            case WARDEN -> Material.SHIELD;
            case STORMCALLER -> Material.LIGHTNING_ROD;
            case SANGUINE -> Material.FERMENTED_SPIDER_EYE;
        };
    }

    private ItemStack traitItem(Trait trait, boolean glint, String footer) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + trait.description());
        lore.add("");
        lore.add(trait.elite() ? "<gold>Trait de Elite" : "<white>Trait comum");
        if (footer != null) {
            lore.add("");
            lore.add(footer);
        }
        return Items.of(traitIcon(trait), trait.color() + trait.symbol() + " " + trait.display(), lore, glint);
    }

    private void adminXp() {
        if (!admin()) {
            go(Page.HOME);
            return;
        }
        if (xpTarget == null) {
            xpTarget = player.getUniqueId();
        }
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (int i = 0; i < online.size() && i < 14; i++) {
            Player other = online.get(i);
            boolean chosen = other.getUniqueId().equals(xpTarget);
            ItemStack head = Items.of(Material.PLAYER_HEAD, (chosen ? "<green>✔ " : "<white>") + Msg.esc(other.getName()),
                    List.of(chosen ? "<gray>Jogador selecionado." : "<yellow>▶ Clique para selecionar"), chosen);
            head.editMeta(org.bukkit.inventory.meta.SkullMeta.class, meta -> meta.setOwningPlayer(other));
            set(LIST_SLOTS[i], head, c -> {
                xpTarget = other.getUniqueId();
                render();
            });
        }
        Stat[] stats = Stat.values();
        set(28, Items.of(xpStat.icon(), "<white>Status: " + xpStat.display(),
                List.of("<gray>Clique esquerdo: próximo", "<gray>Clique direito: anterior")), c -> {
            int next = (xpStat.ordinal() + (c.isRightClick() ? stats.length - 1 : 1)) % stats.length;
            xpStat = stats[next];
            render();
        });
        set(30, Items.of(Material.RED_STAINED_GLASS_PANE, "<red>Quantidade −",
                List.of("<gray>Clique esquerdo: −100", "<gray>Clique direito: −10")), c -> {
            xpAmount = Math.max(1, xpAmount - (c.isRightClick() ? 10 : 100));
            render();
        });
        set(31, Items.of(Material.EXPERIENCE_BOTTLE, "<white>Quantidade: <green>" + xpAmount,
                List.of("<gray>XP de status a conceder.")), null);
        set(32, Items.of(Material.LIME_STAINED_GLASS_PANE, "<green>Quantidade +",
                List.of("<gray>Clique esquerdo: +100", "<gray>Clique direito: +10", "<gray>Shift: +1000")), c -> {
            xpAmount += c.isShiftClick() ? 1000 : c.isRightClick() ? 10 : 100;
            render();
        });
        Player target = Bukkit.getPlayer(xpTarget);
        set(40, Items.of(Material.EMERALD, "<green><bold>Conceder XP",
                List.of("<dark_gray>/vertexx xp " + (target == null ? "?" : target.getName()) + " "
                        + xpStat.name().toLowerCase(Locale.ROOT) + " " + xpAmount, "", "<yellow>▶ Clique para conceder"),
                true), c -> {
            Player who = Bukkit.getPlayer(xpTarget);
            if (who == null) {
                Fx.deny(player);
                Msg.actionBar(player, "<red>Jogador não está online.");
                return;
            }
            plugin.rpg().addXp(who, xpStat, xpAmount);
            Fx.success(player);
            Msg.actionBar(player, "<green>+" + xpAmount + " XP de " + xpStat.display() + " para " + who.getName());
        });
    }

    private void adminBosses() {
        if (!admin()) {
            go(Page.HOME);
            return;
        }
        var bosses = plugin.bosses();
        set(20, Items.of(Material.MAGMA_CREAM, "<gold><bold>Wildfire", bossAdminLore(BossKind.WILDFIRE), true),
                c -> bossAdminClick(BossKind.WILDFIRE, c));
        set(24, Items.of(Material.HEART_OF_THE_SEA, "<aqua><bold>Iceologer", bossAdminLore(BossKind.ICEOLOGER), true),
                c -> bossAdminClick(BossKind.ICEOLOGER, c));
        List<String> info = new ArrayList<>();
        info.add("<gray>Salas registradas: <white>" + bosses.arenaCount());
        info.add("<gray>Lutas ativas: <white>" + bosses.activeFights() + (bosses.isBuilding() ? " <yellow>• construindo…" : ""));
        info.add("");
        bosses.describeArenas().stream().limit(6).forEach(info::add);
        set(22, Items.of(Material.BOOK, "<white>Salas", info), null);
        set(29, Items.of(Material.CLOCK, "<white>Despertar bosses",
                List.of("<gray>Acorda os bosses dormentes.", "", "<yellow>▶ Clique")), c -> runBoss("wake"));
        set(31, Items.of(Material.TOTEM_OF_UNDYING, "<white>Reiniciar salas",
                List.of("<gray>Encerra lutas, abre selos e", "<gray>deixa todos os bosses disponíveis.", "",
                        "<yellow>▶ Clique")), c -> runBoss("reset"));
        set(33, Items.of(Material.BARRIER, "<red>Encerrar lutas",
                List.of("<gray>Remove os bosses ativos agora.", "", "<yellow>▶ Clique")), c -> runBoss("kill"));
    }

    private List<String> bossAdminLore(BossKind kind) {
        long count = plugin.bosses().arenaLocations(kind).size();
        return List.of("<gray>" + kind.place() + ": <white>" + count + " construída" + (count == 1 ? "" : "s"), "",
                "<yellow>▶ Esquerdo: criar boss de teste aqui",
                "<yellow>▶ Direito: construir a sala à sua frente",
                "<yellow>▶ Shift + esquerdo: ir até a sala mais próxima");
    }

    private void bossAdminClick(BossKind kind, ClickType click) {
        player.closeInventory();
        if (click == ClickType.SHIFT_LEFT) {
            runBoss("tp " + kind.key());
        } else if (click.isRightClick()) {
            runBoss("build " + kind.key());
        } else {
            runBoss("spawn " + kind.key());
        }
    }

    private void runBoss(String arguments) {
        if (!admin()) {
            return;
        }
        plugin.bosses().command(player, ("boss " + arguments).split(" "));
        if (player.getOpenInventory().getTopInventory().getHolder(false) == this) {
            render();
        }
    }

    // ── Wiki ───────────────────────────────────────────────────

    private void wiki() {
        String[] names = {"Traits", "Elites", "Bosses", "Rejeitados"};
        Material[] icons = {Material.NETHER_STAR, Material.GOLDEN_APPLE, Material.WITHER_SKELETON_SKULL, Material.MOSS_BLOCK};
        int[] tabSlots = {10, 12, 14, 16};
        for (int i = 0; i < 4; i++) {
            int index = i;
            set(tabSlots[i], Items.of(icons[i], (wikiTab == i ? "<green><bold>" : "<white>") + names[i],
                    List.of(wikiTab == i ? "<gray>Aberta agora." : "<yellow>▶ Clique para abrir"), wikiTab == i), c -> {
                wikiTab = index;
                render();
            });
        }
        switch (wikiTab) {
            case 0 -> wikiTraits();
            case 1 -> wikiElites();
            case 2 -> wikiBosses();
            default -> wikiFauna();
        }
    }

    private void wikiFauna() {
        set(19, Items.of(Material.CYAN_WOOL, "<aqua><bold>Rascal",
                List.of("<gray>Mob Vote 2022. Criatura tímida das", "<gray>minas abandonadas, abaixo de Y=0.", "",
                        "<white>Esconde-esconde: <gray>olhe para ele de perto", "<gray>para achá-lo; ele foge e se esconde.",
                        "<white>Achado " + plugin.getConfig().getInt("fauna.rascal.finds-needed", 3) + " vezes: <gray>dá uma picareta",
                        "<gray>de ferro encantada e vai embora.", "", "<dark_gray>Não pode ser ferido."), true), null);
        set(21, Items.of(Material.TUFF, "<gray><bold>Golem de Tufo",
                List.of("<gray>Mob Vote 2022. Estátua que você constrói.", "",
                        "<white>Construção: <gray>tufo, tufo (ou um bloco de", "<gray>lã, para ganhar manto) e tufo cinzelado",
                        "<gray>no topo.", "<white>Clique com um item: <gray>ele exibe.",
                        "<gray>De tempos em tempos acorda, pega um item", "<gray>do chão e volta ao posto.", "",
                        "<dark_gray>Agache e bata para desmontar."), true), null);
        set(23, Items.of(Material.DANDELION, "<yellow><bold>Moobloom",
                List.of("<gray>Mob Vote 2020. Vaca amarela coberta", "<gray>de botões-de-ouro.", "",
                        "<white>Onde: <gray>florestas floridas e campos.", "<white>Deixa um rastro de flores, entrega",
                        "<gray>néctar às abelhas e pode ser tosquiada", "<gray>para render flores."), true), null);
        set(25, Items.of(Material.MOSS_BLOCK, "<green><bold>Glare",
                List.of("<gray>Mob Vote 2021. Criatura de folhagem das", "<gray>cavernas exuberantes.", "",
                        "<white>Aponta o escuro: <gray>paira ranzinza sobre", "<gray>pontos onde monstros podem nascer e se",
                        "<gray>acalma quando você acende uma luz.", "<white>Bagas brilhantes: <gray>ele passa a te", "<gray>acompanhar e vigiar o escuro."), true), null);
        set(31, Items.of(Material.BARRIER, "<white>Todos são pacíficos",
                List.of("<gray>Nenhum deles ataca ou fere jogadores.")), null);
    }

    private void wikiTraits() {
        int[] normalSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32};
        int[] eliteSlots = {38, 39, 40, 41, 42};
        int n = 0;
        int e = 0;
        for (Trait trait : Trait.values()) {
            int slot = trait.elite() ? eliteSlots[e++] : normalSlots[n++];
            set(slot, traitItem(trait, trait.elite(), null), null);
        }
        set(34, Items.of(Material.PAPER, "<white>Traits comuns",
                List.of("<gray>Um mob pode nascer com até dois,", "<gray>sorteados ao nascer. A chance",
                        "<gray>sobe em estruturas raras.", "",
                        "<gray>Chance base: <white>" + percent(plugin.getConfig().getDouble("mobs.trait-chance", 0.3)))),
                null);
        set(37, Items.of(Material.PAPER, "<gold>Traits de Elite",
                List.of("<gray>Exclusivos dos mobs Elite, que sempre", "<gray>nascem com um deles.")), null);
        set(43, Items.of(Material.SPYGLASS, "<white>Como ver os traits",
                List.of("<gray>Os símbolos aparecem ao lado do nome do", "<gray>mob e, ao atingi-lo, a linha de ação",
                        "<gray>mostra o nível e os nomes dos traits.")), null);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value * 100);
    }

    private void wikiElites() {
        var c = plugin.getConfig();
        set(20, Items.of(Material.GOLDEN_APPLE, "<gold><bold>O que é um Elite?",
                List.of("<gray>Versão mais forte de um mob hostil:", "",
                        "<white>• <gray>Escala <white>" + percent(c.getDouble("mobs.elite.scale", 1.2)),
                        "<white>• <gray>Vida <white>×" + c.getDouble("mobs.elite.health-multiplier", 2.0),
                        "<white>• <gray>Dano <white>×" + c.getDouble("mobs.elite.damage-multiplier", 1.3),
                        "<white>• <gray>1-2 traits comuns + <gold>1 trait de Elite",
                        "<white>• <gray>Nome dourado e barra de chefe ao lutar"), true), null);
        set(22, Items.of(Material.COMPASS, "<white>Quando nascem",
                List.of("<gray>Chance base: <white>" + percent(c.getDouble("mobs.elite.chance", 0.04)),
                        "<gray>Sobe em <white>estruturas raras</white>:",
                        "<dark_gray>chance × (1 + bônus da estrutura ÷ 6)", "",
                        "<gray>Um aviso aparece quando um Elite", "<gray>nasce por perto.")), null);
        set(24, Items.of(Material.ECHO_SHARD, "<gradient:#c4a7ff:#2cb67d><bold>Drops de Elite",
                List.of("<white>• <gray>Essência Elite <dark_gray>(" + percent(c.getDouble("mobs.elite.essence-chance", 0.6)) + ")",
                        "<white>• <gray>Pepitas de ouro e esmeraldas",
                        "<white>• <gray>Diamante <dark_gray>(nível 15+)",
                        "<white>• <gray>Fragmento de netherite <dark_gray>(nível 30+)",
                        "<white>• <gray>Maçã dourada encantada <dark_gray>(15%)", "",
                        "<gray>XP do mob <white>×" + c.getDouble("mobs.elite.xp-multiplier", 3.0))), null);
        set(29, Items.of(Material.MAP, "<white>Nível dos mobs",
                List.of("<gray>Sobe <white>1 nível a cada " + c.getInt("mobs.blocks-per-level", 300) + " blocos</white> do spawn.",
                        "<gray>Bônus por dimensão: Nether <white>+" + c.getInt("mobs.dimension-bonus.nether", 6)
                                + "</white>, End <white>+" + c.getInt("mobs.dimension-bonus.the-end", 12) + "</white>.",
                        "<gray>Profundezas: <white>+" + c.getInt("mobs.deep-bonus", 2) + "</white> abaixo de Y=0.",
                        "<gray>Estruturas raras: <white>até +14</white>.")), null);
        set(31, Items.of(Material.SHIELD, "<white>Dicas de combate",
                List.of("<gray>• <green>Espinhoso</green> reflete dano:", "<gray>  prefira ataques à distância.",
                        "<gray>• <white>Fantasma</white> teleporta ao ser atingido.",
                        "<gray>• <yellow>Explosivo</yellow> explode ao morrer: afaste-se.")), null);
        set(33, Items.of(Material.FURNACE, "<white>Para que serve a Essência",
                List.of("<gray>É usada nos níveis mais altos dos", "<gray>upgrades de fornalhas e baús.", "",
                        "<dark_gray>Sneak + clique com a mão vazia", "<dark_gray>em uma fornalha ou baú.")), null);
    }

    private void wikiBosses() {
        var c = plugin.getConfig();
        set(20, Items.of(Material.MAGMA_CREAM, "<gold><bold>Wildfire",
                List.of("<gray>O Blaze chefe removido da Mojang.", "",
                        "<white>Onde: <gray>Câmara do Wildfire, uma ala", "<gray>especial anexa às fortalezas do Nether.",
                        "<white>Vida: <red>" + c.getInt("bosses.wildfire.health", 450) + " ❤", "",
                        "<gold>Escudos giratórios <gray>bloqueiam projéteis;", "<gray>ataque de perto ou espere os escudos abrirem.", "",
                        "<gold>☄ Onda de Choque <gray>— pule a chama.",
                        "<gold>☄ Chuva de Bolas de Fogo <gray>— desvie.",
                        "<gold>☄ Investida Aérea <gray>— se errar, ele fica",
                        "<gray>exposto (+50% de dano); se agarrar, queima",
                        "<gray>e arremessa você."), true), null);
        set(24, Items.of(Material.HEART_OF_THE_SEA, "<aqua><bold>Iceologer",
                List.of("<gray>O saqueador do gelo removido da Mojang.", "",
                        "<white>Onde: <gray>Castelo de Gelo, em terras geladas.",
                        "<white>Vida: <red>" + c.getInt("bosses.iceologer.health", 380) + " ❤", "",
                        "<aqua>❄ Espinhos de Gelo <gray>— cinco espinhos", "<gray>lançados um a um (mais na fúria).",
                        "<aqua>❄ Iceberg <gray>— arranca o chão, carrega e", "<gray>arremessa na marca vermelha: saia dela!"),
                true), null);
        set(29, Items.of(Material.IRON_DOOR, "<white>A sala do boss",
                List.of("<gray>Ao entrar no salão o boss desperta e a", "<gray>porta é selada até a luta acabar.",
                        "<gray>Se todos caírem, o boss reinicia.")), null);
        set(31, Items.of(Material.PLAYER_HEAD, "<white>Em grupo",
                List.of("<gray>A vida cresce <white>60%</white> por jogador extra", "<gray>(até 4 jogadores).")), null);
        set(33, Items.of(Material.CHEST, "<white>Recompensas",
                List.of("<gray>Essência Elite, recursos raros e um", "<gray>troféu, além de XP de Luta.", "",
                        "<gray>Renasce em <white>" + c.getInt("bosses.respawn-minutes", 90) + " min</white>.")), null);
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
