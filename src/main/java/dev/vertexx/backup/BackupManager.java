package dev.vertexx.backup;

import dev.vertexx.PluginMain;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Backups do servidor: mundos, dados dos jogadores (inventário, XP, conquistas, estatísticas), dados do Vertexx e
 * arquivos de administração, guardados em {@code vertexx-backups/<id>/} (backup.zip + meta.yml).
 * A restauração é agendada por um marcador e aplicada quando o servidor desliga (ou, no máximo, ao iniciar).
 */
public final class BackupManager implements Listener {

    private static final DateTimeFormatter ID = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final List<String> ROOT_FILES = List.of("ops.json", "whitelist.json", "banned-players.json",
            "banned-ips.json", "usercache.json");

    private final PluginMain plugin;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> new Thread(r, "Vertexx-Backup"));
    private Thread restoreHook;
    private boolean dirty = true;

    public BackupManager(PluginMain plugin) {
        this.plugin = plugin;
    }

    // ── Caminhos ────────────────────────────────────────────────

    /** Raiz do servidor (a pasta que contém plugins/). */
    static Path root(JavaPlugin plugin) {
        return plugin.getDataFolder().toPath().toAbsolutePath().normalize().getParent().getParent();
    }

    static Path folder(JavaPlugin plugin) {
        String name = plugin.getConfig().getString("backup.folder", "vertexx-backups");
        Path path = Path.of(name);
        return (path.isAbsolute() ? path : root(plugin).resolve(path)).normalize();
    }

    public Path folder() {
        return folder(plugin);
    }

    private Path pendingFile() {
        return folder().resolve("pending.yml");
    }

    // ── Início e fim ────────────────────────────────────────────

    /** Chamado no onLoad, antes de qualquer mundo existir: aplica uma restauração que o hook não conseguiu fazer. */
    public static void applyPendingAtLoad(PluginMain plugin) {
        Path pending = folder(plugin).resolve("pending.yml");
        if (!Files.isRegularFile(pending)) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(pending.toFile());
        String id = yaml.getString("id", "");
        Path dir = folder(plugin).resolve(id).normalize();
        Path log = folder(plugin).resolve("restore.log");
        BackupInfo info = id.isBlank() || !dir.startsWith(folder(plugin)) ? null : BackupInfo.read(dir);
        try {
            if (info == null) {
                plugin.getLogger().warning("Restauração pendente ignorada: o backup '" + id + "' não existe mais.");
                Files.deleteIfExists(pending);
                return;
            }
            String error = BackupRestorer.applyAtStartup(root(plugin), dir.resolve("backup.zip"), info.roots(), pending, log);
            if (error == null) {
                plugin.getLogger().info("Backup '" + info.name() + "' restaurado na inicialização.");
            } else {
                plugin.getLogger().severe("Falha ao restaurar o backup '" + info.name() + "': " + error);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao tratar a restauração pendente", e);
        }
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::autoTick, 20L * 60, 20L * 60);
    }

    public void shutdown() {
        io.shutdown();
        try {
            if (!io.awaitTermination(60, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Um backup ainda estava em andamento ao desligar e foi interrompido.");
                io.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        dirty = true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        dirty = true;
    }

    // ── Consulta ────────────────────────────────────────────────

    public boolean busy() {
        return busy.get();
    }

    /** Todos os backups, do mais novo para o mais antigo. */
    public List<BackupInfo> list() {
        List<BackupInfo> result = new ArrayList<>();
        Path folder = folder();
        if (!Files.isDirectory(folder)) {
            return result;
        }
        try (Stream<Path> dirs = Files.list(folder)) {
            for (Path dir : dirs.toList()) {
                if (Files.isDirectory(dir)) {
                    BackupInfo info = BackupInfo.read(dir);
                    if (info != null) {
                        result.add(info);
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao listar backups", e);
        }
        result.sort(Comparator.comparingLong(BackupInfo::created).reversed());
        return result;
    }

    /** Resolve por número da lista (1 = mais novo), id (ou começo dele) ou nome exato. */
    public BackupInfo find(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        List<BackupInfo> all = list();
        String text = ref.strip();
        String digits = text.startsWith("#") ? text.substring(1) : text;
        if (digits.matches("\\d{1,4}")) {
            int index = Integer.parseInt(digits);
            if (index >= 1 && index <= all.size()) {
                return all.get(index - 1);
            }
        }
        for (BackupInfo info : all) {
            if (info.id().equalsIgnoreCase(text)) {
                return info;
            }
        }
        for (BackupInfo info : all) {
            if (info.name().equalsIgnoreCase(text)) {
                return info;
            }
        }
        BackupInfo prefix = null;
        for (BackupInfo info : all) {
            if (info.id().toLowerCase(Locale.ROOT).startsWith(text.toLowerCase(Locale.ROOT))) {
                if (prefix != null) {
                    return null;
                }
                prefix = info;
            }
        }
        return prefix;
    }

    public long totalSize() {
        long total = 0;
        for (BackupInfo info : list()) {
            total += info.size();
        }
        return total;
    }

    /** Id do backup com restauração agendada, ou null. */
    public String pendingId() {
        Path file = pendingFile();
        return Files.isRegularFile(file) ? YamlConfiguration.loadConfiguration(file.toFile()).getString("id") : null;
    }

    // ── Criação ─────────────────────────────────────────────────

    /**
     * Cria um backup completo. Salva tudo na thread principal, pausa o autosave dos mundos durante a compactação
     * (assíncrona) e devolve o resultado na thread principal. Falso se já houver um backup em andamento.
     */
    public boolean create(String name, String description, String type, String author, Consumer<BackupInfo> done,
                          Consumer<String> fail) {
        if (!busy.compareAndSet(false, true)) {
            return false;
        }
        Path root = root(plugin);
        Path folder = folder();
        Map<World, Boolean> autosave = new LinkedHashMap<>();
        List<String> roots = new ArrayList<>();
        try {
            plugin.saveData();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.saveData();
            }
            for (World world : Bukkit.getWorlds()) {
                world.save();
                autosave.put(world, world.isAutoSave());
                world.setAutoSave(false);
                Path path = levelRoot(root, world);
                if (path.startsWith(root) && !path.equals(root)) {
                    addRoot(roots, root.relativize(path).toString().replace('\\', '/'));
                }
            }
            addRoot(roots, root.relativize(plugin.getDataFolder().toPath().toAbsolutePath().normalize()).toString().replace('\\', '/'));
            for (String file : ROOT_FILES) {
                if (Files.isRegularFile(root.resolve(file))) {
                    addRoot(roots, file);
                }
            }
            for (String extra : plugin.getConfig().getStringList("backup.extra-paths")) {
                Path path = root.resolve(extra).toAbsolutePath().normalize();
                if (path.startsWith(root) && !path.equals(root) && !path.startsWith(folder) && !folder.startsWith(path)
                        && Files.exists(path)) {
                    addRoot(roots, root.relativize(path).toString().replace('\\', '/'));
                }
            }
        } catch (RuntimeException e) {
            restoreAutosave(autosave);
            busy.set(false);
            fail.accept(String.valueOf(e));
            return true;
        }
        String finalName = name == null || name.isBlank() ? defaultName(type) : name.strip();
        String text = description == null ? "" : description.strip();
        int level = Math.max(0, Math.min(9, plugin.getConfig().getInt("backup.compression", 3)));
        try {
            io.execute(() -> {
                BackupInfo info = null;
                String error = null;
                try {
                    info = write(root, folder, roots, finalName, text, type, author, level);
                } catch (IOException | RuntimeException e) {
                    plugin.getLogger().log(Level.SEVERE, "Falha ao criar o backup", e);
                    error = String.valueOf(e);
                }
                BackupInfo result = info;
                String problem = error;
                finish(() -> {
                    restoreAutosave(autosave);
                    busy.set(false);
                    if (result != null) {
                        plugin.getLogger().info("Backup '" + result.name() + "' criado (" + BackupInfo.human(result.size()) + ").");
                        done.accept(result);
                    } else {
                        fail.accept(problem);
                    }
                });
            });
        } catch (RuntimeException e) {
            restoreAutosave(autosave);
            busy.set(false);
            fail.accept(String.valueOf(e));
        }
        return true;
    }

    private void finish(Runnable action) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, action);
        } else {
            action.run();
        }
    }

    private static void restoreAutosave(Map<World, Boolean> autosave) {
        for (Map.Entry<World, Boolean> entry : autosave.entrySet()) {
            entry.getKey().setAutoSave(entry.getValue());
        }
    }

    private static String defaultName(String type) {
        return switch (type) {
            case BackupInfo.AUTO -> "Backup automático";
            case BackupInfo.SAFETY -> "Antes de restaurar";
            default -> "Backup manual";
        };
    }

    /**
     * Pasta do "nível" do mundo. Nas versões novas cada dimensão fica em {@code <nível>/dimensions/...}, e os dados dos
     * jogadores, level.dat e datapacks ficam na pasta do nível, então o backup leva ela inteira.
     */
    private static Path levelRoot(Path root, World world) {
        Path path = world.getWorldFolder().toPath().toAbsolutePath().normalize();
        if (path.startsWith(root) && !path.equals(root)) {
            Path rel = root.relativize(path);
            for (int i = 1; i < rel.getNameCount(); i++) {
                if (rel.getName(i).toString().equals("dimensions")) {
                    return root.resolve(rel.subpath(0, i));
                }
            }
        }
        return path;
    }

    /** Mantém só raízes que não estão dentro de outra (world e world/dimensions viram só world). */
    private static void addRoot(List<String> roots, String rel) {
        for (String existing : roots) {
            if (rel.equals(existing) || rel.startsWith(existing + "/")) {
                return;
            }
        }
        roots.removeIf(existing -> existing.startsWith(rel + "/"));
        roots.add(rel);
    }

    private BackupInfo write(Path root, Path folder, List<String> roots, String name, String description, String type,
                             String author, int level) throws IOException {
        Files.createDirectories(folder);
        String id = ID.format(LocalDateTime.now());
        Path dir = folder.resolve(id);
        for (int n = 2; Files.exists(dir); n++) {
            id = ID.format(LocalDateTime.now()) + "-" + n;
            dir = folder.resolve(id);
        }
        Files.createDirectories(dir);
        Path tmp = dir.resolve("backup.zip.tmp");
        int files = 0;
        try (OutputStream out = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.setLevel(level);
            for (String rel : roots) {
                files += add(zip, root, root.resolve(rel), folder);
            }
        } catch (IOException | RuntimeException e) {
            deleteTree(dir);
            throw e;
        }
        Files.move(tmp, dir.resolve("backup.zip"), StandardCopyOption.REPLACE_EXISTING);
        BackupInfo info = new BackupInfo(id, name, description, System.currentTimeMillis(), Files.size(dir.resolve("backup.zip")),
                type, author, Bukkit.getMinecraftVersion(), plugin.getPluginMeta().getVersion(), List.copyOf(roots), files);
        info.write(dir);
        return info;
    }

    private int add(ZipOutputStream zip, Path root, Path start, Path skip) throws IOException {
        int[] count = {0};
        if (Files.isRegularFile(start)) {
            addFile(zip, root, start, count);
            return count[0];
        }
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return dir.toAbsolutePath().normalize().startsWith(skip) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                if (!fileName.equals("session.lock") && !fileName.endsWith(".tmp")) {
                    addFile(zip, root, file, count);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                plugin.getLogger().warning("Backup: não foi possível ler " + file + " (" + exc.getMessage() + ")");
                return FileVisitResult.CONTINUE;
            }
        });
        return count[0];
    }

    private void addFile(ZipOutputStream zip, Path root, Path file, int[] count) throws IOException {
        ZipEntry entry = new ZipEntry(root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/'));
        entry.setTime(Files.getLastModifiedTime(file).toMillis());
        try {
            if (Files.size(file) <= 32L * 1024 * 1024) {
                // lê antes de abrir a entrada: um arquivo que falha aqui é pulado sem deixar o zip pela metade
                byte[] data = Files.readAllBytes(file);
                zip.putNextEntry(entry);
                zip.write(data);
            } else {
                zip.putNextEntry(entry);
                Files.copy(file, zip);
            }
            zip.closeEntry();
            count[0]++;
        } catch (java.nio.file.NoSuchFileException e) {
            // apagado durante a cópia (chunk descarregado, arquivo temporário): ignorar
        } catch (java.util.zip.ZipException e) {
            throw e;
        } catch (IOException e) {
            plugin.getLogger().warning("Backup: não foi possível ler " + file + " (" + e.getMessage() + ")");
        }
    }

    // ── Edição ──────────────────────────────────────────────────

    public BackupInfo rename(BackupInfo info, String name) {
        return update(info.withName(name.strip()));
    }

    public BackupInfo describe(BackupInfo info, String description) {
        return update(info.withDescription(description.strip()));
    }

    private BackupInfo update(BackupInfo info) {
        try {
            info.write(folder().resolve(info.id()));
            return info;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao gravar os metadados do backup", e);
            return null;
        }
    }

    /** Duplica o backup (zip e metadados) com outro nome. Roda fora da thread principal. */
    public boolean copy(BackupInfo info, String name, String author, Consumer<BackupInfo> done, Consumer<String> fail) {
        if (!busy.compareAndSet(false, true)) {
            return false;
        }
        Path folder = folder();
        String newName = name == null || name.isBlank() ? info.name() + " (cópia)" : name.strip();
        try {
            io.execute(() -> {
                BackupInfo created = null;
                String error = null;
                try {
                    String id = ID.format(LocalDateTime.now());
                    Path dir = folder.resolve(id);
                    for (int n = 2; Files.exists(dir); n++) {
                        id = ID.format(LocalDateTime.now()) + "-" + n;
                        dir = folder.resolve(id);
                    }
                    Files.createDirectories(dir);
                    Files.copy(folder.resolve(info.id()).resolve("backup.zip"), dir.resolve("backup.zip"));
                    created = new BackupInfo(id, newName, info.description(), System.currentTimeMillis(), info.size(),
                            BackupInfo.MANUAL, author, info.minecraft(), info.plugin(), info.roots(), info.files());
                    created.write(dir);
                } catch (IOException | RuntimeException e) {
                    plugin.getLogger().log(Level.SEVERE, "Falha ao copiar o backup", e);
                    error = String.valueOf(e);
                }
                BackupInfo result = created;
                String problem = error;
                finish(() -> {
                    busy.set(false);
                    if (result != null) {
                        done.accept(result);
                    } else {
                        fail.accept(problem);
                    }
                });
            });
        } catch (RuntimeException e) {
            busy.set(false);
            fail.accept(String.valueOf(e));
        }
        return true;
    }

    /** Apaga o backup. Falso se ele é o alvo de uma restauração agendada ou se houver erro. */
    public boolean delete(BackupInfo info) {
        if (info.id().equals(pendingId())) {
            return false;
        }
        try {
            deleteTree(folder().resolve(info.id()));
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao apagar o backup", e);
            return false;
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    // ── Restauração ─────────────────────────────────────────────

    /**
     * Agenda a restauração: grava o marcador e registra o hook que aplica o backup quando o servidor desligar.
     * Antes disso cria (se configurado) um backup de segurança do estado atual.
     */
    public boolean scheduleRestore(BackupInfo info, String by, Consumer<String> done, Consumer<String> fail) {
        Consumer<String> schedule = safetyName -> {
            try {
                writePending(info, by);
                done.accept(safetyName);
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Falha ao agendar a restauração", e);
                fail.accept(String.valueOf(e));
            }
        };
        if (!plugin.getConfig().getBoolean("backup.safety-backup-before-restore", true)) {
            if (busy.get()) {
                return false;
            }
            schedule.accept(null);
            return true;
        }
        return create("Antes de restaurar " + info.name(), "Criado automaticamente antes de restaurar o backup '" + info.name() + "'.",
                BackupInfo.SAFETY, by, safety -> schedule.accept(safety.name()), fail);
    }

    private void writePending(BackupInfo info, String by) throws IOException {
        Path folder = folder();
        Files.createDirectories(folder);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", info.id());
        yaml.set("by", by);
        yaml.set("time", System.currentTimeMillis());
        Files.writeString(pendingFile(), yaml.saveToString(), StandardCharsets.UTF_8);
        registerHook(info);
    }

    private void registerHook(BackupInfo info) {
        unregisterHook();
        Path root = root(plugin);
        Path dir = folder().resolve(info.id());
        Path zip = dir.resolve("backup.zip");
        List<String> roots = List.copyOf(info.roots());
        Path pending = pendingFile();
        Path log = folder().resolve("restore.log");
        List<World> worlds = Bukkit.getWorlds();
        Path lock = (worlds.isEmpty() ? root : levelRoot(root, worlds.get(0))).resolve("session.lock");
        // carrega agora as classes que o hook vai usar: depois do desligamento o plugin já foi descarregado
        BackupRestorer.apply(root, zip, roots, true);
        restoreHook = new Thread(() -> BackupRestorer.runAfterShutdown(root, zip, roots, lock, pending, log), "Vertexx-Restore");
        Runtime.getRuntime().addShutdownHook(restoreHook);
    }

    private void unregisterHook() {
        if (restoreHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(restoreHook);
            } catch (IllegalStateException ignored) {
                // o JVM já está desligando
            }
            restoreHook = null;
        }
    }

    /** Cancela a restauração agendada. Falso se não havia nenhuma. */
    public boolean cancelRestore() {
        unregisterHook();
        try {
            return Files.deleteIfExists(pendingFile());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao cancelar a restauração", e);
            return false;
        }
    }

    // ── Automático ──────────────────────────────────────────────

    private void autoTick() {
        var config = plugin.getConfig();
        if (!config.getBoolean("backup.auto.enabled", false) || busy.get() || !plugin.isReady()) {
            return;
        }
        boolean online = !Bukkit.getOnlinePlayers().isEmpty();
        if (!online && !dirty) {
            return;
        }
        long interval = Math.max(5, config.getLong("backup.auto.interval-minutes", 60)) * 60_000L;
        long last = 0;
        for (BackupInfo info : list()) {
            if (info.type().equals(BackupInfo.AUTO)) {
                last = info.created();
                break;
            }
        }
        if (System.currentTimeMillis() - last < interval) {
            return;
        }
        boolean wasEmpty = !online;
        create("Backup automático " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")), "",
                BackupInfo.AUTO, "servidor", info -> {
                    if (wasEmpty) {
                        dirty = false;
                    }
                    prune(Math.max(1, config.getInt("backup.auto.keep", 5)));
                }, error -> plugin.getLogger().warning("Backup automático falhou: " + error));
    }

    /** Remove os automáticos mais antigos além do limite (manuais e de segurança nunca são apagados sozinhos). */
    private void prune(int keep) {
        int seen = 0;
        for (BackupInfo info : list()) {
            if (info.type().equals(BackupInfo.AUTO) && ++seen > keep && !info.id().equals(pendingId())) {
                delete(info);
            }
        }
    }
}
