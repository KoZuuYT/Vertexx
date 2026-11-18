package dev.vertexx.rpg;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Canvas;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Menu;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Ui;
import dev.vertexx.mob.Trait;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Menu largo do Vertexx (painel de 360 px desenhado pelo resource pack) com três páginas: Status, árvore de
 * Habilidades e Informações. Com o pacote, textos, barras e molduras vão no título (ver {@link Canvas});
 * sem ele, a mesma informação aparece nas dicas dos itens.
 */
public final class StatsGUI extends Menu {

    public enum Page {
        STATS(Ui.Theme.STATS, "Status", 0),
        TREE(Ui.Theme.TREE, "Habilidades", -1),
        INFO(Ui.Theme.INFO, "Informações", 1);

        private final Ui.Theme theme;
        private final String title;
        private final int panel;

        Page(Ui.Theme theme, String title, int panel) {
            this.theme = theme;
            this.title = title;
            this.panel = panel;
        }
    }

    /** Categorias da árvore, na ordem de tools/pack/ui2.py (índice do painel = 2 + posição). */
    private static final Stat[] CATEGORIES = {Stat.MINING, Stat.WOODCUTTING, Stat.EXCAVATION, Stat.FARMING,
            Stat.COMBAT, Stat.EXPLORATION, Stat.ACROBATICS, Stat.ENDURANCE};
    private static final String[] SHORT = {"MINERACAO", "ESCAVACAO", "LENHADOR", "FAZENDA", "LUTA", "ARQUEARIA",
            "VIGOR", "ACROBACIA", "PESCA", "EXPLORAR"};     // na ordem de Stat.values()
    private static final TextColor GREY = TextColor.color(0x9aa4b8);
    private static final TextColor GOLD = TextColor.color(0xffce50);
    private static final TextColor AQUA = TextColor.color(0x4cd6f0);
    private static final TextColor GREEN = TextColor.color(0x62e07a);
    private static final TextColor RED = TextColor.color(0xf05454);
    private static final TextColor WHITE = TextColor.color(0xe8eefc);
    private static final TextColor PURPLE = TextColor.color(0xb26aff);
    private static final int INFO_PAGES = 3;
    private static final int CLOSE = 8;
    private static final int BUY = 26;
    private static final int RESPEC = 53;
    /** Cabeça e pontos da página de status ficam na fileira de cima, livres das barras. */
    private static final int HEAD = 5;
    private static final int POINTS = 7;

    private final Page page;
    private final int info;
    private final int category;
    private final Skill selectedSkill;

    private StatsGUI(PluginMain plugin, Player player, Page page, int info, int category, Skill selected) {
        super(plugin, player, page.theme, title(plugin, player, page, info, category, selected));
        this.page = page;
        this.info = info;
        this.category = category;
        this.selectedSkill = selected;
    }

    public static void open(PluginMain plugin, Player player, Page page) {
        open(plugin, player, page, 0);
    }

    private static void open(PluginMain plugin, Player player, Page page, int info) {
        open(plugin, player, page, info, 0, null);
    }

    private static void open(PluginMain plugin, Player player, Page page, int info, int category, Skill selected) {
        if (!plugin.rpg().enabled()) {
            Msg.send(player, "<gray>O sistema de status está desativado neste servidor.");
            return;
        }
        new StatsGUI(plugin, player, page, info, category, selected).open();
    }

    private PlayerData data() {
        return plugin.rpg().data(player);
    }

    // ── Título: painel largo com textos, barras e molduras ─────

    private static List<Skill> skillsOf(Stat stat) {
        List<Skill> list = new ArrayList<>();
        for (Skill skill : Skill.values()) {
            if (skill.stat() == stat) {
                list.add(skill);
            }
        }
        return list;
    }

    private static TextColor tone(Stat stat) {
        return switch (stat) {
            case MINING -> AQUA;
            case EXCAVATION -> TextColor.color(0xf0b040);
            case WOODCUTTING -> GREEN;
            case FARMING -> TextColor.color(0xf0dc50);
            case COMBAT -> RED;
            case ARCHERY -> TextColor.color(0xe070f0);
            case ENDURANCE -> TextColor.color(0xc44646);
            case ACROBATICS -> WHITE;
            case FISHING -> TextColor.color(0x5a8cf0);
            case EXPLORATION -> TextColor.color(0x40bec8);
        };
    }

