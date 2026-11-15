package dev.vertexx.model;

import dev.vertexx.PluginMain;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;

/** Registro dos modelos compilados (carregados do jar uma única vez). */
public final class Models {

    private final PluginMain plugin;
    private final Map<String, ModelDef> cache = new HashMap<>();

    public Models(PluginMain plugin) {
        this.plugin = plugin;
    }

    public ModelDef get(String id) {
        return cache.computeIfAbsent(id, key -> {
            try {
                return ModelDef.load(plugin.getClass().getClassLoader(), key);
            } catch (IOException e) {
                throw new IllegalStateException("Modelo " + key + " indisponível: " + e.getMessage(), e);
            }
        });
    }

    public ModelInstance spawn(String id, NamespacedKey tag, Location at, float scaleMul) {
        return ModelInstance.spawn(get(id), tag, at, scaleMul);
    }
}
