package dev.vertexx.kingdom;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Ui;
import dev.vertexx.honor.HonorCommand;
import dev.vertexx.honor.HonorManager;
import dev.vertexx.kingdom.KingdomManager.Member;
import dev.vertexx.waystone.Waystone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Painel do reino: status detalhado (membros, honra, Waystones, prestígio e ranking), lista de
 * membros com promoção pelo líder e diplomacia com alianças. Decorado com as cores do reino.
 */
public final class KingdomPanelGUI implements InventoryHolder {

    private enum Page {
        OVERVIEW("Status"), MEMBERS("Membros"), DIPLOMACY("Diplomacia");

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
    private Kingdom kingdom;
    private Page page = Page.OVERVIEW;
    private int listPage;

    public KingdomPanelGUI(PluginMain plugin, Player player, Kingdom kingdom) {
        this.plugin = plugin;
        this.player = player;
        this.kingdom = kingdom;
        this.pack = plugin.resourcePack().isLoaded(player);
        this.inventory = Bukkit.createInventory(this, SIZE,
                Ui.title(pack, Ui.Theme.of(kingdom), "<bold>" + kingdom.styled() + "</bold> <dark_gray>» <gray>Painel do reino"));
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

    public void handleClick(int slot, ClickType click) {
        Consumer<ClickType> action = actions.get(slot);
        if (action != null) {
            Fx.click(player);
            action.accept(click);
        }
    }

    private KingdomManager manager() {
        return plugin.kingdoms();
    }

    private Kingdom own() {
        return manager().kingdomOf(player.getUniqueId());
    }

    private boolean isLeaderOfThis() {
        return own() == kingdom && manager().rankOf(player.getUniqueId()) == Rank.LIDER;
    }

    private void go(Page target) {
        page = target;
        listPage = 0;
        render();
    }

    private void set(int slot, ItemStack item, Consumer<ClickType> action) {
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    // ── Dados ──────────────────────────────────────────────────

    private record Stats(int members, int online, int leaders, int officers, int totalHonor, int waystones,
                         int visits, int allies, int prestige, String topName, int topHonor) {
    }

    private Stats stats(Kingdom target) {
        Map<UUID, Member> members = manager().membersOf(target);
        int online = 0;
        int leaders = 0;
        int officers = 0;
        int totalHonor = 0;
        int topHonor = Integer.MIN_VALUE;
        String topName = "—";
        for (Map.Entry<UUID, Member> entry : members.entrySet()) {
            if (Bukkit.getPlayer(entry.getKey()) != null) {
                online++;
            }
            switch (entry.getValue().rank()) {
                case LIDER -> leaders++;
                case OFICIAL -> officers++;
                default -> {
                }
            }
            int honor = plugin.honor().get(entry.getKey());
            totalHonor += honor;
            if (honor > topHonor) {
                topHonor = honor;
                topName = entry.getValue().name();
            }
        }
        int waystones = 0;
        int visits = 0;
        for (Waystone waystone : plugin.waystones().all()) {
            if (manager().kingdomOf(waystone.owner()) == target) {
                waystones++;
                visits += waystone.uses();
            }
        }
        int allies = manager().alliesOf(target).size();
        int prestige = members.size() * 10 + waystones * 5 + Math.max(0, totalHonor) / 5 + allies * 25
                + manager().honor(target) / 4;
        return new Stats(members.size(), online, leaders, officers, totalHonor, waystones, visits, allies, prestige,
                topName, topHonor == Integer.MIN_VALUE ? 0 : topHonor);
    }

    private int rankingOf(Kingdom target) {
        int mine = stats(target).prestige();
        int position = 1;
        for (Kingdom other : Kingdom.values()) {
            if (other != target && stats(other).prestige() > mine) {
                position++;
            }
        }
        return position;
    }

    // ── Renderização ───────────────────────────────────────────

    private void frame() {
        actions.clear();
        ItemStack theme = Items.filler(kingdom.pane());
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
        set(CLOSE_SLOT, Items.of(Material.BARRIER, "<red>Fechar", List.of()), c -> player.closeInventory());
        if (page == Page.OVERVIEW) {
            set(BACK_SLOT, Items.of(Material.ARROW, "<yellow>◀ Voltar ao painel", List.of()),
                    c -> plugin.panel().open(player));
        } else {
            set(BACK_SLOT, Items.of(Material.ARROW, "<yellow>◀ Voltar ao status", List.of()), c -> go(Page.OVERVIEW));
        }
    }

    private void render() {
        frame();
        switch (page) {
            case OVERVIEW -> overview();
            case MEMBERS -> members();
            case DIPLOMACY -> diplomacy();
        }
        Ui.skin(pack, inventory);
    }

    private void header(String subtitle) {
        List<String> lore = new ArrayList<>(kingdom.description());
        lore.add("");
        lore.add("<dark_gray>" + subtitle);
        set(4, Ui.emblem(pack, Items.of(kingdom.icon(), "<bold>" + kingdom.styled(), lore, true), kingdom), null);
    }

    private void overview() {
        header("Status do reino");
        Stats s = stats(kingdom);
        int ranking = rankingOf(kingdom);
        boolean mine = own() == kingdom;

        set(20, Items.of(Material.PLAYER_HEAD, "<gradient:#c4a7ff:#2cb67d>Membros",
                List.of("<gray>Total: <white>" + s.members() + " <dark_gray>(" + s.online() + " online)",
                        "<gray>Líderes: <white>" + s.leaders(),
                        "<gray>Oficiais: <white>" + s.officers(),
                        "<gray>Membros: <white>" + Math.max(0, s.members() - s.leaders() - s.officers()),
                        "",
                        "<yellow>▶ Clique para ver a lista")), c -> go(Page.MEMBERS));
        int average = s.members() == 0 ? 0 : Math.round((float) s.totalHonor() / s.members());
        int kingdomHonor = manager().honor(kingdom);
        set(21, Items.of(Material.GOLDEN_HELMET, "<gradient:#c4a7ff:#2cb67d>Honra do reino",
                List.of("<gray>Do reino: " + KingdomManager.honorBar(kingdomHonor) + " <white>" + kingdomHonor
                                + " <dark_gray>(" + KingdomManager.honorLabel(kingdomHonor) + "<dark_gray>)",
                        "<dark_gray>Cai com mercenários, traições e saques;",
                        "<dark_gray>se recompõe aos poucos.",
                        "",
                        "<gray>Média dos membros: " + HonorCommand.bar(average) + " <white>" + average,
                        "<gray>Nível médio: " + HonorManager.Tier.of(average).styled(),
                        "<gray>Mais honrado: <white>" + Msg.esc(s.topName()) + " <dark_gray>(" + s.topHonor() + ")")), null);
        set(22, Items.of(Material.LODESTONE, "<gradient:#c4a7ff:#2cb67d>Waystones do reino",
                List.of("<gray>Erguidos por membros: <white>" + s.waystones(),
                        "<gray>Viagens realizadas: <white>" + s.visits(),
                        "",
                        "<dark_gray>Conte com eles para cruzar o mundo.")), null);
        List<String> allies = new ArrayList<>();
        for (Kingdom ally : manager().alliesOf(kingdom)) {
            allies.add(ally.styled());
        }
        set(23, Items.of(Material.WHITE_BANNER, "<gradient:#c4a7ff:#2cb67d>Diplomacia",
                List.of("<gray>Aliados: " + (allies.isEmpty() ? "<dark_gray>nenhum" : String.join("<gray>, ", allies)),
                        "",
                        "<yellow>▶ Clique para ver relações")), c -> go(Page.DIPLOMACY));
        String medal = ranking == 1 ? "<gold>🏆 1º lugar" : ranking == 2 ? "<white>2º lugar" : "<#cd7f32>3º lugar";
        set(24, Items.of(Material.NETHER_STAR, "<gradient:#c4a7ff:#2cb67d>Prestígio",
                List.of("<gray>Pontos: <white>" + s.prestige(),
                        "<gray>Ranking: " + medal + " <dark_gray>de " + Kingdom.values().length,
                        "",
                        "<dark_gray>10 por membro, 5 por Waystone, 25 por aliança,",
                        "<dark_gray>1 a cada 5 de honra dos membros e 1 a cada 4",
                        "<dark_gray>de honra do reino."), true), null);

        String notice = manager().notice(kingdom);
        List<String> board = new ArrayList<>();
        if (notice == null) {
            board.add("<dark_gray>O líder ainda não fixou um aviso.");
        } else {
            for (String line : wrap(notice, 34)) {
                board.add("<white>" + Msg.esc(line));
            }
        }
        board.add("");
        board.add(isLeaderOfThis() ? "<yellow>Defina com <white>/reino anuncio <texto></white>"
                : "<dark_gray>Só o líder pode alterar o mural.");
        set(30, Items.of(Material.WRITABLE_BOOK, "<gradient:#c4a7ff:#2cb67d>Mural do reino", board), null);

        List<String> leadership = new ArrayList<>();
        for (Rank rank : new Rank[]{Rank.LIDER, Rank.OFICIAL}) {
            for (Map.Entry<UUID, Member> entry : manager().membersOf(kingdom).entrySet()) {
                if (entry.getValue().rank() == rank) {
                    boolean online = Bukkit.getPlayer(entry.getKey()) != null;
                    leadership.add((online ? "<green>● " : "<dark_gray>● ") + "<white>" + Msg.esc(entry.getValue().name())
                            + " <dark_gray>— <gray>" + rank.displayName());
                }
            }
        }
        if (leadership.isEmpty()) {
            leadership.add("<dark_gray>Sem líder nem oficiais ainda.");
        }
        set(32, Items.of(Material.GOLDEN_SWORD, "<gradient:#c4a7ff:#2cb67d>Liderança", leadership), null);

        if (mine) {
            Member self = manager().member(player.getUniqueId());
            set(40, Items.of(Material.PLAYER_HEAD, "<white>" + Msg.esc(player.getName()),
                    List.of("<gray>Seu cargo: <white>" + self.rank().displayName(),
                            "<gray>Honra: " + HonorCommand.bar(plugin.honor().get(player.getUniqueId())))), null);
            set(42, Items.of(Material.IRON_DOOR, "<red><bold>Deixar o reino",
                    List.of("<gray>Transferência por aliança, traição",
                            "<gray>ou vida de fora da lei (mercenário).", "",
                            "<yellow>▶ Clique para ver as opções")),
                    c -> new KingdomSwitchGUI(plugin, player).open());
        } else {
            set(40, Items.of(Material.SPYGLASS, "<gray>Você está só observando",
                    List.of("<dark_gray>Este não é o seu reino.")), null);
        }
    }

    private void members() {
        header("Membros do reino");
        List<Map.Entry<UUID, Member>> sorted = new ArrayList<>(manager().membersOf(kingdom).entrySet());
        sorted.sort(Comparator
                .comparing((Map.Entry<UUID, Member> e) -> e.getValue().rank()).reversed()
                .thenComparing(e -> Bukkit.getPlayer(e.getKey()) == null)
                .thenComparing(e -> e.getValue().name().toLowerCase()));
        int perPage = LIST_SLOTS.length;
        int pages = Math.max(1, (sorted.size() + perPage - 1) / perPage);
        listPage = Math.min(listPage, pages - 1);
        boolean leader = isLeaderOfThis();
        for (int i = 0; i < perPage && listPage * perPage + i < sorted.size(); i++) {
            Map.Entry<UUID, Member> entry = sorted.get(listPage * perPage + i);
            Member member = entry.getValue();
            boolean online = Bukkit.getPlayer(entry.getKey()) != null;
            int honor = plugin.honor().get(entry.getKey());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Cargo: <white>" + member.rank().displayName());
            lore.add("<gray>Estado: " + (online ? "<green>online" : "<dark_gray>offline"));
            lore.add("<gray>Honra: " + HonorCommand.bar(honor) + " <white>" + honor);
            lore.add("<gray>Nível: " + HonorManager.Tier.of(honor).styled());
            boolean canManage = leader && member.rank() != Rank.LIDER;
            if (canManage) {
                lore.add("");
                lore.add(member.rank() == Rank.MEMBRO ? "<yellow>▶ Esquerdo: promover a Oficial"
                        : "<yellow>▶ Direito: rebaixar a Membro");
            }
            ItemStack head = Items.of(Material.PLAYER_HEAD,
                    (online ? "<green>" : "<gray>") + Msg.esc(member.name()), lore, member.rank() == Rank.LIDER);
            head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.getKey())));
            set(LIST_SLOTS[i], head, canManage ? click -> changeRank(entry.getKey(), member, click) : null);
        }
        if (sorted.isEmpty()) {
            set(22, Items.of(Material.COMPASS, "<gray>Nenhum membro ainda", List.of()), null);
        }
        if (pages > 1) {
            set(NEXT_SLOT, Items.of(Material.ARROW, "<yellow>Próxima página ▶",
                    List.of("<dark_gray>Página " + (listPage + 1) + " de " + pages)), c -> {
                listPage = (listPage + 1) % pages;
                render();
            });
        }
    }

