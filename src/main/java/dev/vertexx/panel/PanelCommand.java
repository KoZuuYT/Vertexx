package dev.vertexx.panel;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Tab;
import dev.vertexx.mob.MobManager;
import dev.vertexx.mob.Trait;
import dev.vertexx.rpg.Stat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /vertexx}: painel (administradores), Guia para todos e utilitários de administração. */
public final class PanelCommand implements TabExecutor {

    private static final String ADMIN_USAGE = " [painel|livro|mob|xp|boss|rejeitado|item|end|cabeca|versao|atualizar]";

    private final PluginMain plugin;

    public PanelCommand(PluginMain plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean admin = sender.hasPermission("vertexx.admin");
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "livro", "guia" -> {
                if (sender instanceof Player player) {
                    plugin.panel().giveGuide(player);
                    Msg.send(player, "<green>Você recebeu o <white>Guia do Vertexx</white>.");
                } else {
                    Msg.send(sender, "<red>Apenas jogadores podem receber o livro.");
                }
            }
            case "", "painel" -> {
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
                } else {
                    spawnMob(sender, args);
                }
            }
            case "xp" -> {
                if (!admin) {
                    Msg.send(sender, "<red>Você não tem permissão.");
                } else {
                    giveXp(sender, args);
                }
            }
            case "rejeitado", "rejeitados", "fauna" -> {
                if (!admin) {
                    Msg.send(sender, "<red>Você não tem permissão.");
                } else {
                    plugin.fauna().command(sender, args);
                }
            }
            case "end" -> {
                if (!admin) {
                    Msg.send(sender, "<red>Você não tem permissão.");
                } else {
                    endCommand(sender, args);
                }
            }
            case "item" -> {
                if (!admin) {
                    Msg.send(sender, "<red>Você não tem permissão.");
                } else {
                    giveItem(sender, args);
                }
            }
            case "arida" -> {
                if (!admin || args.length < 4) {
                    Msg.send(sender, "<gray>Uso: <white>/vertexx arida <oasis|baobab|mound|cactus|tumble> <x> <z></white>");
                } else {
                    try {
                        org.bukkit.World w = sender instanceof Player p ? p.getWorld() : plugin.getServer().getWorlds().get(0);
                        String done = plugin.arid().demo(args[1], w, Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                        Msg.send(sender, done == null ? "<red>Estrutura desconhecida." : "<green>Gerado: " + done);
                    } catch (NumberFormatException e) {
                        Msg.send(sender, "<red>Coordenadas inválidas.");
                    }
                }
            }
            case "clima" -> {
                if (!admin) {
                    Msg.send(sender, "<red>Você não tem permissão.");
                } else {
                    climaCommand(sender, args);
                }
            }
            case "cabeca", "cabeça" -> {
                if (!admin || !(sender instanceof org.bukkit.entity.Player self)) {
                    Msg.send(sender, "<red>Somente administradores, dentro do jogo.");
                } else if (args.length < 2) {
                    Msg.send(sender, "<gray>Uso: <white>/vertexx cabeca [nome]</white> <gray>(" + String.join(", ", dev.vertexx.model.CustomHeads.names()) + ")");
                } else if (!dev.vertexx.model.CustomHeads.names().contains(args[1])) {
                    Msg.send(sender, "<red>Cabeça desconhecida.");
                } else {
                    self.getInventory().addItem(dev.vertexx.model.CustomHeads.of(args[1]));
                    Msg.send(sender, "<green>Cabeça <white>" + Msg.esc(args[1]) + "</white> entregue (precisa do resource pack).");
                }
            }
            case "boss" -> {
                if (!admin) {
                    Msg.send(sender, "<red>Você não tem permissão.");
                } else {
                    plugin.bosses().command(sender, args);
                }
            }
            default -> Msg.send(sender, "<gray>Uso: <white>/vertexx" + (admin ? ADMIN_USAGE : " livro"));
        }
        return true;
    }

    /** {@code /vertexx end <zona|gerar> [x z]}: consulta a zona e aplica os biomas do End ao chunk (teste). */
    private void endCommand(CommandSender sender, String[] args) {
        org.bukkit.World world = sender instanceof Player p ? p.getWorld() : null;
        for (org.bukkit.World w : plugin.getServer().getWorlds()) {
            if (world == null && w.getEnvironment() == org.bukkit.World.Environment.THE_END) {
                world = w;
            }
        }
        if (world == null || args.length < 2) {
            Msg.send(sender, "<gray>Uso: <white>/vertexx end <zona|gerar> [x z]");
            return;
        }
        int x = args.length >= 4 ? Integer.parseInt(args[2]) : sender instanceof Player p ? p.getLocation().getBlockX() : 2000;
        int z = args.length >= 4 ? Integer.parseInt(args[3]) : sender instanceof Player p ? p.getLocation().getBlockZ() : 0;
        if (args[1].equalsIgnoreCase("zona")) {
            Msg.send(sender, "<gray>Zona em " + x + " " + z + ": <white>" + plugin.endWorld().describe(world, x, z));
        } else if (args[1].equalsIgnoreCase("gerar")) {
            int changed = plugin.endWorld().process(world.getChunkAt(x >> 4, z >> 4));
            Msg.send(sender, "<green>Chunk processado: <white>" + changed + "</white> blocos alterados.");
        }
    }

    /** Livros de maldição ({@code curse:<id>}) e Óculos de Explorador ({@code oculos}). */
    private boolean giveOverworld(CommandSender sender, String[] args) {
        ItemStack stack;
        dev.vertexx.overworld.Curses.Curse curse = args[1].toLowerCase(Locale.ROOT).startsWith("curse:")
                ? dev.vertexx.overworld.Curses.Curse.parse(args[1].substring(6)) : null;
        if (curse != null) {
            stack = plugin.curses().book(curse);
        } else if (args[1].equalsIgnoreCase("oculos")) {
            stack = plugin.climates().goggles();
        } else {
            return false;
        }
        Player target = args.length >= 3 ? plugin.getServer().getPlayerExact(args[2]) : sender instanceof Player self ? self : null;
        if (target == null) {
            Msg.send(sender, "<red>Informe um jogador online.");
            return true;
        }
        target.getInventory().addItem(stack).values().forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
        Msg.send(sender, "<green>Item entregue a <white>" + target.getName() + "</white>.");
        return true;
    }

    /** {@code /vertexx clima <sandstorm|blizzard|gale>}: força um clima no mundo do jogador (teste/admin). */
    private void climaCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player self) || args.length < 2) {
            Msg.send(sender, "<gray>Uso (no jogo): <white>/vertexx clima <sandstorm|blizzard|gale></white>");
            return;
        }
        String label = plugin.climates().force(args[1], self.getWorld());
        Msg.send(sender, label == null ? "<red>Clima desconhecido." : "<green>" + label + " iniciada neste mundo.");
    }

    /** {@code /vertexx item <id> [jogador] [quantidade]}: entrega um item da End Update (materiais e mochilas). */
    private void giveItem(CommandSender sender, String[] args) {
        dev.vertexx.end.EndItem item = null;
        if (args.length >= 2 && giveOverworld(sender, args)) {
            return;
        }
        if (args.length >= 2) {
            for (dev.vertexx.end.EndItem value : dev.vertexx.end.EndItem.values()) {
                if (value.id().equalsIgnoreCase(args[1])) {
                    item = value;
                }
            }
        }
        if (item == null) {
            Msg.send(sender, "<gray>Uso: <white>/vertexx item <id> [jogador] [quantidade]</white> <gray>("
                    + String.join(", ", Arrays.stream(dev.vertexx.end.EndItem.values()).map(dev.vertexx.end.EndItem::id).toList()) + ")");
            return;
        }
        Player target = args.length >= 3 ? plugin.getServer().getPlayerExact(args[2]) : sender instanceof Player self ? self : null;
        if (target == null) {
            Msg.send(sender, "<red>Informe um jogador online.");
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException e) {
                amount = 1;
            }
        }
        for (int i = 0; i < (item.tier() > 0 ? amount : 1); i++) {
            ItemStack stack = item.tier() > 0 ? plugin.backpacks().create(item.tier()) : item.create(plugin);
            if (item.tier() == 0) {
                stack.setAmount(amount);
            }
            target.getInventory().addItem(stack).values().forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
        }
        Msg.send(sender, "<green>" + amount + "x <white>" + item.display() + "</white> para <white>" + target.getName() + "</white>.");
    }

    /** {@code /vertexx xp <jogador> <status> <quantidade>}: concede XP de status (teste e eventos). */
    private void giveXp(CommandSender sender, String[] args) {
        if (args.length < 4) {
            Msg.send(sender, "<gray>Uso: <white>/vertexx xp <jogador> <status> <quantidade>");
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            Msg.send(sender, "<red>Jogador não está online.");
            return;
        }
        Stat stat;
        double amount;
        try {
            stat = Stat.valueOf(args[2].toUpperCase(Locale.ROOT));
            amount = Double.parseDouble(args[3]);
        } catch (IllegalArgumentException e) {
            Msg.send(sender, "<red>Status ou quantidade inválidos. Status: <white>"
                    + String.join(", ", Tab.names("", Stat.values())));
            return;
        }
        plugin.rpg().addXp(target, stat, amount);
        Msg.send(sender, "<green>+" + (long) amount + " XP de " + stat.display() + " para <white>" + target.getName() + "</white>.");
    }

    /**
     * {@code /vertexx mob <tipo> [nível] [elite] [traits...]}: cria um mob nivelado à sua frente.
     * Os parâmetros depois do tipo podem vir em qualquer ordem.
     */
    private void spawnMob(CommandSender player, String[] args) {
        if (args.length < 2) {
            Msg.send(player, "<gray>Uso: <white>/vertexx mob <tipo> [nível] [elite] [traits...]");
            return;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            Msg.send(player, "<red>Tipo de mob inválido: <white>" + Msg.esc(args[1]));
            return;
        }
        if (!MobManager.accepts(type)) {
            Msg.send(player, "<red>Esse tipo não recebe nível (use mobs hostis comuns, como zombie, skeleton, creeper).");
            return;
        }
        int level = -1;
        boolean elite = false;
        List<Trait> traits = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            String token = args[i].toLowerCase(Locale.ROOT);
            if (token.equals("elite")) {
                elite = true;
                continue;
            }
            try {
                level = Math.max(1, Math.min(200, Integer.parseInt(token)));
                continue;
            } catch (NumberFormatException ignored) {
                // não é número: tenta como trait
            }
            Trait trait = Trait.parse(token);
            if (trait == null) {
                Msg.send(player, "<red>Parâmetro desconhecido: <white>" + Msg.esc(args[i])
                        + "<red>. Use nível, <white>elite</white> ou um trait (<white>"
                        + String.join(", ", Tab.names("", Trait.values())) + "</white>).");
                return;
            }
            if (!traits.contains(trait)) {
                traits.add(trait);
            }
        }
        if (level < 0) {
            level = 1;
        }
        for (Trait trait : traits) {
            if (trait.elite() && !elite) {
                elite = true; // traits de elite só existem em mobs elite
            }
        }
        org.bukkit.Location at;
        if (player instanceof Player who) {
            at = who.getLocation().add(who.getLocation().getDirection().setY(0).normalize().multiply(3));
        } else {
            at = plugin.getServer().getWorlds().get(0).getSpawnLocation().add(0.5, 1, 0.5); // console: no spawn
        }
        var mob = plugin.mobs().spawnTest(at, type, level, elite, traits);
        if (mob == null) {
            Msg.send(player, "<red>Não foi possível criar o mob.");
            return;
        }
        Msg.send(player, "<green>Mob criado: <white>" + type.name().toLowerCase(Locale.ROOT) + " <gray>nível <white>" + level
                + (elite ? " <gold>elite" : "") + (traits.isEmpty() ? "" : " <gray>• "
                + String.join("<gray>, ", traits.stream().map(Trait::colored).toList())));
    }

    // ── Autocomplete ───────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        boolean admin = sender.hasPermission("vertexx.admin");
        if (args.length == 1) {
            return Tab.of(args[0], admin ? List.of("painel", "livro", "mob", "xp", "boss", "rejeitado", "item", "end", "clima", "arida", "cabeca", "versao", "atualizar")
                    : List.of("livro"));
        }
        if (!admin) {
            return List.of();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "mob":
                return tabMob(args);
            case "xp":
                if (args.length == 2) {
                    return Tab.players(args[1]);
                }
                if (args.length == 3) {
                    return Tab.names(args[2], Stat.values());
                }
                if (args.length == 4) {
                    return Tab.of(args[3], "10", "100", "500", "1000", "5000");
                }
                return List.of();
            case "end":
                return args.length == 2 ? Tab.of(args[1], "zona", "gerar") : List.of();
            case "clima":
                return args.length == 2 ? Tab.of(args[1], "sandstorm", "blizzard", "gale") : List.of();
            case "item":
                if (args.length == 2) {
                    List<String> ids = new java.util.ArrayList<>(Arrays.stream(dev.vertexx.end.EndItem.values()).map(dev.vertexx.end.EndItem::id).toList());
                    for (dev.vertexx.overworld.Curses.Curse c : dev.vertexx.overworld.Curses.Curse.values()) {
                        ids.add("curse:" + c.id());
                    }
                    ids.add("oculos");
                    return Tab.of(args[1], ids);
                }
                return args.length == 3 ? Tab.players(args[2]) : List.of();
            case "cabeca", "cabeça":
                return args.length == 2 ? Tab.of(args[1], dev.vertexx.model.CustomHeads.names()) : List.of();
            case "boss":
                return plugin.bosses().tab(args);
            case "rejeitado", "rejeitados", "fauna":
                return plugin.fauna().tab(args);
            case "versao", "atualizar":
            default:
                return List.of();
        }
    }

    private List<String> tabMob(String[] args) {
        if (args.length == 2) {
            List<String> types = new ArrayList<>();
            for (EntityType type : EntityType.values()) {
                if (MobManager.accepts(type)) {
                    types.add(type.name().toLowerCase(Locale.ROOT));
                }
            }
            return Tab.of(args[1], types);
        }
        List<String> options = new ArrayList<>();
        boolean hasLevel = false;
        boolean hasElite = false;
        List<String> used = new ArrayList<>();
        for (int i = 2; i < args.length - 1; i++) {
            String token = args[i].toLowerCase(Locale.ROOT);
            used.add(token);
            hasElite |= token.equals("elite");
            hasLevel |= token.chars().allMatch(Character::isDigit) && !token.isEmpty();
        }
        if (!hasLevel) {
            options.addAll(List.of("1", "5", "10", "20", "30", "50"));
        }
        if (!hasElite) {
            options.add("elite");
        }
        for (Trait trait : Trait.values()) {
            String name = trait.name().toLowerCase(Locale.ROOT);
            if (!used.contains(name)) {
                options.add(name);
            }
        }
        return Tab.of(args[args.length - 1], options);
    }
}
