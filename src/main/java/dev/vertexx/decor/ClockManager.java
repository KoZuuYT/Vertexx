package dev.vertexx.decor;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Switch;
import org.bukkit.block.BlockSupport;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Relógio do Minecraft como item útil e como decoração.
 * <ul>
 *   <li><b>Contador de dias</b>: segurando um relógio, a barra de ação mostra o dia e a hora exata do mundo.</li>
 *   <li><b>Relógio decorativo</b>: botão direito num bloco com um relógio o fixa no chão, no teto ou na parede.
 *       É uma {@code ItemDisplay} (o relógio vanilla anima sozinho) que guarda a configuração no próprio PDC:
 *       alarme num horário do dia, timer, som, sinal de redstone e horário flutuante.</li>
 * </ul>
 * O sinal de redstone é um botão de pedra que aparece só durante o pulso, no espaço do relógio.
 */
public final class ClockManager implements Listener {

    static final Sound[] SOUNDS = {Sound.BLOCK_NOTE_BLOCK_BELL, Sound.BLOCK_BELL_USE, Sound.BLOCK_NOTE_BLOCK_CHIME,
            Sound.BLOCK_AMETHYST_BLOCK_CHIME, Sound.BLOCK_NOTE_BLOCK_PLING, Sound.ENTITY_EXPERIENCE_ORB_PICKUP};
    static final String[] SOUND_NAMES = {"Sino de nota", "Sino", "Carrilhão", "Ametista", "Plim", "Orbe"};
    static final int[] PULSES = {2, 5, 10, 20, 40, 100, 200};

    private final PluginMain plugin;
    private final NamespacedKey mark;
    final NamespacedKey kFace, kOwner, kOwnerName, kAlarmOn, kAlarmMin, kTimerSec, kTimerLeft, kRepeat,
            kPulse, kRedstone, kSound, kText;
    private final Map<UUID, ItemDisplay> clocks = new HashMap<>();
    private final Map<UUID, TextDisplay> texts = new HashMap<>();
    private final Map<UUID, String> labels = new HashMap<>();
    private final Map<UUID, Long> lastTime = new HashMap<>();
    private final Set<UUID> pulsing = new HashSet<>();
    private BukkitTask task;
    private BukkitTask dayTask;

    public ClockManager(PluginMain plugin) {
        this.plugin = plugin;
        this.mark = new NamespacedKey(plugin, "clock");
        this.kFace = new NamespacedKey(plugin, "clk_face");
        this.kOwner = new NamespacedKey(plugin, "clk_owner");
        this.kOwnerName = new NamespacedKey(plugin, "clk_owner_name");
        this.kAlarmOn = new NamespacedKey(plugin, "clk_alarm_on");
        this.kAlarmMin = new NamespacedKey(plugin, "clk_alarm_min");
        this.kTimerSec = new NamespacedKey(plugin, "clk_timer_sec");
        this.kTimerLeft = new NamespacedKey(plugin, "clk_timer_left");
        this.kRepeat = new NamespacedKey(plugin, "clk_repeat");
        this.kPulse = new NamespacedKey(plugin, "clk_pulse");
        this.kRedstone = new NamespacedKey(plugin, "clk_redstone");
        this.kSound = new NamespacedKey(plugin, "clk_sound");
        this.kText = new NamespacedKey(plugin, "clk_text");
    }

    // ── Ciclo de vida ───────────────────────────────────────────

