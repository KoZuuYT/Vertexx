package dev.vertexx.waystone;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Modelo 3D dos Waystones feito de display entities, com animação de montagem e renderização
 * sob demanda: as entidades só existem enquanto há jogador por perto e só são enviadas a quem
 * está perto, olhando na direção do modelo e sem uma parede no meio.
 */
public final class ModelManager implements Listener {

    // ── Geometria (coordenadas relativas ao centro-base do bloco) ──────────

    private static final float ICON_Y = 1.52f;
    private static final float NAME_Y = 2.2f;
    private static final float ICON_SCALE = 0.7f;
    private static final int ICON_DELAY = 62;
    private static final int NAME_DELAY = 72;
    private static final int ASSEMBLY_END = 84;

    private enum Style {
        /** Cresce de baixo para cima. */
        RISE,
        /** Cresce a partir do centro. */
        POP,
        /** Cai de cima. */
        DROP,
        /** Abre horizontalmente a partir do centro. */
        SPREAD
    }

    private record Part(Material block, float cx, float y0, float cz, float sx, float sy, float sz,
                        int delay, int duration, Style style, boolean glow) {

        Transformation to() {
            return transform(new Vector3f(cx - sx / 2, y0, cz - sz / 2), new Vector3f(sx, sy, sz));
        }

        Transformation from() {
            float fx = 1;
            float fy = 1;
            float fz = 1;
            float oy = 0;
            switch (style) {
                case RISE -> fy = 0.001f;
                case POP -> {
                    fx = 0.001f;
                    fy = 0.001f;
                    fz = 0.001f;
                }
                case DROP -> oy = 1.1f;
                case SPREAD -> {
                    fx = 0.001f;
                    fz = 0.001f;
                }
            }
            float ssx = sx * fx;
            float ssy = sy * fy;
            float ssz = sz * fz;
            float ty = style == Style.POP ? y0 + sy / 2 - ssy / 2 : y0;
            return transform(new Vector3f(cx - ssx / 2, ty + oy, cz - ssz / 2), new Vector3f(ssx, ssy, ssz));
        }
    }

    private record Stage(int tick, Sound sound, float pitch, Particle particle, int count, double y) {
    }

    private static final List<Part> PARTS = new ArrayList<>();
    private static final List<Stage> STAGES = List.of(
            new Stage(0, Sound.BLOCK_STONE_PLACE, 0.7f, Particle.CLOUD, 12, 0.1),
            new Stage(6, Sound.BLOCK_DEEPSLATE_PLACE, 0.7f, Particle.SMOKE, 8, 0.2),
            new Stage(16, Sound.BLOCK_STONE_PLACE, 1.0f, Particle.CLOUD, 14, 0.6),
            new Stage(26, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, Particle.HAPPY_VILLAGER, 12, 0.7),
            new Stage(36, Sound.BLOCK_ANVIL_PLACE, 1.3f, Particle.CLOUD, 14, 1.0),
            new Stage(46, Sound.BLOCK_DEEPSLATE_BRICKS_PLACE, 0.9f, Particle.SMOKE, 8, 1.2),
            new Stage(56, Sound.BLOCK_CHAIN_PLACE, 0.8f, Particle.CLOUD, 8, 1.5),
            new Stage(ICON_DELAY, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, Particle.END_ROD, 30, 1.5),
            new Stage(ICON_DELAY + 6, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, Particle.ENCHANT, 40, 1.5));

