package dev.vertexx.core;

import dev.vertexx.PluginMain;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Recipe;

/** Mostra no livro de receitas do jogo todas as receitas do Vertexx (mochilas, blocos, Pedra de Warp...). */
public final class RecipeBook implements Listener {

    private final PluginMain plugin;
    private List<NamespacedKey> keys = List.of();

    public RecipeBook(PluginMain plugin) {
        this.plugin = plugin;
    }

    /** Chame depois de todas as receitas estarem registradas. */
    public void start() {
        keys = collect();
        Bukkit.getOnlinePlayers().forEach(this::discover);
    }

    private List<NamespacedKey> collect() {
        List<NamespacedKey> found = new ArrayList<>();
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            if (iterator.next() instanceof Keyed keyed && keyed.getKey().getNamespace().equals(plugin.getName().toLowerCase())) {
                found.add(keyed.getKey());
            }
        }
        return found;
    }

    private void discover(Player player) {
        if (!keys.isEmpty()) {
            player.discoverRecipes(keys);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        discover(event.getPlayer());
    }
}