    private void changeRank(UUID id, Member member, ClickType click) {
        if (!isLeaderOfThis()) {
            return;
        }
        Rank target;
        if (member.rank() == Rank.MEMBRO && !click.isRightClick()) {
            target = Rank.OFICIAL;
        } else if (member.rank() == Rank.OFICIAL && click.isRightClick()) {
            target = Rank.MEMBRO;
        } else {
            return;
        }
        manager().set(id, member.name(), member.kingdom(), target);
        Fx.select(player);
        Msg.send(player, "<green><name> agora é <white>" + target.displayName() + "</white>.",
                Placeholder.unparsed("name", member.name()));
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            Msg.send(online, "<gray>Seu cargo no reino agora é <white>" + target.displayName() + "</white>.");
            Fx.success(online);
        }
        render();
    }

    private void diplomacy() {
        header("Diplomacia");
        Kingdom own = own();
        boolean leader = own != null && manager().rankOf(player.getUniqueId()) == Rank.LIDER;
        int[] slots = {20, 22, 24};
        int index = 0;
        for (Kingdom other : Kingdom.values()) {
            List<String> lore = new ArrayList<>();
            String status;
            if (other == own) {
                status = "<green>Seu reino";
            } else if (own == null) {
                status = "<gray>—";
            } else if (manager().allied(own, other)) {
                status = "<green>Aliado";
            } else if (manager().proposed(own, other)) {
                status = "<yellow>Proposta enviada";
            } else if (manager().proposed(other, own)) {
                status = "<gold>Proposta recebida";
            } else {
                status = "<gray>Neutro";
            }
            lore.add("<gray>Relação: " + status);
            lore.add("<gray>Membros: <white>" + manager().count(other));
            lore.add("<gray>Prestígio: <white>" + stats(other).prestige());
            lore.add("");
            if (own != null && other != own) {
                if (leader) {
                    boolean allied = manager().allied(own, other);
                    boolean received = manager().proposed(other, own);
                    lore.add(allied ? "<dark_gray>▶ Esquerdo: aliança já firmada"
                            : received ? "<yellow>▶ Esquerdo: aceitar a aliança" : "<yellow>▶ Esquerdo: propor aliança");
                    lore.add("<red>▶ Direito: " + (allied ? "romper aliança <dark_gray>("
                            + plugin.getConfig().getInt("honor.alliance-break", -20) + " de honra)" : "retirar proposta"));
                } else {
                    lore.add("<dark_gray>Só o líder trata de alianças.");
                }
            }
            lore.add("<dark_gray>▶ Shift + clique: ver o status deste reino");
            Kingdom target = other;
            set(slots[index++], Ui.emblem(pack, Items.of(other.icon(), "<bold>" + other.styled(), lore, other == own), other),
                    click -> {
                if (click.isShiftClick()) {
                    new KingdomPanelGUI(plugin, player, target).open();
                    return;
                }
                if (own == null || target == own || !leader) {
                    return;
                }
                String command = (click.isRightClick() ? "reino romper " : "reino aliar ") + target.id();
                Bukkit.dispatchCommand(player, command);
                Bukkit.getScheduler().runTask(plugin, this::render);
            });
        }
        boolean friendly = plugin.getConfig().getBoolean("combat.friendly-fire", false);
        set(31, Items.of(Material.IRON_SWORD, "<gradient:#c4a7ff:#2cb67d>Regras de combate",
                List.of("<gray>Fogo amigo entre aliados: " + (friendly ? "<red>ligado" : "<green>desligado"),
                        "<gray>Membros do mesmo reino e de reinos",
                        "<gray>aliados não se ferem.")), null);
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (current.length() + word.length() + 1 > width && current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }
}
