package dev.vertexx.blueprint;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import dev.vertexx.PluginMain;
import dev.vertexx.model.ModelDef;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.kyori.adventure.resource.ResourcePackInfo;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/**
 * Sistema automático de modelos: tudo que for colocado em {@code plugins/Vertexx/blueprints/*.bbmodel} é lido,
 * fatiado em ossos, compilado para um resource pack próprio, hospedado por um servidor HTTP interno e enviado aos
 * jogadores, sem nenhum comando. Salvar um arquivo de novo (no Blockbench, por exemplo) recompila na hora e atualiza
 * os mobs que já estão no mundo; apagar o arquivo remove o modelo.
 *
 * <p>Ao lado do modelo, um {@code <id>.yml} opcional (criado sozinho com valores padrão) define o mob-base, vida,
 * escala, animações, hitboxes, assento e surgimento natural (veja {@link BlueprintMobs}).
 */
public final class Blueprints {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("vertexx:blueprints".getBytes(StandardCharsets.UTF_8));

    private final PluginMain plugin;
    private final File dir;
    private final BlueprintMobs mobs;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Map<String, String> failed = new ConcurrentHashMap<>();
    private final Map<String, String> seen = new ConcurrentHashMap<>();   // id → assinatura do último estado lido
    private volatile byte[] packBytes;
    private volatile String packHash = "";
    private volatile String publicHost = "";
    private HttpServer http;
    private BukkitTask poll;
    private boolean scanning;

    /** Um modelo compilado. */
    record Entry(String id, BbCompiler.Result result, ModelDef def, BlueprintMobs.Spec spec, String signature) {
    }

