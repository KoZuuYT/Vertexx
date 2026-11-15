package dev.vertexx.intro;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import dev.vertexx.intro.IntroAssets.Cue;
import dev.vertexx.kingdom.Kingdom;
import dev.vertexx.kingdom.Rank;
import io.papermc.paper.entity.TeleportFlag;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A cerimônia de escolha de reino de um jogador: o jogador é levado a um palco no céu, onde assiste
 * à revelação de três bandeirões (display entities visíveis só para ele), escolhe com o mouse e
 * confirma agachando. Tudo é restaurado ao final (posição, modo de jogo, inventário, hora e clima).
 * Toda a lógica roda na thread principal, um tick por vez.
 */
final class IntroSession {

    // ── Geometria da cena ──
    private static final double DIST = 10.0;
    private static final double SPACING = 6.4;
    private static final float BANNER_SCALE = 3.0f;
    private static final float BANNER_SCALE_VANILLA = 3.6f;
    private static final double LABEL_DROP = 3.7;
    private static final float FAN_DEGREES = 9f;

    // ── Linha do tempo (ticks desde que o palco ficou pronto) ──
    private static final int[] REVEAL_AT = {52, 76, 100};
    private static final int RISE_TICKS = 26;
    private static final int SELECT_AT = 130;
    private static final int PACK_TIMEOUT = 500;
    private static final int DRONE_LOOP = 480;

    // ── Linha do tempo da confirmação ──
    private static final int PUSH_TICKS = 46;
    private static final int BLACK_AT = 46;
    private static final int RESTORE_AT = BLACK_AT + 14;
    private static final int FINISH_AT = BLACK_AT + 12 + 22 + 14;

    private enum Phase { PACK, TELEPORT, STAGE, CONFIRM, DONE }

    private final PluginMain plugin;
    private final IntroManager manager;
    private final Player player;
    private final List<Entity> entities = new ArrayList<>();
    private final Banner[] banners = new Banner[3];
    private final Kingdom[] kingdoms = Kingdom.values();
    /** Ensaio: percorre tudo, mas não atribui reino (comando de administrador). */
    private final boolean demo;
    private boolean wasInvulnerable;

    private Phase phase = Phase.PACK;
    private BukkitTask task;
    private boolean pack;
    private boolean packSent;
    private boolean packFailed;
    private int ticks;
    private int stageTick;
    private int confirmTick;
    private boolean staged;
    private boolean restored;
    private boolean releasing;

    // estado salvo do jogador
    private Location origin;
    private GameMode originMode;
    private int savedHeld;
    private ItemStack savedSlotItem;

    // câmera
    private ArmorStand seat;
    private double sx;
    private double sy;
    private double sz;
    private double eyeOffset = 1.3;
    private double bannerY;
    private double lateral;
    private float yaw = 180f;
    private float pitch = 0f;
    private double shake;
    private double pushStartLateral;

    // seleção
    private int selected = 1;
    private int lastInput = -100;
    private Kingdom chosen;

    private static final class Banner {
        ItemDisplay display;
        TextDisplay label;
        Location pos;
        boolean landed;
        boolean sunk;
    }

