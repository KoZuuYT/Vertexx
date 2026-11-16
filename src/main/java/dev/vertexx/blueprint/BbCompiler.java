package dev.vertexx.blueprint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compilador de {@code .bbmodel} feito em tempo de execução (porta de {@code tools/models/vx}): fatia o modelo em
 * ossos, gera os modelos de item e as texturas do resource pack (namespace {@value #NS}) e os dados de runtime
 * (ossos, localizadores e animações amostradas a 20 Hz) no mesmo formato lido por {@code ModelDef}.
 *
 * <p>Convenções iguais às do kit de modelos: o editor olha para -Z, o jogo para +Z (o modelo é girado 180° em Y);
 * elementos de item só vão de -16 a 32, então ossos grandes são reduzidos por {@code k} e a display é ampliada;
 * cubos com rotação própria viram sub-ossos.
 */
public final class BbCompiler {

    public static final String NS = "vxbp";
    private static final double LIMIT = 23.5;
    private static final int FPS = 20;
    private static final String[] FACES = {"north", "east", "south", "west", "up", "down"};

    /** Resultado: dados de runtime, arquivos do pack (caminho dentro do zip → bytes) e nomes úteis. */
    public record Result(String id, JsonObject runtime, Map<String, byte[]> files, List<String> animations,
                         List<String> locators, int bones) {
    }

    private BbCompiler() {
    }

    // ───────────────────────── estrutura ─────────────────────────

    private static final class Face {
        double[] uv;
        int tex;
        int rot;

        Face(double[] uv, int tex, int rot) {
            this.uv = uv;
            this.tex = tex;
            this.rot = rot;
        }
    }

    private static final class Cube {
        String name;
        double[] frm;
        double[] to;
        double[] origin;
        double[] rot;
        double inflate;
        Map<String, Face> faces = new LinkedHashMap<>();

        Cube copy() {
            Cube c = new Cube();
            c.name = name;
            c.frm = frm.clone();
            c.to = to.clone();
            c.origin = origin.clone();
            c.rot = rot.clone();
            c.inflate = inflate;
            c.faces = new LinkedHashMap<>(faces);
            return c;
        }
    }

    private record Locator(String name, double[] pos) {
    }

    private static final class Bone {
        String name;
        double[] origin = new double[3];
        double[] rot = new double[3];
        List<Cube> cubes = new ArrayList<>();
        List<Bone> children = new ArrayList<>();
        List<Locator> locators = new ArrayList<>();
        boolean glow;
    }

    private static final class Key {
        String channel;
        double time;
        List<Object[]> points = new ArrayList<>();      // cada ponto: x,y,z (Double ou String Molang)
        String interp = "linear";
        Map<String, double[]> bezier = new LinkedHashMap<>();
    }

    private static final class Anim {
        String name;
        double length;
        String loop;
        Map<String, List<Key>> tracks = new LinkedHashMap<>();   // "osso|canal"
    }

    private static final class Model {
        int resW = 64;
        int resH = 64;
        List<byte[]> textures = new ArrayList<>();
        List<Bone> roots = new ArrayList<>();
        List<Anim> anims = new ArrayList<>();
        boolean facingNorth = true;
    }

    // ───────────────────────── leitura do .bbmodel ─────────────────────────

    private static double[] vec(JsonElement e, double... def) {
        if (e == null || !e.isJsonArray()) {
            return def.clone();
        }
        JsonArray a = e.getAsJsonArray();
        double[] out = new double[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsDouble();
        }
        return out;
    }

    private static boolean flag(JsonObject o, String key, boolean def) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? def : e.getAsBoolean();
    }

    private static Object num(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return 0.0;
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isNumber()) {
            return p.getAsDouble();
        }
        String s = p.getAsString().trim();
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return s;       // expressão Molang
        }
    }

    private static Model read(JsonObject data) throws IOException {
        Model model = new Model();
        JsonObject meta = data.has("meta") ? data.getAsJsonObject("meta") : new JsonObject();
        boolean globalBox = flag(meta, "box_uv", false);
        if (data.has("resolution")) {
            JsonObject r = data.getAsJsonObject("resolution");
            model.resW = r.has("width") ? r.get("width").getAsInt() : 64;
            model.resH = r.has("height") ? r.get("height").getAsInt() : 64;
        }
        model.facingNorth = !data.has("vx_facing") || "north".equals(data.get("vx_facing").getAsString());

        if (data.has("textures")) {
            for (JsonElement te : data.getAsJsonArray("textures")) {
                JsonObject t = te.getAsJsonObject();
                String src = t.has("source") ? t.get("source").getAsString() : "";
                if (!src.startsWith("data:") || !src.contains(",")) {
                    throw new IOException("textura sem dados embutidos: " + (t.has("name") ? t.get("name").getAsString() : "?")
                            + " (salve o modelo no Blockbench com a textura embutida)");
                }
                model.textures.add(Base64.getMimeDecoder().decode(src.substring(src.indexOf(',') + 1)));
            }
        }
        if (model.textures.isEmpty()) {
            throw new IOException("o modelo não tem textura");
        }

        Map<String, JsonObject> elements = new LinkedHashMap<>();
        if (data.has("elements")) {
            for (JsonElement e : data.getAsJsonArray("elements")) {
                JsonObject o = e.getAsJsonObject();
                elements.put(o.get("uuid").getAsString(), o);
            }
        }

        if (data.has("outliner")) {
            Bone loose = new Bone();
            loose.name = "root";
            for (JsonElement node : data.getAsJsonArray("outliner")) {
                if (node.isJsonPrimitive()) {
                    JsonObject e = elements.get(node.getAsString());
                    if (e != null && "cube".equals(typeOf(e))) {
                        loose.cubes.add(makeCube(e, globalBox));
                    }
                } else {
                    Bone b = makeBone(node.getAsJsonObject(), elements, globalBox);
                    if (b != null) {
                        model.roots.add(b);
                    }
                }
            }
            if (!loose.cubes.isEmpty()) {
                model.roots.add(0, loose);
            }
        }

        Map<String, String> byUuid = new LinkedHashMap<>();
        collectUuids(data.has("outliner") ? data.getAsJsonArray("outliner") : new JsonArray(), byUuid);
        if (data.has("animations")) {
            for (JsonElement ae : data.getAsJsonArray("animations")) {
                JsonObject a = ae.getAsJsonObject();
                Anim anim = new Anim();
                anim.name = a.has("name") ? a.get("name").getAsString() : "anim";
                anim.length = a.has("length") ? a.get("length").getAsDouble() : 1;
                anim.loop = a.has("loop") ? a.get("loop").getAsString() : "once";
                if (a.has("animators")) {
                    for (Map.Entry<String, JsonElement> en : a.getAsJsonObject("animators").entrySet()) {
                        JsonObject an = en.getValue().getAsJsonObject();
                        String boneName = byUuid.getOrDefault(en.getKey(), an.has("name") ? an.get("name").getAsString() : null);
                        if (boneName == null || (an.has("type") && !"bone".equals(an.get("type").getAsString()))) {
                            continue;
                        }
                        if (!an.has("keyframes")) {
                            continue;
                        }
                        for (JsonElement ke : an.getAsJsonArray("keyframes")) {
                            JsonObject k = ke.getAsJsonObject();
                            String channel = k.has("channel") ? k.get("channel").getAsString() : "";
                            if (!channel.equals("rotation") && !channel.equals("position") && !channel.equals("scale")) {
                                continue;
                            }
                            Key key = new Key();
                            key.channel = channel;
                            key.time = k.has("time") ? k.get("time").getAsDouble() : 0;
                            key.interp = k.has("interpolation") ? k.get("interpolation").getAsString() : "linear";
                            if (k.has("data_points")) {
                                for (JsonElement pe : k.getAsJsonArray("data_points")) {
                                    JsonObject p = pe.getAsJsonObject();
                                    key.points.add(new Object[]{num(p.get("x")), num(p.get("y")), num(p.get("z"))});
                                }
                            }
                            if (key.points.isEmpty()) {
                                continue;
                            }
                            for (String n : new String[]{"bezier_left_time", "bezier_left_value", "bezier_right_time", "bezier_right_value"}) {
                                if (k.has(n) && k.get(n).isJsonArray()) {
                                    key.bezier.put(n, vec(k.get(n)));
                                }
                            }
                            anim.tracks.computeIfAbsent(boneName + "|" + channel, x -> new ArrayList<>()).add(key);
                        }
                    }
                }
                for (List<Key> keys : anim.tracks.values()) {
                    keys.sort((x, y) -> Double.compare(x.time, y.time));
                }
                model.anims.add(anim);
            }
        }
        return model;
    }

    private static String typeOf(JsonObject e) {
        return e.has("type") ? e.get("type").getAsString() : "cube";
    }

    private static void collectUuids(JsonArray nodes, Map<String, String> out) {
        for (JsonElement n : nodes) {
            if (n.isJsonObject()) {
                JsonObject o = n.getAsJsonObject();
                if (o.has("uuid") && o.has("name")) {
                    out.put(o.get("uuid").getAsString(), o.get("name").getAsString());
                }
                if (o.has("children")) {
                    collectUuids(o.getAsJsonArray("children"), out);
                }
            }
        }
    }

    private static Bone makeBone(JsonObject node, Map<String, JsonObject> elements, boolean globalBox) {
        if (!flag(node, "export", true)) {
            return null;
        }
        Bone b = new Bone();
        b.name = node.has("name") ? node.get("name").getAsString() : "bone";
        b.origin = vec(node.get("origin"), 0, 0, 0);
        b.rot = vec(node.get("rotation"), 0, 0, 0);
        b.glow = b.name.endsWith("_glow") || b.name.startsWith("glow_");
        if (node.has("children")) {
            for (JsonElement child : node.getAsJsonArray("children")) {
                if (child.isJsonPrimitive()) {
                    JsonObject e = elements.get(child.getAsString());
                    if (e == null) {
                        continue;
                    }
                    String kind = typeOf(e);
                    if (kind.equals("cube")) {
                        if (flag(e, "export", true) && flag(e, "visibility", true)) {
                            b.cubes.add(makeCube(e, globalBox));
                        }
                    } else if (kind.equals("locator") || kind.equals("null_object")) {
                        b.locators.add(new Locator(e.has("name") ? e.get("name").getAsString() : "loc",
                                vec(e.has("position") ? e.get("position") : e.get("from"), 0, 0, 0)));
                    }
                } else {
                    Bone sub = makeBone(child.getAsJsonObject(), elements, globalBox);
                    if (sub != null) {
                        b.children.add(sub);
                    }
                }
            }
        }
        return b;
    }

    private static Cube makeCube(JsonObject e, boolean globalBox) {
        Cube c = new Cube();
        c.name = e.has("name") ? e.get("name").getAsString() : "cube";
        c.frm = vec(e.get("from"), 0, 0, 0);
        c.to = vec(e.get("to"), 0, 0, 0);
        c.origin = vec(e.get("origin"), 0, 0, 0);
        c.rot = vec(e.get("rotation"), 0, 0, 0);
        c.inflate = e.has("inflate") ? e.get("inflate").getAsDouble() : 0;
        if (flag(e, "box_uv", globalBox)) {
            double[] off = vec(e.get("uv_offset"), 0, 0);
            double w = c.to[0] - c.frm[0];
            double h = c.to[1] - c.frm[1];
            double d = c.to[2] - c.frm[2];
            double u = off[0];
            double v = off[1];
            boolean mirror = flag(e, "mirror_uv", false);
            put(c, "up", new double[]{u + d, v, u + d + w, v + d}, mirror);
            put(c, "down", new double[]{u + d + w, v, u + d + 2 * w, v + d}, mirror);
            put(c, "east", new double[]{u, v + d, u + d, v + d + h}, mirror);
            put(c, "north", new double[]{u + d, v + d, u + d + w, v + d + h}, mirror);
            put(c, "west", new double[]{u + d + w, v + d, u + 2 * d + w, v + d + h}, mirror);
            put(c, "south", new double[]{u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h}, mirror);
        } else if (e.has("faces")) {
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject("faces").entrySet()) {
                JsonObject f = en.getValue().getAsJsonObject();
                if (!f.has("uv") || f.get("uv").isJsonNull() || !f.has("texture") || f.get("texture").isJsonNull()) {
                    continue;
                }
                c.faces.put(en.getKey(), new Face(vec(f.get("uv")), f.get("texture").getAsInt(),
                        f.has("rotation") ? f.get("rotation").getAsInt() : 0));
            }
        }
        return c;
    }

    private static void put(Cube c, String face, double[] uv, boolean mirror) {
        if (mirror) {
            double t = uv[0];
            uv[0] = uv[2];
            uv[2] = t;
        }
        c.faces.put(face, new Face(uv, 0, 0));
    }

    // ───────────────────────── editor → jogo ─────────────────────────

    private static String opposite(String face) {
        return switch (face) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            case "west" -> "east";
            default -> face;
        };
    }

    private static double[] flipPos(double[] p) {
        return new double[]{-p[0], p[1], -p[2]};
    }

    private static double[] flipRot(double[] r) {
        return new double[]{-r[0], r[1], -r[2]};
    }

    private static Object neg(Object v) {
        return v instanceof Double d ? (Object) (-d) : "-(" + v + ")";
    }

    private static void toGame(Model m) {
        if (!m.facingNorth) {
            return;
        }
        walk(m.roots, b -> {
            b.origin = flipPos(b.origin);
            b.rot = flipRot(b.rot);
            List<Locator> locs = new ArrayList<>();
            for (Locator l : b.locators) {
                locs.add(new Locator(l.name(), flipPos(l.pos())));
            }
            b.locators = locs;
            for (Cube c : b.cubes) {
                double[] nf = {-c.to[0], c.frm[1], -c.to[2]};
                double[] nt = {-c.frm[0], c.to[1], -c.frm[2]};
                c.frm = nf;
                c.to = nt;
                c.origin = flipPos(c.origin);
                c.rot = flipRot(c.rot);
                Map<String, Face> faces = new LinkedHashMap<>();
                for (Map.Entry<String, Face> en : c.faces.entrySet()) {
                    Face f = en.getValue();
                    int rot = en.getKey().equals("up") || en.getKey().equals("down") ? (f.rot + 180) % 360 : f.rot;
                    faces.put(opposite(en.getKey()), new Face(f.uv, f.tex, rot));
                }
                c.faces = faces;
            }
        });
        for (Anim a : m.anims) {
            for (List<Key> keys : a.tracks.values()) {
                for (Key k : keys) {
                    if (k.channel.equals("rotation") || k.channel.equals("position")) {
                        for (Object[] p : k.points) {
                            p[0] = neg(p[0]);
                            p[2] = neg(p[2]);
                        }
                        for (String n : new String[]{"bezier_left_value", "bezier_right_value"}) {
                            double[] v = k.bezier.get(n);
                            if (v != null && v.length >= 3) {
                                k.bezier.put(n, new double[]{-v[0], v[1], -v[2]});
                            }
                        }
                    }
                }
            }
        }
    }

    private static void walk(List<Bone> bones, java.util.function.Consumer<Bone> fn) {
        for (Bone b : bones) {
            fn.accept(b);
            walk(b.children, fn);
        }
    }

    private static String slug(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "bone" : s;
    }

    private static boolean rotated(Cube c) {
        return Math.abs(c.rot[0]) > 1e-6 || Math.abs(c.rot[1]) > 1e-6 || Math.abs(c.rot[2]) > 1e-6;
    }

    private static double r4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static void splitRotated(Bone bone) {
        List<Cube> keep = new ArrayList<>();
        Map<String, Bone> groups = new LinkedHashMap<>();
        for (Cube c : bone.cubes) {
            if (rotated(c)) {
                String key = r4(c.rot[0]) + "," + r4(c.rot[1]) + "," + r4(c.rot[2]) + "|" + r4(c.origin[0]) + ","
                        + r4(c.origin[1]) + "," + r4(c.origin[2]);
                Bone sub = groups.get(key);
                if (sub == null) {
                    sub = new Bone();
                    sub.name = bone.name + "__" + slug(c.name);
                    sub.origin = c.origin.clone();
                    sub.rot = c.rot.clone();
                    groups.put(key, sub);
                    bone.children.add(sub);
                }
                Cube cc = c.copy();
                cc.rot = new double[3];
                sub.cubes.add(cc);
            } else {
                keep.add(c);
            }
        }
        bone.cubes = keep;
        for (Bone child : new ArrayList<>(bone.children)) {
            splitRotated(child);
        }
    }

    // ───────────────────────── compilação ─────────────────────────

    private static JsonObject element(Cube cube, double[] origin, double k, Model m) {
        double pad = cube.inflate;
        double[] lo = new double[3];
        double[] hi = new double[3];
        double[] dlo = new double[3];
        double[] dhi = new double[3];
        for (int i = 0; i < 3; i++) {
            dlo[i] = (cube.frm[i] - pad - origin[i]) / k;
            dhi[i] = (cube.to[i] + pad - origin[i]) / k;
        }
        lo[0] = 8 - dhi[0];
        lo[1] = dlo[1] + 8;
        lo[2] = 8 - dhi[2];
        hi[0] = 8 - dlo[0];
        hi[1] = dhi[1] + 8;
        hi[2] = 8 - dlo[2];
        JsonObject faces = new JsonObject();
        for (String name : FACES) {
            Face f = cube.faces.get(name);
            if (f == null) {
                continue;
            }
            int rot = f.rot + (name.equals("up") || name.equals("down") ? 180 : 0);
            JsonObject entry = new JsonObject();
            JsonArray uv = new JsonArray();
            uv.add(r4(f.uv[0] * 16 / m.resW));
            uv.add(r4(f.uv[1] * 16 / m.resH));
            uv.add(r4(f.uv[2] * 16 / m.resW));
            uv.add(r4(f.uv[3] * 16 / m.resH));
            entry.add("uv", uv);
            entry.addProperty("texture", "#t" + f.tex);
            if (rot % 360 != 0) {
                entry.addProperty("rotation", rot % 360);
            }
            entry.addProperty("tintindex", 0);
            faces.add(opposite(name), entry);
        }
        JsonObject el = new JsonObject();
        el.add("from", arr(r4(lo[0]), r4(lo[1]), r4(lo[2])));
        el.add("to", arr(r4(hi[0]), r4(hi[1]), r4(hi[2])));
        el.add("faces", faces);
        return el;
    }

    private static JsonArray arr(double... v) {
        JsonArray a = new JsonArray();
        for (double d : v) {
            a.add(d);
        }
        return a;
    }

    /** Compila o conteúdo de um {@code .bbmodel}. {@code id} já deve estar normalizado (a-z, 0-9, _). */
    public static Result compile(String id, JsonObject bbmodel) throws IOException {
        Model model = read(bbmodel);
        toGame(model);
        for (Bone r : model.roots) {
            splitRotated(r);
        }
        if (model.roots.isEmpty()) {
            throw new IOException("o modelo não tem nenhum grupo/osso");
        }

        List<Bone> bones = new ArrayList<>();
        List<Integer> parents = new ArrayList<>();
        java.util.IdentityHashMap<Bone, Integer> index = new java.util.IdentityHashMap<>();
        for (Bone r : model.roots) {
            collect(r, -1, bones, parents, index);
        }

        Map<String, byte[]> files = new LinkedHashMap<>();
        List<String> texIds = new ArrayList<>();
        for (int i = 0; i < model.textures.size(); i++) {
            String rel = model.textures.size() > 1 ? id + "_" + i : id;
            files.put("assets/" + NS + "/textures/" + rel + ".png", model.textures.get(i));
            texIds.add(NS + ":" + rel);
        }

        JsonArray entries = new JsonArray();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (int bi = 0; bi < bones.size(); bi++) {
            Bone b = bones.get(bi);
            String name = slug(b.name);
            while (!used.add(name)) {
                name += "_";
            }
            String modelRef = null;
            double k = 1.0;
            if (!b.cubes.isEmpty()) {
                double extent = 0;
                for (Cube c : b.cubes) {
                    for (int i = 0; i < 3; i++) {
                        extent = Math.max(extent, Math.max(Math.abs(c.frm[i] - c.inflate - b.origin[i]),
                                Math.abs(c.to[i] + c.inflate - b.origin[i])));
                    }
                }
                k = Math.max(1.0, Math.ceil(extent / LIMIT * 100) / 100);
                JsonArray elements = new JsonArray();
                TreeSet<Integer> usedTex = new TreeSet<>();
                for (Cube c : b.cubes) {
                    if (c.faces.isEmpty()) {
                        continue;
                    }
                    elements.add(element(c, b.origin, k, model));
                    for (Face f : c.faces.values()) {
                        if (f.tex < 0 || f.tex >= texIds.size()) {
                            throw new IOException("face aponta para a textura " + f.tex + ", que não existe");
                        }
                        usedTex.add(f.tex);
                    }
                }
                if (elements.size() > 0) {
                    JsonObject textures = new JsonObject();
                    for (int t : usedTex) {
                        textures.addProperty("t" + t, texIds.get(t));
                    }
                    textures.addProperty("particle", texIds.get(usedTex.first()));
                    JsonObject mj = new JsonObject();
                    mj.add("textures", textures);
                    mj.add("elements", elements);
                    files.put("assets/" + NS + "/models/" + id + "/" + name + ".json", json(mj));
                    JsonObject def = new JsonObject();
                    def.addProperty("type", "minecraft:model");
                    def.addProperty("model", NS + ":" + id + "/" + name);
                    JsonArray tints = new JsonArray();
                    JsonObject tint = new JsonObject();
                    tint.addProperty("type", "minecraft:dye");
                    tint.addProperty("default", -1);
                    tints.add(tint);
                    def.add("tints", tints);
                    JsonObject item = new JsonObject();
                    item.add("model", def);
                    files.put("assets/" + NS + "/items/" + id + "/" + name + ".json", json(item));
                    modelRef = NS + ":" + id + "/" + name;
                }
            }
            JsonObject e = new JsonObject();
            e.addProperty("n", b.name);
            e.addProperty("p", parents.get(bi));
            e.add("o", arr(r4(b.origin[0]), r4(b.origin[1]), r4(b.origin[2])));
            e.add("r", arr(r4(b.rot[0]), r4(b.rot[1]), r4(b.rot[2])));
            if (modelRef == null) {
                e.add("m", com.google.gson.JsonNull.INSTANCE);
            } else {
                e.addProperty("m", modelRef);
            }
            e.addProperty("k", k);
            e.addProperty("g", b.glow);
            entries.add(e);
        }

        JsonArray locs = new JsonArray();
        List<String> locNames = new ArrayList<>();
        for (int bi = 0; bi < bones.size(); bi++) {
            for (Locator l : bones.get(bi).locators) {
                JsonObject o = new JsonObject();
                o.addProperty("n", l.name());
                o.addProperty("b", bi);
                o.add("p", arr(r4(l.pos()[0]), r4(l.pos()[1]), r4(l.pos()[2])));
                locs.add(o);
                locNames.add(l.name());
            }
        }

        JsonObject anims = new JsonObject();
        List<String> animNames = new ArrayList<>();
        java.util.Set<String> boneNames = new java.util.HashSet<>();
        for (Bone b : bones) {
            boneNames.add(b.name);
        }
        for (Anim a : model.anims) {
            anims.add(a.name, bake(a, boneNames));
            animNames.add(a.name);
        }

        JsonObject runtime = new JsonObject();
        runtime.addProperty("id", id);
        runtime.addProperty("scale", 1.0);
        runtime.add("bones", entries);
        runtime.add("loc", locs);
        runtime.add("anim", anims);
        return new Result(id, runtime, files, animNames, locNames, bones.size());
    }

    private static void collect(Bone b, int parent, List<Bone> bones, List<Integer> parents,
                                java.util.IdentityHashMap<Bone, Integer> index) {
        int i = bones.size();
        index.put(b, i);
        bones.add(b);
        parents.add(parent);
        for (Bone c : b.children) {
            collect(c, i, bones, parents, index);
        }
    }

    private static byte[] json(JsonObject o) {
        return o.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ───────────────────────── animações (bake a 20 Hz) ─────────────────────────

    private static JsonObject bake(Anim anim, java.util.Set<String> boneNames) {
        int total = Math.max(1, (int) Math.round(anim.length * FPS));
        boolean loop = anim.loop.equals("loop");
        int count = loop ? total : total + 1;
        Map<String, Map<String, List<Key>>> grouped = new LinkedHashMap<>();
        for (Map.Entry<String, List<Key>> en : anim.tracks.entrySet()) {
            int cut = en.getKey().lastIndexOf('|');
            String bone = en.getKey().substring(0, cut);
            if (boneNames.contains(bone)) {
                grouped.computeIfAbsent(bone, x -> new LinkedHashMap<>()).put(en.getKey().substring(cut + 1), en.getValue());
            }
        }
        JsonObject bones = new JsonObject();
        for (Map.Entry<String, Map<String, List<Key>>> en : grouped.entrySet()) {
            JsonObject entry = new JsonObject();
            String[][] channels = {{"rotation", "rot"}, {"position", "pos"}, {"scale", "scl"}};
            for (String[] ch : channels) {
                List<Key> keys = en.getValue().get(ch[0]);
                if (keys == null || keys.isEmpty()) {
                    continue;
                }
                JsonArray table = new JsonArray();
                for (int i = 0; i < count; i++) {
                    double[] v = sample(keys, i / (double) FPS, loop);
                    table.add(arr(r4(v[0]), r4(v[1]), r4(v[2])));
                }
                entry.add(ch[1], table);
            }
            if (entry.size() > 0) {
                bones.add(en.getKey(), entry);
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("len", r4(anim.length));
        out.addProperty("loop", anim.loop);
        out.addProperty("n", count);
        out.add("b", bones);
        return out;
    }

    private static double pre(Key k, int axis, double t) {
        return molang(k.points.get(0)[axis], t);
    }

    private static double post(Key k, int axis, double t) {
        return molang(k.points.get(k.points.size() - 1)[axis], t);
    }

    private static double[] sample(List<Key> frames, double t, boolean loop) {
        int n = frames.size();
        double[] out = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            if (t <= frames.get(0).time) {
                out[axis] = t >= frames.get(0).time ? post(frames.get(0), axis, t) : pre(frames.get(0), axis, t);
                continue;
            }
            if (t >= frames.get(n - 1).time) {
                out[axis] = post(frames.get(n - 1), axis, t);
                continue;
            }
            int i = 0;
            while (i + 1 < n && frames.get(i + 1).time <= t) {
                i++;
            }
            Key a = frames.get(i);
            Key b = frames.get(i + 1);
            double va = post(a, axis, t);
            double vb = pre(b, axis, t);
            double span = b.time - a.time;
            double u = span > 0 ? (t - a.time) / span : 1.0;
            if (a.interp.equals("step")) {
                out[axis] = va;
            } else if (a.interp.equals("catmullrom") || b.interp.equals("catmullrom")) {
                double p0 = neighbour(frames, i - 1, va, true, axis, t, loop);
                double p3 = neighbour(frames, i + 2, vb, false, axis, t, loop);
                out[axis] = 0.5 * ((2 * va) + (-p0 + vb) * u + (2 * p0 - 5 * va + 4 * vb - p3) * u * u
                        + (-p0 + 3 * va - 3 * vb + p3) * u * u * u);
            } else if (a.interp.equals("bezier") || b.interp.equals("bezier")) {
                out[axis] = bezier(t, a, b, axis, va, vb);
            } else {
                out[axis] = va + (vb - va) * u;
            }
        }
        return out;
    }

    private static double neighbour(List<Key> frames, int idx, double edge, boolean afterPost, int axis, double t, boolean loop) {
        int n = frames.size();
        if (idx >= 0 && idx < n) {
            return afterPost ? post(frames.get(idx), axis, t) : pre(frames.get(idx), axis, t);
        }
        if (loop && n > 2) {
            return post(frames.get(Math.floorMod(idx, n)), axis, t);
        }
        return edge;
    }

    private static double bezier(double t, Key a, Key b, int axis, double va, double vb) {
        double rt = side(a, "bezier_right_time", axis, 0.1);
        double rv = side(a, "bezier_right_value", axis, 0.0);
        double lt = side(b, "bezier_left_time", axis, -0.1);
        double lv = side(b, "bezier_left_value", axis, 0.0);
        double x0 = a.time;
        double x3 = b.time;
        double x1 = Math.min(Math.max(a.time + rt, x0), x3);
        double x2 = Math.min(Math.max(b.time + lt, x0), x3);
        double y0 = va;
        double y1 = va + rv;
        double y2 = vb + lv;
        double y3 = vb;
        double lo = 0;
        double hi = 1;
        for (int i = 0; i < 30; i++) {
            double s = (lo + hi) / 2;
            double m = 1 - s;
            double x = m * m * m * x0 + 3 * m * m * s * x1 + 3 * m * s * s * x2 + s * s * s * x3;
            if (x < t) {
                lo = s;
            } else {
                hi = s;
            }
        }
        double s = (lo + hi) / 2;
        double m = 1 - s;
        return m * m * m * y0 + 3 * m * m * s * y1 + 3 * m * s * s * y2 + s * s * s * y3;
    }

    private static double side(Key k, String name, int axis, double def) {
        double[] v = k.bezier.get(name);
        return v != null && v.length > axis ? v[axis] : def;
    }

    // ───────────────────────── Molang mínimo ─────────────────────────

    private static final Pattern TOKEN = Pattern.compile(
            "\\s*(?:(\\d+\\.?\\d*(?:[eE][+-]?\\d+)?)|([A-Za-z_][\\w.]*)|(<=|>=|==|!=|&&|\\|\\||[-+*/()?:,<>!]))");

    private static double molang(Object src, double t) {
        if (src instanceof Double d) {
            return d;
        }
        String s = String.valueOf(src).trim();
        while (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        int ret = s.indexOf("return");
        if (ret >= 0) {
            s = s.substring(ret + 6);
        }
        if (s.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            // expressão
        }
        try {
            return new Molang(s, t).expr();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static final class Molang {
        private final List<String[]> toks = new ArrayList<>();      // {tipo, texto}
        private int i;
        private final double time;

        Molang(String src, double time) {
            this.time = time;
            Matcher m = TOKEN.matcher(src);
            int pos = 0;
            while (pos < src.length()) {
                m.region(pos, src.length());
                if (!m.lookingAt()) {
                    throw new IllegalArgumentException("Molang inválido");
                }
                pos = m.end();
                if (m.group(1) != null) {
                    toks.add(new String[]{"n", m.group(1)});
                } else if (m.group(2) != null) {
                    toks.add(new String[]{"id", m.group(2).toLowerCase(Locale.ROOT)});
                } else {
                    toks.add(new String[]{"op", m.group(3)});
                }
            }
        }

        private boolean peek(String op) {
            return i < toks.size() && toks.get(i)[0].equals("op") && toks.get(i)[1].equals(op);
        }

        private boolean peekAny(String... ops) {
            for (String op : ops) {
                if (peek(op)) {
                    return true;
                }
            }
            return false;
        }

        double expr() {
            double c = logic();
            if (peek("?")) {
                i++;
                double a = expr();
                double b = 0;
                if (peek(":")) {
                    i++;
                    b = expr();
                }
                return c != 0 ? a : b;
            }
            return c;
        }

        private double logic() {
            double v = compare();
            while (peekAny("&&", "||")) {
                String op = toks.get(i++)[1];
                double r = compare();
                v = op.equals("&&") ? ((v != 0 && r != 0) ? 1 : 0) : ((v != 0 || r != 0) ? 1 : 0);
            }
            return v;
        }

        private double compare() {
            double v = add();
            while (peekAny("<", ">", "<=", ">=", "==", "!=")) {
                String op = toks.get(i++)[1];
                double r = add();
                boolean res = switch (op) {
                    case "<" -> v < r;
                    case ">" -> v > r;
                    case "<=" -> v <= r;
                    case ">=" -> v >= r;
                    case "==" -> v == r;
                    default -> v != r;
                };
                v = res ? 1 : 0;
            }
            return v;
        }

        private double add() {
            double v = mul();
            while (peekAny("+", "-")) {
                String op = toks.get(i++)[1];
                double r = mul();
                v = op.equals("+") ? v + r : v - r;
            }
            return v;
        }

        private double mul() {
            double v = unary();
            while (peekAny("*", "/")) {
                String op = toks.get(i++)[1];
                double r = unary();
                v = op.equals("*") ? v * r : (r != 0 ? v / r : 0);
            }
            return v;
        }

        private double unary() {
            if (peek("-")) {
                i++;
                return -unary();
            }
            if (peek("+")) {
                i++;
                return unary();
            }
            if (peek("!")) {
                i++;
                return unary() == 0 ? 1 : 0;
            }
            return atom();
        }

        private double atom() {
            String[] tok = toks.get(i++);
            if (tok[0].equals("n")) {
                return Double.parseDouble(tok[1]);
            }
            if (tok[0].equals("op") && tok[1].equals("(")) {
                double v = expr();
                i++;
                return v;
            }
            if (tok[0].equals("id")) {
                String name = tok[1];
                if (peek("(")) {
                    i++;
                    List<Double> args = new ArrayList<>();
                    if (!peek(")")) {
                        args.add(expr());
                        while (peek(",")) {
                            i++;
                            args.add(expr());
                        }
                    }
                    i++;
                    return call(name, args);
                }
                return switch (name) {
                    case "math.pi" -> Math.PI;
                    case "query.anim_time", "query.life_time", "time" -> time;
                    case "query.delta_time" -> 0.05;
                    default -> 0;
                };
            }
            return 0;
        }

        private static double arg(List<Double> a, int i) {
            return i < a.size() ? a.get(i) : 0;
        }

        private static double call(String fn, List<Double> a) {
            double x = arg(a, 0);
            double y = arg(a, 1);
            return switch (fn) {
                case "math.sin" -> Math.sin(Math.toRadians(x));
                case "math.cos" -> Math.cos(Math.toRadians(x));
                case "math.asin" -> Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, x))));
                case "math.acos" -> Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, x))));
                case "math.atan" -> Math.toDegrees(Math.atan(x));
                case "math.abs" -> Math.abs(x);
                case "math.sqrt" -> Math.sqrt(Math.max(0, x));
                case "math.floor" -> Math.floor(x);
                case "math.ceil" -> Math.ceil(x);
                case "math.round" -> Math.rint(x);
                case "math.trunc" -> x < 0 ? Math.ceil(x) : Math.floor(x);
                case "math.exp" -> Math.exp(x);
                case "math.ln" -> x > 0 ? Math.log(x) : 0;
                case "math.min" -> Math.min(x, y);
                case "math.max" -> Math.max(x, y);
                case "math.pow" -> Math.pow(x, y);
                case "math.mod" -> y != 0 ? x - y * Math.floor(x / y) : 0;
                case "math.atan2" -> Math.toDegrees(Math.atan2(x, y));
                case "math.clamp" -> Math.max(y, Math.min(arg(a, 2), x));
                case "math.lerp" -> x + (y - x) * arg(a, 2);
                case "math.random" -> (x + (a.size() > 1 ? y : 1)) / 2;
                default -> 0;
            };
        }
    }
}
