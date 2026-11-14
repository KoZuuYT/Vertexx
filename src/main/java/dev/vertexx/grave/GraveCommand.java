package dev.vertexx.grave;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /tumulo} — lista os seus túmulos; {@code /tumulo chave} entrega Chaves de Túmulo (admin). */
public final class GraveCommand implements TabExecutor {

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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (!sender.hasPermission("vertexx.admin")) {
            return options;
        }
        if (args.length == 1 && "chave".startsWith(args[0].toLowerCase())) {
            options.add("chave");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("chave")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    options.add(online.getName());
                }
            }
        }
        return options;
    }
}
