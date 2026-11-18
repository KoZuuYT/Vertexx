package dev.vertexx.backup;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** {@code /vbackup}: criar, listar, renomear, descrever, copiar, apagar e restaurar backups (administradores). */
public final class BackupCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("menu", "criar", "listar", "info", "renomear", "descricao", "copiar",
            "apagar", "restaurar", "cancelar", "status");

    private final PluginMain plugin;

    public BackupCommand(PluginMain plugin) {
        this.plugin = plugin;
    }

    private BackupManager backups() {
        return plugin.backups();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vertexx.admin")) {
            Msg.send(sender, "<red>Você não tem permissão.");
            return true;
        }
        String sub = args.length == 0 ? (sender instanceof Player ? "menu" : "listar") : args[0].toLowerCase(Locale.ROOT);
        List<String> rest = tokens(args, 1);
        switch (sub) {
            case "menu", "gui" -> {
                if (sender instanceof Player player) {
                    new BackupMenu(plugin, player, 0).open();
                } else {
                    list(sender);
                }
            }
            case "criar", "novo" -> create(sender, rest);
            case "listar", "lista" -> list(sender);
            case "status" -> status(sender);
            case "info" -> withBackup(sender, rest, label, "info", info -> info(sender, info));
            case "renomear" -> withBackup(sender, rest, label, "renomear <backup> <novo nome>", info -> {
                if (rest.size() < 2) {
                    usage(sender, label, "renomear <backup> <novo nome>");
                    return;
                }
                BackupInfo updated = backups().rename(info, rest.get(1));
                Msg.send(sender, updated == null ? "<red>Não foi possível renomear." : "<green>Renomeado para <white>" + Msg.esc(updated.name()) + "</white>.");
            });
            case "descricao", "descrição", "desc" -> withBackup(sender, rest, label, "descricao <backup> <texto|->", info -> {
                String text = rest.size() < 2 || rest.get(1).equals("-") ? "" : String.join(" ", rest.subList(1, rest.size()));
                BackupInfo updated = backups().describe(info, text);
                Msg.send(sender, updated == null ? "<red>Não foi possível alterar." : text.isEmpty()
                        ? "<green>Descrição removida." : "<green>Descrição atualizada.");
            });
            case "copiar", "duplicar" -> withBackup(sender, rest, label, "copiar <backup> [nome]", info -> {
                boolean started = backups().copy(info, rest.size() > 1 ? rest.get(1) : null, sender.getName(),
                        copy -> Msg.send(sender, "<green>Cópia criada: <white>" + Msg.esc(copy.name()) + "</white> <dark_gray>(" + copy.id() + ")"),
                        error -> Msg.send(sender, "<red>Falha ao copiar: " + Msg.esc(String.valueOf(error))));
                Msg.send(sender, started ? "<gray>Copiando..." : "<red>Já há um backup em andamento. Aguarde.");
            });
            case "apagar", "deletar", "remover" -> withBackup(sender, rest, label, "apagar <backup> confirmar", info -> {
                if (rest.size() < 2 || !rest.get(rest.size() - 1).equalsIgnoreCase("confirmar")) {
                    Msg.send(sender, "<yellow>Isto apaga <white>" + Msg.esc(info.name()) + "</white> para sempre. Confirme com <white>/"
                            + label + " apagar " + info.id() + " confirmar</white>.");
                    return;
                }
                Msg.send(sender, backups().delete(info) ? "<green>Backup apagado."
                        : "<red>Não foi possível apagar (ele é o alvo de uma restauração agendada?).");
            });
            case "restaurar", "restore" -> withBackup(sender, rest, label, "restaurar <backup> confirmar", info -> restore(sender, label, info, rest));
            case "cancelar" -> Msg.send(sender, backups().cancelRestore() ? "<green>Restauração agendada cancelada."
                    : "<gray>Não há restauração agendada.");
            default -> Msg.send(sender, "<gray>Uso: <white>/" + label + " [menu|criar|listar|info|renomear|descricao|copiar|apagar|restaurar|cancelar|status]");
        }
        return true;
    }

    private void usage(CommandSender sender, String label, String text) {
        Msg.send(sender, "<gray>Uso: <white>/" + label + " " + text);
    }

    private void withBackup(CommandSender sender, List<String> rest, String label, String usage, java.util.function.Consumer<BackupInfo> action) {
        if (rest.isEmpty()) {
            usage(sender, label, usage.contains(" ") ? usage : usage + " <backup>");
            Msg.send(sender, "<gray>O backup pode ser o número da lista, o id ou o nome (entre aspas se tiver espaços).");
            return;
        }
        BackupInfo info = backups().find(rest.get(0));
        if (info == null) {
            Msg.send(sender, "<red>Backup não encontrado: <white>" + Msg.esc(rest.get(0)) + "</white>. <gray>Veja <white>/" + label + " listar</white>.");
            return;
        }
        action.accept(info);
    }

    private void create(CommandSender sender, List<String> rest) {
        String name = rest.isEmpty() ? null : rest.get(0);
        String description = rest.size() > 1 ? String.join(" ", rest.subList(1, rest.size())) : "";
        boolean started = backups().create(name, description, BackupInfo.MANUAL, sender.getName(),
                info -> Msg.send(sender, "<green>Backup criado: <white>" + Msg.esc(info.name()) + "</white> <dark_gray>("
                        + info.id() + ", " + BackupInfo.human(info.size()) + ")"),
                error -> Msg.send(sender, "<red>Falha ao criar o backup: " + Msg.esc(String.valueOf(error))));
        Msg.send(sender, started ? "<gray>Salvando o servidor e criando o backup... (o jogo continua normal)"
                : "<red>Já há um backup em andamento. Aguarde terminar.");
    }

    private void list(CommandSender sender) {
        List<BackupInfo> all = backups().list();
        if (all.isEmpty()) {
            Msg.send(sender, "<gray>Nenhum backup ainda. Crie um com <white>/vbackup criar [nome] [descrição]</white>.");
            return;
        }
        Msg.send(sender, "<gray>Backups <dark_gray>(" + all.size() + ", " + BackupInfo.human(backups().totalSize()) + "):");
        String pending = backups().pendingId();
        for (int i = 0; i < all.size(); i++) {
            BackupInfo info = all.get(i);
            Msg.send(sender, "<yellow>" + (i + 1) + ". <white>" + Msg.esc(info.name()) + " <dark_gray>" + info.date() + " • "
                    + BackupInfo.human(info.size()) + " • " + info.typeLabel()
                    + (info.id().equals(pending) ? " <red>• restauração agendada" : ""));
        }
    }

    private void status(CommandSender sender) {
        String pending = backups().pendingId();
        BackupInfo target = pending == null ? null : backups().find(pending);
        Msg.send(sender, "<gray>Pasta: <white>" + Msg.esc(backups().folder().toString()));
        Msg.send(sender, "<gray>Backups: <white>" + backups().list().size() + " <dark_gray>(" + BackupInfo.human(backups().totalSize()) + ")");
        Msg.send(sender, "<gray>Automático: " + (plugin.getConfig().getBoolean("backup.auto.enabled", false)
                ? "<green>ligado <dark_gray>(a cada " + plugin.getConfig().getInt("backup.auto.interval-minutes", 60) + " min, guarda "
                + plugin.getConfig().getInt("backup.auto.keep", 5) + ")" : "<red>desligado"));
        Msg.send(sender, target == null ? "<gray>Restauração agendada: <white>nenhuma"
                : "<gray>Restauração agendada: <red>" + Msg.esc(target.name()) + " <gray>(aplicada ao reiniciar o servidor)");
    }

    private void info(CommandSender sender, BackupInfo info) {
        Msg.send(sender, "<white>" + Msg.esc(info.name()) + " <dark_gray>(" + info.id() + ")");
        Msg.send(sender, "<gray>Criado em <white>" + info.date() + " <gray>por <white>" + Msg.esc(info.author()) + " <gray>• "
                + info.typeLabel() + " • <white>" + BackupInfo.human(info.size()) + " <gray>• " + info.files() + " arquivos");
        Msg.send(sender, "<gray>Minecraft <white>" + info.minecraft() + " <gray>• Vertexx <white>" + info.plugin());
        Msg.send(sender, "<gray>Contém: <white>" + Msg.esc(String.join(", ", info.roots())));
        Msg.send(sender, info.description().isBlank() ? "<dark_gray>Sem descrição." : "<gray>Descrição: <white>" + Msg.esc(info.description()));
    }

    private void restore(CommandSender sender, String label, BackupInfo info, List<String> rest) {
        boolean confirmed = rest.stream().anyMatch(t -> t.equalsIgnoreCase("confirmar"));
        if (!confirmed) {
            Msg.send(sender, "<red><bold>Atenção:</bold> <yellow>restaurar <white>" + Msg.esc(info.name()) + "</white> (" + info.date()
                    + ") substitui mundos, dados dos jogadores e dados do Vertexx pelo estado desse backup.");
            Msg.send(sender, "<gray>O estado atual é guardado antes em um backup de segurança. Confirme com <white>/" + label + " restaurar "
                    + info.id() + " confirmar</white> <gray>(adicione <white>parar</white> para desligar o servidor em seguida).");
            return;
        }
        boolean stop = rest.stream().anyMatch(t -> t.equalsIgnoreCase("parar"));
        boolean started = backups().scheduleRestore(info, sender.getName(), safety -> {
            Msg.send(sender, "<green>Restauração de <white>" + Msg.esc(info.name()) + "</white> agendada."
                    + (safety == null ? "" : " <gray>Estado atual guardado em <white>" + Msg.esc(safety) + "</white>."));
            if (stop) {
                Msg.send(sender, "<yellow>Desligando o servidor para aplicar...");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        online.kick(Msg.mm("<yellow>O servidor está sendo restaurado a partir de um backup.\n<gray>Volte em instantes."));
                    }
                    Bukkit.shutdown();
                }, 40L);
            } else {
                Msg.send(sender, "<yellow>Reinicie o servidor (<white>/stop</white> e inicie de novo) para aplicar. <gray>Cancele com <white>/"
                        + label + " cancelar</white>.");
            }
        }, error -> Msg.send(sender, "<red>Falha ao agendar a restauração: " + Msg.esc(String.valueOf(error))));
        if (!started) {
            Msg.send(sender, "<red>Já há um backup em andamento. Aguarde terminar.");
        } else {
            Msg.send(sender, "<gray>Guardando o estado atual antes de agendar...");
        }
    }

    /** Separa os argumentos respeitando aspas: {@code criar "Antes do boss" texto livre}. */
    private static List<String> tokens(String[] args, int from) {
        List<String> out = new ArrayList<>();
        StringBuilder quoted = null;
        for (int i = from; i < args.length; i++) {
            String arg = args[i];
            if (quoted != null) {
                quoted.append(' ').append(arg);
                if (arg.endsWith("\"")) {
                    out.add(quoted.substring(0, quoted.length() - 1));
                    quoted = null;
                }
            } else if (arg.startsWith("\"") && !(arg.length() > 1 && arg.endsWith("\""))) {
                quoted = new StringBuilder(arg.substring(1));
            } else if (arg.startsWith("\"")) {
                out.add(arg.substring(1, arg.length() - 1));
            } else {
                out.add(arg);
            }
        }
        if (quoted != null) {
            out.add(quoted.toString());
        }
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vertexx.admin")) {
            return List.of();
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            SUBS.stream().filter(s -> s.startsWith(prefix)).forEach(out::add);
        } else if (args.length == 2 && List.of("info", "renomear", "descricao", "copiar", "apagar", "restaurar").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (BackupInfo info : backups().list()) {
                if (info.id().startsWith(prefix)) {
                    out.add(info.id());
                }
            }
        } else if (args.length >= 3 && List.of("apagar", "restaurar").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (String word : args[0].equalsIgnoreCase("restaurar") ? List.of("confirmar", "parar") : List.of("confirmar")) {
                if (word.startsWith(prefix)) {
                    out.add(word);
                }
            }
        }
        return out;
    }
}
