package dev.vertexx.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Fonte única da "release mais recente", usada pelo atualizador do plugin e pelo resource pack.
 * <p>
 * A tag vem do redirecionamento de {@code github.com/<repo>/releases/latest}: sem limite de requisições e
 * sempre atual (a API fica atrás de cache e conta contra o limite de 60/h por IP). Com a tag em mãos, a API
 * ({@code releases/tags/<tag>}) só complementa com os assets e o SHA-256; se ela falhar, a release é montada
 * a partir da tag. Só se a página falhar é que se recorre ao {@code releases/latest} da API.
 * O resultado fica 90 s em memória para os dois consumidores não repetirem a consulta.
 */
public final class ReleaseSource {

    private static final long TTL_MS = 90_000;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static JsonObject cached;
    private static String cachedRepository = "";
    private static long cachedAt;

    private ReleaseSource() {
    }

    /**
     * Bloqueante: chame fora da thread principal. {@code assetPatterns} descrevem os arquivos esperados e só
     * valem quando a API não responde ({@code {v}} vira a versão). Retorna {@code null} se nada funcionou.
     */
    public static synchronized JsonObject latest(String repository, String userAgent, boolean force, String... assetPatterns)
            throws InterruptedException {
        long now = System.currentTimeMillis();
        if (!force && cached != null && cachedRepository.equals(repository) && now - cachedAt < TTL_MS) {
            return cached;
        }
        JsonObject release = null;
        Optional<String> tag = GithubLatest.tag(repository, userAgent);
        if (tag.isPresent()) {
            release = api(repository, "tags/" + tag.get(), userAgent);
            if (release == null) {
                release = GithubLatest.synthetic(repository, tag.get(), assetPatterns);
            }
        } else {
            release = api(repository, "latest", userAgent);
        }
        if (release != null) {
            cached = release;
            cachedRepository = repository;
            cachedAt = now;
        }
        return release;
    }

    /** Esquece o resultado em memória (próxima consulta vai à rede). */
    public static synchronized void forget() {
        cached = null;
    }

    private static JsonObject api(String repository, String path, String userAgent) throws InterruptedException {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://api.github.com/repos/" + repository + "/releases/" + path))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", userAgent)
                    .header("Cache-Control", "no-cache")
                    .timeout(Duration.ofSeconds(20))
                    .GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            return json.has("tag_name") ? json : null;
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }
}
