package dev.vertexx.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.joml.Vector3f;

/**
 * Modelo de entidade já compilado (gerado por {@code tools/models/convert.py} a partir de um .bbmodel):
 * árvore de ossos com pivôs, rotação de repouso, modelo de item de cada osso, localizadores e
 * animações pré-amostradas a 20 quadros por segundo.
 */
public final class ModelDef {

    public static final int FPS = 20;

    /** Um osso: pivô em pixels (espaço do modelo, frente em +Z) e o modelo de item com seus cubos. */
    public record Bone(String name, int parent, Vector3f origin, Vector3f rot, NamespacedKey item, float k, boolean glow) {
    }

    public record Locator(String name, int bone, Vector3f pos) {
    }

    public enum Loop { LOOP, ONCE, HOLD }

    /** Animação: por osso (índice) tabelas de quadros {@code [rot, pos, scl][frame*3 + eixo]}, ou nulas. */
    public static final class Clip {
        public final String name;
        public final float length;
        public final Loop loop;
        public final int frames;
        final float[][] rot;
        final float[][] pos;
        final float[][] scl;

        Clip(String name, float length, Loop loop, int frames, int bones) {
            this.name = name;
            this.length = length;
            this.loop = loop;
            this.frames = frames;
            this.rot = new float[bones][];
            this.pos = new float[bones][];
            this.scl = new float[bones][];
        }
    }

    public final String id;
    public final float scale;
    public final Bone[] bones;
    public final Locator[] locators;
    public final Map<String, Clip> clips = new LinkedHashMap<>();
    private final Map<String, Integer> byName = new HashMap<>();

    private ModelDef(String id, float scale, Bone[] bones, Locator[] locators) {
        this.id = id;
        this.scale = scale;
        this.bones = bones;
        this.locators = locators;
        for (int i = 0; i < bones.length; i++) {
            byName.putIfAbsent(bones[i].name(), i);
        }
    }

    /** Índice do osso pelo nome, ou -1. */
    public int bone(String name) {
        return byName.getOrDefault(name, -1);
    }

    public Locator locator(String name) {
        for (Locator locator : locators) {
            if (locator.name().equals(name)) {
                return locator;
            }
        }
        return null;
    }

    private static Vector3f vec(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    private static float[] table(JsonArray frames) {
        float[] out = new float[frames.size() * 3];
        for (int i = 0; i < frames.size(); i++) {
            JsonArray row = frames.get(i).getAsJsonArray();
            out[i * 3] = row.get(0).getAsFloat();
            out[i * 3 + 1] = row.get(1).getAsFloat();
            out[i * 3 + 2] = row.get(2).getAsFloat();
        }
        return out;
    }

    /** Lê {@code models/<id>.json} de dentro do jar. */
    public static ModelDef load(ClassLoader loader, String id) throws IOException {
        try (InputStream in = loader.getResourceAsStream("models/" + id + ".json")) {
            if (in == null) {
                throw new IOException("modelo não encontrado: " + id);
            }
            return parse(id, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        }
    }

    /** Monta o modelo a partir dos dados de runtime (do jar ou gerados em tempo de execução pelo compilador de blueprints). */
    public static ModelDef parse(String id, JsonObject root) {
        {
            JsonArray jb = root.getAsJsonArray("bones");
            Bone[] bones = new Bone[jb.size()];
            for (int i = 0; i < bones.length; i++) {
                JsonObject b = jb.get(i).getAsJsonObject();
                JsonElement m = b.get("m");
                NamespacedKey item = m == null || m.isJsonNull() ? null : NamespacedKey.fromString(m.getAsString());
                bones[i] = new Bone(b.get("n").getAsString(), b.get("p").getAsInt(), vec(b.getAsJsonArray("o")),
                        vec(b.getAsJsonArray("r")), item, b.get("k").getAsFloat(), b.get("g").getAsBoolean());
            }
            JsonArray jl = root.getAsJsonArray("loc");
            Locator[] locators = new Locator[jl.size()];
            for (int i = 0; i < locators.length; i++) {
                JsonObject l = jl.get(i).getAsJsonObject();
                locators[i] = new Locator(l.get("n").getAsString(), l.get("b").getAsInt(), vec(l.getAsJsonArray("p")));
            }
            ModelDef def = new ModelDef(id, root.get("scale").getAsFloat(), bones, locators);
            JsonObject anims = root.getAsJsonObject("anim");
            for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
                JsonObject a = e.getValue().getAsJsonObject();
                Loop loop = switch (a.get("loop").getAsString()) {
                    case "loop" -> Loop.LOOP;
                    case "hold" -> Loop.HOLD;
                    default -> Loop.ONCE;
                };
                Clip clip = new Clip(e.getKey(), a.get("len").getAsFloat(), loop, a.get("n").getAsInt(), bones.length);
                for (Map.Entry<String, JsonElement> be : a.getAsJsonObject("b").entrySet()) {
                    int index = def.bone(be.getKey());
                    if (index < 0) {
                        continue;
                    }
                    JsonObject ch = be.getValue().getAsJsonObject();
                    if (ch.has("rot")) {
                        clip.rot[index] = table(ch.getAsJsonArray("rot"));
                    }
                    if (ch.has("pos")) {
                        clip.pos[index] = table(ch.getAsJsonArray("pos"));
                    }
                    if (ch.has("scl")) {
                        clip.scl[index] = table(ch.getAsJsonArray("scl"));
                    }
                }
                def.clips.put(e.getKey(), clip);
            }
            return def;
        }
    }
}
