package dev.vertexx.rpg;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Tab;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * {@code /vxp <operação> <jogador> <valor> [alvo] [nivel|xp]}: mexe direto no XP do jogador.
 * Alvos: {@code pontos} e {@code niveis} (XP do jogo), {@code geral} e cada status do RPG (com {@code nivel} ou {@code xp}).
 */
public final class XpCommand implements TabExecutor {

    private static final List<String> OPS = List.of("adicionar", "definir", "multiplicar", "dividir", "remover");

    private final PluginMain plugin;

    public XpCommand(PluginMain plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vertexx.admin")) {
            Msg.send(sender, "<red>Você não tem permissão.");
            return true;
        }
        if (args.length < 3) {
            Msg.send(sender, "<gray>Uso: <white>/vxp <adicionar|definir|multiplicar|dividir|remover> <jogador> <valor> "
                    + "[pontos|niveis|geral|status] [nivel|xp]");
            return true;
        }
        String op = normalize(args[0]);
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (op == null || target == null) {
            Msg.send(sender, op == null ? "<red>Operação inválida. Use: <white>" + String.join(", ", OPS)
                    : "<red>Jogador não está online.");
            return true;
        }
        double value;
        try {
            value = Double.parseDouble(args[2].replace(',', '.'));
        } catch (NumberFormatException e) {
            Msg.send(sender, "<red>Valor inválido.");
            return true;
        }
        if ((op.equals("dividir")) && value == 0) {
            Msg.send(sender, "<red>Não dá para dividir por zero.");
            return true;
        }
        String what = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "niveis";
        String field = args.length > 4 ? args[4].toLowerCase(Locale.ROOT) : "nivel";
        switch (what) {
            case "pontos", "xp" -> {
                int before = totalXp(target);
                int after = (int) Math.max(0, Math.round(apply(op, before, value)));
                setTotalXp(target, after);
                done(sender, target, "pontos de XP", before, after);
            }
            case "niveis", "níveis", "level", "levels" -> {
                int before = target.getLevel();
                int after = (int) Math.max(0, Math.round(apply(op, before, value)));
                target.setLevel(after);
                done(sender, target, "níveis", before, after);
            }
            case "geral" -> rpg(sender, target, null, op, value, field);
            default -> {
                Stat stat;
                try {
                    stat = Stat.valueOf(what.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    Msg.send(sender, "<red>Alvo inválido. Use <white>pontos, niveis, geral</white> ou um status.");
                    return true;
                }
                rpg(sender, target, stat, op, value, field);
            }
        }
        return true;
    }

    private void rpg(CommandSender sender, Player target, Stat stat, String op, double value, String field) {
        PlayerData d = plugin.rpg().data(target);
        int max = plugin.getConfig().getInt(stat == null ? "rpg.max-level" : "rpg.max-stat-level", 100);
        String label = stat == null ? "nível geral" : stat.display();
        if (field.equals("xp")) {
            double before = stat == null ? d.xp : d.xp(stat);
            double after = Math.max(0, apply(op, before, value));
            if (stat == null) {
                d.xp = after;
            } else {
                d.statXp[stat.ordinal()] = after;
            }
            done(sender, target, "XP de " + label, Math.round(before), Math.round(after));
        } else {
            int before = stat == null ? d.level : d.level(stat);
            int after = (int) Math.max(1, Math.min(max, Math.round(apply(op, before, value))));
            if (stat == null) {
                d.level = after;
                d.xp = 0;
            } else {
                d.statLevel[stat.ordinal()] = after;
                d.statXp[stat.ordinal()] = 0;
            }
            done(sender, target, "nível de " + label, before, after);
        }
        plugin.rpg().markDirty();
        plugin.rpg().applyPassives(target);
    }

    private void done(CommandSender sender, Player target, String what, long before, long after) {
        Msg.send(sender, "<green>" + what + " de <white>" + target.getName() + "</white>: <gray>" + before
                + " <dark_gray>→ <white>" + after + "</white>.");
    }

    private static double apply(String op, double current, double value) {
        return switch (op) {
            case "adicionar" -> current + value;
            case "remover" -> current - value;
            case "multiplicar" -> current * value;
            case "dividir" -> current / value;
            default -> value;
        };
    }

    private static String normalize(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "adicionar", "add", "somar" -> "adicionar";
            case "definir", "set", "setar" -> "definir";
            case "multiplicar", "multiply", "mult" -> "multiplicar";
            case "dividir", "divide", "div" -> "dividir";
            case "remover", "diminuir", "subtrair", "remove", "sub" -> "remover";
            default -> null;
        };
    }

    /** XP total em pontos (nível + barra), como o jogo conta. */
    private static int totalXp(Player player) {
        int level = player.getLevel();
        int atLevel = level <= 16 ? level * level + 6 * level
                : level <= 31 ? (int) (2.5 * level * level - 40.5 * level + 360)
                : (int) (4.5 * level * level - 162.5 * level + 2220);
        return atLevel + Math.round(player.getExp() * player.getExpToLevel());
    }

    private static void setTotalXp(Player player, int points) {
        player.setLevel(0);
        player.setExp(0);
        player.setTotalExperience(0);
        player.giveExp(points);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vertexx.admin")) {
            return List.of();
        }
        return switch (args.length) {
            case 1 -> Tab.of(args[0], OPS.toArray(new String[0]));
            case 2 -> Tab.of(args[1], plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toArray(String[]::new));
            case 4 -> {
                List<String> names = new ArrayList<>(List.of("pontos", "niveis", "geral"));
                for (Stat stat : Stat.values()) {
                    names.add(stat.name().toLowerCase(Locale.ROOT));
                }
                yield Tab.of(args[3], names.toArray(new String[0]));
            }
            case 5 -> Tab.of(args[4], "nivel", "xp");
            default -> List.of();
        };
    }
}
