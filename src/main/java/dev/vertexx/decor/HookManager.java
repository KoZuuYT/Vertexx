package dev.vertexx.decor;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Ganchos de exposição: um Tripwire Hook vira um suporte onde se pendura um item de forma bonita.
 * O item é uma {@code ItemDisplay} que guarda no próprio PDC o dono, a posição do gancho e a configuração
 * (escala, rotação, inclinação, altura, distância, modo e giro).
 */
public final class HookManager implements Listener {

    /** Como o item é mostrado. */
    enum Mode {
        FIXED("Moldura", ItemDisplay.ItemDisplayTransform.FIXED),
        HEAD("Bloco", ItemDisplay.ItemDisplayTransform.HEAD),
        GROUND("Solto", ItemDisplay.ItemDisplayTransform.GROUND);

        final String label;
        final ItemDisplay.ItemDisplayTransform transform;

        Mode(String label, ItemDisplay.ItemDisplayTransform transform) {
            this.label = label;
            this.transform = transform;
        }

        Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private final PluginMain plugin;
    private final NamespacedKey mark;
    final NamespacedKey kOwner, kOwnerName, kPos, kScale, kYaw, kTilt, kUp, kOut, kMode, kSpin, kSide, kRoll;
    /** Ordem fixa da configuração salva e copiada: tamanho, rotação, inclinação, altura, distância, modo, giro, lateral, rolagem. */
    private NamespacedKey[] order;
    private static final int[] DEFAULTS = {70, 0, 0, -10, 22, 0, 0, 0, 0};
    private final Map<UUID, String> clipboard = new HashMap<>();
    private final Map<UUID, ItemDisplay> hooks = new HashMap<>();
    private final Map<UUID, Float> spinAngle = new HashMap<>();
    private BukkitTask task;

    public HookManager(PluginMain plugin) {
        this.plugin = plugin;
        this.mark = new NamespacedKey(plugin, "hook_display");
        this.kOwner = new NamespacedKey(plugin, "hk_owner");
        this.kOwnerName = new NamespacedKey(plugin, "hk_owner_name");
        this.kPos = new NamespacedKey(plugin, "hk_pos");
        this.kScale = new NamespacedKey(plugin, "hk_scale");
        this.kYaw = new NamespacedKey(plugin, "hk_yaw");
        this.kTilt = new NamespacedKey(plugin, "hk_tilt");
        this.kUp = new NamespacedKey(plugin, "hk_up");
        this.kOut = new NamespacedKey(plugin, "hk_out");
        this.kMode = new NamespacedKey(plugin, "hk_mode");
        this.kSpin = new NamespacedKey(plugin, "hk_spin");
        this.kSide = new NamespacedKey(plugin, "hk_side");
        this.kRoll = new NamespacedKey(plugin, "hk_roll");
        this.order = new NamespacedKey[] {kScale, kYaw, kTilt, kUp, kOut, kMode, kSpin, kSide, kRoll};
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("decor.hooks.enabled", true);
    }

    // ── Ciclo de vida ───────────────────────────────────────────

    public void start() {
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                register(display);
            }
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
    }

    private boolean isHook(Entity entity) {
        return entity instanceof ItemDisplay && entity.getPersistentDataContainer().has(mark, PersistentDataType.BYTE);
    }

    private void register(Entity entity) {
        if (isHook(entity)) {
            hooks.put(entity.getUniqueId(), (ItemDisplay) entity);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        event.getEntities().forEach(this::register);
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            hooks.remove(entity.getUniqueId());
            spinAngle.remove(entity.getUniqueId());
        }
    }

    // ── Estado ──────────────────────────────────────────────────

