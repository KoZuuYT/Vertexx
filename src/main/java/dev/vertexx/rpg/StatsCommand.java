package dev.vertexx.rpg;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** {@code /vstats [status|habilidades|info]}: abre o menu especial de status. */
public final class StatsCommand implements TabExecutor {

    private final PluginMain plugin;

    public StatsCommand(PluginMain plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "<red>Apenas jogadores podem usar este comando.");
            return true;
        }
        StatsGUI.Page page = StatsGUI.Page.STATS;
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "habilidades", "skills", "arvore", "árvore" -> page = StatsGUI.Page.TREE;
                case "info", "informacoes", "informações" -> page = StatsGUI.Page.INFO;
                default -> {
                }
            }
        }
        StatsGUI.open(plugin, player, page);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? dev.vertexx.core.Tab.of(args[0], "status", "habilidades", "info") : List.of();
    }
}
