package dev.vertexx.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Uma instância de {@link ModelDef} no mundo: uma ItemDisplay por osso (o modelo de item do resource pack
 * traz os cubos, já com a textura) animada a cada atualização.
 *
 * <p>Anti-flicker: as displays ficam ancoradas num ponto e o deslocamento do mob vai na própria transformação
 * (um único pacote por osso, sem teleportes dessincronizados); a primeira pose e as trocas de visibilidade
 * são aplicadas sem interpolação (nada cresce a partir de zero nem gira a partir da identidade); o sinal do
 * quaternion é mantido contínuo (senão o cliente interpola pelo caminho longo); poses idênticas não são reenviadas.
 */
public final class ModelInstance {

    private static final float HIDDEN = 0.0005f;
    private static final int BLEND_TICKS = 5;
    private static final float REANCHOR = 10f;

    private final ModelDef def;
    private final float scale;
    private final ItemDisplay[] displays;
    private Location anchor;

    // animação
    private ModelDef.Clip clip;
    private float time;
    private float speed = 1f;
    private ModelDef.Clip prev;
    private float prevTime;
    private int blendLeft;

    // ajustes por código
    private final Vector3f[] addRot;
    private final Vector3f[] addPos;
    private final boolean[] hidden;
    private final Vector3f[] pinPos;
    private final Quaternionf[] pinRot;

    // pose calculada (espaço do modelo, em pixels)
    private final Vector3f[] wPos;
    private final Quaternionf[] wRot;
    private final Vector3f[] wScl;
    private final Vector3f[] rest;

    // último estado enviado
    private final Vector3f[] sentT;
    private final Quaternionf[] sentQ;
    private final Vector3f[] sentS;
    private final boolean[] sentHidden;

    private Location lastAt;
    private long lastUpdateMs;
    private float roll;
    private final Quaternionf lastBasis = new Quaternionf();

    // luz e tinta
    private int lightClock;
    private int sentBlock = -1;
    private int sentSky = -1;
    private final org.bukkit.Color[] baseTint;
    private int flash;
    private static final org.bukkit.Color HURT = org.bukkit.Color.fromRGB(255, 96, 96);