    int num(Entity display, NamespacedKey key, int fallback) {
        return display.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, fallback);
    }

    void put(Entity display, NamespacedKey key, int value) {
        display.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
        if (display instanceof ItemDisplay item) {
            save(item);
        }
    }

    // ── Configuração salva no gancho (sobrevive à troca/retirada do item) ──

    private NamespacedKey storeKey(Block hook) {
        return new NamespacedKey(plugin, "hkcfg_" + hook.getX() + "_" + hook.getY() + "_" + hook.getZ());
    }

    String serialize(Entity display) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < order.length; i++) {
            out.append(i == 0 ? "" : ",").append(num(display, order[i], DEFAULTS[i]));
        }
        return out.toString();
    }

    /** Aplica uma configuração serializada ao item exposto (valores inválidos viram o padrão). */
    void deserialize(Entity display, String data) {
        String[] parts = data.split(",");
        for (int i = 0; i < order.length; i++) {
            int value = DEFAULTS[i];
            try {
                if (i < parts.length) {
                    value = Integer.parseInt(parts[i].trim());
                }
            } catch (NumberFormatException ignored) {
                // mantém o padrão
            }
            display.getPersistentDataContainer().set(order[i], PersistentDataType.INTEGER, value);
        }
    }

    private void save(ItemDisplay display) {
        Block hook = hookBlock(display);
        hook.getChunk().getPersistentDataContainer().set(storeKey(hook), PersistentDataType.STRING, serialize(display));
    }

    private void forget(Block hook) {
        hook.getChunk().getPersistentDataContainer().remove(storeKey(hook));
    }

    void copy(Player player, ItemDisplay display) {
        clipboard.put(player.getUniqueId(), serialize(display));
        Fx.sound(player, Sound.ENTITY_ITEM_PICKUP, 1f, 1.4f);
        Msg.actionBar(player, "<green>Posição copiada <gray>— abra outro gancho e clique em Colar");
    }

    boolean hasCopy(Player player) {
        return clipboard.containsKey(player.getUniqueId());
    }

    void paste(Player player, ItemDisplay display) {
        String data = clipboard.get(player.getUniqueId());
        if (data == null) {
            Msg.actionBar(player, "<red>Nada copiado ainda");
            Fx.deny(player);
            return;
        }
        deserialize(display, data);
        save(display);
        Fx.sound(player, Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 1.2f);
        Msg.actionBar(player, "<green>Posição colada neste gancho");
    }

    void reset(ItemDisplay display) {
        deserialize(display, "");
        save(display);
    }

    boolean spins(Entity display) {
        return num(display, kSpin, 0) != 0;
    }

    Mode mode(Entity display) {
        return Mode.values()[Math.floorMod(num(display, kMode, 0), Mode.values().length)];
    }

    boolean canManage(Player player, ItemDisplay display) {
        String owner = display.getPersistentDataContainer().get(kOwner, PersistentDataType.STRING);
        return player.hasPermission("vertexx.admin") || player.getUniqueId().toString().equals(owner);
    }

    private static BlockFace facing(Block hook) {
        return hook.getBlockData() instanceof Directional directional ? directional.getFacing() : BlockFace.NORTH;
    }

    private static String key(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private ItemDisplay displayAt(Block hook) {
        String wanted = key(hook);
        Location center = hook.getLocation().add(0.5, 0.5, 0.5);
        for (Entity near : hook.getWorld().getNearbyEntities(center, 0.8, 0.8, 0.8)) {
            if (isHook(near) && wanted.equals(near.getPersistentDataContainer().get(kPos, PersistentDataType.STRING))) {
                return (ItemDisplay) near;
            }
        }
        return null;
    }

    private Block hookBlock(ItemDisplay display) {
        String[] parts = display.getPersistentDataContainer().getOrDefault(kPos, PersistentDataType.STRING, "0,0,0").split(",");
        try {
            return display.getWorld().getBlockAt(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (RuntimeException e) {
            return display.getLocation().getBlock();
        }
    }

    // ── Visual ──────────────────────────────────────────────────

    /** Recalcula a transformação a partir da configuração (com interpolação curta para os ajustes ficarem suaves). */
    void apply(ItemDisplay display, int interpolate) {
        Block hook = hookBlock(display);
        BlockFace face = facing(hook);
        float out = num(display, kOut, 22) / 100f;
        float up = num(display, kUp, -10) / 100f;
        float scale = Math.max(5, num(display, kScale, 70)) / 100f;
        float yaw = (float) (Math.atan2(face.getModX(), face.getModZ()) + Math.toRadians(num(display, kYaw, 0))
                + spinAngle.getOrDefault(display.getUniqueId(), 0f));
        float tilt = (float) Math.toRadians(num(display, kTilt, 0));
        float roll = (float) Math.toRadians(num(display, kRoll, 0));
        float side = num(display, kSide, 0) / 100f;
        Quaternionf rotation = new Quaternionf().rotationY(yaw).rotateX(tilt).rotateZ(roll);
        Vector3f translation = new Vector3f(face.getModX() * out + face.getModZ() * side, up,
                face.getModZ() * out - face.getModX() * side);
        display.setItemDisplayTransform(mode(display).transform);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(interpolate);
        display.setTransformation(new Transformation(translation, rotation, new Vector3f(scale), new Quaternionf()));
    }

    private void tick() {
        for (ItemDisplay display : new ArrayList<>(hooks.values())) {
            if (!display.isValid()) {
                hooks.remove(display.getUniqueId());
                spinAngle.remove(display.getUniqueId());
                continue;
            }
            if (hookBlock(display).getType() != Material.TRIPWIRE_HOOK) {
                release(display);
                continue;
            }
            if (spins(display) && !display.getWorld().getNearbyPlayers(display.getLocation(), 48).isEmpty()) {
                float angle = (spinAngle.getOrDefault(display.getUniqueId(), 0f) + (float) (Math.PI / 2)) % (float) (Math.PI * 2);
                spinAngle.put(display.getUniqueId(), angle);
                apply(display, 20);
            } else if (!spins(display) && spinAngle.remove(display.getUniqueId()) != null) {
                apply(display, 5);
            }
        }
    }

    // ── Pendurar, trocar e retirar ──────────────────────────────

    private ItemDisplay hang(Player owner, Block hook, ItemStack source) {
        ItemStack one = source.clone();
        one.setAmount(1);
        ItemDisplay display = hook.getWorld().spawn(hook.getLocation().add(0.5, 0.5, 0.5), ItemDisplay.class, entity -> {
            entity.setItemStack(one);
            entity.setPersistent(true);
            entity.setViewRange(0.6f);
            var pdc = entity.getPersistentDataContainer();
            pdc.set(mark, PersistentDataType.BYTE, (byte) 1);
            pdc.set(kOwner, PersistentDataType.STRING, owner.getUniqueId().toString());
            pdc.set(kOwnerName, PersistentDataType.STRING, owner.getName());
            pdc.set(kPos, PersistentDataType.STRING, key(hook));
        });
        hooks.put(display.getUniqueId(), display);
        // reaproveita a posição que o jogador já configurou neste gancho
        String saved = hook.getChunk().getPersistentDataContainer().get(storeKey(hook), PersistentDataType.STRING);
        if (saved != null) {
            deserialize(display, saved);
        }
        apply(display, 0);
        return display;
    }

    private void giveBack(Player player, ItemStack stack) {
        if (stack == null || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        for (ItemStack left : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    /** Tira o item do gancho e devolve ao jogador. */
    void take(Player player, ItemDisplay display) {
        giveBack(player, display.getItemStack());
        hooks.remove(display.getUniqueId());
        spinAngle.remove(display.getUniqueId());
        display.remove();
        Fx.sound(player, Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, 1f, 1f);
        player.closeInventory();
    }

    /** O gancho sumiu: o item cai no chão. */
    private void release(ItemDisplay display) {
        forget(hookBlock(display));
        ItemStack stack = display.getItemStack();
        Location at = display.getLocation();
        hooks.remove(display.getUniqueId());
        spinAngle.remove(display.getUniqueId());
        display.remove();
        if (stack != null && !stack.getType().isAir()) {
            at.getWorld().dropItemNaturally(at, stack);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.TRIPWIRE_HOOK) {
            return;
        }
        ItemDisplay display = displayAt(event.getBlock());
        if (display != null) {
            release(display);
        }
        forget(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled() || event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.TRIPWIRE_HOOK
                || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        Block hook = event.getClickedBlock();
        ItemStack held = event.getItem();
        boolean empty = held == null || held.getType().isAir();
        ItemDisplay display = displayAt(hook);
        if (display == null) {
            // gancho livre: pendura o item da mão (string e ganchos ficam livres para montar armadilhas)
            if (empty || player.isSneaking() || held.getType() == Material.STRING || held.getType() == Material.TRIPWIRE_HOOK) {
                return;
            }
            event.setCancelled(true);
            hang(player, hook, held);
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.setAmount(held.getAmount() - 1);
            }
            Fx.worldSound(hook.getLocation().add(0.5, 0.5, 0.5), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 1f, 1f);
            Msg.actionBar(player, "<yellow>Item pendurado <gray>— clique com a mão vazia para configurar");
            return;
        }
        event.setCancelled(true);
        if (!canManage(player, display)) {
            String owner = display.getPersistentDataContainer().getOrDefault(kOwnerName, PersistentDataType.STRING, "?");
            Msg.actionBar(player, "<red>Este gancho é de " + owner);
            Fx.deny(player);
            return;
        }
        if (!empty && !player.isSneaking()) {
            ItemStack old = display.getItemStack(); // troca: o item da mão entra e o antigo volta ao jogador
            ItemStack one = held.clone();
            one.setAmount(1);
            display.setItemStack(one);
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.setAmount(held.getAmount() - 1);
            }
            giveBack(player, old);
            Fx.worldSound(hook.getLocation().add(0.5, 0.5, 0.5), Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 1f);
            return;
        }
        if (player.isSneaking() && empty) {
            take(player, display);
            return;
        }
        if (empty) {
            new HookMenu(plugin, player, this, display).open();
        }
    }
}