    private static boolean canBuy(PlayerData d, Skill skill) {
        boolean parentOk = skill.parent() == null || d.rank(skill.parent()) > 0;
        return parentOk && d.level(skill.stat()) >= skill.requiredLevel();
    }

    private static int nodeState(PlayerData d, Skill skill, boolean selected) {
        int rank = d.rank(skill);
        if (selected) {
            return 4;
        }
        if (rank >= skill.maxRank()) {
            return 6;
        }
        if (rank > 0) {
            return d.selected == skill ? 5 : 1;
        }
        return canBuy(d, skill) ? 2 : 3;
    }

    private static Component title(PluginMain plugin, Player player, Page page, int info, int category, Skill selected) {
        String plain = "<gradient:#7f5af0:#2cb67d><bold>Vertexx</bold></gradient> <dark_gray>» <gray>" + page.title;
        if (!packed(plugin, player)) {
            return Msg.mm(plain);
        }
        PlayerData d = plugin.rpg().data(player);
        Canvas c = Canvas.panel(page == Page.TREE ? 2 + category : page.panel);
        c.text(-84, 6, "VERTEXX", GOLD).text(-84 + Canvas.width("VERTEXX") + 4, 6, "/ " + page.title, GREY);
        for (int tab = 0; tab < 3; tab++) {
            c.slotFrame(tab, tab == page.ordinal() ? 4 : 0);
        }
        c.slotFrame(CLOSE, 0);
        double need = PlayerData.generalNeed(d.level);
        int cap = plugin.getConfig().getInt("rpg.max-stat-level", 100);
        switch (page) {
            case STATS -> {
                for (Stat s : Stat.values()) {
                    int i = s.ordinal();
                    int col = i < 5 ? 0 : 5;
                    int row = 1 + i % 5;
                    int cell = 17 + 18 * row;
                    int tx = 7 + 18 * col + 21;
                    int level = d.level(s);
                    c.slotFrame(row * 9 + col, level >= 10 ? 1 : 0);
                    c.text(tx, cell + 1, SHORT[i], tone(s));
                    c.bar(tx, cell + 9, level >= cap ? 1 : d.xp(s) / PlayerData.statNeed(level), tone(s));
                    c.text(tx + Canvas.BAR_W + 3, cell + 9, String.valueOf(level), WHITE);
                }
                c.slotFrame(HEAD, 0);
                c.slotFrame(POINTS, 2);
                c.text(-84, 24, "PONTOS", GREY).text(-84, 34, String.valueOf(d.points), AQUA);
                c.text(-84, 52, "GASTE NA", GREY).text(-84, 61, "ARVORE DE", GREY).text(-84, 70, "HABILIDADES", GREY);
                c.text(188, 24, "NIVEL GERAL", GREY).text(188, 34, String.valueOf(d.level), GOLD);
                c.bar(188, 44, d.xp / need, GOLD);
                c.text(188, 54, Math.round(d.xp) + "/" + Math.round(need), GREY);
            }
            case TREE -> {
                Stat cat = CATEGORIES[category];
                List<Skill> skills = skillsOf(cat);
                Skill sel = selected != null ? selected : skills.get(0);
                for (int i = 0; i < CATEGORIES.length; i++) {
                    c.slotFrame(45 + i, i == category ? 4 : 0);
                }
                c.slotFrame(RESPEC, 0);
                c.text(68, 23, cat.display(), tone(cat));
                c.text(8, 37, Canvas.fit("SOBE COM: " + cat.activity(), 166), GREY);
                c.text(8, 45, Canvas.fit("BONUS: " + cat.passive(), 166), GREEN);
                for (int i = 0; i < skills.size(); i++) {
                    Skill s = skills.get(i);
                    int cellX = 7 + 36 * i;
                    c.frame(cellX, 53, nodeState(d, s, s == sel));
                    int rank = d.rank(s);
                    TextColor color = rank >= s.maxRank() ? GOLD : rank > 0 ? GREEN : canBuy(d, s) ? GOLD : GREY;
                    String label = rank + "/" + s.maxRank();
                    c.text(cellX + 9 - Canvas.width(label) / 2, 73, label, color);
                    if (s.parent() != null && skills.contains(s.parent())) {
                        c.text(cellX - 12, 58, ">", d.rank(s.parent()) > 0 ? GREEN : GREY);
                    }
                }
                c.frame(7 + 18 * 8, 53, canBuy(d, sel) && d.points > 0 && d.rank(sel) < sel.maxRank() ? 2 : 3);
                TextColor sc = d.rank(sel) > 0 ? GREEN : canBuy(d, sel) ? GOLD : GREY;
                c.text(8, 82, sel.display(), sc);
                c.text(8 + Canvas.width(Canvas.ascii(sel.display())) + 6, 82, sel.active() ? "ATIVA" : "PASSIVA",
                        sel.active() ? PURPLE : GREY);
                c.text(8, 91, Canvas.fit(Canvas.ascii(sel.description()), 168), WHITE);
                boolean statOk = d.level(sel.stat()) >= sel.requiredLevel();
                boolean parentOk = sel.parent() == null || d.rank(sel.parent()) > 0;
                String req = "REQ: " + sel.stat().display() + " NV " + sel.requiredLevel()
                        + (sel.parent() != null ? " + " + sel.parent().display() : "");
                c.text(8, 100, Canvas.fit(Canvas.ascii(req), 168), statOk && parentOk ? GREEN : RED);
                c.text(-84, 24, "NIVEL GERAL", GREY).text(-84, 34, String.valueOf(d.level), GOLD);
                c.bar(-84, 44, d.xp / need, GOLD);
                c.text(-84, 54, Math.round(d.xp) + "/" + Math.round(need), GREY);
                c.text(-84, 76, "PONTOS", GREY).text(-84, 86, String.valueOf(d.points), AQUA);
                c.text(-84, 104, "HAB. ATIVA", GREY);
                c.text(-84, 113, d.selected == null ? "NENHUMA" : Canvas.fit(Canvas.ascii(d.selected.display()), 76),
                        d.selected == null ? GREY : PURPLE);
                int sl = d.level(cat);
                c.text(188, 24, "NIVEL", GREY).text(188, 34, String.valueOf(sl), tone(cat));
                c.bar(188, 44, sl >= cap ? 1 : d.xp(cat) / PlayerData.statNeed(sl), tone(cat));
                c.text(188, 54, Math.round(d.xp(cat)) + "/" + Math.round(PlayerData.statNeed(sl)), GREY);
            }
            case INFO -> {
                for (int slot : LIST_SLOTS) {
                    c.slotFrame(slot, 0);
                }
                if (info > 0) {
                    c.slotFrame(BACK_SLOT, 2);
                }
                if (info < INFO_PAGES - 1) {
                    c.slotFrame(NEXT_SLOT, 2);
                }
                c.text(188, 24, "PAGINA " + (info + 1) + "/" + INFO_PAGES, GREY);
            }
        }
        return c.build();
    }

