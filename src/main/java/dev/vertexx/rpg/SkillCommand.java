package dev.vertexx.rpg;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * {@code /habilidade [usar|trocar|menu|item]}: o Foco de Habilidades sem precisar do item. Sem argumento abre o menu.
 * É o caminho garantido no Bedrock e útil para atalhos de teclado e macros.
 */
public final class SkillCommand implements TabExecutor {

    private final PluginMain plugin;

    public SkillCommand(PluginMain plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "<red>Apenas jogadores podem usar este comando.");
            return true;
        }
        if (!plugin.rpg().enabled()) {
            Msg.send(player, "<gray>O sistema de habilidades está desativado neste servidor.");
            return true;
        }
        SkillFocus focus = plugin.skills().focus();
        switch (args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT)) {
            case "usar", "use", "u" -> focus.use(player);
            case "trocar", "proxima", "próxima", "t" -> focus.change(player);
            case "item", "foco" -> {
                if (focus.has(player)) {
                    Msg.send(player, "<gray>Você já tem o <white>Foco de Habilidades</white>.");
                } else if (plugin.skills().hasActives(player)) {
                    focus.ensure(player);
                } else {
                    Msg.send(player, "<gray>Desbloqueie uma habilidade ativa em <white>/vstats habilidades</white> primeiro.");
                }
            }
            default -> focus.open(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? dev.vertexx.core.Tab.of(args[0], "usar", "trocar", "menu", "item") : List.of();
    }
}
