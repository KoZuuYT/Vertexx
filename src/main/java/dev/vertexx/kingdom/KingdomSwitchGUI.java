package dev.vertexx.kingdom;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Menu;
import dev.vertexx.core.Ui;
import dev.vertexx.kingdom.Membership.Mode;
import dev.vertexx.mercenary.MercenaryManager;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Saída e troca de reino em três passos: escolher o caminho (aliança, traição ou fora da lei),
 * escolher o reino de destino e confirmar vendo as consequências. Quem já está sem reino vê
 * direto a lista de reinos para entrar.
 */
public final class KingdomSwitchGUI extends Menu {

    private enum Step {
        PATH, TARGET, CONFIRM
    }

    private Step step;
    private Mode mode;
    private Kingdom target;

    public KingdomSwitchGUI(PluginMain plugin, Player player) {
        super(plugin, player, Ui.Theme.of(plugin.kingdoms().kingdomOf(player.getUniqueId())),
                "<bold>Deixar o reino</bold>");
        boolean outlaw = plugin.kingdoms().kingdomOf(player.getUniqueId()) == null;
        this.step = outlaw ? Step.TARGET : Step.PATH;
        this.mode = outlaw ? Mode.JOIN : null;
    }

    private Kingdom own() {
        return plugin.kingdoms().kingdomOf(player.getUniqueId());
    }

    private void go(Step next) {
        step = next;
        refresh();
    }

    @Override
    protected void render() {
        Runnable back = switch (step) {
            case PATH -> () -> new KingdomPanelGUI(plugin, player, own()).open();
            case TARGET -> own() == null ? () -> plugin.panel().open(player) : () -> go(Step.PATH);
            case CONFIRM -> () -> go(Step.TARGET);
        };
        frame(back);
        switch (step) {
            case PATH -> path();
            case TARGET -> targets();
            case CONFIRM -> confirm();
        }
    }

    private MercenaryManager mercs() {
        return plugin.mercenaries();
    }

    private ItemStack header(String title, List<String> lore) {
        return icon(Material.WRITABLE_BOOK, "ui_daggers", title, lore, true);
    }

    /** Aviso comum no topo: carência ativa ou cargo de líder. */
    private List<String> notes() {
        List<String> notes = new ArrayList<>();
        long wait = mercs().cooldownLeft(player.getUniqueId());
        if (wait > 0) {
            notes.add("<red>Aguarde " + MercenaryManager.formatDuration(wait) + " para mudar de novo.");
        }
        if (plugin.kingdoms().rankOf(player.getUniqueId()) == Rank.LIDER) {
            notes.add("<yellow>Você é o Líder: o comando passa a outro membro.");
        }
        return notes;
    }

    // ── 1. Caminho ──────────────────────────────────────────────

    private void path() {
        Kingdom own = own();
        var cfg = plugin.getConfig();
        List<String> intro = new ArrayList<>(List.of("<gray>Deixar " + own.styled() + "<gray> é uma decisão séria.",
                "<gray>Escolha o motivo:", ""));
        intro.addAll(notes());
        set(HEADER_SLOT, header("<gradient:#c4a7ff:#2cb67d><bold>Deixar o reino", intro));

        boolean hasAlly = !plugin.kingdoms().alliesOf(own).isEmpty();
        set(20, icon(Material.WHITE_BANNER, "ui_link", "<green><bold>Aliança",
                List.of("<gray>Transfira-se para um reino <white>aliado</white>",
                        "<gray>do seu, em paz.", "",
                        "<gray>Honra: <green>sem perda",
                        "<gray>Marca de traidor: <green>não",
                        "",
                        hasAlly ? "<yellow>▶ Clique para escolher o reino" : "<red>Seu reino não tem aliados."),
                false), c -> {
            if (hasAlly) {
                mode = Mode.ALLIANCE;
                go(Step.TARGET);
            } else {
                Fx.deny(player);
            }
        });
        set(22, icon(Material.NETHERITE_SWORD, "ui_broken", "<dark_red><bold>Traição",
                List.of("<gray>Mude de lado para um reino que",
                        "<gray>não é aliado do seu.", "",
                        "<gray>Sua honra: <red>" + cfg.getInt("kingdoms.leave.betrayal-honor", -30),
                        "<gray>Honra do seu reino: <red>" + cfg.getInt("kingdom-honor.betrayal", -12),
                        "<gray>Reputação de mercenário: <gold>+" + cfg.getInt("mercenary.reputation.betrayal", 10),
                        "<gray>Marcado como <dark_red>traidor</dark_red> por <white>"
                                + cfg.getInt("kingdoms.traitor-days", 3) + " dia(s)",
                        "",
                        "<yellow>▶ Clique para escolher o reino"), false), c -> {
            mode = Mode.BETRAYAL;
            go(Step.TARGET);
        });
        set(24, icon(Material.SKELETON_SKULL, "ui_skull", "<red><bold>Fora da lei",
                List.of("<gray>Abandone tudo e viva sem reino:",
                        "<gray>um <red>mercenário</red>.", "",
                        "<gray>Sua honra: <red>" + cfg.getInt("kingdoms.leave.outlaw-honor", -10),
                        "<gray>Honra do seu reino: <red>" + cfg.getInt("kingdom-honor.desertion", -4),
                        "<gray>Qualquer um pode pôr preço na sua cabeça.",
                        "<gray>Fama traz benefícios; morrer a diminui.",
                        "",
                        "<yellow>▶ Clique para continuar"), false), c -> {
            mode = Mode.OUTLAW;
            target = null;
            go(Step.CONFIRM);
        });
        set(31, icon(Material.PAPER, "ui_star", "<gray>Carência entre trocas",
                List.of("<gray>Depois de mudar, você só poderá",
                        "<gray>mudar de novo após <white>"
                                + cfg.getInt("kingdoms.switch-cooldown-hours", 24) + " h</white>."), false));
    }

