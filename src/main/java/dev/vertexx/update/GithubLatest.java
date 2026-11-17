package dev.vertexx.update;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Descobre a tag da release mais recente <b>sem usar a API do GitHub</b>. A API anônima limita a 60
 * requisições por hora por IP (compartilhado com outras máquinas da mesma rede), e responde 403 quando
 * estoura. A página {@code github.com/<repo>/releases/latest} não tem esse limite: ela redireciona
 * para {@code /releases/tag/<tag>}.
 */
public final class GithubLatest {

    private static final HttpClient NO_REDIRECT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private GithubLatest() {
    }

    /** Tag da release mais recente (ex.: {@code v0.15.0}), ou vazio se não houver release ou falhar. */
    public static Optional<String> tag(String repository, String userAgent) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://github.com/" + repository + "/releases/latest"))
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofSeconds(20))
                    .GET().build();
            HttpResponse<Void> response = NO_REDIRECT.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 3) {
                return Optional.empty();
            }
            String location = response.headers().firstValue("location").orElse("");
            int i = location.indexOf("/releases/tag/");
            if (i < 0) {
                return Optional.empty();
            }
            String tag = location.substring(i + "/releases/tag/".length());
            int cut = tag.indexOf('?');
            if (cut >= 0) {
                tag = tag.substring(0, cut);
            }
            return tag.matches("[A-Za-z0-9_.+-]+") ? Optional.of(tag) : Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Release "sintética" no mesmo formato da API, montada a partir da tag, para quando a API estiver
     * bloqueada (403/429). {@code asset} é o nome do arquivo; {@code {v}} nele vira a versão sem o "v".
     * O id do asset é um hash da tag (muda a cada release). Não traz SHA-256: o chamador valida o conteúdo.
     */
    public static com.google.gson.JsonObject fallbackRelease(String repository, String userAgent, String... assetPatterns) {
        Optional<String> tag = tag(repository, userAgent);
        return tag.isEmpty() ? null : synthetic(repository, tag.get(), assetPatterns);
    }

    /** Release montada só com a tag: cada padrão de arquivo vira um asset ({@code {v}} = versão sem o "v"). */
    public static com.google.gson.JsonObject synthetic(String repository, String tag, String... assetPatterns) {
        com.google.gson.JsonArray assets = new com.google.gson.JsonArray();
        for (String pattern : assetPatterns) {
            String name = pattern.replace("{v}", tag.replaceFirst("^[vV]", ""));
            com.google.gson.JsonObject a = new com.google.gson.JsonObject();
            a.addProperty("name", name);
            a.addProperty("id", tag.hashCode() & 0xffffffffL);
            a.addProperty("size", 0);
            a.addProperty("browser_download_url", downloadUrl(repository, tag, name));
            assets.add(a);
        }
        com.google.gson.JsonObject release = new com.google.gson.JsonObject();
        release.addProperty("tag_name", tag);
        release.addProperty("synthetic", true);
        release.add("assets", assets);
        return release;
    }

    public static String downloadUrl(String repository, String tag, String asset) {
        return "https://github.com/" + repository + "/releases/download/" + tag + "/" + asset;
    }
}
