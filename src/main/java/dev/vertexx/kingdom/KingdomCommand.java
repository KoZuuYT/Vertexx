package dev.vertexx.kingdom;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import dev.vertexx.kingdom.KingdomManager.Member;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** {@code /reino} — informações, alianças e administração dos reinos. */
public final class KingdomCommand implements TabExecutor {

    private final PluginMain plugin;

    public KingdomCommand(PluginMain plugin) {
        this.plugin = plugin;
    }

    private KingdomManager kingdoms() {
        return plugin.kingdoms();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> info(sender);
            case "membros" -> members(sender);
            case "aliar" -> ally(sender, args);
            case "romper" -> breakAlliance(sender, args);
            case "admin" -> admin(sender, args);
            default -> Msg.send(sender, "<gray>Uso: <white>/reino</white>, <white>/reino membros</white>, "
                    + "<white>/reino aliar <reino></white>, <white>/reino romper <reino></white>");
        }
        return true;
    }

    private Member requireMember(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "<red>Apenas jogadores podem usar este comando.");
            return null;
        }
        Member member = kingdoms().member(player.getUniqueId());
        if (member == null) {
            Msg.send(sender, "<red>Você ainda não pertence a um reino.");
        }
        return member;
    }

    private void info(CommandSender sender) {
        Member member = requireMember(sender);
        if (member == null) {
            return;
        }
        Kingdom kingdom = member.kingdom();
        Set<Kingdom> allies = kingdoms().alliesOf(kingdom);
        String alliesText = allies.isEmpty() ? "<dark_gray>nenhum"
                : allies.stream().map(Kingdom::styled).collect(Collectors.joining("<gray>, "));
        long online = Bukkit.getOnlinePlayers().stream()
                .filter(p -> kingdoms().kingdomOf(p.getUniqueId()) == kingdom).count();
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm(" <bold>" + kingdom.styled()));
        sender.sendMessage(Msg.mm(" <gray>Seu cargo: <white>" + member.rank().displayName()));
        sender.sendMessage(Msg.mm(" <gray>Membros: <white>" + kingdoms().count(kingdom)
                + " <dark_gray>(" + online + " online)"));
        sender.sendMessage(Msg.mm(" <gray>Aliados: " + alliesText));
        plugin.kingdomInfoExtras((Player) sender);
        sender.sendMessage(Msg.mm(""));
        Fx.open((Player) sender);
    }

    private void members(CommandSender sender) {
        Member member = requireMember(sender);
        if (member == null) {
            return;
        }
        Map<UUID, Member> all = kingdoms().membersOf(member.kingdom());
        List<Map.Entry<UUID, Member>> sorted = new ArrayList<>(all.entrySet());
        sorted.sort((a, b) -> {
            int byRank = b.getValue().rank().compareTo(a.getValue().rank());
            return byRank != 0 ? byRank : a.getValue().name().compareToIgnoreCase(b.getValue().name());
        });
        sender.sendMessage(Msg.mm(""));
        sender.sendMessage(Msg.mm(" <bold>" + member.kingdom().styled() + "</bold> <dark_gray>— " + sorted.size() + " membro(s)"));
        for (Map.Entry<UUID, Member> entry : sorted) {
            boolean online = Bukkit.getPlayer(entry.getKey()) != null;
            sender.sendMessage(Msg.mm(" " + (online ? "<green>●" : "<dark_gray>●") + " <white><name> <dark_gray>— <gray>"
                    + entry.getValue().rank().displayName(), Placeholder.unparsed("name", entry.getValue().name())));
        }
        sender.sendMessage(Msg.mm(""));
        Fx.open((Player) sender);
    }

    private Kingdom parseTarget(CommandSender sender, String[] args, int index, Kingdom own) {
        if (args.length <= index) {
            Msg.send(sender, "<red>Informe o reino: <white>" + String.join("</white>, <white>", kingdoms().ids()));
            return null;
        }
        Kingdom target = Kingdom.byId(args[index]);
        if (target == null) {
            Msg.send(sender, "<red>Reino desconhecido. Opções: <white>" + String.join("</white>, <white>", kingdoms().ids()));
            return null;
        }
        if (target == own) {
            Msg.send(sender, "<red>Escolha um reino diferente do seu.");
            return null;
        }
        return target;
    }

    private Member requireLeader(CommandSender sender) {
        Member member = requireMember(sender);
        if (member == null) {
            return null;
        }
        if (member.rank() != Rank.LIDER) {
            Msg.send(sender, "<red>Apenas o <white>Líder</white> do reino pode tratar de alianças.");
            Fx.deny((Player) sender);
            return null;
        }
        return member;
    }

    private void ally(CommandSender sender, String[] args) {
        Member member = requireLeader(sender);
        if (member == null) {
            return;
        }
        Kingdom own = member.kingdom();
        Kingdom target = parseTarget(sender, args, 1, own);
        if (target == null) {
            return;
        }
        switch (kingdoms().propose(own, target)) {
            case ALREADY_ALLIED -> Msg.send(sender, "<yellow>Seu reino já é aliado de " + target.styled() + "<yellow>.");
            case ALREADY_PROPOSED -> Msg.send(sender, "<yellow>A proposta para " + target.styled()
                    + " <yellow>já foi enviada e aguarda resposta.");
            case PROPOSED -> {
                Bukkit.broadcast(Msg.mm(Msg.PREFIX + own.styled() + " <gray>propôs uma aliança a " + target.styled()
                        + "<gray>. O líder pode aceitar com <white>/reino aliar " + own.id() + "</white>."));
                playAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f);
            }
            case FORMED -> {
                Bukkit.broadcast(Msg.mm(Msg.PREFIX + "<green>Aliança firmada entre " + own.styled()
                        + " <green>e " + target.styled() + "<green>!"));
                playAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
            }
        }
    }

    private void breakAlliance(CommandSender sender, String[] args) {
        Member member = requireLeader(sender);
        if (member == null) {
            return;
        }
        Kingdom own = member.kingdom();
        Kingdom target = parseTarget(sender, args, 1, own);
        if (target == null) {
            return;
        }
        if (!kingdoms().breakAlliance(own, target)) {
            Msg.send(sender, "<yellow>Não há aliança ativa com " + target.styled() + "<yellow>.");
            return;
        }
        Bukkit.broadcast(Msg.mm(Msg.PREFIX + "<red>" + own.styled() + " <red>rompeu a aliança com "
                + target.styled() + "<red>."));
        playAll(Sound.ENTITY_WITHER_SPAWN, 1.4f);
        plugin.onAllianceBroken((Player) sender);
    }

    private void playAll(Sound sound, float pitch) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            Fx.sound(online, sound, 0.5f, pitch);
        }
    }

    // /reino admin definir <jogador> <reino>   |   /reino admin cargo <jogador> <cargo>
    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vertexx.admin")) {
            Msg.send(sender, "<red>Você não tem permissão.");
            return;
        }
        if (args.length < 4) {
            Msg.send(sender, "<gray>Uso: <white>/reino admin definir <jogador> <reino></white> ou "
                    + "<white>/reino admin cargo <jogador> <membro|oficial|lider></white>");
            return;
        }
        String name = args[2];
        Player online = Bukkit.getPlayerExact(name);
        UUID id = online != null ? online.getUniqueId() : kingdoms().findByName(name);
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "definir" -> {
                Kingdom kingdom = Kingdom.byId(args[3]);
                if (kingdom == null) {
                    Msg.send(sender, "<red>Reino desconhecido. Opções: <white>" + String.join("</white>, <white>", kingdoms().ids()));
                    return;
                }
                if (id == null) {
                    Msg.send(sender, "<red>Jogador não encontrado (precisa estar online ou já ter um reino).");
                    return;
                }
                Member current = kingdoms().member(id);
                String storedName = online != null ? online.getName() : current.name();
                kingdoms().set(id, storedName, kingdom, Rank.MEMBRO);
                if (online != null) {
                    plugin.onboarding().adminAssigned(online);
                }
                Msg.send(sender, "<green><name> agora pertence a " + kingdom.styled() + "<green>.",
                        Placeholder.unparsed("name", storedName));
            }
            case "cargo" -> {
                Rank rank = Rank.parse(args[3]);
                Member current = id == null ? null : kingdoms().member(id);
                if (rank == null) {
                    Msg.send(sender, "<red>Cargo inválido. Opções: <white>membro</white>, <white>oficial</white>, <white>lider</white>.");
                    return;
                }
                if (current == null) {
                    Msg.send(sender, "<red>Esse jogador não pertence a nenhum reino.");
                    return;
                }
                kingdoms().set(id, current.name(), current.kingdom(), rank);
                Msg.send(sender, "<green><name> agora é <white>" + rank.displayName() + "</white> de "
                        + current.kingdom().styled() + "<green>.", Placeholder.unparsed("name", current.name()));
                if (online != null) {
                    Msg.send(online, "<gray>Seu cargo no reino agora é <white>" + rank.displayName() + "</white>.");
                    Fx.success(online);
                }
            }
            default -> Msg.send(sender, "<red>Subcomando desconhecido.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("membros", "aliar", "romper"));
            if (sender.hasPermission("vertexx.admin")) {
                options.add("admin");
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("aliar") || args[0].equalsIgnoreCase("romper"))) {
            options.addAll(kingdoms().ids());
        } else if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("vertexx.admin")) {
            if (args.length == 2) {
                options.addAll(List.of("definir", "cargo"));
            } else if (args.length == 3) {
                Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            } else if (args.length == 4) {
                options.addAll(args[1].equalsIgnoreCase("cargo") ? List.of("membro", "oficial", "lider") : kingdoms().ids());
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