    // ── 2. Destino ──────────────────────────────────────────────

    private void targets() {
        Kingdom own = own();
        boolean joining = mode == Mode.JOIN;
        List<String> lore = new ArrayList<>();
        lore.add(joining ? "<gray>Você vive como mercenário." : "<gray>Escolha o reino de destino.");
        lore.add("<gray>Cargo inicial: <white>" + Rank.MEMBRO.displayName());
        lore.addAll(notes());
        set(HEADER_SLOT, header("<gradient:#c4a7ff:#2cb67d><bold>" + (joining ? "Entrar em um reino" : "Escolher o destino"),
                lore));

        int[] slots = {20, 22, 24};
        int index = 0;
        for (Kingdom kingdom : Kingdom.values()) {
            int slot = slots[index++];
            String blocked = blocked(kingdom, own);
            List<String> info = new ArrayList<>(kingdom.description());
            info.add("");
            info.add("<gray>Membros: <white>" + plugin.kingdoms().count(kingdom));
            int honor = plugin.kingdoms().honor(kingdom);
            info.add("<gray>Honra do reino: " + KingdomManager.honorLabel(honor) + " <dark_gray>(" + honor + ")");
            if (own != null) {
                info.add("<gray>Relação: " + (plugin.kingdoms().allied(own, kingdom) ? "<green>aliado" : "<gray>neutro"));
            }
            info.add("");
            info.add(blocked != null ? blocked : "<yellow>▶ Clique para escolher");
            ItemStack item = Ui.emblem(pack, Items.of(kingdom.icon(), "<bold>" + kingdom.styled(), info, blocked == null),
                    kingdom);
            set(slot, item, c -> {
                if (blocked != null) {
                    Fx.deny(player);
                    return;
                }
                target = kingdom;
                go(Step.CONFIRM);
            });
        }
    }

    private String blocked(Kingdom kingdom, Kingdom own) {
        if (kingdom == own) {
            return "<dark_gray>Este é o seu reino.";
        }
        if (mode == Mode.ALLIANCE && !plugin.kingdoms().allied(own, kingdom)) {
            return "<red>Não é aliado do seu reino.";
        }
        if (mode == Mode.BETRAYAL && plugin.kingdoms().allied(own, kingdom)) {
            return "<red>É aliado: use a via da aliança.";
        }
        return null;
    }

    // ── 3. Confirmar ────────────────────────────────────────────

    private void confirm() {
        Kingdom own = own();
        var cfg = plugin.getConfig();
        List<String> lines = new ArrayList<>();
        String title;
        switch (mode) {
            case ALLIANCE -> {
                title = "<green><bold>Transferência por aliança";
                lines.add("<gray>De " + own.styled() + " <gray>para " + target.styled());
                lines.add("<gray>Sem perda de honra nem marca.");
            }
            case BETRAYAL -> {
                title = "<dark_red><bold>Traição";
                lines.add("<gray>De " + own.styled() + " <gray>para " + target.styled());
                lines.add("<gray>Sua honra: <red>" + cfg.getInt("kingdoms.leave.betrayal-honor", -30));
                lines.add("<gray>Honra de " + own.styled() + "<gray>: <red>" + cfg.getInt("kingdom-honor.betrayal", -12));
                lines.add("<gray>Fama de mercenário: <gold>+" + cfg.getInt("mercenary.reputation.betrayal", 10));
                lines.add("<gray>Marca de traidor por <white>" + cfg.getInt("kingdoms.traitor-days", 3) + " dia(s)");
            }
            case OUTLAW -> {
                title = "<red><bold>Tornar-se fora da lei";
                lines.add("<gray>Você deixa " + own.styled() + "<gray> e fica sem reino.");
                lines.add("<gray>Sua honra: <red>" + cfg.getInt("kingdoms.leave.outlaw-honor", -10));
                lines.add("<gray>Honra de " + own.styled() + "<gray>: <red>" + cfg.getInt("kingdom-honor.desertion", -4));
                lines.add("<gray>Podem pôr preço na sua cabeça.");
            }
            default -> {
                title = "<green><bold>Entrar em um reino";
                lines.add("<gray>Você entra em " + target.styled() + "<gray> como <white>" + Rank.MEMBRO.displayName());
                lines.add("<gray>Deixa de ser mercenário; a fama fica guardada.");
            }
        }
        lines.add("");
        lines.addAll(notes());
        lines.add("<dark_gray>Carência depois: " + cfg.getInt("kingdoms.switch-cooldown-hours", 24) + " h");
        set(HEADER_SLOT, header(title, lines));

        String problem = plugin.membership().validate(player, mode, target);
        if (problem != null) {
            set(22, icon(Material.BARRIER, "ui_close", "<red><bold>Não é possível", List.of(problem), false));
            return;
        }
        set(38, icon(Material.RED_DYE, "ui_close", "<red><bold>Cancelar", List.of("<gray>Voltar sem mudar nada."), false),
                c -> go(mode == Mode.OUTLAW ? Step.PATH : Step.TARGET));
        set(42, icon(Material.LIME_DYE, "ui_check", "<green><bold>Confirmar", List.of("<gray>Esta troca é <white>imediata</white>.",
                "<red>Não dá para desfazer."), true), c -> plugin.membership().execute(player, mode, target));
        if (target != null) {
            set(22, Ui.emblem(pack, Items.of(target.icon(), "<bold>" + target.styled(),
                    List.of("<gray>Destino da troca"), true), target));
        } else {
            set(22, icon(Material.SKELETON_SKULL, "ui_skull", "<red><bold>Sem reino",
                    List.of("<gray>Mercenário"), true));
        }
    }
}