    static {
        Material stone = Material.STONE_BRICKS;
        Material obsidian = Material.OBSIDIAN;
        Material crying = Material.CRYING_OBSIDIAN;
        Material glow = Material.VERDANT_FROGLIGHT;
        // fundação
        PARTS.add(new Part(stone, 0, 0.002f, 0, 1.5f, 0.08f, 1.5f, 0, 8, Style.SPREAD, false));
        PARTS.add(new Part(obsidian, 0, 0.08f, 0, 1.16f, 0.14f, 1.16f, 6, 8, Style.RISE, false));
        PARTS.add(new Part(crying, 0, 0.22f, 0, 1.1f, 0.1f, 1.1f, 10, 8, Style.RISE, false));
        // pilar (cobre as faces do Lodestone)
        PARTS.add(new Part(stone, 0, 0.32f, 0, 1.006f, 0.58f, 1.006f, 16, 12, Style.RISE, false));
        // runas verdes brilhantes nas quatro faces
        int[][] faces = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        int delay = 26;
        for (int[] face : faces) {
            boolean alongX = face[1] != 0;
            PARTS.add(new Part(glow, face[0] * 0.506f, 0.46f, face[1] * 0.506f,
                    alongX ? 0.3f : 0.02f, 0.34f, alongX ? 0.02f : 0.3f, delay, 6, Style.POP, true));
            PARTS.add(new Part(Material.LIME_CONCRETE, face[0] * 0.512f, 0.53f, face[1] * 0.512f,
                    alongX ? 0.08f : 0.02f, 0.2f, alongX ? 0.02f : 0.08f, delay + 1, 6, Style.POP, true));
            delay += 2;
        }
        // beiral e tampo
        PARTS.add(new Part(stone, 0, 0.9f, 0, 1.3f, 0.17f, 1.3f, 36, 8, Style.DROP, false));
        PARTS.add(new Part(Material.SMOOTH_STONE, 0, 1.07f, 0, 1.0f, 0.06f, 1.0f, 42, 6, Style.POP, false));
        // arcos de obsidiana
        int postDelay = 46;
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                PARTS.add(new Part(obsidian, sx * 0.4f, 1.13f, sz * 0.32f, 0.14f, 0.36f, 0.14f,
                        postDelay, 8, Style.RISE, false));
                postDelay += 2;
            }
        }
        PARTS.add(new Part(obsidian, -0.4f, 1.49f, 0, 0.14f, 0.12f, 0.78f, 56, 8, Style.DROP, false));
        PARTS.add(new Part(obsidian, 0.4f, 1.49f, 0, 0.14f, 0.12f, 0.78f, 58, 8, Style.DROP, false));
        PARTS.add(new Part(crying, -0.4f, 1.61f, 0, 0.16f, 0.05f, 0.4f, 60, 6, Style.POP, true));
        PARTS.add(new Part(crying, 0.4f, 1.61f, 0, 0.16f, 0.05f, 0.4f, 60, 6, Style.POP, true));
    }

    private static Transformation transform(Vector3f translation, Vector3f scale) {
        return new Transformation(translation, new Quaternionf(), scale, new Quaternionf());
    }

    // ── Estado ─────────────────────────────────────────────────────────────

    private static final class Instance {
        final Waystone waystone;
        final List<Display> parts = new ArrayList<>();
        final List<BukkitTask> pending = new ArrayList<>();
        final Set<UUID> viewers = new HashSet<>();
        final Map<UUID, Integer> misses = new HashMap<>();
        ItemDisplay icon;
        TextDisplay name;
        boolean spawned;
        boolean animate;
        float spin;

        Instance(Waystone waystone, boolean animate) {
            this.waystone = waystone;
            this.animate = animate;
        }
    }

    private final PluginMain plugin;
    private final Map<UUID, Instance> instances = new HashMap<>();
    private BukkitTask task;

    public ModelManager(PluginMain plugin) {
        this.plugin = plugin;
    }

    // ── Registro ───────────────────────────────────────────────────────────

    public void start() {
        if (task != null) {
            return;
        }
        long interval = Math.max(2, plugin.getConfig().getInt("waystones.model.check-interval-ticks", 10));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::cycle, 20L, interval);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Instance instance : instances.values()) {
            despawn(instance);
        }
        instances.clear();
    }

    /** Substitui tudo pelos Waystones carregados do disco (sem animação de montagem). */
    public void load(java.util.Collection<Waystone> waystones) {
        for (Instance instance : instances.values()) {
            despawn(instance);
        }
        instances.clear();
        for (Waystone waystone : waystones) {
            add(waystone, false);
        }
    }

    /** Passa a gerenciar o modelo. Com {@code animate}, ele se monta diante de quem estiver perto. */
    public void add(Waystone waystone, boolean animate) {
        instances.put(waystone.id(), new Instance(waystone, animate));
    }

    public void remove(Waystone waystone) {
        Instance instance = instances.remove(waystone.id());
        if (instance == null) {
            return;
        }
        Location center = waystone.pos().center();
        if (instance.spawned && center != null) {
            Fx.burst(center.clone().add(0, 0.6, 0), Particle.POOF, 25, 0.4, 0.05);
        }
        despawn(instance);
    }

    /** Aplica ícone e visibilidade do nome após o dono mudar as opções. */
    public void refresh(Waystone waystone) {
        Instance instance = instances.get(waystone.id());
        if (instance == null || !instance.spawned || instance.animate) {
            return;
        }
        Location center = waystone.pos().center();
        if (instance.icon != null && instance.icon.isValid()) {
            instance.icon.setItemStack(new ItemStack(waystone.icon()));
            if (center != null) {
                Fx.burst(center.clone().add(0, 1.0, 0), Particle.END_ROD, 12, 0.25, 0.02);
                Fx.worldSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.3f);
            }
        }
        if (waystone.showName() && instance.name == null) {
            instance.name = spawnName(instance, baseLocation(waystone));
        } else if (!waystone.showName() && instance.name != null) {
            instance.name.remove();
            instance.name = null;
        }
    }

    /** {total, renderizados, observadores}. */
    public int[] stats() {
        int rendered = 0;
        int viewers = 0;
        for (Instance instance : instances.values()) {
            if (instance.spawned) {
                rendered++;
                viewers += instance.viewers.size();
            }
        }
        return new int[]{instances.size(), rendered, viewers};
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    private void forget(UUID player) {
        for (Instance instance : instances.values()) {
            instance.viewers.remove(player);
            instance.misses.remove(player);
        }
    }

    // ── Ciclo de renderização ──────────────────────────────────────────────

    private void cycle() {
        var config = plugin.getConfig();
        if (!config.getBoolean("waystones.model.enabled", true)) {
            for (Instance instance : instances.values()) {
                despawn(instance);
            }
            return;
        }
        double render = config.getDouble("waystones.model.render-distance", 48);
        double despawn = Math.max(render + 8, config.getDouble("waystones.model.despawn-distance", 96));
        boolean fov = config.getBoolean("waystones.model.fov-culling", true);
        boolean occlusion = config.getBoolean("waystones.model.occlusion-culling", true);
        int interval = Math.max(2, config.getInt("waystones.model.check-interval-ticks", 10));

        for (Instance instance : instances.values()) {
            Waystone waystone = instance.waystone;
            World world = waystone.pos().bukkitWorld();
            if (world == null || !waystone.pos().isChunkLoaded()) {
                despawn(instance);
                continue;
            }
            if (instance.spawned && !instance.parts.isEmpty() && !instance.parts.get(0).isValid()) {
                despawn(instance); // o chunk foi descarregado por baixo de nós
            }
            double cx = waystone.pos().x() + 0.5;
            double cy = waystone.pos().y() + 1.0;
            double cz = waystone.pos().z() + 0.5;
            boolean nearSpawn = false;
            boolean nearAny = false;
            Set<UUID> wanted = new HashSet<>();
            for (Player player : world.getPlayers()) {
                Location location = player.getLocation();
                double dx = location.getX() - cx;
                double dy = location.getY() - cy;
                double dz = location.getZ() - cz;
                double distanceSquared = dx * dx + dy * dy + dz * dz;
                if (distanceSquared <= despawn * despawn) {
                    nearAny = true;
                }
                UUID id = player.getUniqueId();
                boolean watching = instance.viewers.contains(id);
                double limit = watching ? render + 4 : render;
                if (distanceSquared > limit * limit) {
                    instance.misses.remove(id);
                    continue;
                }
                if (distanceSquared <= render * render) {
                    nearSpawn = true;
                }
                if (visible(player, world, cx, cy, cz, Math.sqrt(distanceSquared), fov, occlusion)) {
                    instance.misses.remove(id);
                    wanted.add(id);
                } else if (watching && instance.misses.merge(id, 1, Integer::sum) < 2) {
                    wanted.add(id); // tolerância contra piscadas ao girar a câmera
                } else if (!watching) {
                    instance.misses.remove(id);
                }
            }
            if (!nearAny) {
                despawn(instance);
                continue;
            }
            if (!instance.spawned) {
                if (!nearSpawn) {
                    continue;
                }
                spawn(instance);
            }
            syncViewers(instance, wanted);
            if (!instance.viewers.isEmpty()) {
                spin(instance, interval);
            }
        }
    }

    private boolean visible(Player player, World world, double cx, double cy, double cz, double distance,
                            boolean fov, boolean occlusion) {
        if (distance <= 6 || (!fov && !occlusion)) {
            return true;
        }
        Location eye = player.getEyeLocation();
        Vector toTop = new Vector(cx - eye.getX(), cy + 0.6 - eye.getY(), cz - eye.getZ());
        if (fov && eye.getDirection().dot(toTop.clone().normalize()) < -0.15) {
            return false; // atrás de quem olha
        }
        if (occlusion && distance > 10) {
            Vector toBase = new Vector(cx - eye.getX(), cy - 0.5 - eye.getY(), cz - eye.getZ());
            return clearLine(world, eye, toTop) || clearLine(world, eye, toBase);
        }
        return true;
    }

    private static boolean clearLine(World world, Location eye, Vector to) {
        double length = to.length() - 0.8;
        if (length <= 0) {
            return true;
        }
        RayTraceResult hit = world.rayTraceBlocks(eye, to.clone().normalize(), length, FluidCollisionMode.NEVER, true);
        return hit == null;
    }

    private void syncViewers(Instance instance, Set<UUID> wanted) {
        for (UUID id : wanted) {
            if (instance.viewers.add(id)) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    forEntity(instance, entity -> player.showEntity(plugin, entity));
                }
            }
        }
        var iterator = instance.viewers.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            if (wanted.contains(id)) {
                continue;
            }
            iterator.remove();
            instance.misses.remove(id);
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                forEntity(instance, entity -> player.hideEntity(plugin, entity));
            }
        }
    }

    private static void forEntity(Instance instance, Consumer<Entity> action) {
        for (Display part : instance.parts) {
            action.accept(part);
        }
        if (instance.icon != null) {
            action.accept(instance.icon);
        }
        if (instance.name != null) {
            action.accept(instance.name);
        }
    }

    private void showTo(Instance instance, Entity entity) {
        for (UUID id : instance.viewers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.showEntity(plugin, entity);
            }
        }
    }

    // ── Giro do ícone ──────────────────────────────────────────────────────

    private void spin(Instance instance, int interval) {
        ItemDisplay icon = instance.icon;
        if (icon == null || !icon.isValid() || instance.animate) {
            return;
        }
        instance.spin = (instance.spin + (float) (Math.PI / 2)) % (float) (Math.PI * 2);
        icon.setInterpolationDelay(0);
        icon.setInterpolationDuration(interval);
        icon.setTransformation(new Transformation(new Vector3f(0, ICON_Y, 0),
                new Quaternionf().rotateY(instance.spin), new Vector3f(ICON_SCALE), new Quaternionf()));
    }

    // ── Criação e remoção das entidades ────────────────────────────────────

    private static Location baseLocation(Waystone waystone) {
        return new Location(waystone.pos().bukkitWorld(), waystone.pos().x() + 0.5, waystone.pos().y(),
                waystone.pos().z() + 0.5);
    }

    private void spawn(Instance instance) {
        Waystone waystone = instance.waystone;
        Location base = baseLocation(waystone);
        instance.spawned = true;
        boolean animated = instance.animate
                && plugin.getConfig().getBoolean("waystones.model.assembly-animation", true);
        if (!animated) {
            instance.animate = false;
            for (Part part : PARTS) {
                instance.parts.add(spawnPart(instance, base, part, false));
            }
            instance.icon = spawnIcon(instance, base, false);
            if (waystone.showName()) {
                instance.name = spawnName(instance, base);
            }
            return;
        }
        for (Part part : PARTS) {
            schedule(instance, part.delay(), () -> {
                Display display = spawnPart(instance, base, part, true);
                instance.parts.add(display);
                showTo(instance, display);
                Bukkit.getScheduler().runTaskLater(plugin, () -> animateTo(display, part.duration(), part.to()), 2L);
            });
        }
        schedule(instance, ICON_DELAY, () -> {
            ItemDisplay icon = spawnIcon(instance, base, true);
            instance.icon = icon;
            showTo(instance, icon);
            Bukkit.getScheduler().runTaskLater(plugin, () -> animateTo(icon, 12,
                    transform(new Vector3f(0, ICON_Y, 0), new Vector3f(ICON_SCALE))), 2L);
        });
        if (waystone.showName()) {
            schedule(instance, NAME_DELAY, () -> {
                TextDisplay text = spawnName(instance, base);
                instance.name = text;
                showTo(instance, text);
            });
        }
        for (Stage stage : STAGES) {
            schedule(instance, stage.tick(), () -> {
                Location at = base.clone().add(0, stage.y(), 0);
                Fx.worldSound(at, stage.sound(), 0.9f, stage.pitch());
                Fx.burst(at, stage.particle(), stage.count(), 0.5, 0.02);
            });
        }
        schedule(instance, ASSEMBLY_END, () -> {
            instance.animate = false;
            Fx.ring(base.clone().add(0, 0.15, 0), Particle.END_ROD, 1.1, 24);
        });
    }

    private void schedule(Instance instance, int delay, Runnable action) {
        instance.pending.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (instance.spawned) {
                action.run();
            }
        }, delay));
    }

    private static void animateTo(Display display, int duration, Transformation target) {
        if (display.isValid()) {
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(duration);
            display.setTransformation(target);
        }
    }

    private BlockDisplay spawnPart(Instance instance, Location base, Part part, boolean animated) {
        BlockDisplay display = base.getWorld().spawn(base, BlockDisplay.class, entity -> {
            entity.setBlock(part.block().createBlockData());
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
            entity.setTransformation(animated ? part.from() : part.to());
            entity.setViewRange(1.0f);
            if (part.glow()) {
                entity.setBrightness(new Display.Brightness(15, 15));
            }
        });
        if (!animated) {
            showTo(instance, display);
        }
        return display;
    }

    private ItemDisplay spawnIcon(Instance instance, Location base, boolean animated) {
        ItemDisplay display = base.getWorld().spawn(base, ItemDisplay.class, entity -> {
            entity.setItemStack(new ItemStack(instance.waystone.icon()));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
            entity.setViewRange(0.8f);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setTransformation(transform(new Vector3f(0, ICON_Y, 0),
                    new Vector3f(animated ? 0.001f : ICON_SCALE)));
        });
        if (!animated) {
            showTo(instance, display);
        }
        return display;
    }

    private TextDisplay spawnName(Instance instance, Location base) {
        TextDisplay display = base.getWorld().spawn(base, TextDisplay.class, entity -> {
            entity.text(Msg.mm("<gradient:#c4a7ff:#2cb67d><bold>" + Msg.esc(instance.waystone.name())));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
            entity.setViewRange(0.5f);
            entity.setSeeThrough(false);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(110, 12, 10, 24));
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setTransformation(transform(new Vector3f(0, NAME_Y, 0), new Vector3f(1.1f)));
        });
        showTo(instance, display);
        return display;
    }

    private void despawn(Instance instance) {
        for (BukkitTask pending : instance.pending) {
            pending.cancel();
        }
        instance.pending.clear();
        for (Display part : instance.parts) {
            part.remove();
        }
        instance.parts.clear();
        if (instance.icon != null) {
            instance.icon.remove();
            instance.icon = null;
        }
        if (instance.name != null) {
            instance.name.remove();
            instance.name = null;
        }
        instance.viewers.clear();
        instance.misses.clear();
        instance.spawned = false;
        instance.animate = false; // uma montagem interrompida é considerada concluída
    }
}
