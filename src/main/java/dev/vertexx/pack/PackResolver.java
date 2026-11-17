package dev.vertexx.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import dev.vertexx.update.ReleaseSource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/**
 * Descobre sozinho o resource pack do servidor: consulta a release mais recente do repositório,
 * baixa o {@code Vertexx-Pack.zip}, valida o conteúdo e calcula o SHA-1. O resultado fica em cache
 * ({@code pack-cache.properties}) para o servidor já ter o link ao reiniciar, mesmo sem internet.
 * Toda a rede roda fora da thread principal. Um {@code resource-pack.url} manual no config tem prioridade.
 * <p>
 * Falhas passageiras (rede fora, release ainda subindo os arquivos) são retentadas em 2 min, até 5 vezes,
 * em vez de esperar o ciclo de 30 min. O pacote só é trocado depois de baixado e validado por inteiro.
 */
public final class PackResolver {

    private static final long MAX_BYTES = 40L * 1024 * 1024;

    private final PluginMain plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final AtomicBoolean checking = new AtomicBoolean();
    private final File cacheFile;
    private volatile String url = "";
    private volatile String sha1 = "";
    private volatile String key = "";
    private volatile String tag = "";
    private volatile int retries;

    public PackResolver(PluginMain plugin) {
        this.plugin = plugin;
        this.cacheFile = new File(plugin.getDataFolder(), "pack-cache.properties");
        loadCache();
    }

    public String url() {
        return url;
    }

    public String sha1() {
        return sha1;
    }

    public boolean isReady() {
        return !url.isEmpty() && !sha1.isEmpty();
    }

    /** Versão (tag) do pacote em uso, ou vazio. */
    public String tag() {
        return tag;
    }

