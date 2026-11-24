package dev.vertexx.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Sincronização automática com o GitHub: consulta a release mais recente do repositório e,
 * se for mais nova que a versão em execução, baixa o jar para {@code plugins/update/}.
 * O próprio servidor troca o jar na próxima inicialização (mecanismo padrão da pasta update).
 * Toda a rede roda fora da thread principal.
 */
public final class AutoUpdater implements Listener, TabExecutor {

    private final PluginMain plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final AtomicBoolean checking = new AtomicBoolean();
    /** Versão já baixada, aguardando reinício. */
    private volatile String pendingVersion;

    public AutoUpdater(PluginMain plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::inspect);
        if (!plugin.getConfig().getBoolean("updater.enabled", true)) {
            plugin.getLogger().info("Atualização automática desativada no config.yml.");
            return;
        }
        long interval = Math.max(5, plugin.getConfig().getLong("updater.check-interval-minutes", 30)) * 60 * 20;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::cleanStale);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> check(null), 20L * 15, interval);
    }

    /** Falhas seguidas já reagendadas (zera ao dar certo). */
    private volatile int retries;

    /** Falha passageira (rede, release ainda subindo): tenta de novo em 2 min, até 5 vezes, em vez de esperar o ciclo. */
    private void retrySoon() {
        if (!plugin.isEnabled() || retries >= 5) {
            return;
        }
        retries++;
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> check(null), 20L * 120);
    }

    /**
     * Limpa a pasta update de resíduos: downloads incompletos e jars que não são mais novos que o em execução
     * (senão o próximo reinício <i>desfaria</i> uma atualização manual). Um jar mais novo vira "pendente".
     */
    private void cleanStale() {
        try {
            Path dir = Bukkit.getUpdateFolderFile().toPath();
            if (!Files.isDirectory(dir)) {
                return;
            }
            try (var files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".part")) {
                        Files.deleteIfExists(file);
                    } else if (name.startsWith(plugin.jarFile().getName().replaceFirst("-[0-9][^/]*\\.jar$", "")) && name.endsWith(".jar")) {
                        String version = jarVersion(file);
                        if (version == null || compare(version, currentVersion()) <= 0) {
                            Files.deleteIfExists(file);
                            plugin.getLogger().info("Removido da pasta update um jar velho ou inválido: " + name);
                        } else {
                            pendingVersion = version;
                        }
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não consegui limpar a pasta update: " + e);
        }
    }

    /** Versão declarada no plugin.yml do jar, ou {@code null} se não for um plugin legível. */
    private static String jarVersion(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entry = jar.getEntry("plugin.yml");
            if (entry == null) {
                return null;
            }
            try (var reader = new java.io.InputStreamReader(jar.getInputStream(entry), java.nio.charset.StandardCharsets.UTF_8)) {
                String version = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader).getString("version");
                return version == null || version.isBlank() ? null : version.strip();
            }
        } catch (IOException e) {
            return null;
        }
    }


    /** Versão que já rodou neste servidor e é MAIOR que a atual (a versão voltou atrás), ou {@code null}. */
    private volatile String rollbackFrom;
    /** Jars do Vertexx em plugins/ mais novos que o em execução (o servidor carregou o errado). */
    private volatile List<String> newerDuplicates = List.of();
    private volatile List<String> removedDuplicates = List.of();

    public String rollbackFrom() {
        return rollbackFrom;
    }

    public List<String> newerDuplicates() {
        return newerDuplicates;
    }

    /**
     * Diagnóstico de inicialização (sem precisar do log): detecta se a versão voltou atrás em relação à última que
     * rodou aqui e se há outros jars do Vertexx em plugins/ (jar duplicado faz o servidor carregar o antigo).
     * Duplicados mais antigos ou iguais são removidos ({@code updater.remove-duplicates}).
     */
    private void inspect() {
        try {
            Path marker = plugin.getDataFolder().toPath().resolve("last-version.txt");
            String previous = Files.exists(marker) ? Files.readString(marker).strip() : null;
            String current = currentVersion();
            if (previous != null && !previous.isEmpty() && compare(previous, current) > 0) {
                rollbackFrom = previous;
                plugin.getLogger().warning("A versão voltou atrás: já rodou a " + previous + " e agora está na " + current
                        + ". Veja jars duplicados em plugins/ ou se a hospedagem restaura o jar antigo.");
            } else {
                Files.createDirectories(marker.getParent());
                Files.writeString(marker, current);
            }
            Path self = plugin.jarFile().toPath().toAbsolutePath().normalize();
            Path dir = self.getParent();
            boolean remove = plugin.getConfig().getBoolean("updater.remove-duplicates", true);
            List<String> newer = new java.util.ArrayList<>();
            List<String> removed = new java.util.ArrayList<>();
            try (var files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".jar") || file.toAbsolutePath().normalize().equals(self)) {
                        continue;
                    }
                    String version = jarVersion(file);
                    if (version == null || !"Vertexx".equals(jarName(file))) {
                        continue;
                    }
                    if (compare(version, current) > 0) {
                        newer.add(name + " (" + version + ")");
                    } else if (remove) {
                        Files.deleteIfExists(file);
                        removed.add(name + " (" + version + ")");
                        plugin.getLogger().warning("Removido jar duplicado e antigo do Vertexx: " + name);
                    } else {
                        newer.add(name + " (" + version + ", antigo)");
                    }
                }
            }
            if (!newer.isEmpty()) {
                plugin.getLogger().warning("Há jars do Vertexx mais novos em plugins/: " + newer
                        + ". Apague o jar antigo para o servidor carregar o novo.");
            }
            newerDuplicates = newer;
            removedDuplicates = removed;
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Não consegui inspecionar os jars do plugin: " + e);
        }
    }

    private static String jarName(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entry = jar.getEntry("plugin.yml");
            if (entry == null) {
                return null;
            }
            try (var reader = new java.io.InputStreamReader(jar.getInputStream(entry), java.nio.charset.StandardCharsets.UTF_8)) {
                return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader).getString("name");
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * No desligamento, se há um jar mais novo esperando em plugins/update/, grava-o por cima do jar em execução. Garante
     * a atualização mesmo quando a hospedagem não aplica a pasta update. Chamar por último no onDisable.
     */
    public void applyOnShutdown() {
        try {
            Path update = Bukkit.getUpdateFolderFile().toPath().resolve(plugin.jarFile().getName());
            if (!Files.isRegularFile(update)) {
                return;
            }
            String version = jarVersion(update);
            if (version == null || compare(version, currentVersion()) <= 0) {
                return;
            }
            Path self = plugin.jarFile().toPath();
            Path temp = self.resolveSibling(self.getFileName() + ".new");
            Files.copy(update, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, self, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(update);
            plugin.getLogger().info("Vertexx " + version + " aplicado ao desligar.");
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Não consegui aplicar a atualização ao desligar (o servidor tentará na inicialização): " + e);
        }
    }

    /** Versão baixada que aguarda reinício, ou {@code null}. */
    public String pendingVersion() {
        return pendingVersion;
    }

    /** Checa o GitHub agora (assíncrono), respondendo a {@code feedback}. */
    public void checkNow(CommandSender feedback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> check(feedback));
        plugin.resourcePack().checkPackNow(feedback);
    }

    public String currentVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    // ── Checagem ────────────────────────────────────────────────

    /** Bloqueante: chame fora da thread principal. {@code feedback} pode ser {@code null}. */
    private void check(CommandSender feedback) {
        if (!checking.compareAndSet(false, true)) {
            tell(feedback, "<yellow>Já existe uma checagem em andamento.");
            return;
        }
        try {
            String repository = plugin.getConfig().getString("updater.repository", "").strip();
            if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
                tell(feedback, "<red>updater.repository inválido no config.yml (use dono/repositorio).");
                return;
            }
            JsonObject release = ReleaseSource.latest(repository, "Vertexx-Updater", feedback != null, "Vertexx-{v}.jar");
            if (release == null) {
                tell(feedback, "<red>Não consegui consultar o GitHub agora (sem resposta da página nem da API). "
                        + "Vou tentar de novo em alguns minutos.");
                retrySoon();
                return;
            }
            String latest = release.get("tag_name").getAsString().replaceFirst("^[vV]", "");
            if (compare(latest, currentVersion()) <= 0) {
                retries = 0;
                tell(feedback, "<green>O servidor já está na versão mais recente (<white>" + currentVersion() + "</white>).");
                return;
            }
            if (pendingVersion != null && compare(latest, pendingVersion) <= 0) {
                tell(feedback, "<yellow>A versão <white>" + pendingVersion + "</white> já foi baixada e será aplicada no próximo reinício.");
                return;
            }
            JsonObject asset = findJar(release);
            if (asset == null) {
                // a release pode ter sido criada antes de os arquivos terminarem de subir
                tell(feedback, "<yellow>A release " + latest + " ainda não tem o arquivo .jar; tentarei de novo em instantes.");
                retrySoon();
                return;
            }
            String url = asset.get("browser_download_url").getAsString();
            if (!url.startsWith("https://github.com/" + repository + "/releases/download/")) {
                tell(feedback, "<red>URL de download inesperada; atualização ignorada por segurança.");
                return;
            }
            tell(feedback, "<gray>Baixando a versão <white>" + latest + "</white>…");
            download(url, asset, latest);
            retries = 0;
            pendingVersion = latest;
            plugin.getLogger().info("Vertexx " + latest + " baixado para plugins/update/. Reinicie o servidor para aplicar.");
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    notifyAdmin(online);
                }
                restartIfEmpty();
            });
            tell(feedback, "<green>Versão <white>" + latest + "</white> baixada. Ela será aplicada no próximo reinício.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao checar atualizações: " + e);
            tell(feedback, "<red>Falha ao checar atualizações: <gray>" + Msg.esc(String.valueOf(e.getMessage())));
            retrySoon();
        } finally {
            checking.set(false);
        }
    }

    private static JsonObject findJar(JsonObject release) {
        if (!release.has("assets")) {
            return null;
        }
        for (JsonElement element : release.getAsJsonArray("assets")) {
            JsonObject asset = element.getAsJsonObject();
            if (asset.get("name").getAsString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return asset;
            }
        }
        return null;
    }

    /** Baixa, valida (SHA-256 da release e conteúdo do jar) e só então move para a pasta update. */
    private void download(String url, JsonObject asset, String expectedVersion) throws Exception {
        Path updateDir = Bukkit.getUpdateFolderFile().toPath();
        Files.createDirectories(updateDir);
        String jarName = plugin.jarFile().getName();
        Path partial = updateDir.resolve(jarName + ".part");
        Path target = updateDir.resolve(jarName);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Vertexx-Updater")
                .timeout(Duration.ofMinutes(2))
                .GET().build();
        HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(partial));
        try {
            if (response.statusCode() != 200) {
                throw new IOException("download respondeu com o código " + response.statusCode());
            }
            JsonElement digest = asset.get("digest");
            if (digest != null && !digest.isJsonNull() && digest.getAsString().startsWith("sha256:")) {
                String expected = digest.getAsString().substring("sha256:".length());
                if (!expected.equalsIgnoreCase(sha256(partial))) {
                    throw new IOException("o SHA-256 do arquivo baixado não confere com o da release");
                }
            }
            String inside = jarVersion(partial);
            if (inside == null) {
                throw new IOException("o arquivo baixado não é um plugin válido");
            }
            if (compare(inside, expectedVersion) != 0) {
                throw new IOException("o jar baixado é da versão " + inside + ", mas a release diz " + expectedVersion);
            }
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Compara versões numéricas separadas por ponto (ex.: 0.10.0 > 0.9.2). */
    static int compare(String a, String b) {
        String[] left = a.split("[.\\-+]");
        String[] right = b.split("[.\\-+]");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int l = i < left.length ? number(left[i]) : 0;
            int r = i < right.length ? number(right[i]) : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private static int number(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ── Avisos e reinício ───────────────────────────────────────

    private void tell(CommandSender feedback, String message) {
        if (feedback != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> Msg.send(feedback, message));
        }
    }

    private void notifyAdmin(Player player) {
        if (player.hasPermission("vertexx.admin")) {
            if (rollbackFrom != null) {
                Msg.send(player, "<red>A versão do plugin voltou atrás: já rodou a <white>" + rollbackFrom
                        + "</white>, agora está na <white>" + currentVersion() + "</white>. <gray>Veja <white>/vertexx versao</white>.");
            }
            if (!newerDuplicates.isEmpty()) {
                Msg.send(player, "<red>Há outro jar do Vertexx mais novo em plugins/: <white>" + Msg.esc(String.join(", ", newerDuplicates))
                        + "</white><red>. Apague o jar antigo.");
            }
        }
        if (pendingVersion != null && player.hasPermission("vertexx.admin")) {
            Msg.send(player, "<yellow>Vertexx <white>" + pendingVersion + "</white> já foi baixado. "
                    + "<gray>Reinicie o servidor para aplicar.");
        }
    }

    /** Opcional: desliga o servidor vazio para o painel/script de reinício aplicar a atualização. */
    private void restartIfEmpty() {
        if (pendingVersion != null && plugin.getConfig().getBoolean("updater.restart-when-empty", false)
                && Bukkit.getOnlinePlayers().isEmpty()) {
            plugin.getLogger().info("Servidor vazio: desligando para aplicar o Vertexx " + pendingVersion + ".");
            Bukkit.shutdown();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        notifyAdmin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (pendingVersion != null) {
            Bukkit.getScheduler().runTask(plugin, this::restartIfEmpty);
        }
    }

    // ── /vertexx ────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("atualizar")) {
            Msg.send(sender, "<gray>Checando a release mais recente no GitHub…");
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> check(sender));
            plugin.resourcePack().checkPackNow(sender);
            return true;
        }
        Msg.send(sender, "<gray>Versão em execução: <white>" + currentVersion() + "</white>"
                + (pendingVersion == null ? "" : " <dark_gray>| <yellow>aguardando reinício: <white>" + pendingVersion));
        Msg.send(sender, "<gray>Arquivo do plugin: <white>" + Msg.esc(plugin.jarFile().getName()) + "</white>"
                + (rollbackFrom == null ? "" : " <dark_gray>| <red>voltou de " + rollbackFrom + " para " + currentVersion()));
        if (!newerDuplicates.isEmpty()) {
            Msg.send(sender, "<red>Jars do Vertexx mais novos em plugins/: <white>" + Msg.esc(String.join(", ", newerDuplicates)));
        }
        if (!removedDuplicates.isEmpty()) {
            Msg.send(sender, "<gray>Duplicados antigos removidos: <white>" + Msg.esc(String.join(", ", removedDuplicates)));
        }
        Msg.send(sender, "<gray>Use <white>/vertexx atualizar</white> para checar o GitHub agora.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? dev.vertexx.core.Tab.of(args[0], "versao", "atualizar") : List.of();
    }
}
