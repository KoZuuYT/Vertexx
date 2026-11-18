package dev.vertexx.grave;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /tumulo} — lista os seus túmulos; {@code /tumulo chave} entrega Chaves de Túmulo (admin). */
public final class GraveCommand implements TabExecutor {

    private static final List<String> ADMIN = List.of("admin", "menu", "lista", "ir", "abrir", "tempo", "quebrar", "config");

    private final PluginMain plugin;

    public GraveCommand(PluginMain plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("chave")) {
            giveKey(sender, args);
            return true;
        }
        if (args.length > 0 && ADMIN.contains(args[0].toLowerCase(java.util.Locale.ROOT))) {
            admin(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "<red>Apenas jogadores têm túmulos.");
            return true;
        }
        List<Grave> graves = plugin.graves().ownedBy(player.getUniqueId());
        if (graves.isEmpty()) {
            Msg.send(player, "<gray>Você não tem nenhum túmulo pendente.");
            return true;
        }
        player.sendMessage(Msg.mm(""));
        player.sendMessage(Msg.mm(" <white><bold>☠ Seus túmulos</bold> <dark_gray>— " + graves.size()));
        for (Grave grave : graves) {
            long left = plugin.graves().protectionLeft(grave);
            String protection = left > 0 ? "<green>protegido por " + GraveManager.formatTime(left) : "<red>desprotegido";
            player.sendMessage(Msg.mm(" <dark_gray>• <white>" + grave.pos().coords() + " <dark_gray>("
                    + grave.pos().world() + ") <dark_gray>— " + protection));
        }
        player.sendMessage(Msg.mm(""));
        Fx.open(player);
        return true;
    }

    // /tumulo chave [jogador] [quantidade]
    private void giveKey(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vertexx.admin")) {
            Msg.send(sender, "<red>Você não tem permissão.");
            return;
        }
        Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
        if (target == null) {
            Msg.send(sender, "<red>Jogador não encontrado. Uso: <white>/tumulo chave <jogador> [quantidade]");
            return;
        }
        int amount = 1;
        if (args.length > 2) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                Msg.send(sender, "<red>Quantidade inválida.");
                return;
            }
        }
        ItemStack key = plugin.graves().createKey(amount);
        for (ItemStack leftover : target.getInventory().addItem(key).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
        Fx.success(target);
        Msg.send(sender, "<green>" + key.getAmount() + "x Chave de Túmulo entregue a <white>" + target.getName() + "</white>.");
    }

    // ── Administração ───────────────────────────────────────────

    private static String shortId(Grave grave) {
        return grave.id().toString().substring(0, 8);
    }

    /** Túmulos citados: "todos", um id (ou prefixo) ou o nome de um jogador. */
    private List<Grave> targets(String ref) {
        if (ref.equalsIgnoreCase("todos")) {
            return plugin.graves().all();
        }
        Grave one = plugin.graves().find(ref);
        if (one != null) {
            return List.of(one);
        }
        List<Grave> result = new java.util.ArrayList<>();
        for (Grave grave : plugin.graves().all()) {
            if (grave.ownerName().equalsIgnoreCase(ref)) {
                result.add(grave);
            }
        }
        return result;
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vertexx.admin")) {
            Msg.send(sender, "<red>Você não tem permissão.");
            return;
        }
        GraveManager graves = plugin.graves();
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "admin", "menu" -> {
                if (sender instanceof Player player) {
                    new GraveMenu(plugin, player, 0).open();
                } else {
                    Msg.send(sender, "<red>O menu é só para jogadores. Use <white>/tumulo lista</white>.");
                }
            }
            case "lista" -> {
                List<Grave> list = new java.util.ArrayList<>(args.length > 1 ? targets(args[1]) : graves.all());
                list.sort(java.util.Comparator.comparing(g -> g.ownerName().toLowerCase(java.util.Locale.ROOT)));
                Msg.send(sender, "<white>" + list.size() + " túmulo(s).");
                for (Grave grave : list) {
                    long left = graves.protectionLeft(grave);
                    sender.sendMessage(Msg.mm(" <dark_gray>• <aqua>" + shortId(grave) + " <white>" + Msg.esc(grave.ownerName())
                            + " <dark_gray>" + grave.pos().world() + " " + grave.pos().coords() + " <gray>"
                            + graves.contents(grave).size() + " itens, " + grave.xp() + " xp <dark_gray>— "
                            + (left == 0 ? "<red>desprotegido" : "<green>" + GraveManager.formatTime(left))));
                }
            }
            case "ir", "abrir" -> {
                if (!(sender instanceof Player player) || args.length < 2) {
                    Msg.send(sender, "<red>Uso (jogador): <white>/tumulo " + sub + " <id>");
                    return;
                }
                Grave grave = graves.find(args[1]);
                if (grave == null) {
                    Msg.send(sender, "<red>Túmulo não encontrado. Veja <white>/tumulo lista</white>.");
                } else if (sub.equals("ir")) {
                    GraveMenu.teleport(plugin, player, grave);
                } else {
                    new GraveMenu.GraveContentsMenu(plugin, player, grave.id(), 0).open();
                }
            }
            case "quebrar" -> {
                if (args.length < 2) {
                    Msg.send(sender, "<red>Uso: <white>/tumulo quebrar <id|jogador|todos> [apagar]");
                    return;
                }
                boolean drop = !(args.length > 2 && args[2].equalsIgnoreCase("apagar"));
                List<Grave> list = targets(args[1]);
                for (Grave grave : list) {
                    graves.breakGrave(grave, drop);
                }
                Msg.send(sender, "<green>" + list.size() + " túmulo(s) " + (drop ? "quebrado(s): itens soltos no chão." : "apagado(s): conteúdo destruído."));
            }
            case "tempo" -> time(sender, args);
            case "config" -> config(sender, args);
            default -> {
            }
        }
    }

    // /tumulo tempo <id|jogador|todos> <+N|-N|N|permanente|padrao>
    private void time(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Msg.send(sender, "<red>Uso: <white>/tumulo tempo <id|jogador|todos> <+N|-N|N|permanente|padrao> <gray>(minutos; 0 remove a proteção)");
            return;
        }
        List<Grave> list = targets(args[1]);
        if (list.isEmpty()) {
            Msg.send(sender, "<red>Nenhum túmulo encontrado.");
            return;
        }
        String value = args[2].toLowerCase(java.util.Locale.ROOT);
        long minutes = 0;
        boolean relative = value.startsWith("+") || value.startsWith("-");
        boolean permanent = value.equals("permanente");
        boolean reset = value.equals("padrao") || value.equals("padrão");
        if (!permanent && !reset) {
            try {
                minutes = Long.parseLong(value.startsWith("+") ? value.substring(1) : value);
            } catch (NumberFormatException e) {
                Msg.send(sender, "<red>Valor inválido: <white>" + Msg.esc(args[2]));
                return;
            }
        }
        for (Grave grave : list) {
            if (permanent) {
                plugin.graves().setPermanent(grave);
            } else if (reset) {
                plugin.graves().resetProtection(grave);
            } else if (relative) {
                plugin.graves().addProtection(grave, minutes * 60_000L);
            } else {
                plugin.graves().setProtection(grave, minutes * 60_000L);
            }
        }
        Msg.send(sender, "<green>Proteção atualizada em " + list.size() + " túmulo(s).");
    }

    // /tumulo config <ligar|desligar|tempo <min>|xp <porcentagem>>
    private void config(CommandSender sender, String[] args) {
        String key = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
        switch (key) {
            case "ligar", "desligar" -> {
                plugin.getConfig().set("graves.enabled", key.equals("ligar"));
                plugin.saveConfig();
                Msg.send(sender, "<green>Túmulos " + (key.equals("ligar") ? "ligados." : "desligados."));
            }
            case "tempo", "xp" -> {
                if (args.length < 3) {
                    Msg.send(sender, "<red>Uso: <white>/tumulo config " + key + " <valor>");
                    return;
                }
                try {
                    long value = Long.parseLong(args[2]);
                    if (key.equals("tempo")) {
                        plugin.getConfig().set("graves.protection-minutes", Math.max(0, Math.min(10_080, value)));
                    } else {
                        plugin.getConfig().set("graves.xp-percent", Math.max(0, Math.min(100, value)));
                    }
                    plugin.saveConfig();
                    Msg.send(sender, "<green>Configuração salva: <white>" + (key.equals("tempo")
                            ? plugin.getConfig().getLong("graves.protection-minutes") + " min de proteção"
                            : plugin.getConfig().getLong("graves.xp-percent") + "% do XP") + "</white>.");
                } catch (NumberFormatException e) {
                    Msg.send(sender, "<red>Número inválido.");
                }
            }
            default -> Msg.send(sender, "<gray>Atual: <white>" + (plugin.getConfig().getBoolean("graves.enabled", true) ? "ligado" : "desligado")
                    + "</white>, proteção <white>" + plugin.getConfig().getLong("graves.protection-minutes", 30) + " min</white>, XP <white>"
                    + plugin.getConfig().getInt("graves.xp-percent", 100) + "%</white>. <dark_gray>Uso: /tumulo config <ligar|desligar|tempo|xp> [valor]");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vertexx.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> all = new java.util.ArrayList<>(ADMIN);
            all.add("chave");
            return dev.vertexx.core.Tab.of(args[0], all);
        }
        String first = args[0].toLowerCase(java.util.Locale.ROOT);
        if (args.length == 2 && List.of("lista", "tempo", "quebrar", "ir", "abrir").contains(first)) {
            List<String> refs = new java.util.ArrayList<>(List.of("todos"));
            for (Grave grave : plugin.graves().all()) {
                refs.add(shortId(grave));
                if (!refs.contains(grave.ownerName())) {
                    refs.add(grave.ownerName());
                }
            }
            return dev.vertexx.core.Tab.of(args[1], refs);
        }
        if (args.length == 2 && first.equals("config")) {
            return dev.vertexx.core.Tab.of(args[1], "ligar", "desligar", "tempo", "xp");
        }
        if (args.length == 3 && first.equals("tempo")) {
            return dev.vertexx.core.Tab.of(args[2], "+5", "-5", "0", "30", "permanente", "padrao");
        }
        if (args.length == 3 && first.equals("quebrar")) {
            return dev.vertexx.core.Tab.of(args[2], "apagar");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("chave")) {
            return dev.vertexx.core.Tab.players(args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("chave")) {
            return dev.vertexx.core.Tab.of(args[2], "1", "2", "5", "10");
        }
        return List.of();
    }
}
