package dev.vertexx.panel;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** {@code /vertexx}: painel (administradores), Guia para todos e utilitários de versão. */
public final class PanelCommand implements TabExecutor {

    private final PluginMain plugin;

    public PanelCommand(PluginMain plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean admin = sender.hasPermission("vertexx.admin");
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        switch (sub) {
            case "livro", "guia" -> {
                if (sender instanceof Player player) {
                    plugin.panel().giveGuide(player);
                    Msg.send(player, "<green>Você recebeu o <white>Guia do Vertexx</white>.");
                } else {
                    Msg.send(sender, "<red>Apenas jogadores podem receber o livro.");
                }
            }
            case "" , "painel" -> {
                if (!admin) {
                    Msg.send(sender, "<gray>Use o <white>Guia do Vertexx</white> (livro) para abrir o painel. "
                            + "Perdeu o seu? <white>/vertexx livro</white>.");
                } else if (sender instanceof Player player) {
                    plugin.panel().open(player);
                } else {
                    Msg.send(sender, "<gray>Vertexx <white>" + plugin.updater().currentVersion() + "</white>.");
                }
            }
            case "versao", "atualizar" -> {
                if (!admin) {
                    Msg.send(sender, "<red>Você não tem permissão.");
                } else {
                    return plugin.updater().onCommand(sender, command, label, args);
                }
            }
            case "cerimonia" -> {
                if (!admin) {
                    Msg.send(sender, "<red>Você não tem permissão.");
                    return true;
                }
                Player target = args.length > 1 ? org.bukkit.Bukkit.getPlayerExact(args[1])
                        : (sender instanceof Player p ? p : null);
                if (target == null) {
                    Msg.send(sender, "<gray>Uso: <white>/vertexx cerimonia [jogador]</white> <dark_gray>(ensaio; não altera o reino)");
                } else if (!plugin.intro().demo(target)) {
                    Msg.send(sender, "<red>Esse jogador já está em uma cerimônia.");
                } else {
                    Msg.send(sender, "<green>Ensaio da cerimônia iniciado para <white>" + target.getName() + "</white>.");
                }
            }
            default -> Msg.send(sender, "<gray>Uso: <white>/vertexx" + (admin ? " [painel|livro|versao|atualizar|cerimonia]" : " livro"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return sender.hasPermission("vertexx.admin") ? List.of("painel", "livro", "versao", "atualizar", "cerimonia") : List.of("livro");
    }
}