    @Override
    protected void render() {
        tab(0, Page.STATS, Material.BOOK, "tab_stats", "<green>Status", "Seus níveis em cada atividade.");
        tab(1, Page.TREE, Material.NETHER_STAR, "tab_tree", "<aqua>Habilidades", "Gaste pontos na árvore.");
        tab(2, Page.INFO, Material.KNOWLEDGE_BOOK, "tab_info", "<yellow>Informações", "Como o sistema funciona.");
        set(CLOSE, Items.of(Material.BARRIER, "<red>Fechar", List.of()), c -> player.closeInventory());
        switch (page) {
            case STATS -> stats();
            case TREE -> tree();
            case INFO -> info();
        }
    }

    private void tab(int slot, Page target, Material material, String model, String name, String hint) {
        boolean current = target == page;
        set(slot, icon(material, model, name, List.of("<gray>" + hint, "", current ? "<dark_gray>Página atual"
                : "<yellow>▶ Clique para abrir"), current), c -> {
            if (!current) {
                open(plugin, player, target, 0);
            }
        });
    }

    // ── Página 1: Status ───────────────────────────────────────

    private void stats() {
        PlayerData d = data();
        int cap = plugin.getConfig().getInt("rpg.max-stat-level", 100);
        for (Stat stat : Stat.values()) {
            int i = stat.ordinal();
            int slot = (1 + i % 5) * 9 + (i < 5 ? 0 : 5);
            int level = d.level(stat);
            double need = PlayerData.statNeed(level);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Sobe com: <white>" + stat.activity());
            lore.add("");
            lore.add("<gray>Nível: " + stat.color() + level + "<dark_gray>/<gray>" + cap);
            if (level < cap) {
                lore.add(RpgManager.bar(d.xp(stat) / need, 14));
                lore.add("<gray>XP: <white>" + Math.round(d.xp(stat)) + "<dark_gray>/<white>" + Math.round(need));
            } else {
                lore.add("<gold>Nível máximo!");
            }
            lore.add("");
            lore.add("<gray>Bônus: <white>" + stat.passive());
            set(slot, Items.of(stat.icon(), stat.colored(), lore, level >= 10), null);
        }
        double need = PlayerData.generalNeed(d.level);
        ItemStack head = Items.of(Material.PLAYER_HEAD, "<white><bold>" + Msg.esc(player.getName()),
                List.of("<gray>Nível geral: <gold>" + d.level, "<gray>Pontos de habilidade: <aqua>" + d.points,
                        RpgManager.bar(d.xp / need, 16),
                        "<gray>XP: <white>" + Math.round(d.xp) + "<dark_gray>/<white>" + Math.round(need)));
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(player));
        set(HEAD, head, null);
        set(POINTS, icon(Material.EXPERIENCE_BOTTLE, "ui_star", "<aqua><bold>" + d.points + " ponto(s)",
                List.of("<gray>Gaste na árvore de habilidades.", "", "<yellow>▶ Clique para abrir"), d.points > 0),
                c -> open(plugin, player, Page.TREE, 0));
    }

    // ── Página 2: Árvore ───────────────────────────────────────

    private void tree() {
        PlayerData d = data();
        Stat cat = CATEGORIES[category];
        List<Skill> skills = skillsOf(cat);
        Skill sel = selectedSkill != null ? selectedSkill : skills.get(0);
        for (int i = 0; i < CATEGORIES.length; i++) {
            int index = i;
            Stat s = CATEGORIES[i];
            set(45 + i, Items.of(s.icon(), s.colored(), List.of("<gray>" + skillsOf(s).size() + " habilidade(s)",
                    "", i == category ? "<dark_gray>Categoria atual" : "<yellow>▶ Clique para abrir"), i == category),
                    c -> open(plugin, player, Page.TREE, 0, index, null));
        }
        for (int i = 0; i < skills.size(); i++) {
            Skill skill = skills.get(i);
            int rank = d.rank(skill);
            boolean parentOk = skill.parent() == null || d.rank(skill.parent()) > 0;
            boolean statOk = d.level(skill.stat()) >= skill.requiredLevel();
            boolean unlockable = parentOk && statOk;
            String color = rank > 0 ? "<green>" : unlockable ? "<yellow>" : "<gray>";
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + skill.description());
            for (String line : skill.details()) {
                lore.add("<dark_gray>• <gray>" + line);
            }
            lore.add("");
            lore.add("<gray>Nível: <white>" + rank + "<dark_gray>/<white>" + skill.maxRank()
                    + (skill.active() ? " <dark_gray>• <aqua>ativa" : " <dark_gray>• <green>passiva"));
            lore.add((statOk ? "<green>✔ " : "<red>✘ ") + "<gray>" + skill.stat().display() + " nível " + skill.requiredLevel());
            if (skill.parent() != null) {
                lore.add((parentOk ? "<green>✔ " : "<red>✘ ") + "<gray>Requer " + skill.parent().display());
            }
            lore.add("");
            lore.add("<yellow>▶ Clique: ver detalhes");
            lore.add("<yellow>▶ Shift + clique: gastar 1 ponto");
            if (skill.active() && rank > 0) {
                lore.add(d.selected == skill ? "<aqua>★ Habilidade selecionada" : "<yellow>▶ Clique direito: selecionar");
            }
            Material material = unlockable || rank > 0 ? skill.icon() : Material.GRAY_DYE;
            ItemStack item = Items.of(material, color + skill.display() + (rank > 0 ? " <gray>[" + rank + "]" : ""),
                    lore, rank > 0);
            if (material == Material.GRAY_DYE) {
                Ui.model(pack, item, "ui_lock");
            }
            set(18 + 2 * i, item, click -> treeClick(skill, click));
        }
        boolean can = canBuy(d, sel) && d.points > 0 && d.rank(sel) < sel.maxRank();
        set(BUY, icon(can ? Material.EMERALD : Material.GRAY_DYE, can ? "ui_plus" : "ui_lock",
                can ? "<green><bold>Gastar 1 ponto" : "<gray>Sem como comprar",
                List.of("<gray>Habilidade: <white>" + sel.display(),
                        "<gray>Pontos: <aqua>" + d.points,
                        d.rank(sel) >= sel.maxRank() ? "<gold>Nível máximo." : can ? "<yellow>▶ Clique para comprar"
                                : "<dark_gray>Requisitos ou pontos faltando."), can),
                click -> buy(sel));
        int cost = plugin.getConfig().getInt("rpg.respec-cost-levels", 5);
        set(RESPEC, Items.of(Material.LAVA_BUCKET, "<red>Redefinir habilidades",
                List.of("<gray>Devolve todos os pontos gastos.",
                        "<gray>Custa <white>" + cost + " níveis de XP<gray>.",
                        "",
                        "<yellow>▶ Shift + clique para confirmar")), click -> {
            if (!click.isShiftClick()) {
                Msg.send(player, "<gray>Use <white>Shift + clique</white> para confirmar a redefinição.");
                return;
            }
            String error = plugin.rpg().reset(player);
            if (error != null) {
                Fx.deny(player);
                Msg.send(player, "<red>" + error);
            } else {
                Msg.send(player, "<green>Habilidades redefinidas. Pontos devolvidos.");
                Fx.success(player);
            }
            reopen(sel);
        });
    }

    private void reopen(Skill sel) {
        open(plugin, player, Page.TREE, 0, category, sel);
    }

    private void buy(Skill skill) {
        String error = plugin.rpg().buy(player, skill);
        if (error != null) {
            Fx.deny(player);
            Msg.actionBar(player, "<red>" + error);
        } else {
            Fx.success(player);
        }
        reopen(skill);
    }

    private void treeClick(Skill skill, ClickType click) {
        PlayerData d = data();
        if (click.isRightClick()) {
            if (skill.active() && d.rank(skill) > 0) {
                plugin.rpg().select(player, skill);
                Fx.select(player);
            }
            reopen(skill);
            return;
        }
        if (click.isShiftClick()) {
            buy(skill);
            return;
        }
        reopen(skill);
    }

    // ── Página 3: Informações ──────────────────────────────────

    private void info() {
        switch (info) {
            case 0 -> infoOverview();
            case 1 -> infoTraits();
            default -> infoUpgrades();
        }
        if (info > 0) {
            set(BACK_SLOT, Items.of(Material.ARROW, "<yellow>◀ Anterior", List.of()),
                    c -> open(plugin, player, Page.INFO, info - 1));
        }
        if (info < INFO_PAGES - 1) {
            set(NEXT_SLOT, Items.of(Material.ARROW, "<yellow>Próxima ▶",
                    List.of("<dark_gray>Página " + (info + 1) + " de " + INFO_PAGES)),
                    c -> open(plugin, player, Page.INFO, info + 1));
        }
    }

    private void infoOverview() {
        set(11, Items.of(Material.NETHER_STAR, "<gold>Nível geral e pontos",
                List.of("<gray>Todo XP de atividade soma no seu", "<gray>nível geral. Cada nível dá 1 ponto de",
                        "<gray>habilidade (2 a cada 5 níveis).", "",
                        "<gray>Os status sobem de forma independente,", "<gray>e cada um dá um bônus passivo.")), null);
        set(12, Items.of(Material.DIAMOND_PICKAXE, "<aqua>Fontes de XP",
                statSources()), null);
        set(13, Items.of(Material.COMPARATOR, "<white>Controles",
                List.of("<yellow>Shift + botão direito <gray>usa a habilidade ativa.", "<yellow>Shift + botão esquerdo <gray>troca (no ar).",
                        "<yellow>Agachado + quebrar <gray>usa Veeiro e Machadeiro.", "",
                        "<gray>Habilidades ativas: <white>Investida, Pico Largo,", "<white>Golpe Atordoante<gray>.",
                        "<dark_gray>Nenhuma habilidade afeta jogadores.")), null);
        set(14, Items.of(Material.ZOMBIE_HEAD, "<red>Mobs nivelados",
                List.of("<gray>Mobs hostis ganham nível conforme:", "<dark_gray>• <white>distância do spawn <gray>(300 blocos por nível)",
                        "<dark_gray>• <white>dimensão <gray>(Nether e End são mais fortes)",
                        "<dark_gray>• <white>profundidade <gray>(abaixo de Y=0)",
                        "<dark_gray>• <white>estruturas <gray>(as raras rendem mais)", "",
                        "<gray>Mais nível = mais vida, dano e XP.")), null);
        set(15, Items.of(Material.WITHER_SKELETON_SKULL, "<gold>Mobs Elite",
                List.of("<gray>Raros e maiores, com traits melhores,", "<gray>barra de chefe e vida dobrada.", "",
                        "<gray>Dropam <white>ouro<gray>, <white>esmeraldas<gray>, itens raros e a",
                        "<gradient:#c4a7ff:#2cb67d>Essência Elite</gradient><gray>.")), null);
        set(21, Items.of(Material.ECHO_SHARD, "<gradient:#c4a7ff:#2cb67d>Essência Elite",
                List.of("<gray>Dropa de mobs Elite.", "<gray>Necessária nos upgrades mais altos", "<gray>de fornalhas e baús.")), null);
        set(22, Items.of(Material.NAME_TAG, "<light_purple>Traits",
                List.of("<gray>Poderes especiais de mobs. Veja a lista", "<gray>completa na próxima página.", "",
                        "<gray>Ao bater em um mob, a barra de ação", "<gray>mostra o nível e os traits dele.")), null);
        set(23, Items.of(Material.ANVIL, "<gold>Upgrades",
                List.of("<gray>Agache e clique com a <white>mão vazia</white> em", "<gray>uma fornalha ou baú para melhorá-lo.",
                        "<gray>Veja os custos na última página.")), null);
    }

    private List<String> statSources() {
        List<String> lines = new ArrayList<>();
        for (Stat stat : Stat.values()) {
            lines.add(stat.color() + stat.display() + "<dark_gray>: <gray>" + stat.activity());
        }
        return lines;
    }

    private void infoTraits() {
        int i = 0;
        for (Trait trait : Trait.values()) {
            if (i >= LIST_SLOTS.length) {
                break;
            }
            set(LIST_SLOTS[i++], Items.of(trait.elite() ? Material.NETHER_STAR : Material.NAME_TAG,
                    trait.color() + trait.symbol() + " " + trait.display(),
                    List.of("<gray>" + trait.description(), "",
                            trait.elite() ? "<gold>Apenas mobs Elite" : "<dark_gray>Pode aparecer em qualquer mob"),
                    trait.elite()), null);
        }
    }

    private void infoUpgrades() {
        plugin.upgrades().describe(this::setInfo);
    }

    private void setInfo(int slot, ItemStack item) {
        set(slot, item, null);
    }
}