    public Blueprints(PluginMain plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "blueprints");
        this.mobs = new BlueprintMobs(plugin, this);
    }

    public BlueprintMobs mobs() {
        return mobs;
    }

    boolean enabled() {
        return plugin.getConfig().getBoolean("blueprints.enabled", true);
    }

    File dir() {
        return dir;
    }

    public List<String> ids() {
        return new ArrayList<>(new java.util.TreeSet<>(entries.keySet()));
    }

    /** Nome exibido do blueprint (do .yml). */
    public String displayName(String id) {
        Entry e = entries.get(id);
        return e == null ? BlueprintMobs.prettify(id) : e.spec().name;
    }

    Entry entry(String id) {
        return entries.get(id);
    }

    // ── Ciclo ──────────────────────────────────────────────────

    public void start() {
        if (!enabled()) {
            return;
        }
        try {
            Files.createDirectories(dir.toPath());
            File readme = new File(dir, "LEIA-ME.txt");
            if (!readme.exists()) {
                Files.writeString(readme.toPath(), """
                        Blueprints do Vertexx
                        =====================
                        Coloque aqui arquivos .bbmodel salvos no Blockbench (com a textura embutida).
                        Nenhum comando é necessário: o plugin detecta o arquivo, fatia o modelo em ossos,
                        gera o resource pack e envia aos jogadores. Salvar o arquivo de novo atualiza tudo.

                        - O nome do arquivo vira o id do modelo (so letras minusculas, numeros e _).
                        - Um arquivo <id>.yml com o mesmo nome e criado ao lado com as opcoes do mob
                          (mob-base, vida, escala, animacoes, hitboxes, assento, surgimento natural).
                        - Locators chamados "hitbox", "hitbox_larga"... viram sub-hitboxes (areas de dano).
                        - Um locator chamado "seat" e o assento de quem monta (rideable: true no .yml).
                        - Animacoes: idle, walk, run, fly, attack, hurt e death sao usadas sozinhas.
                        - Ossos com nome terminado em _glow brilham no escuro.
                        - O ovo de cada mob fica no painel de administracao (Ovos e itens).
                        """, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível preparar a pasta de blueprints: " + e);
            return;
        }
        startHttp();
        detectHost();
        // varredura leve a cada 3 s (compara tamanho e data); a compilação só acontece quando algo mudou
        poll = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::scan, 20L, 60L);
        mobs.start();
    }

    public void shutdown() {
        if (poll != null) {
            poll.cancel();
        }
        mobs.shutdown();
        if (http != null) {
            http.stop(0);
        }
    }

    // ── Varredura e compilação ──────────────────────────────────

    private static String slug(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "model" : s;
    }

    private void scan() {
        if (scanning) {
            return;
        }
        scanning = true;
        try {
            File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".bbmodel"));
            Map<String, File> found = new TreeMap<>();
            if (files != null) {
                for (File f : files) {
                    found.put(slug(f.getName().substring(0, f.getName().length() - ".bbmodel".length())), f);
                }
            }
            boolean changed = false;
            List<String> touched = new ArrayList<>();
            for (Map.Entry<String, File> en : found.entrySet()) {
                String id = en.getKey();
                File file = en.getValue();
                File yml = new File(dir, id + ".yml");
                String sig = file.length() + ":" + file.lastModified() + ":" + yml.length() + ":" + yml.lastModified();
                if (sig.equals(seen.get(id))) {
                    continue;
                }
                // arquivo ainda sendo gravado (o Blockbench escreve em partes): espera estabilizar
                Thread.sleep(250);
                String again = file.length() + ":" + file.lastModified() + ":" + yml.length() + ":" + yml.lastModified();
                if (!again.equals(sig)) {
                    continue;
                }
                seen.put(id, sig);
                changed |= compile(id, file, yml);
                touched.add(id);
            }
            List<String> removed = new ArrayList<>();
            for (String id : new ArrayList<>(entries.keySet())) {
                if (!found.containsKey(id)) {
                    entries.remove(id);
                    seen.remove(id);
                    removed.add(id);
                    changed = true;
                }
            }
            if (changed) {
                rebuildPack();
                List<String> upd = touched.stream().filter(entries::containsKey).toList();
                Bukkit.getScheduler().runTask(plugin, () -> applyChanges(upd, removed));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Falha na varredura de blueprints: " + e);
        } finally {
            scanning = false;
        }
    }

    private boolean compile(String id, File file, File yml) {
        try {
            JsonObject bb = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
            BbCompiler.Result result = BbCompiler.compile(id, bb);
            if (!yml.exists()) {
                Files.writeString(yml.toPath(), BlueprintMobs.template(id, result), StandardCharsets.UTF_8);
                seen.put(id, file.length() + ":" + file.lastModified() + ":" + yml.length() + ":" + yml.lastModified());
            }
            BlueprintMobs.Spec spec = BlueprintMobs.Spec.read(id, yml, plugin);
            ModelDef def = ModelDef.parse(id, result.runtime());
            entries.put(id, new Entry(id, result, def, spec, seen.get(id)));
            failed.remove(id);
            plugin.getLogger().info("Blueprint '" + id + "': " + result.bones() + " ossos, " + result.animations().size()
                    + " animações, " + result.locators().size() + " localizadores.");
            return true;
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            if (!msg.equals(failed.put(id, msg))) {
                plugin.getLogger().warning("Blueprint '" + id + "' ignorado: " + msg
                        + (entries.containsKey(id) ? " (segue valendo a versão anterior)" : ""));
            }
            return false;
        }
    }

    private void rebuildPack() {
        Map<String, byte[]> files = new TreeMap<>();
        for (Entry en : entries.values()) {
            files.putAll(en.result().files());
        }
        if (files.isEmpty()) {
            packBytes = null;
            packHash = "";
            return;
        }
        files.put("pack.mcmeta", defaultMeta().getBytes(StandardCharsets.UTF_8));
        writePack(files);
    }

    private static String defaultMeta() {
        return "{\"pack\":{\"description\":\"§dVertexx §7— modelos automáticos\",\"min_format\":[97,0],\"max_format\":[99,0]}}";
    }

    private void writePack(Map<String, byte[]> files) {
        try {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
                for (Map.Entry<String, byte[]> en : new TreeMap<>(files).entrySet()) {
                    ZipEntry ze = new ZipEntry(en.getKey());
                    ze.setTime(0L);                      // determinístico: o SHA-1 só muda se o conteúdo mudar
                    zip.putNextEntry(ze);
                    zip.write(en.getValue());
                    zip.closeEntry();
                }
            }
            byte[] bytes = buffer.toByteArray();
            packBytes = bytes;
            packHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
            Files.write(new File(plugin.getDataFolder(), "blueprints-pack.zip").toPath(), bytes);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao montar o pack dos blueprints: " + e);
        }
    }

    /** Thread principal: registra os modelos e atualiza mobs e jogadores. */
    private void applyChanges(List<String> updated, List<String> removed) {
        for (String id : removed) {
            plugin.entityModels().unregister(id);
            mobs.dropModel(id);
        }
        for (String id : updated) {
            Entry en = entries.get(id);
            if (en != null) {
                plugin.entityModels().register(id, en.def());
            }
        }
        mobs.refresh(updated);
        plugin.resourcePack().refreshOnline();
    }

    // ── Pack hospedado ──────────────────────────────────────────

    /** Informações do pack para enviar aos jogadores, ou nulo se não houver modelos ou endereço. */
    public ResourcePackInfo packInfo() {
        String hash = packHash;
        String host = publicHost;
        if (http == null || hash.isEmpty() || host.isEmpty() || packBytes == null) {
            return null;
        }
        try {
            int port = plugin.getConfig().getInt("blueprints.http-port", 8765);
            String hostPart = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
            return ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create("http://" + hostPart + ":" + port + "/" + hash + ".zip"), hash);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("blueprints.public-host inválido: " + e.getMessage());
            return null;
        }
    }

    public UUID packId() {
        return PACK_ID;
    }

    private void startHttp() {
        int port = plugin.getConfig().getInt("blueprints.http-port", 8765);
        try {
            http = HttpServer.create(new InetSocketAddress(port), 0);
            http.createContext("/", exchange -> {
                try (exchange) {
                    byte[] bytes = packBytes;
                    String path = exchange.getRequestURI().getPath();
                    if (bytes == null || !path.equals("/" + packHash + ".zip")) {
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }
                    exchange.getResponseHeaders().add("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            http.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "Vertexx-Blueprints-HTTP");
                t.setDaemon(true);
                return t;
            }));
            http.start();
            plugin.getLogger().info("Servidor de pack dos blueprints na porta " + port
                    + " (libere a porta TCP " + port + " no firewall para os jogadores).");
        } catch (IOException e) {
            http = null;
            plugin.getLogger().warning("Não foi possível abrir a porta " + port + " para o pack dos blueprints: " + e.getMessage()
                    + ". Mude blueprints.http-port no config.yml.");
        }
    }

    /** Endereço que os jogadores usam para baixar o pack: config, server-ip, IP público (opcional) ou o da máquina. */
    private void detectHost() {
        String manual = plugin.getConfig().getString("blueprints.public-host", "").strip();
        if (!manual.isEmpty()) {
            publicHost = manual;
            return;
        }
        String serverIp = Bukkit.getIp().strip();
        if (!serverIp.isEmpty() && !serverIp.equals("0.0.0.0")) {
            publicHost = serverIp;
            return;
        }
        try {
            publicHost = InetAddress.getLocalHost().getHostAddress();
        } catch (IOException e) {
            publicHost = "127.0.0.1";
        }
        if (plugin.getConfig().getBoolean("blueprints.detect-public-ip", true)) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    HttpResponse<String> r = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(
                            HttpRequest.newBuilder(URI.create("https://api.ipify.org")).timeout(Duration.ofSeconds(8)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    String ip = r.body().strip();
                    if (r.statusCode() == 200 && ip.matches("[0-9a-fA-F:.]{3,45}")) {
                        publicHost = ip;
                        plugin.getLogger().info("Endereço público detectado para o pack dos blueprints: " + ip
                                + " (fixe com blueprints.public-host se estiver errado).");
                        Bukkit.getScheduler().runTask(plugin, () -> plugin.resourcePack().refreshOnline());
                    }
                } catch (IOException e) {
                    // sem internet: segue com o endereço local
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    Map<String, Entry> all() {
        return new LinkedHashMap<>(entries);
    }
}