    /** Agenda a checagem periódica (a primeira, logo após a inicialização). */
    public void start() {
        if (!plugin.getConfig().getBoolean("resource-pack.auto.enabled", true)) {
            return;
        }
        long interval = Math.max(5, plugin.getConfig().getLong("updater.check-interval-minutes", 30)) * 60 * 20;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> check(null, false), 20L * 5, interval);
    }

    /** Checa agora (assíncrono), ignorando o resultado em memória, e responde a {@code feedback}. */
    public void checkNow(CommandSender feedback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> check(feedback, true));
    }

    private void tell(CommandSender feedback, String message) {
        if (feedback != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> Msg.send(feedback, message));
        }
    }

    private void retrySoon() {
        if (!plugin.isEnabled() || retries >= 5) {
            return;
        }
        retries++;
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> check(null, false), 20L * 120);
    }

    private void loadCache() {
        if (!cacheFile.isFile()) {
            return;
        }
        try (InputStream in = Files.newInputStream(cacheFile.toPath())) {
            Properties props = new Properties();
            props.load(in);
            url = props.getProperty("url", "");
            sha1 = props.getProperty("sha1", "");
            key = props.getProperty("key", "");
            tag = props.getProperty("tag", "");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Cache do resource pack ilegível, será refeito: " + e);
        }
    }

    private void saveCache() {
        Properties props = new Properties();
        props.setProperty("url", url);
        props.setProperty("sha1", sha1);
        props.setProperty("key", key);
        props.setProperty("tag", tag);
        try {
            Files.createDirectories(cacheFile.toPath().getParent());
            // grava num temporário e troca: um desligamento no meio não deixa o cache pela metade
            File temp = new File(cacheFile.getPath() + ".tmp");
            try (var out = Files.newOutputStream(temp.toPath())) {
                props.store(out, "Gerado automaticamente pelo Vertexx");
            }
            Files.move(temp.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível salvar o cache do resource pack: " + e);
        }
    }

    /** Bloqueante: chame fora da thread principal. */
    private void check(CommandSender feedback, boolean force) {
        if (!checking.compareAndSet(false, true)) {
            tell(feedback, "<yellow>Já existe uma checagem do resource pack em andamento.");
            return;
        }
        try {
            String repository = plugin.getConfig().getString("updater.repository", "").strip();
            String assetName = plugin.getConfig().getString("resource-pack.auto.asset", "Vertexx-Pack.zip").strip();
            if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") || assetName.isEmpty()) {
                return;
            }
            JsonObject release = ReleaseSource.latest(repository, "Vertexx-PackResolver", force, "Vertexx-{v}.jar", assetName);
            if (release == null) {
                tell(feedback, "<red>Resource pack: não consegui consultar o GitHub agora.");
                retrySoon();
                return;
            }
            String releaseTag = release.get("tag_name").getAsString();
            JsonObject asset = findAsset(release, assetName);
            if (asset == null) {
                // a release pode ter sido criada antes de os arquivos terminarem de subir
                plugin.getLogger().info("A release " + releaseTag + " ainda não tem o arquivo " + assetName + ".");
                tell(feedback, "<yellow>Resource pack: a release " + releaseTag + " ainda não tem o " + assetName + ".");
                retrySoon();
                return;
            }
            String downloadUrl = asset.get("browser_download_url").getAsString();
            if (!downloadUrl.startsWith("https://github.com/" + repository + "/releases/download/")) {
                plugin.getLogger().warning("URL do resource pack inesperada; ignorada por segurança.");
                return;
            }
            JsonElement digest = asset.get("digest");
            boolean hasDigest = digest != null && !digest.isJsonNull() && digest.getAsString().startsWith("sha256:");
            // identifica o arquivo: o SHA-256 da release quando existe (pega até reenvio com o mesmo nome), senão a tag
            String newKey = hasDigest ? digest.getAsString() : "tag:" + releaseTag;
            if (newKey.equals(key) && isReady() && downloadUrl.equals(url)) {
                retries = 0;
                tell(feedback, "<green>Resource pack já está na versão mais recente (<white>" + releaseTag + "</white>).");
                return;
            }
            long size = asset.has("size") ? asset.get("size").getAsLong() : 0;
            if (size > MAX_BYTES) {
                plugin.getLogger().warning("Resource pack grande demais (" + size + " bytes); ignorado.");
                return;
            }
            HttpResponse<byte[]> file = http.send(HttpRequest.newBuilder(URI.create(downloadUrl))
                    .header("User-Agent", "Vertexx-PackResolver")
                    .header("Cache-Control", "no-cache")
                    .timeout(Duration.ofMinutes(2)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (file.statusCode() != 200) {
                throw new IOException("download respondeu com o código " + file.statusCode());
            }
            byte[] bytes = file.body();
            if (bytes.length > MAX_BYTES || !hasPackMeta(bytes)) {
                throw new IOException("o arquivo baixado não é um resource pack válido");
            }
            if (hasDigest) {
                String expected = digest.getAsString().substring("sha256:".length());
                String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                if (!expected.equalsIgnoreCase(actual)) {
                    throw new IOException("o SHA-256 do resource pack não confere com o da release");
                }
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
            boolean changed = !hash.equals(sha1) || !downloadUrl.equals(url);
            // só agora, com tudo validado, o pacote anterior é substituído
            sha1 = hash;
            url = downloadUrl;
            key = newKey;
            tag = releaseTag;
            saveCache();
            retries = 0;
            plugin.getLogger().info("Resource pack " + releaseTag + " configurado automaticamente: " + downloadUrl + " (sha1 " + hash + ")");
            tell(feedback, "<green>Resource pack atualizado para <white>" + releaseTag + "</white>.");
            if (changed) {
                // quem entrou antes da descoberta recebeu o pacote do cache (versão antiga): reenvia o novo
                Bukkit.getScheduler().runTask(plugin, () -> plugin.resourcePack().refreshOnline());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao obter o resource pack da release: " + e);
            tell(feedback, "<red>Falha ao atualizar o resource pack: <gray>" + Msg.esc(String.valueOf(e.getMessage())));
            retrySoon();
        } finally {
            checking.set(false);
        }
    }

    private static JsonObject findAsset(JsonObject release, String name) {
        if (!release.has("assets")) {
            return null;
        }
        for (JsonElement element : release.getAsJsonArray("assets")) {
            JsonObject asset = element.getAsJsonObject();
            if (asset.get("name").getAsString().equalsIgnoreCase(name)) {
                return asset;
            }
        }
        return null;
    }

    private static boolean hasPackMeta(byte[] zip) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.getName().equals("pack.mcmeta")) {
                    return true;
                }
            }
        }
        return false;
    }
}
