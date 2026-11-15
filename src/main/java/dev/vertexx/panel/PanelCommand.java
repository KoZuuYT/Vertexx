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
            case "mob" -> {
                if (!admin) {
                    Msg.send(sender, "<red>Você não tem permissão.");
                } else if (sender instanceof Player player) {
                    spawnTest(player, args);
                } else {
                    Msg.send(sender, "<red>Apenas jogadores podem usar este comando.");
                }
            }
            default -> Msg.send(sender, "<gray>Uso: <white>/vertexx" + (admin ? " [painel|livro|mob|versao|atualizar]" : " livro"));
        }
        return true;
    }

    /** {@code /vertexx mob <tipo> [nivel] [elite]}: cria um mob nivelado à sua frente (teste). */
    private void spawnTest(Player player, String[] args) {
        if (args.length < 2) {
            Msg.send(player, "<gray>Uso: <white>/vertexx mob <tipo> [nivel] [elite]");
            return;
        }
        org.bukkit.entity.EntityType type;
        try {
            type = org.bukkit.entity.EntityType.valueOf(args[1].toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            Msg.send(player, "<red>Tipo de entidade inválido.");
            return;
        }
        int level = 1;
        if (args.length > 2) {
            try {
                level = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                Msg.send(player, "<red>Nível inválido.");
                return;
            }
        }
        boolean elite = args.length > 3 && args[3].equalsIgnoreCase("elite");
        var at = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(3));
        var mob = plugin.mobs().spawnTest(at, type, level, elite);
        Msg.send(player, mob == null ? "<red>Não foi possível criar o mob." : "<green>Mob criado.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return sender.hasPermission("vertexx.admin") ? List.of("painel", "livro", "mob", "versao", "atualizar") : List.of("livro");
    }
}
