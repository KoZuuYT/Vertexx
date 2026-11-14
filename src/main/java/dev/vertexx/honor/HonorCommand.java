package dev.vertexx.honor;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** {@code /honra [jogador]} e {@code /honra definir <jogador> <valor>} (admin). */
public final class HonorCommand implements TabExecutor {

    private static final int BAR_LENGTH = 20;

    private final PluginMain plugin;

    public HonorCommand(PluginMain plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("definir")) {
            define(sender, args);
            return true;
        }
        UUID id;
        String name;
        if (args.length > 0) {
            Player online = Bukkit.getPlayerExact(args[0]);
            id = online != null ? online.getUniqueId() : plugin.kingdoms().findByName(args[0]);
            if (id == null) {
                Msg.send(sender, "<red>Jogador não encontrado.");
                return true;
            }
            name = online != null ? online.getName() : plugin.kingdoms().member(id).name();
        } else if (sender instanceof Player player) {
            id = player.getUniqueId();
            name = player.getName();
        } else {
            Msg.send(sender, "<gray>Uso: <white>/honra <jogador>");
            return true;
        }
        int honor = plugin.honor().get(id);
        Msg.send(sender, "<white>" + name + " <dark_gray>— " + bar(honor) + " <white>" + honor
                + " <dark_gray>(" + HonorManager.Tier.of(honor).styled() + "<dark_gray>)");
        if (sender instanceof Player player) {
            Fx.open(player);
        }
        return true;
    }

    /** Barra de -100 a 100 em MiniMessage. */
    public static String bar(int honor) {
        int filled = Math.round((honor - HonorManager.MIN) / (float) (HonorManager.MAX - HonorManager.MIN) * BAR_LENGTH);
        String color = honor <= -30 ? "<red>" : honor >= 70 ? "<gold>" : honor >= 30 ? "<green>" : "<yellow>";
        return color + "▮".repeat(filled) + "<dark_gray>" + "▮".repeat(BAR_LENGTH - filled);
    }

    private void define(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vertexx.admin")) {
            Msg.send(sender, "<red>Você não tem permissão.");
            return;
        }
        if (args.length < 3) {
            Msg.send(sender, "<gray>Uso: <white>/honra definir <jogador> <valor>");
            return;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        UUID id = online != null ? online.getUniqueId() : plugin.kingdoms().findByName(args[1]);
        if (id == null) {
            Msg.send(sender, "<red>Jogador não encontrado.");
            return;
        }
        try {
            plugin.honor().set(id, Integer.parseInt(args[2]));
        } catch (NumberFormatException e) {
            Msg.send(sender, "<red>Valor inválido (use de -100 a 100).");
            return;
        }
        Msg.send(sender, "<green>Honra de <white>" + args[1] + "</white> definida para <white>" + plugin.honor().get(id) + "</white>.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1 || (args.length == 2 && args[0].equalsIgnoreCase("definir"))) {
            String prefix = args[args.length - 1].toLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(prefix)) {
                    options.add(online.getName());
                }
            }
            if (args.length == 1 && sender.hasPermission("vertexx.admin") && "definir".startsWith(prefix)) {
                options.add("definir");
            }
        }
        return options;
    }
}
