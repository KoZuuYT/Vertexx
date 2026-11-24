package dev.vertexx.rpg;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Menu;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Ui;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Seleção rápida das habilidades do Foco (Shift + botão direito, ou {@code /habilidade menu}). Clicar escolhe a
 * habilidade; o botão de usar dispara a selecionada. Funciona no Java e no Bedrock.
 */
final class SkillMenu extends Menu {

    private static final String TITLE = "<gradient:#4cc9f0:#7f5af0><bold>Habilidades</bold></gradient>";

    private final SkillManager skills;
    private final SkillFocus focus;

    SkillMenu(PluginMain plugin, Player player, SkillManager skills, SkillFocus focus) {
        super(plugin, player, Ui.Theme.TREE, TITLE);
        this.skills = skills;
        this.focus = focus;
    }

    @Override
    protected void render() {
        frame(null);
        PlayerData d = plugin.rpg().data(player);
        List<Skill> list = skills.unlockedActives(d);
        set(HEADER_SLOT, Items.of(Material.AMETHYST_SHARD, "<gradient:#4cc9f0:#7f5af0><bold>Foco de Habilidades",
                List.of("<gray>Escolha a habilidade e use com", "<gray>o botão direito do Foco na mão.", "",
                        "<dark_gray>Equivale a /habilidade"), true));
        int[] slots = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
        for (int i = 0; i < list.size() && i < slots.length; i++) {
            Skill skill = list.get(i);
            boolean selected = d.selected == skill;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + skill.description());
            lore.add("");
            lore.add("<gray>Nível: <white>" + d.rank(skill) + "<dark_gray>/<white>" + skill.maxRank()
                    + (skill.toggle() ? " <dark_gray>• <aqua>modo: " + (skills.modeOn(player, skill) ? "<green>ligado" : "<red>desligado")
                    : " <dark_gray>• <aqua>ativa"));
            lore.add(selected ? "<aqua>★ Selecionada" : "<yellow>▶ Clique para selecionar");
            set(slots[i], Items.of(skill.icon(), (selected ? "<aqua>★ " : "<white>") + skill.display(), lore, selected), c -> {
                plugin.rpg().select(player, skill);
                Fx.select(player);
                refresh();
            });
        }
        set(40, Items.of(Material.LIME_CONCRETE, "<green><bold>✔ Usar a selecionada",
                List.of("<gray>Usa a habilidade (ou liga/desliga o modo)", "<gray>sem fechar o menu de escolha.", "",
                        "<yellow>▶ Clique para usar")), c -> {
            skills.cast(player);
            refresh();
        });
        if (!focus.has(player)) {
            set(38, Items.of(Material.DISC_FRAGMENT_5, "<aqua>Pegar o Foco de Habilidades",
                    List.of("<gray>Você não está com o item.", "", "<yellow>▶ Clique para receber")), c -> {
                focus.ensure(player);
                Msg.actionBar(player, "<aqua>Foco entregue.");
                refresh();
            });
        }
        set(42, Items.of(Material.EXPERIENCE_BOTTLE, "<white>Árvore de habilidades",
                List.of("<gray>Gaste pontos e veja todas.", "", "<yellow>▶ Clique para abrir")), c -> StatsGUI.open(plugin, player, StatsGUI.Page.TREE));
    }
}
