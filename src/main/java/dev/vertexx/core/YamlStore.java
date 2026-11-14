package dev.vertexx.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Arquivo YAML com gravação assíncrona e atômica.
 * O snapshot é serializado na thread principal e apenas o I/O vai para a thread assíncrona.
 */
public final class YamlStore {

    private final Plugin plugin;
    private final Path path;
    private final Object lock = new Object();
    private final AtomicLong sequence = new AtomicLong();
    private long written;

    public YamlStore(Plugin plugin, String fileName) {
        this.plugin = plugin;
        this.path = plugin.getDataFolder().toPath().resolve(fileName);
    }

    /** Leitura bloqueante: chame fora da thread principal. */
    public YamlConfiguration load() {
        File file = path.toFile();
        return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    public void saveAsync(YamlConfiguration snapshot) {
        String data = snapshot.saveToString();
        long seq = sequence.incrementAndGet();
        if (!plugin.isEnabled()) {
            write(seq, data);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(seq, data));
    }

    /** Gravação síncrona, usada no desligamento. */
    public void saveNow(YamlConfiguration snapshot) {
        write(sequence.incrementAndGet(), snapshot.saveToString());
    }

    private void write(long seq, String data) {
        synchronized (lock) {
            if (seq < written) {
                return; // um snapshot mais novo já foi gravado
            }
            try {
                Files.createDirectories(path.getParent());
                Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
                Files.writeString(tmp, data, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                }
                written = seq;
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Falha ao salvar " + path.getFileName(), e);
            }
        }
    }
}