    private ModelInstance(ModelDef def, NamespacedKey tag, Location at, float scaleMul) {
        this.def = def;
        this.scale = def.scale * scaleMul;
        int n = def.bones.length;
        displays = new ItemDisplay[n];
        addRot = new Vector3f[n];
        addPos = new Vector3f[n];
        hidden = new boolean[n];
        pinPos = new Vector3f[n];
        pinRot = new Quaternionf[n];
        wPos = new Vector3f[n];
        wRot = new Quaternionf[n];
        wScl = new Vector3f[n];
        rest = new Vector3f[n];
        sentT = new Vector3f[n];
        sentQ = new Quaternionf[n];
        sentS = new Vector3f[n];
        sentHidden = new boolean[n];
        baseTint = new org.bukkit.Color[n];
        anchor = at.clone();
        anchor.setYaw(0);
        anchor.setPitch(0);
        // caixa de culling: sem ela (0x0) mods de culling do cliente (Entity Culling etc.) testam só um ponto no chão
        // e escondem o modelo por intermitência; com a caixa cobrindo o modelo inteiro ele só some de verdade fora da tela
        float reach = 0f;
        for (ModelDef.Bone b : def.bones) {
            reach = Math.max(reach, b.origin().length() + 24f * b.k());
        }
        float box = 2f * (REANCHOR + reach * scale / 16f + 1f);
        for (int i = 0; i < n; i++) {
            wPos[i] = new Vector3f();
            wRot[i] = new Quaternionf();
            wScl[i] = new Vector3f(1);
            rest[i] = new Vector3f(def.bones[i].rot());
            ModelDef.Bone bone = def.bones[i];
            if (bone.item() == null) {
                continue;
            }
            ItemStack stack = new ItemStack(Material.PAPER);
            stack.editMeta(meta -> meta.setItemModel(bone.item()));
            displays[i] = anchor.getWorld().spawn(anchor, ItemDisplay.class, entity -> {
                entity.setItemStack(stack);
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                entity.setPersistent(false);
                entity.setViewRange(1.6f);
                entity.setDisplayWidth(box);
                entity.setDisplayHeight(box);
                // ossos normais recebem a luz do lugar onde o mob está (ver light()); só os emissivos ficam no máximo
                entity.setBrightness(bone.glow() ? new Display.Brightness(15, 15) : new Display.Brightness(0, 15));
                entity.setShadowRadius(0f);
                entity.setInterpolationDuration(0);
                entity.setTeleportDuration(0);
                entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(HIDDEN),
                        new Quaternionf()));
                entity.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
            });
        }
    }

    public static ModelInstance spawn(ModelDef def, NamespacedKey tag, Location at, float scaleMul) {
        return new ModelInstance(def, tag, at, scaleMul);
    }

    public ModelDef def() {
        return def;
    }

    // ── Animação ───────────────────────────────────────────────

    /** Toca a animação (com transição suave); repete se já estiver tocando a mesma. */
    public void play(String name) {
        ModelDef.Clip next = def.clips.get(name);
        if (next == null || next == clip) {
            return;
        }
        prev = clip;
        prevTime = time;
        blendLeft = prev == null ? 0 : BLEND_TICKS;
        clip = next;
        time = 0;
    }

    /** Reinicia a animação mesmo que já esteja tocando. */
    public void restart(String name) {
        ModelDef.Clip next = def.clips.get(name);
        if (next == null) {
            return;
        }
        if (next == clip) {
            time = 0;
            return;
        }
        play(name);
    }

    public String playing() {
        return clip == null ? "" : clip.name;
    }

    /** Verdadeiro quando uma animação "once" terminou. */
    public boolean finished() {
        return clip != null && clip.loop == ModelDef.Loop.ONCE && time >= clip.length;
    }

    public void speed(float value) {
        this.speed = value;
    }

    /** Avança o relógio da animação (ticks de jogo). */
    public void advance(int ticks) {
        float dt = ticks / 20f * speed;
        if (clip != null) {
            time = step(clip, time, dt);
        }
        if (blendLeft > 0) {
            blendLeft = Math.max(0, blendLeft - ticks);
            if (prev != null) {
                prevTime = step(prev, prevTime, dt);
            }
        }
    }

    private static float step(ModelDef.Clip c, float t, float dt) {
        t += dt;
        if (c.loop == ModelDef.Loop.LOOP) {
            return c.length > 0 ? t % c.length : 0;
        }
        return Math.min(t, c.length);
    }

    // ── Ajustes por código (somados à animação) ────────────────

    /** Soma graus de rotação ao osso (por exemplo, a cabeça olhando para o jogador). */
    public void rotate(String bone, float x, float y, float z) {
        int i = def.bone(bone);
        if (i >= 0) {
            (addRot[i] == null ? addRot[i] = new Vector3f() : addRot[i]).set(x, y, z);
        }
    }

    /** Soma um deslocamento em pixels do modelo ao osso. */
    public void offset(String bone, float x, float y, float z) {
        int i = def.bone(bone);
        if (i >= 0) {
            (addPos[i] == null ? addPos[i] = new Vector3f() : addPos[i]).set(x, y, z);
        }
    }

    /** Tinge um osso marcado como "_tint" (a textura é multiplicada pela cor). */
    public void tint(String bone, org.bukkit.Color color) {
        int i = def.bone(bone);
        if (i < 0 || displays[i] == null) {
            return;
        }
        baseTint[i] = color;
        if (flash <= 0) {
            paint(i, color);
        }
    }

    private void paint(int i, org.bukkit.Color color) {
        if (displays[i] == null || !displays[i].isValid()) {
            return;
        }
        ItemStack stack = displays[i].getItemStack();
        if (color == null) {
            stack.unsetData(io.papermc.paper.datacomponent.DataComponentTypes.DYED_COLOR);
        } else {
            stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.DYED_COLOR,
                    io.papermc.paper.datacomponent.item.DyedItemColor.dyedItemColor(color));
        }
        displays[i].setItemStack(stack);
    }

    /** Pisca o modelo inteiro em vermelho, como um mob comum ao levar dano. */
    public void hurtFlash(int ticks) {
        boolean was = flash > 0;
        flash = Math.max(flash, ticks);
        if (!was) {
            for (int i = 0; i < displays.length; i++) {
                org.bukkit.Color base = baseTint[i];
                paint(i, base == null ? HURT : base.mixColors(HURT));
            }
        }
    }

    private void tickFlash(int ticks) {
        if (flash <= 0) {
            return;
        }
        flash -= Math.max(1, ticks);
        if (flash <= 0) {
            for (int i = 0; i < displays.length; i++) {
                paint(i, baseTint[i]);
            }
        }
    }

    /** Ilumina os ossos com a luz do bloco onde o mob está (a ItemDisplay fica ancorada longe às vezes). */
    private void light(Location at, int ticks) {
        lightClock -= Math.max(1, ticks);
        if (lightClock > 0 && sentBlock >= 0) {
            return;
        }
        lightClock = 10;
        org.bukkit.block.Block b = at.clone().add(0, 0.3, 0).getBlock();
        if (b.getType().isOccluding()) {
            b = b.getRelative(0, 1, 0);
        }
        int block = b.getLightFromBlocks();
        int sky = b.getLightFromSky();
        if (block == sentBlock && sky == sentSky) {
            return;
        }
        sentBlock = block;
        sentSky = sky;
        Display.Brightness brightness = new Display.Brightness(block, sky);
        for (int i = 0; i < displays.length; i++) {
            if (displays[i] != null && displays[i].isValid() && !def.bones[i].glow()) {
                displays[i].setBrightness(brightness);
            }
        }
    }

    /** Tomba o modelo inteiro de lado (graus), girando em torno dos pés: animação de morte. */
    public void tilt(float degrees) {
        this.roll = degrees;
    }

    public void hideAll(boolean value) {
        java.util.Arrays.fill(hidden, value);
    }

    public void hide(String bone, boolean value) {
        int i = def.bone(bone);
        if (i >= 0) {
            hidden[i] = value;
        }
    }

    /** Solta o osso da hierarquia e o prende a uma pose absoluta (blocos relativos à base, sem girar com o corpo). */
    public void pin(String bone, Vector3f blocks, Quaternionf rotation) {
        int i = def.bone(bone);
        if (i >= 0) {
            (pinPos[i] == null ? pinPos[i] = new Vector3f() : pinPos[i]).set(blocks);
            (pinRot[i] == null ? pinRot[i] = new Quaternionf() : pinRot[i]).set(rotation);
        }
    }

    public void unpin(String bone) {
        int i = def.bone(bone);
        if (i >= 0) {
            pinPos[i] = null;
            pinRot[i] = null;
        }
    }

    /** Posição no mundo de um localizador do modelo (pontas de mão, olhos…), com a última pose calculada. */
    public Location locator(String name) {
        ModelDef.Locator loc = def.locator(name);
        if (loc == null || lastAt == null) {
            return lastAt == null ? null : lastAt.clone();
        }
        int b = loc.bone();
        Vector3f p = new Vector3f(loc.pos()).sub(def.bones[b].origin()).mul(wScl[b]);
        wRot[b].transform(p);
        p.add(wPos[b]).mul(scale / 16f);
        lastBasis.transform(p);
        return lastAt.clone().add(p.x, p.y, p.z);
    }

    // ── Pose e envio ───────────────────────────────────────────

    private void sample(ModelDef.Clip c, float t, int bone, Vector3f rot, Vector3f pos, Vector3f scl) {
        float f = t * ModelDef.FPS;
        int n = c.frames;
        int i0 = (int) Math.floor(f);
        float u = f - i0;
        int i1 = i0 + 1;
        if (c.loop == ModelDef.Loop.LOOP) {
            i0 = Math.floorMod(i0, n);
            i1 = Math.floorMod(i1, n);
        } else {
            i0 = Math.min(i0, n - 1);
            i1 = Math.min(i1, n - 1);
        }
        lerp(c.rot[bone], i0, i1, u, rot);
        lerp(c.pos[bone], i0, i1, u, pos);
        lerp(c.scl[bone], i0, i1, u, scl);
    }

    private static void lerp(float[] table, int i0, int i1, float u, Vector3f out) {
        if (table == null) {
            return;
        }
        for (int a = 0; a < 3; a++) {
            float x = table[i0 * 3 + a];
            out.setComponent(a, x + (table[i1 * 3 + a] - x) * u);
        }
    }

    private void compute() {
        Vector3f rot = new Vector3f();
        Vector3f pos = new Vector3f();
        Vector3f scl = new Vector3f();
        Vector3f rot2 = new Vector3f();
        Vector3f pos2 = new Vector3f();
        Vector3f scl2 = new Vector3f();
        float blend = blendLeft > 0 ? blendLeft / (float) BLEND_TICKS : 0f;
        for (int i = 0; i < def.bones.length; i++) {
            ModelDef.Bone bone = def.bones[i];
            if (pinPos[i] != null) {
                wPos[i].set(pinPos[i]).mul(16f / scale);
                wRot[i].set(pinRot[i]);
                wScl[i].set(1);
                continue;
            }
            rot.set(0);
            pos.set(0);
            scl.set(1);
            if (clip != null) {
                sample(clip, time, i, rot, pos, scl);
            }
            if (blend > 0 && prev != null) {
                rot2.set(0);
                pos2.set(0);
                scl2.set(1);
                sample(prev, prevTime, i, rot2, pos2, scl2);
                rot.lerp(rot2, blend);
                pos.lerp(pos2, blend);
                scl.lerp(scl2, blend);
            }
            rot.add(rest[i]);
            if (addRot[i] != null) {
                rot.add(addRot[i]);
            }
            if (addPos[i] != null) {
                pos.add(addPos[i]);
            }
            Quaternionf q = new Quaternionf().rotateZ((float) Math.toRadians(rot.z)).rotateY((float) Math.toRadians(rot.y))
                    .rotateX((float) Math.toRadians(rot.x));
            int p = bone.parent();
            if (p < 0) {
                wPos[i].set(bone.origin()).add(pos);
                wRot[i].set(q);
                wScl[i].set(scl);
            } else {
                Vector3f local = new Vector3f(bone.origin()).add(pos).sub(def.bones[p].origin()).mul(wScl[p]);
                wRot[p].transform(local);
                wPos[i].set(wPos[p]).add(local);
                wRot[i].set(wRot[p]).mul(q);
                wScl[i].set(wScl[p]).mul(scl);
            }
        }
    }

    /**
     * Move a âncora com um corte seco: a posição das displays e a translação da pose mudam no mesmo instante, sem
     * interpolação nenhuma. (Antes as duas interpolavam juntas, mas a posição e a transformação têm relógios próprios
     * no cliente: qualquer defasagem de meio tick vezes os ~10 blocos de deslocamento virava um "teleporte" visível a
     * cada vez que o mob se afastava da âncora.) A pose seguinte também vai sem interpolação.
     */
    private void reanchor(Location at) {
        anchor = at.clone();
        anchor.setYaw(0);
        anchor.setPitch(0);
        for (int i = 0; i < displays.length; i++) {
            if (displays[i] != null && displays[i].isValid()) {
                displays[i].setTeleportDuration(0);
                displays[i].teleport(anchor);
            }
            sentT[i] = null;          // próxima pose sem interpolação, junto com o teleporte
        }
    }

    /** Calcula a pose e a envia. {@code at} é a origem do modelo (pés), {@code yaw} o do Bukkit. */
    public void update(Location at, float yaw, int duration) {
        long now = System.currentTimeMillis();
        boolean stale = lastUpdateMs != 0 && now - lastUpdateMs > 750;   // ficou sem atualizar (ninguém perto, lag): sem deslizar da pose velha
        lastUpdateMs = now;
        if (anchor.getWorld() != at.getWorld() || anchor.distanceSquared(at) > REANCHOR * REANCHOR) {
            reanchor(at);
        } else if (stale) {
            java.util.Arrays.fill(sentT, null);
        }
        lastAt = at.clone();
        light(at, duration);
        tickFlash(duration);
        lastBasis.identity().rotateY((float) Math.toRadians(-yaw)).rotateZ((float) Math.toRadians(roll));
        compute();
        Vector3f offset = new Vector3f((float) (at.getX() - anchor.getX()), (float) (at.getY() - anchor.getY()),
                (float) (at.getZ() - anchor.getZ()));
        float unit = scale / 16f;
        boolean[] hiddenEff = new boolean[displays.length];   // esconder um osso esconde os filhos (ossos vêm pai antes de filho)
        for (int i = 0; i < displays.length; i++) {
            ModelDef.Bone bone = def.bones[i];
            hiddenEff[i] = hidden[i] || (bone.parent() >= 0 && hiddenEff[bone.parent()]);
            ItemDisplay display = displays[i];
            if (display == null || !display.isValid()) {
                continue;
            }
            boolean pinned = pinPos[i] != null;
            boolean gone = hiddenEff[i];
            Vector3f translation = new Vector3f(wPos[i]).mul(unit);
            Quaternionf rotation = pinned ? new Quaternionf(wRot[i]) : new Quaternionf(lastBasis).mul(wRot[i]);
            if (!pinned) {
                lastBasis.transform(translation);
            }
            translation.add(offset);
            Vector3f size = new Vector3f(wScl[i]).mul(gone ? HIDDEN : bone.k() * scale);
            if (sentQ[i] != null && sentQ[i].dot(rotation) < 0) {
                rotation.set(-rotation.x, -rotation.y, -rotation.z, -rotation.w);
            }
            boolean snap = sentT[i] == null || sentHidden[i] != gone;
            if (!snap && sentT[i].distanceSquared(translation) < 1e-8f && sentS[i].distanceSquared(size) < 1e-8f
                    && Math.abs(sentQ[i].dot(rotation)) > 0.9999999f) {
                continue;
            }
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(snap ? 0 : duration + 1);   // +1: uma atualização atrasada não deixa o modelo parar e arrancar
            display.setTransformation(new Transformation(translation, rotation, size, new Quaternionf()));
            sentT[i] = translation;
            sentQ[i] = rotation;
            sentS[i] = size;
            sentHidden[i] = gone;
        }
    }

    public boolean valid() {
        for (ItemDisplay d : displays) {
            if (d != null) {
                return d.isValid();
            }
        }
        return false;
    }

    public void remove() {
        for (ItemDisplay d : displays) {
            if (d != null) {
                d.remove();
            }
        }
    }
}
