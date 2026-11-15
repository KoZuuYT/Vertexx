package dev.vertexx.rpg;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Menu;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Ui;
import dev.vertexx.mob.Trait;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Menu especial do Vertexx com três páginas, cada uma com o próprio fundo no resource pack:
 * Status, Árvore de Habilidades e Informações do sistema.
 */
public final class StatsGUI extends Menu {

    public enum Page {
        STATS(Ui.Theme.STATS, "Status"),
        TREE(Ui.Theme.TREE, "Habilidades"),
        INFO(Ui.Theme.INFO, "Informações");

        private final Ui.Theme theme;
        private final String title;

        Page(Ui.Theme theme, String title) {
            this.theme = theme;
            this.title = title;
        }
    }

    private static final int INFO_PAGES = 3;

    private final Page page;
    private final int info;

    private StatsGUI(PluginMain plugin, Player player, Page page, int info) {
        super(plugin, player, page.theme,
                "<gradient:#7f5af0:#2cb67d><bold>Vertexx</bold></gradient> <dark_gray>» <gray>" + page.title);
        this.page = page;
        this.info = info;
    }

    public static void open(PluginMain plugin, Player player, Page page) {
        open(plugin, player, page, 0);
    }

    private static void open(PluginMain plugin, Player player, Page page, int info) {
        if (!plugin.rpg().enabled()) {
            Msg.send(player, "<gray>O sistema de status está desativado neste servidor.");
            return;
        }
        new StatsGUI(plugin, player, page, info).open();
    }

    private PlayerData data() {
        return plugin.rpg().data(player);
    }

    @Override
    protected void render() {
        frame(null);
        tab(2, Page.STATS, Material.BOOK, "tab_stats", "<green>Status", "Seus níveis em cada atividade.");
        tab(4, Page.TREE, Material.NETHER_STAR, "tab_tree", "<aqua>Habilidades", "Gaste pontos na árvore.");
        tab(6, Page.INFO, Material.KNOWLEDGE_BOOK, "tab_info", "<yellow>Informações", "Como o sistema funciona.");
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
        int[] slots = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};
        int cap = plugin.getConfig().getInt("rpg.max-stat-level", 100);
        for (Stat stat : Stat.values()) {
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
            set(slots[stat.ordinal()], Items.of(stat.icon(), stat.colored(), lore, level >= 10), null);
        }

        ItemStack head = Items.of(Material.PLAYER_HEAD, "<white><bold>" + Msg.esc(player.getName()),
                List.of("<gray>Nível geral: <gold>" + d.level, "<gray>Pontos de habilidade: <aqua>" + d.points));
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(player));
        set(30, head, null);

        double need = PlayerData.generalNeed(d.level);
        set(31, icon(Material.NETHER_STAR, "ui_star", "<gold><bold>Nível geral " + d.level,
                List.of(RpgManager.bar(d.xp / need, 16),
                        "<gray>XP: <white>" + Math.round(d.xp) + "<dark_gray>/<white>" + Math.round(need),
                        "",
                        "<gray>Todo XP de atividade também enche",
                        "<gray>esta barra. A cada nível: <aqua>pontos",
                        "<gray>(2 a cada 5 níveis)."), true), null);
        set(32, icon(Material.EXPERIENCE_BOTTLE, "ui_star", "<aqua><bold>" + d.points + " ponto(s)",
                List.of("<gray>Gaste na árvore de habilidades.", "", "<yellow>▶ Clique para abrir"), d.points > 0),
                c -> open(plugin, player, Page.TREE, 0));
    }

    // ── Página 2: Árvore ───────────────────────────────────────

    private void tree() {
        PlayerData d = data();
        for (Skill skill : Skill.values()) {
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
            if (rank < skill.maxRank()) {
                lore.add(unlockable && d.points > 0 ? "<yellow>▶ Clique esquerdo: gastar 1 ponto"
                        : "<dark_gray>Não é possível comprar agora.");
            } else {
                lore.add("<gold>Nível máximo.");
            }
            if (skill.active() && rank > 0) {
                lore.add(d.selected == skill ? "<aqua>★ Habilidade selecionada"
                        : "<yellow>▶ Clique direito: selecionar");
            }
            Material material = unlockable || rank > 0 ? skill.icon() : Material.GRAY_DYE;
            ItemStack item = Items.of(material, color + skill.display() + (rank > 0 ? " <gray>[" + rank + "]" : ""),
                    lore, rank > 0);
            if (material == Material.GRAY_DYE) {
                Ui.model(pack, item, "ui_lock");
            }
            set(skill.slot(), item, click -> treeClick(skill, click));
        }

        set(38, icon(Material.EXPERIENCE_BOTTLE, "ui_star", "<aqua><bold>" + d.points + " ponto(s)",
                List.of("<gray>Sobem de nível geral (" + d.level + ").",
                        "<gray>1 ponto = 1 nível de habilidade."), d.points > 0), null);
        set(40, Items.of(Material.COMPARATOR, "<white>Como usar",
                List.of("<gray>Habilidade ativa selecionada: <white>" + (d.selected == null ? "nenhuma" : d.selected.display()),
                        "",
                        "<yellow>F <gray>usa a habilidade selecionada.",
                        "<yellow>Shift + F <gray>troca de habilidade.",
                        "<yellow>Agachado <gray>ativa Veeiro e Machadeiro.")), null);
        int cost = plugin.getConfig().getInt("rpg.respec-cost-levels", 5);
        set(42, Items.of(Material.LAVA_BUCKET, "<red>Redefinir habilidades",
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
            refresh();
        });
    }

    private void treeClick(Skill skill, ClickType click) {
        PlayerData d = data();
        if (click.isRightClick()) {
            if (skill.active() && d.rank(skill) > 0) {
                plugin.rpg().select(player, skill);
                Fx.select(player);
                refresh();
            }
            return;
        }
        String error = plugin.rpg().buy(player, skill);
        if (error != null) {
            Fx.deny(player);
            Msg.actionBar(player, "<red>" + error);
        }
        refresh();
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
                List.of("<yellow>F <gray>usa a habilidade ativa escolhida.", "<yellow>Shift + F <gray>troca a habilidade.",
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
