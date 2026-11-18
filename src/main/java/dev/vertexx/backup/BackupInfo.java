package dev.vertexx.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Metadados de um backup, gravados em {@code meta.yml} ao lado do {@code backup.zip}.
 * {@code roots} são as pastas/arquivos (relativos à raiz do servidor) que o zip contém.
 */
public record BackupInfo(String id, String name, String description, long created, long size, String type, String author,
                         String minecraft, String plugin, List<String> roots, int files) {

    public static final String MANUAL = "manual";
    public static final String AUTO = "auto";
    public static final String SAFETY = "seguranca";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public BackupInfo withName(String newName) {
        return new BackupInfo(id, newName, description, created, size, type, author, minecraft, plugin, roots, files);
    }

    public BackupInfo withDescription(String text) {
        return new BackupInfo(id, name, text, created, size, type, author, minecraft, plugin, roots, files);
    }

    public String date() {
        return DATE.format(Instant.ofEpochMilli(created).atZone(ZoneId.systemDefault()));
    }

    public String typeLabel() {
        return switch (type) {
            case AUTO -> "automático";
            case SAFETY -> "segurança";
            default -> "manual";
        };
    }

    public static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    static BackupInfo read(Path dir) {
        Path file = dir.resolve("meta.yml");
        if (!Files.isRegularFile(file) || !Files.isRegularFile(dir.resolve("backup.zip"))) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        long size;
        try {
            size = Files.size(dir.resolve("backup.zip"));
        } catch (IOException e) {
            size = yaml.getLong("size");
        }
        return new BackupInfo(dir.getFileName().toString(), yaml.getString("name", dir.getFileName().toString()),
                yaml.getString("description", ""), yaml.getLong("created"), size, yaml.getString("type", MANUAL),
                yaml.getString("author", "?"), yaml.getString("minecraft", "?"), yaml.getString("plugin", "?"),
                yaml.getStringList("roots"), yaml.getInt("files"));
    }

    void write(Path dir) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", name);
        yaml.set("description", description);
        yaml.set("created", created);
        yaml.set("size", size);
        yaml.set("type", type);
        yaml.set("author", author);
        yaml.set("minecraft", minecraft);
        yaml.set("plugin", plugin);
        yaml.set("roots", roots);
        yaml.set("files", files);
        Files.createDirectories(dir);
        Path tmp = dir.resolve("meta.yml.tmp");
        Files.writeString(tmp, yaml.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
        Files.move(tmp, dir.resolve("meta.yml"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
