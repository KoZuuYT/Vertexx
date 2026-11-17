package dev.vertexx.waystone;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/** Pede um texto ao jogador pelo chat (a próxima mensagem dele vira a resposta e não aparece para os outros). */
public final class NamePrompt implements Listener {

    private record Pending(Consumer<String> answer, BukkitTask expire) {
    }

    private final PluginMain plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public NamePrompt(PluginMain plugin) {
        this.plugin = plugin;
    }

    /** Fecha o menu aberto, mostra a pergunta e entrega a resposta na thread principal. */
    public void ask(Player player, String question, Consumer<String> answer) {
        UUID id = player.getUniqueId();
        drop(id);
        player.closeInventory();
        Msg.send(player, "<yellow>" + question + " <gray>Digite no chat <dark_gray>(ou <white>cancelar<dark_gray>, expira em 60 s).");
        BukkitTask expire = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pending.remove(id) != null && player.isOnline()) {
                Msg.send(player, "<gray>Tempo esgotado: nada foi alterado.");
            }
        }, 1200L);
        pending.put(id, new Pending(answer, expire));
    }

    private void drop(UUID id) {
        Pending old = pending.remove(id);
        if (old != null) {
            old.expire().cancel();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Pending taken = pending.remove(event.getPlayer().getUniqueId());
        if (taken == null) {
            return;
        }
        event.setCancelled(true);
        taken.expire().cancel();
        Player player = event.getPlayer();
        String text = Msg.plain(event.message()).strip();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("cancelar")) {
                Msg.send(player, "<gray>Cancelado.");
            } else {
                taken.answer().accept(text);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        drop(event.getPlayer().getUniqueId());
    }
}