    IntroSession(PluginMain plugin, IntroManager manager, Player player, boolean demo) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.demo = demo;
    }

    Player player() {
        return player;
    }

    boolean isSeat(Entity entity) {
        return seat != null && seat.equals(entity);
    }

    boolean dismountAllowed() {
        return releasing || phase == Phase.DONE;
    }

    // ═══════════════════════ ciclo de vida ═══════════════════════

    void begin() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        Msg.actionBar(player, "<gray>Preparando a cerimônia dos reinos…");
    }

    void onPackStatus(org.bukkit.event.player.PlayerResourcePackStatusEvent.Status status) {
        switch (status) {
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> packFailed = true;
            default -> {
            }
        }
    }

    private void tick() {
        if (!player.isOnline()) {
            manager.abort(player, false);
            return;
        }
        ticks++;
        try {
            switch (phase) {
                case PACK -> tickPack();
                case STAGE -> tickStage();
                case CONFIRM -> tickConfirm();
                default -> {
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Falha na cerimônia de " + player.getName()
                    + "; escolha por menu como reserva.", e);
            manager.abort(player, true);
        }
    }

    // ═══════════════════════ pacote ═══════════════════════

    private void tickPack() {
        var packs = plugin.resourcePack();
        if (packs.isLoaded(player)) {
            pack = true;
            startStage();
            return;
        }
        if (!packSent) {
            if (packs.declined(player)) {
                startStage();
                return;
            }
            if (packs.isConfigured()) {
                packs.send(player);
                packSent = true;
            } else if (ticks > 100) {
                startStage();
                return;
            }
        } else if (packFailed || ticks > PACK_TIMEOUT) {
            startStage();
            return;
        }
        if (ticks % 20 == 0) {
            Msg.actionBar(player, "<gray>Preparando a cerimônia dos reinos…");
        }
    }

    // ═══════════════════════ palco ═══════════════════════

    private void startStage() {
        phase = Phase.TELEPORT;
        origin = player.getLocation().clone();
        originMode = player.getGameMode();
        player.getPersistentDataContainer().set(IntroManager.ORIGIN, PersistentDataType.STRING, IntroManager.encode(origin, originMode));

        World world = origin.getWorld();
        double y = origin.getY();
        if (world.getEnvironment() == World.Environment.NORMAL) {
            y = Math.min(world.getMaxHeight() - 14, Math.max(origin.getY() + 50, 210));
        }
        sx = origin.getBlockX() + 0.5;
        sz = origin.getBlockZ() + 0.5;
        sy = y;
        blackout(0, 18, 22);
        Location stage = new Location(world, sx, sy, sz, 180f, 0f);
        player.teleportAsync(stage).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (phase != Phase.TELEPORT) {
                return;
            }
            if (ok) {
                onStaged(world);
            } else {
                manager.abort(player, true);
            }
        }));
    }

    private void onStaged(World world) {
        staged = true;
        wasInvulnerable = player.isInvulnerable();
        player.setInvulnerable(true);
        player.setGameMode(GameMode.ADVENTURE);
        PlayerInventory inventory = player.getInventory();
        savedHeld = inventory.getHeldItemSlot();
        savedSlotItem = inventory.getItem(4);
        inventory.setItem(4, IntroAssets.selector(pack, IntroManager.SELECTOR_TAG));
        inventory.setHeldItemSlot(4);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.hidePlayer(plugin, player);
            }
        }
        if (world.getEnvironment() == World.Environment.NORMAL) {
            player.setPlayerTime(18000L, false);
            player.setPlayerWeather(WeatherType.CLEAR);
        }

        seat = world.spawn(new Location(world, sx, sy + 1.2, sz, 180f, 0f), ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
            stand.setVisibleByDefault(false);
        });
        entities.add(seat);
        player.showEntity(plugin, seat);
        stageTick = 0;
        phase = Phase.STAGE;
    }

    private void tickStage() {
        int t = stageTick++;
        if (t == 2 || (t % 10 == 0 && seat.getPassengers().isEmpty())) {
            seat.addPassenger(player);
        }
        if (t == 5) {
            eyeOffset = player.getEyeLocation().getY() - seat.getY();
            bannerY = sy + 1.2 + eyeOffset + 0.25;
        }
        if (t < 5) {
            return;
        }
        if (t == 5) {
            play(IntroAssets.DRONE, 0.85f, 1f, null);
            play(IntroAssets.RISER, 0.9f, 1f, null);
        }
        if (t % DRONE_LOOP == 0 && t > 5) {
            play(IntroAssets.DRONE, 0.85f, 1f, null);
        }
        if (t == 24) {
            showSubtitle(Component.empty(), Msg.mm("<gradient:#c4a7ff:#ffe082>Três reinos. Um destino."), 14, 40, 14);
        }
        for (int i = 0; i < REVEAL_AT.length; i++) {
            timelineBanner(i, t - REVEAL_AT[i]);
        }
        if (t == SELECT_AT) {
            showSubtitle(Msg.mm("<bold><gradient:#c4a7ff:#ffe082>ESCOLHA O SEU REINO"),
                    Msg.mm("<gray>A decisão é <white>permanente</white>"), 10, 60, 20);
            play(IntroAssets.TICK, 1f, 0.7f, null);
            applySelection(10);
        }
        if (t > SELECT_AT) {
            if (t % 10 == 0) {
                player.sendActionBar(IntroAssets.hint(kingdoms[selected], pack));
            }
            if (t % 2 == 0) {
                aura(kingdoms[selected], banners[selected]);
            }
        }
        if (t % 40 == 0) {
            sway();
        }
        updateCamera(t);
    }

    // ── Revelação dos bandeirões ──

    private void timelineBanner(int i, int rel) {
        Kingdom kingdom = kingdoms[i];
        if (rel == 0) {
            spawnBanner(i);
            play(IntroAssets.WHOOSH, 2.5f, 0.9f + i * 0.1f, banners[i].pos);
        } else if (rel == 3) {
            Banner b = banners[i];
            b.display.setInterpolationDelay(0);
            b.display.setInterpolationDuration(RISE_TICKS);
            b.display.setTransformation(bannerTransform(i, 0.92f, 0f, 0f));
        } else if (rel == 8) {
            play(IntroAssets.stinger(kingdom), 2.8f, 1f, banners[i].pos);
        } else if (rel == RISE_TICKS) {
            landBanner(i);
        } else if (rel == RISE_TICKS + 10) {
            spawnLabel(i);
        }
    }

    private void spawnBanner(int i) {
        Kingdom kingdom = kingdoms[i];
        World world = origin.getWorld();
        Banner b = new Banner();
        b.pos = new Location(world, sx + (i - 1) * SPACING, bannerY, sz - DIST + 0.9 * Math.abs(i - 1));
        b.display = world.spawn(b.pos, ItemDisplay.class, e -> {
            e.setItemStack(IntroAssets.banner(kingdom, pack));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setPersistent(false);
            e.setVisibleByDefault(false);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setViewRange(2.0f);
            e.setShadowRadius(0f);
            e.setTransformation(new Transformation(new Vector3f(0, -8f, 0), new Quaternionf(),
                    new Vector3f(0.05f), new Quaternionf()));
        });
        entities.add(b.display);
        player.showEntity(plugin, b.display);
        banners[i] = b;
    }

    private void landBanner(int i) {
        Banner b = banners[i];
        Kingdom kingdom = kingdoms[i];
        b.landed = true;
        boolean last = i == banners.length - 1;
        play(IntroAssets.BOOM, last ? 1.6f : 0.9f, last ? 1f : 1.2f, null);
        Location base = b.pos.clone().add(0, -2.9, 0.4);
        Color color = IntroAssets.dust(kingdom);
        for (int k = 0; k < 36; k++) {
            double a = Math.PI * 2 * k / 36;
            player.spawnParticle(Particle.DUST, base.getX() + Math.cos(a) * 1.9, base.getY(), base.getZ() + Math.sin(a) * 1.9,
                    1, 0, 0, 0, 0, new Particle.DustOptions(color, 2.2f));
            player.spawnParticle(Particle.END_ROD, base.getX() + Math.cos(a) * 0.9, base.getY(), base.getZ() + Math.sin(a) * 0.9,
                    0, Math.cos(a) * 0.25, 0.12, Math.sin(a) * 0.25, 0.9);
        }
        player.spawnParticle(Particle.FIREWORK, b.pos, 40, 1.0, 2.2, 0.4, 0.05);
        player.spawnParticle(Particle.ELECTRIC_SPARK, b.pos, 30, 1.4, 2.4, 0.5, 0.3);
        shake = last ? 0.16 : 0.09;
    }

    private void spawnLabel(int i) {
        Banner b = banners[i];
        Location at = b.pos.clone().add(0, -LABEL_DROP, 0.3);
        b.label = at.getWorld().spawn(at, TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setPersistent(false);
            e.setVisibleByDefault(false);
            e.setViewRange(2.0f);
            e.setSeeThrough(false);
            e.setDefaultBackground(false);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(true);
            e.setLineWidth(260);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setBrightness(new Display.Brightness(15, 15));
            e.text(labelText(i, false));
            e.setTransformation(labelTransform(0.01f));
        });
        entities.add(b.label);
        player.showEntity(plugin, b.label);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (b.label != null && b.label.isValid()) {
                b.label.setInterpolationDelay(0);
                b.label.setInterpolationDuration(8);
                b.label.setTransformation(labelTransform(i == selected && stageTick > SELECT_AT ? 1.9f : 1.35f));
            }
        }, 2L);
    }

    private static Transformation labelTransform(float scale) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf());
    }

    private Component labelText(int i, boolean active) {
        Kingdom kingdom = kingdoms[i];
        if (!active) {
            return Msg.mm("<bold>" + kingdom.styled());
        }
        StringBuilder text = new StringBuilder("<bold>").append(kingdom.styled()).append("</bold>");
        for (String line : kingdom.description()) {
            text.append("<newline>").append(line);
        }
        text.append("<newline><dark_gray>Membros: <white>").append(plugin.kingdoms().count(kingdom));
        return Msg.mm(text.toString());
    }

    // ── Transformações e seleção ──

    /** Pano que balança preso pela barra do topo (pivô no alto). */
    private Transformation bannerTransform(int i, float mul, float rise, float swayRad) {
        float s = (pack ? BANNER_SCALE : BANNER_SCALE_VANILLA) * mul;
        Quaternionf q = new Quaternionf()
                .rotateY((float) Math.toRadians(-(i - 1) * FAN_DEGREES))
                .rotateZ(swayRad);
        Vector3f top = new Vector3f(0, 0.94f * s, 0);
        Vector3f shift = new Vector3f(top).sub(q.transform(new Vector3f(top)));
        return new Transformation(shift.add(0, rise, 0), q, new Vector3f(s), new Quaternionf());
    }

    private float swayFor(int i) {
        return (((stageTick / 40) + i) % 2 == 0 ? 1 : -1) * 0.04f;
    }

    private void sway() {
        for (int i = 0; i < banners.length; i++) {
            Banner b = banners[i];
            if (b != null && b.landed && !b.sunk && b.display.isValid()) {
                float mul = stageTick >= SELECT_AT && i == selected ? 1.12f : 0.92f;
                b.display.setInterpolationDelay(0);
                b.display.setInterpolationDuration(40);
                b.display.setTransformation(bannerTransform(i, mul, 0f, swayFor(i)));
            }
        }
    }

    private void applySelection(int duration) {
        for (int i = 0; i < banners.length; i++) {
            Banner b = banners[i];
            if (b == null || !b.landed || b.sunk) {
                continue;
            }
            boolean on = i == selected;
            b.display.setInterpolationDelay(0);
            b.display.setInterpolationDuration(duration);
            b.display.setTransformation(bannerTransform(i, on ? 1.12f : 0.92f, 0f, swayFor(i)));
            b.display.setBrightness(on ? new Display.Brightness(15, 15) : new Display.Brightness(7, 8));
            b.display.setGlowColorOverride(IntroAssets.dust(kingdoms[i]));
            b.display.setGlowing(on);
            if (b.label != null && b.label.isValid()) {
                b.label.text(labelText(i, on));
                b.label.setInterpolationDelay(0);
                b.label.setInterpolationDuration(duration);
                b.label.setTransformation(labelTransform(on ? 1.9f : 1.2f));
            }
        }
    }

    // ═══════════════════════ entrada do jogador ═══════════════════════

    boolean canInteract() {
        return phase == Phase.STAGE && stageTick > SELECT_AT && chosen == null;
    }

    /** -1 = botão esquerdo (reino à esquerda), +1 = botão direito. */
    void move(int delta) {
        if (!canInteract() || ticks - lastInput < 3) {
            return;
        }
        lastInput = ticks;
        int target = Math.max(0, Math.min(banners.length - 1, selected + delta));
        if (target == selected) {
            play(IntroAssets.TICK, 1f, 0.5f, null);
            shake = 0.1;
            return;
        }
        selected = target;
        play(IntroAssets.TICK, 1f, 0.85f + 0.15f * selected, null);
        play(IntroAssets.WHOOSH, 1.4f, 1.3f, banners[selected].pos);
        applySelection(8);
        player.sendActionBar(IntroAssets.hint(kingdoms[selected], pack));
        Banner b = banners[selected];
        player.spawnParticle(Particle.END_ROD, b.pos, 24, 1.1, 2.0, 0.3, 0.05);
    }

    void confirm() {
        if (!canInteract() || ticks - lastInput < 3) {
            return;
        }
        lastInput = ticks;
        chosen = kingdoms[selected];
        phase = Phase.CONFIRM;
        confirmTick = 0;
        pushStartLateral = lateral;
    }

    // ═══════════════════════ confirmação ═══════════════════════

    private void tickConfirm() {
        int c = confirmTick++;
        Banner pick = banners[chosen.ordinal()];
        if (c == 0) {
            player.clearTitle();
            player.sendActionBar(Component.empty());
            play(IntroAssets.CONFIRM, 1f, 1f, null);
            play(IntroAssets.BOOM, 1.8f, 0.85f, null);
            overlay(IntroAssets.color(chosen), 2, 2, 14);
            for (int i = 0; i < banners.length; i++) {
                Banner b = banners[i];
                if (i == chosen.ordinal() || b == null) {
                    continue;
                }
                b.sunk = true;
                b.display.setInterpolationDelay(0);
                b.display.setInterpolationDuration(32);
                b.display.setTransformation(bannerTransform(i, 0.05f, -9f, 0f));
                if (b.label != null && b.label.isValid()) {
                    b.label.remove();
                }
            }
            pick.display.setInterpolationDelay(0);
            pick.display.setInterpolationDuration(14);
            pick.display.setTransformation(bannerTransform(chosen.ordinal(), 1.3f, 0f, 0f));
            pick.display.setGlowing(true);
            pick.display.setBrightness(new Display.Brightness(15, 15));
            if (pick.label != null && pick.label.isValid()) {
                pick.label.text(Msg.mm("<bold>" + chosen.styled() + "</bold><newline><green>✔ Reino escolhido"));
            }
            player.spawnParticle(Particle.TOTEM_OF_UNDYING, pick.pos, 90, 1.6, 2.4, 0.5, 0.45);
            player.spawnParticle(Particle.FIREWORK, pick.pos, 70, 1.6, 2.6, 0.6, 0.18);
            shake = 0.2;
        }
        if (c == 14) {
            pick.display.setInterpolationDelay(0);
            pick.display.setInterpolationDuration(30);
            pick.display.setTransformation(bannerTransform(chosen.ordinal(), 1.18f, 0f, 0f));
        }
        if (c < 24 && c % 2 == 0) {
            double radius = 1.2 + c * 0.35;
            Color color = IntroAssets.dust(chosen);
            for (int k = 0; k < 40; k++) {
                double a = Math.PI * 2 * k / 40;
                player.spawnParticle(Particle.DUST, pick.pos.getX() + Math.cos(a) * radius, pick.pos.getY() - 2.6 + c * 0.12,
                        pick.pos.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 2f));
            }
            player.spawnParticle(Particle.END_ROD, pick.pos.clone().add(0, -2.6, 0), 0, 0, 1, 0, 0.5 + c * 0.02);
        }
        if (c == BLACK_AT) {
            blackout(12, 22, 14);
            player.stopSound(SoundStop.named(IntroAssets.DRONE.key()));
        }
        if (c == RESTORE_AT) {
            restorePlayer(false);
        }
        if (c == FINISH_AT) {
            finish();
            return;
        }
        if (!restored) {
            updateCamera(stageTick++);
        }
    }

    private void finish() {
        Kingdom kingdom = chosen;
        phase = Phase.DONE;
        if (!demo) {
            plugin.onboarding().complete(player, kingdom, true);
        }
        play(IntroAssets.TITLE, 1f, 1f, null);
        play(IntroAssets.stinger(kingdom), 1.4f, 0.9f, null);
        Component subtitle = Msg.mm("<bold>" + kingdom.styled());
        Component title = pack
                ? IntroAssets.emblem(kingdom)
                : Msg.mm("<bold>" + kingdom.styled());
        player.showTitle(Title.title(title,
                pack ? subtitle : Msg.mm("<gray>Você agora é <white>" + Rank.MEMBRO.displayName() + "</white> do reino"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(1100))));
        if (pack) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    Msg.actionBar(player, "<gray>Você agora é <white>" + Rank.MEMBRO.displayName() + "</white> do reino " + kingdom.styled());
                }
            }, 20L);
        }
        Location center = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, center, 50, 0.4, 0.5, 0.4, 0.35);
        manager.end(this);
    }

    // ═══════════════════════ câmera ═══════════════════════

    private static double outCubic(double x) {
        return 1 - Math.pow(1 - Math.min(1, Math.max(0, x)), 3);
    }

    private static double inOutCubic(double x) {
        x = Math.min(1, Math.max(0, x));
        return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
    }

    private static float wrap(float degrees) {
        degrees %= 360f;
        if (degrees > 180f) {
            degrees -= 360f;
        } else if (degrees < -180f) {
            degrees += 360f;
        }
        return degrees;
    }

    private void updateCamera(int t) {
        if (seat == null || !seat.isValid()) {
            return;
        }
        double intro = outCubic(t / 130.0);
        double back = 5.0 * (1 - intro);
        double up = 1.3 * (1 - intro);
        double focusX = sx;
        double focusZ = sz - DIST;
        double camZ = sz + back;
        double lat;
        int look = t >= SELECT_AT || phase == Phase.CONFIRM ? (chosen != null ? chosen.ordinal() : selected) : 1;
        if (phase == Phase.CONFIRM) {
            double p = inOutCubic(confirmTick / (double) PUSH_TICKS);
            double target = (chosen.ordinal() - 1) * SPACING * 0.8;
            lat = pushStartLateral + (target - pushStartLateral) * p;
            camZ = (sz + back) + (-DIST + 4.6 - back) * p;
        } else {
            double target = (t >= SELECT_AT ? (selected - 1) : 0) * SPACING * 0.3;
            lateral += (target - lateral) * 0.07;
            lat = lateral;
        }
        shake *= 0.86;
        double jitter = shake * Math.sin(ticks * 2.3);
        double bob = Math.sin(ticks * 0.045) * 0.07;
        Location cam = new Location(origin.getWorld(), sx + lat + jitter, sy + 1.2 + up + bob, camZ, 180f, 0f);
        seat.teleport(cam, TeleportFlag.EntityState.RETAIN_PASSENGERS);

        // olhar suavemente para o bandeirão em foco
        double fx = sx + (look - 1) * SPACING;
        double dx = fx - cam.getX();
        double dz = focusZ + 0.9 * Math.abs(look - 1) - cam.getZ();
        double dy = bannerY - (cam.getY() + eyeOffset);
        float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float rate = phase == Phase.CONFIRM ? 0.18f : 0.1f;
        yaw += wrap(wantYaw - yaw) * rate;
        pitch += (wantPitch - pitch) * rate;
        player.setRotation(yaw, pitch);
    }

    // ═══════════════════════ efeitos ═══════════════════════

    private void aura(Kingdom kingdom, Banner b) {
        if (b == null || !b.landed) {
            return;
        }
        Color color = IntroAssets.dust(kingdom);
        double angle = ticks * 0.22;
        for (int arm = 0; arm < 2; arm++) {
            double a = angle + Math.PI * arm;
            double y = b.pos.getY() + Math.sin(ticks * 0.07 + arm * Math.PI) * 2.2;
            player.spawnParticle(Particle.DUST, b.pos.getX() + Math.cos(a) * 2.5, y, b.pos.getZ() + Math.sin(a) * 1.0 + 0.4,
                    1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.5f));
        }
        if (ticks % 6 == 0) {
            player.spawnParticle(Particle.END_ROD, b.pos.getX(), b.pos.getY() - 2.8, b.pos.getZ() + 0.4, 0, 0, 1, 0, 0.32);
        }
    }

    private void overlay(TextColor color, int in, int stay, int out) {
        if (!pack) {
            return;
        }
        player.showTitle(Title.title(IntroAssets.overlay(color), Component.empty(), Title.Times.times(
                Duration.ofMillis(in * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(out * 50L))));
    }

    /** Escurece a tela: sobreposição do pack, ou cegueira como reserva. */
    private void blackout(int in, int stay, int out) {
        if (pack) {
            overlay(NamedTextColor.BLACK, in, stay, out);
        } else {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, in + stay + out, 0, false, false, false));
        }
    }

    private void showSubtitle(Component title, Component subtitle, int in, int stay, int out) {
        player.showTitle(Title.title(title, subtitle, Title.Times.times(
                Duration.ofMillis(in * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(out * 50L))));
    }

    /** Toca um evento do pack, ou o equivalente vanilla quando o jogador não tem o pacote. */
    private void play(Cue cue, float volume, float pitch, Location at) {
        if (pack) {
            Sound sound = cue.custom(volume, pitch);
            if (at == null) {
                player.playSound(sound, Sound.Emitter.self());
            } else {
                player.playSound(sound, at.getX(), at.getY(), at.getZ());
            }
        } else if (cue.fallback() != null) {
            float p = Math.max(0.5f, Math.min(2f, pitch * cue.fallbackPitch()));
            Location where = at == null ? player.getLocation() : at;
            player.playSound(where, cue.fallback(), Math.min(volume, 1.5f), p);
        }
    }

    // ═══════════════════════ encerramento ═══════════════════════

    /** Devolve o jogador ao ponto original e desfaz tudo o que a cerimônia alterou nele. */
    void restorePlayer(boolean sync) {
        if (restored) {
            return;
        }
        restored = true;
        releasing = true;
        if (seat != null && seat.isValid()) {
            seat.eject();
        }
        if (staged && origin != null) {
            if (sync) {
                player.teleport(origin);
            } else {
                player.teleportAsync(origin);
            }
        }
        player.getPersistentDataContainer().remove(IntroManager.ORIGIN);
        if (staged) {
            player.setInvulnerable(wasInvulnerable);
        }
        if (originMode != null) {
            player.setGameMode(originMode);
        }
        IntroManager.stripSelector(player, savedSlotItem, savedHeld);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.showPlayer(plugin, player);
            }
        }
        player.resetPlayerTime();
        player.resetPlayerWeather();
        player.stopSound(SoundStop.named(IntroAssets.DRONE.key()));
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        removeEntities();
    }

    private void removeEntities() {
        for (Entity entity : entities) {
            if (entity.isValid()) {
                entity.remove();
            }
        }
        entities.clear();
    }

    /** Encerra a sessão sem atribuir reino (saída do jogador, erro ou desligamento). */
    void abort(boolean sync) {
        phase = Phase.DONE;
        restorePlayer(sync);
        stop();
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        removeEntities();
    }

    boolean running() {
        return phase != Phase.DONE;
    }
}