    public void start() {
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                register(display);
            }
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
        dayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::dayCounter, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        if (dayTask != null) {
            dayTask.cancel();
        }
        for (ItemDisplay clock : new ArrayList<>(clocks.values())) {
            Block cell = cell(clock);
            if (cell.getType() == Material.STONE_BUTTON) {
                cell.setType(Material.AIR, false); // pulso interrompido pelo desligamento
            }
        }
        for (TextDisplay text : texts.values()) {
            text.remove();
        }
        texts.clear();
    }

    private boolean isClock(Entity entity) {
        return entity instanceof ItemDisplay && entity.getPersistentDataContainer().has(mark, PersistentDataType.BYTE);
    }

    private void register(Entity entity) {
        if (isClock(entity)) {
            clocks.put(entity.getUniqueId(), (ItemDisplay) entity);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        event.getEntities().forEach(this::register);
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (clocks.remove(entity.getUniqueId()) != null) {
                dropTransient(entity.getUniqueId());
            }
        }
    }

    private void dropTransient(UUID id) {
        TextDisplay text = texts.remove(id);
        if (text != null) {
            text.remove();
        }
        labels.remove(id);
        lastTime.remove(id);
    }

    // ── Hora e dia ──────────────────────────────────────────────

    /** Hora do dia do Minecraft no formato HH:MM (o tick 0 é 6:00). */
    public static String hhmm(World world) {
        int minutes = (int) ((world.getTime() * 60 / 1000 + 360) % 1440);
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    /** Dia do mundo, começando em 1. */
    public static long day(World world) {
        return world.getFullTime() / 24000 + 1;
    }

    static String phase(World world) {
        long t = world.getTime();
        return t < 12000 ? "<yellow>☀" : t < 13800 ? "<gold>☾" : "<blue>☾";
    }

    private static long alarmTick(int minuteOfDay) {
        return ((minuteOfDay - 360 + 1440) % 1440) * 1000L / 60;
    }

    static String clockText(int minuteOfDay) {
        return String.format("%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    // ── Contador de dias (relógio na mão) ───────────────────────

    private void dayCounter() {
        if (!plugin.getConfig().getBoolean("decor.clock.day-counter", true)) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            var inventory = player.getInventory();
            if (inventory.getItemInMainHand().getType() != Material.CLOCK
                    && inventory.getItemInOffHand().getType() != Material.CLOCK) {
                continue;
            }
            World world = player.getWorld();
            if (world.getEnvironment() != World.Environment.NORMAL) {
                Msg.actionBar(player, "<gray>⌚ Este mundo não tem ciclo de dia e noite");
                continue;
            }
            Msg.actionBar(player, phase(world) + " <white>Dia <yellow>" + day(world) + " <dark_gray>• <white>" + hhmm(world));
        }
    }

    // ── Estado salvo no relógio ─────────────────────────────────

    int num(Entity clock, NamespacedKey key, int fallback) {
        return clock.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, fallback);
    }

    void put(Entity clock, NamespacedKey key, int value) {
        clock.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
    }

    boolean flag(Entity clock, NamespacedKey key, boolean fallback) {
        Byte value = clock.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return value == null ? fallback : value != 0;
    }

    void put(Entity clock, NamespacedKey key, boolean value) {
        clock.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) (value ? 1 : 0));
    }

    BlockFace face(ItemDisplay clock) {
        try {
            return BlockFace.valueOf(clock.getPersistentDataContainer().getOrDefault(kFace, PersistentDataType.STRING, "UP"));
        } catch (IllegalArgumentException e) {
            return BlockFace.UP;
        }
    }

    /** O bloco de ar onde o relógio fica (a frente do bloco de apoio). */
    Block cell(ItemDisplay clock) {
        return clock.getLocation().getBlock();
    }

    boolean canManage(Player player, ItemDisplay clock) {
        String owner = clock.getPersistentDataContainer().get(kOwner, PersistentDataType.STRING);
        return player.hasPermission("vertexx.admin") || player.getUniqueId().toString().equals(owner);
    }

    int maxTimer() {
        return Math.max(5, plugin.getConfig().getInt("decor.clock.max-timer-seconds", 3600));
    }

    // ── Colocar ─────────────────────────────────────────────────

    private static Quaternionf rotation(BlockFace face, float playerYaw) {
        float yaw = (float) Math.toRadians(Math.round(playerYaw / 45f) * 45f);
        return switch (face) {
            case UP -> new Quaternionf().rotationY((float) Math.PI - yaw).rotateX((float) -Math.PI / 2);
            case DOWN -> new Quaternionf().rotationY(-yaw).rotateX((float) Math.PI / 2);
            default -> new Quaternionf().rotationY((float) Math.atan2(face.getModX(), face.getModZ()));
        };
    }

    private ItemDisplay create(Player owner, Block cell, BlockFace face, ItemStack source) {
        Vector normal = face.getDirection();
        Location at = cell.getLocation().add(0.5, 0.5, 0.5).add(normal.clone().multiply(-0.5 + 0.035));
        ItemStack one = source.clone();
        one.setAmount(1);
        Quaternionf rotation = rotation(face, owner.getLocation().getYaw());
        ItemDisplay display = cell.getWorld().spawn(at, ItemDisplay.class, entity -> {
            entity.setItemStack(one);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setTransformation(new Transformation(new Vector3f(), rotation, new Vector3f(0.85f), new Quaternionf()));
            entity.setPersistent(true);
            entity.setViewRange(0.6f);
            var pdc = entity.getPersistentDataContainer();
            pdc.set(mark, PersistentDataType.BYTE, (byte) 1);
            pdc.set(kFace, PersistentDataType.STRING, face.name());
            pdc.set(kOwner, PersistentDataType.STRING, owner.getUniqueId().toString());
            pdc.set(kOwnerName, PersistentDataType.STRING, owner.getName());
        });
        register(display);
        return display;
    }

    private ItemDisplay clockAt(Block cell, BlockFace face) {
        for (ItemDisplay clock : clocks.values()) {
            if (clock.isValid() && face(clock) == face && cell(clock).equals(cell)) {
                return clock;
            }
        }
        return null;
    }

    private ItemDisplay clockInCell(Block cell) {
        for (ItemDisplay clock : clocks.values()) {
            if (clock.isValid() && cell(clock).equals(cell)) {
                return clock;
            }
        }
        return null;
    }

    /** O clique caiu em cima do relógio (a face do bloco de apoio, perto do centro)? */
    private ItemDisplay hitBy(PlayerInteractEvent event) {
        BlockFace face = event.getBlockFace();
        ItemDisplay clock = clockAt(event.getClickedBlock().getRelative(face), face);
        if (clock == null) {
            return null;
        }
        Vector at = event.getClickedPosition();
        if (at == null) {
            return clock;
        }
        double a = face.getModX() != 0 ? at.getZ() : at.getX();
        double b = face.getModY() != 0 ? at.getZ() : at.getY();
        return Math.abs(a - 0.5) <= 0.43 && Math.abs(b - 0.5) <= 0.43 ? clock : null;
    }

    private static boolean plainClock(ItemStack item) {
        return item != null && item.getType() == Material.CLOCK && dev.vertexx.end.EndItem.of(item) == null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        ItemDisplay hit = hitBy(event);
        if (hit == null && block.getType() == Material.STONE_BUTTON) {
            hit = clockInCell(block); // o botão do pulso fica no lugar do relógio
        }
        if (hit != null) {
            event.setCancelled(true);
            use(player, hit);
            return;
        }
        ItemStack item = event.getItem();
        if (!plainClock(item) || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (block.getType().isInteractable() && !player.isSneaking()) {
            return;
        }
        BlockFace face = event.getBlockFace();
        Block cell = block.getRelative(face);
        if (!cell.getType().isAir() || !block.getBlockData().isFaceSturdy(face, BlockSupport.FULL)
                || clockInCell(cell) != null) {
            return;
        }
        event.setCancelled(true);
        create(player, cell, face, item);
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
        Fx.worldSound(cell.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_COPPER_PLACE, 1f, 1.3f);
        Msg.actionBar(player, "<yellow>⌚ Relógio fixado <gray>— clique nele para configurar");
    }

    private void use(Player player, ItemDisplay clock) {
        boolean manage = canManage(player, clock);
        if (player.isSneaking() && player.getInventory().getItemInMainHand().getType().isAir()) {
            if (!manage) {
                Msg.actionBar(player, "<red>Só o dono pode recolher este relógio");
                Fx.deny(player);
                return;
            }
            pickUp(player, clock);
            return;
        }
        World world = clock.getWorld();
        if (!manage) {
            String owner = clock.getPersistentDataContainer().getOrDefault(kOwnerName, PersistentDataType.STRING, "?");
            Msg.actionBar(player, phase(world) + " <white>Dia <yellow>" + day(world) + " <dark_gray>• <white>" + hhmm(world)
                    + " <dark_gray>(de " + owner + ")");
            return;
        }
        new ClockMenu(plugin, player, this, clock).open();
    }

    void pickUp(Player player, ItemDisplay clock) {
        ItemStack stack = clock.getItemStack();
        remove(clock);
        if (stack != null && player.getGameMode() != GameMode.CREATIVE) {
            for (ItemStack left : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
        Fx.sound(player, Sound.BLOCK_COPPER_BREAK, 1f, 1.2f);
        player.closeInventory();
    }

    private void remove(ItemDisplay clock) {
        clocks.remove(clock.getUniqueId());
        dropTransient(clock.getUniqueId());
        Block cell = cell(clock);
        if (cell.getType() == Material.STONE_BUTTON) {
            cell.setType(Material.AIR, true);
        }
        pulsing.remove(clock.getUniqueId());
        clock.remove();
    }

    private void breakClock(ItemDisplay clock) {
        ItemStack stack = clock.getItemStack();
        Location at = clock.getLocation();
        remove(clock);
        if (stack != null) {
            at.getWorld().dropItemNaturally(at, stack);
        }
    }

    // ── Apoio quebrado ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Bukkit.getScheduler().runTask(plugin, this::validate);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Bukkit.getScheduler().runTask(plugin, this::validate);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Bukkit.getScheduler().runTask(plugin, this::validate);
    }

    private boolean supported(ItemDisplay clock) {
        Block cell = cell(clock);
        BlockFace face = face(clock);
        Material inside = cell.getType();
        if (!inside.isAir() && inside != Material.STONE_BUTTON) {
            return false;
        }
        return cell.getRelative(face.getOppositeFace()).getBlockData().isFaceSturdy(face, BlockSupport.FULL);
    }

    private void validate() {
        for (ItemDisplay clock : new ArrayList<>(clocks.values())) {
            if (clock.isValid() && !supported(clock)) {
                breakClock(clock);
            }
        }
    }

    // ── Alarme, timer e texto ───────────────────────────────────

    private void tick() {
        for (ItemDisplay clock : new ArrayList<>(clocks.values())) {
            if (!clock.isValid()) {
                clocks.remove(clock.getUniqueId());
                dropTransient(clock.getUniqueId());
                continue;
            }
            if (!supported(clock)) {
                breakClock(clock);
                continue;
            }
            UUID id = clock.getUniqueId();
            Block cell = cell(clock);
            if (cell.getType() == Material.STONE_BUTTON && !pulsing.contains(id)) {
                cell.setType(Material.AIR, true); // botão de um pulso que foi cortado (queda do servidor)
            }
            World world = clock.getWorld();
            long now = world.getTime();
            Long last = lastTime.put(id, now);
            if (last != null && last != now && flag(clock, kAlarmOn, false)) {
                long alarm = alarmTick(num(clock, kAlarmMin, 360));
                boolean fire = last <= now ? alarm > last && alarm <= now : alarm > last || alarm <= now;
                if (fire) {
                    ring(clock, "<yellow>⏰ Alarme <white>" + clockText(num(clock, kAlarmMin, 360)));
                }
            }
            int left = num(clock, kTimerLeft, -1);
            if (left >= 0) {
                left -= 10;
                if (left <= 0) {
                    ring(clock, "<aqua>⌛ Tempo esgotado");
                    left = flag(clock, kRepeat, false) ? Math.max(1, num(clock, kTimerSec, 60)) * 20 : -1;
                }
                put(clock, kTimerLeft, left);
            }
            updateText(clock, world);
        }
    }

    private void updateText(ItemDisplay clock, World world) {
        UUID id = clock.getUniqueId();
        TextDisplay text = texts.get(id);
        if (!flag(clock, kText, false)) {
            if (text != null) {
                text.remove();
                texts.remove(id);
                labels.remove(id);
            }
            return;
        }
        if (text == null || !text.isValid()) {
            BlockFace face = face(clock);
            double dy = face == BlockFace.DOWN ? -0.45 : 0.5;
            Location at = clock.getLocation().add(0, dy, 0);
            if (face.getModY() == 0) {
                at.add(face.getDirection().multiply(0.1));
            }
            text = world.spawn(at, TextDisplay.class, entity -> {
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setPersistent(false);
                entity.setViewRange(0.25f);
                entity.setDefaultBackground(false);
                entity.setBackgroundColor(org.bukkit.Color.fromARGB(90, 12, 10, 24));
                entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.55f), new Quaternionf()));
            });
            texts.put(id, text);
            labels.remove(id);
        }
        String label = phase(world) + " <white>" + hhmm(world) + "\n<gray>Dia <yellow>" + day(world);
        if (!label.equals(labels.get(id))) {
            text.text(Msg.mm(label));
            labels.put(id, label);
        }
    }

    private void ring(ItemDisplay clock, String message) {
        Location at = clock.getLocation();
        Sound sound = SOUNDS[Math.floorMod(num(clock, kSound, 0), SOUNDS.length)];
        for (int i = 0; i < 3; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (clock.isValid()) {
                    at.getWorld().playSound(at, sound, SoundCategory.BLOCKS, 1.1f, 1.0f);
                }
            }, i * 6L);
        }
        at.getWorld().spawnParticle(Particle.NOTE, at.clone().add(0, 0.3, 0), 6, 0.25, 0.2, 0.25, 1);
        for (Player near : at.getWorld().getNearbyPlayers(at, 12)) {
            Msg.actionBar(near, message);
        }
        if (flag(clock, kRedstone, true)) {
            pulse(clock);
        }
    }

    /** Liga um botão de pedra no lugar do relógio pelo tempo configurado: ele emite o sinal de redstone. */
    void pulse(ItemDisplay clock) {
        UUID id = clock.getUniqueId();
        Block cell = cell(clock);
        if (!cell.getType().isAir() || !pulsing.add(id)) {
            return;
        }
        BlockFace face = face(clock);
        BlockData data = Bukkit.createBlockData(Material.STONE_BUTTON);
        if (data instanceof Switch button) {
            button.setFace(face == BlockFace.UP ? Switch.Face.FLOOR : face == BlockFace.DOWN ? Switch.Face.CEILING : Switch.Face.WALL);
            button.setFacing(face.getModY() == 0 ? face : BlockFace.NORTH);
            button.setPowered(true);
        }
        cell.setBlockData(data, true);
        int ticks = Math.max(2, num(clock, kPulse, 20));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (cell.getType() == Material.STONE_BUTTON) {
                cell.setType(Material.AIR, true);
            }
            pulsing.remove(id);
        }, ticks);
    }

    void testRing(ItemDisplay clock) {
        ring(clock, "<yellow>⏰ Teste do relógio");
    }
}
