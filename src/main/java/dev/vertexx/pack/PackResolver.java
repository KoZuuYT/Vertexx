package dev.vertexx.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vertexx.PluginMain;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.bukkit.Bukkit;

/**
 * Descobre sozinho o resource pack do servidor: consulta a release mais recente do repositório,
 * baixa o {@code Vertexx-Pack.zip}, valida o conteúdo e calcula o SHA-1. O resultado fica em cache
 * ({@code pack-cache.properties}) para o servidor já ter o link ao reiniciar, mesmo sem internet.
 * Toda a rede roda fora da thread principal. Um {@code resource-pack.url} manual no config tem prioridade.
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
    private volatile long assetId = -1;

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

    /** Agenda a checagem periódica (a primeira, logo após a inicialização). */
    public void start() {
        if (!plugin.getConfig().getBoolean("resource-pack.auto.enabled", true)) {
            return;
        }
        long interval = Math.max(5, plugin.getConfig().getLong("updater.check-interval-minutes", 30)) * 60 * 20;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::check, 20L * 5, interval);
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
            assetId = Long.parseLong(props.getProperty("asset", "-1"));
        } catch (IOException | NumberFormatException e) {
            plugin.getLogger().log(Level.WARNING, "Cache do resource pack ilegível, será refeito: " + e);
        }
    }

    private void saveCache() {
        Properties props = new Properties();
        props.setProperty("url", url);
        props.setProperty("sha1", sha1);
        props.setProperty("asset", Long.toString(assetId));
        try {
            Files.createDirectories(cacheFile.toPath().getParent());
            try (var out = Files.newOutputStream(cacheFile.toPath())) {
                props.store(out, "Gerado automaticamente pelo Vertexx");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível salvar o cache do resource pack: " + e);
        }
    }

    /** Bloqueante: chame fora da thread principal. */
    private void check() {
        if (!checking.compareAndSet(false, true)) {
            return;
        }
        try {
            String repository = plugin.getConfig().getString("updater.repository", "").strip();
            String assetName = plugin.getConfig().getString("resource-pack.auto.asset", "Vertexx-Pack.zip").strip();
            if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") || assetName.isEmpty()) {
                return;
            }
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://api.github.com/repos/" + repository + "/releases/latest"))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Vertexx-PackResolver")
                    .timeout(Duration.ofSeconds(20))
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return;
            }
            JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject asset = findAsset(release, assetName);
            if (asset == null) {
                plugin.getLogger().info("A release mais recente não tem o arquivo " + assetName
                        + "; o resource pack automático segue desativado.");
                return;
            }
            long id = asset.get("id").getAsLong();
            String downloadUrl = asset.get("browser_download_url").getAsString();
            if (!downloadUrl.startsWith("https://github.com/" + repository + "/releases/download/")) {
                plugin.getLogger().warning("URL do resource pack inesperada; ignorada por segurança.");
                return;
            }
            if (id == assetId && isReady()) {
                return;
            }
            long size = asset.has("size") ? asset.get("size").getAsLong() : 0;
            if (size > MAX_BYTES) {
                plugin.getLogger().warning("Resource pack grande demais (" + size + " bytes); ignorado.");
                return;
            }
            HttpResponse<byte[]> file = http.send(HttpRequest.newBuilder(URI.create(downloadUrl))
                    .header("User-Agent", "Vertexx-PackResolver")
                    .timeout(Duration.ofMinutes(2)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (file.statusCode() != 200) {
                throw new IOException("download respondeu com o código " + file.statusCode());
            }
            byte[] bytes = file.body();
            if (bytes.length > MAX_BYTES || !hasPackMeta(bytes)) {
                throw new IOException("o arquivo baixado não é um resource pack válido");
            }
            JsonElement digest = asset.get("digest");
            if (digest != null && !digest.isJsonNull() && digest.getAsString().startsWith("sha256:")) {
                String expected = digest.getAsString().substring("sha256:".length());
                String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                if (!expected.equalsIgnoreCase(actual)) {
                    throw new IOException("o SHA-256 do resource pack não confere com o da release");
                }
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
            assetId = id;
            sha1 = hash;
            url = downloadUrl;
            saveCache();
            plugin.getLogger().info("Resource pack configurado automaticamente: " + downloadUrl + " (sha1 " + hash + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao obter o resource pack da release: " + e);
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
