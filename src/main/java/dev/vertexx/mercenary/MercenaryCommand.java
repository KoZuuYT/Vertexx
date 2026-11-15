package dev.vertexx.mercenary;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import dev.vertexx.mercenary.MercenaryManager.Tier;
import dev.vertexx.mercenary.MercenaryManager.Wanted;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * {@code /recompensa} — quadro de procurados e recompensas; {@code /mercenario} — sua reputação.
 * Os dois comandos usam esta classe.
 */
public final class MercenaryCommand implements TabExecutor {

    private final PluginMain plugin;

    public MercenaryCommand(PluginMain plugin) {
        this.plugin = plugin;
    }

    private MercenaryManager mercs() {
        return plugin.mercenaries();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean status = command.getName().equalsIgnoreCase("mercenario");
        if (status) {
            status(sender, args);
            return true;
        }
        String sub = args.length == 0 ? "quadro" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "quadro" -> board(sender);
            case "lista" -> list(sender);
            case "colocar" -> place(sender, args);
            default -> Msg.send(sender, "<gray>Uso: <white>/recompensa</white> (quadro), <white>/recompensa lista</white>, "
                    + "<white>/recompensa colocar <jogador> <quantia></white>");
        }
        return true;
    }

    private void board(CommandSender sender) {
        if (sender instanceof Player player) {
            new BountyGUI(plugin, player).open();
        } else {
            list(sender);
        }
    }

    private void list(CommandSender sender) {
        List<Wanted> wanted = mercs().wanted();
        Msg.send(sender, "<red><bold>Procurados");
        if (wanted.isEmpty()) {
            sender.sendMessage(Msg.mm(" <dark_gray>Ninguém tem recompensa."));
            return;
        }
        for (Wanted entry : wanted) {
            boolean outlaw = mercs().isOutlaw(entry.target());
            sender.sendMessage(Msg.mm(" <gold>" + entry.total() + " " + mercs().currencyName() + " <dark_gray>— "
                    + (outlaw ? "<red>" : "<gray>") + "<name> <dark_gray>(" + mercs().tier(entry.target()).styled()
                    + "<dark_gray>)", Placeholder.unparsed("name", entry.name())));
        }
    }

    private void place(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "<red>Apenas jogadores podem pôr recompensas.");
            return;
        }
        if (args.length < 3) {
            Msg.send(sender, "<gray>Uso: <white>/recompensa colocar <jogador> <quantia></white>");
            return;
        }
        UUID id = mercs().findOutlaw(args[1]);
        if (id == null || !mercs().isOutlaw(id)) {
            Msg.send(sender, "<red>Esse jogador não é um fora da lei.");
            Fx.deny(player);
            return;
        }
        if (id.equals(player.getUniqueId())) {
            Msg.send(sender, "<red>Você não pode pôr preço na própria cabeça.");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            Msg.send(sender, "<red>Quantia inválida.");
            return;
        }
        int min = Math.max(1, plugin.getConfig().getInt("bounty.min", 8));
        int max = Math.max(min, plugin.getConfig().getInt("bounty.max", 2304));
        if (amount < min || amount > max) {
            Msg.send(sender, "<red>A recompensa deve ficar entre <white>" + min + "</white> e <white>" + max + "</white>.");
            return;
        }
        if (!mercs().take(player, amount)) {
            Msg.send(sender, "<red>Você precisa carregar <white>" + amount + " " + mercs().currencyName() + "</white>.");
            Fx.deny(player);
            return;
        }
        String name = mercs().nameOf(id);
        mercs().place(player, id, name, amount);
        Bukkit.broadcast(Msg.mm(Msg.PREFIX + "<gold>Procurado! <white><who></white> <gray>pôs <yellow>" + amount + " "
                        + mercs().currencyName() + "<gray> na cabeça de <red><target></red><gray>. Total: <yellow>"
                        + mercs().total(id) + "<gray>.",
                Placeholder.unparsed("who", player.getName()), Placeholder.unparsed("target", name)));
        for (Player online : Bukkit.getOnlinePlayers()) {
            Fx.sound(online, Sound.BLOCK_BELL_USE, 0.6f, 0.6f);
        }
    }

    private void status(CommandSender sender, String[] args) {
        UUID id;
        String name;
        if (args.length > 0) {
            Player online = Bukkit.getPlayerExact(args[0]);
            id = online != null ? online.getUniqueId() : mercs().findOutlaw(args[0]);
            if (id == null) {
                Msg.send(sender, "<red>Jogador não encontrado entre os mercenários.");
                return;
            }
            name = online != null ? online.getName() : mercs().nameOf(id);
        } else if (sender instanceof Player player) {
            id = player.getUniqueId();
            name = player.getName();
        } else {
            Msg.send(sender, "<gray>Uso: <white>/mercenario <jogador>");
            return;
        }
        int rep = mercs().rep(id);
        Tier tier = Tier.of(rep);
        boolean outlaw = mercs().isOutlaw(id);
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm(" <white><name> <dark_gray>— " + (outlaw ? "<red>fora da lei" : "<gray>com reino"),
                Placeholder.unparsed("name", name)));
        sender.sendMessage(Msg.mm(" <gray>Reputação: <white>" + rep + "<dark_gray>/" + MercenaryManager.MAX_REP
                + " <dark_gray>(" + tier.styled() + "<dark_gray>)"));
        Tier next = tier.next();
        if (next != null) {
            sender.sendMessage(Msg.mm(" <gray>Próximo nível: " + next.styled() + " <dark_gray>em " + (next.min() - rep)
                    + " ponto(s)"));
        }
        int bounty = mercs().total(id);
        sender.sendMessage(Msg.mm(" <gray>Recompensa: " + (bounty > 0 ? "<yellow>" + bounty + " " + mercs().currencyName()
                : "<dark_gray>nenhuma")));
        if (!outlaw && rep > 0) {
            sender.sendMessage(Msg.mm(" <dark_gray>Benefícios só valem para quem está sem reino."));
        }
        sender.sendMessage(Msg.mm(""));
        if (sender instanceof Player player) {
            Fx.open(player);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        boolean status = command.getName().equalsIgnoreCase("mercenario");
        if (!status && args.length == 1) {
            options.addAll(List.of("quadro", "lista", "colocar"));
        } else if ((status && args.length == 1) || (!status && args.length == 2 && args[0].equalsIgnoreCase("colocar"))) {
            for (UUID id : mercs().outlawIds()) {
                String name = mercs().nameOf(id);
                if (name != null) {
                    options.add(name);
                }
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
