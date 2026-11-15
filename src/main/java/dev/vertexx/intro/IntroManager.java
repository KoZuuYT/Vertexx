package dev.vertexx.intro;

import dev.vertexx.PluginMain;
import dev.vertexx.kingdom.KingdomGUI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/**
 * Gerencia as cerimônias de escolha de reino ({@link IntroSession}) e traduz as entradas do jogador:
 * botão esquerdo/direito trocam de bandeirão e agachar confirma.
 * A posição original fica gravada no próprio jogador (PDC), então mesmo um crash durante a cerimônia
 * é desfeito no próximo login.
 */
public final class IntroManager implements Listener {

    static final NamespacedKey ORIGIN = NamespacedKey.fromString("vertexx:intro_origin");
    static final NamespacedKey SELECTOR_TAG = NamespacedKey.fromString("vertexx:intro_selector");

    private final PluginMain plugin;
    private final Map<UUID, IntroSession> sessions = new HashMap<>();
    /** Jogadores já reservados, aguardando o início (pequeno atraso após o login). */
    private final Set<UUID> starting = new HashSet<>();
    private final Map<UUID, Integer> lastRightClick = new HashMap<>();

    public IntroManager(PluginMain plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return "cutscene".equalsIgnoreCase(plugin.getConfig().getString("onboarding.mode", "cutscene"));
    }

    /** Há uma cerimônia em andamento (ou prestes a começar) para o jogador. */
    public boolean isActive(Player player) {
        return sessions.containsKey(player.getUniqueId()) || starting.contains(player.getUniqueId());
    }

    /** Reserva e inicia a cerimônia após um instante, para o login terminar de assentar. */
    public void schedule(Player player) {
        starting.add(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            starting.remove(player.getUniqueId());
            if (!player.isOnline() || plugin.kingdoms().kingdomOf(player.getUniqueId()) != null) {
                return;
            }
            start(player);
        }, 30L);
    }

    private void start(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            return;
        }
        launch(player, false);
    }

    /** Ensaio da cerimônia para um administrador (não altera o reino). */
    public boolean demo(Player player) {
        if (isActive(player)) {
            return false;
        }
        launch(player, true);
        return true;
    }

    private void launch(Player player, boolean demo) {
        IntroSession session = new IntroSession(plugin, this, player, demo);
        sessions.put(player.getUniqueId(), session);
        try {
            session.begin();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível iniciar a cerimônia de " + player.getName(), e);
            abort(player, true);
        }
    }

    /** Encerra a cerimônia restaurando o jogador; {@code fallbackMenu} abre o menu clássico de escolha. */
    void abort(Player player, boolean fallbackMenu) {
        IntroSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.abort(!player.isOnline());
        }
        if (fallbackMenu && player.isOnline() && plugin.kingdoms().kingdomOf(player.getUniqueId()) == null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && plugin.kingdoms().kingdomOf(player.getUniqueId()) == null) {
                    new KingdomGUI(plugin, player).open();
                }
            }, 40L);
        }
    }

    void end(IntroSession session) {
        sessions.remove(session.player().getUniqueId());
        session.stop();
    }

    public void shutdown() {
        for (IntroSession session : new java.util.ArrayList<>(sessions.values())) {
            session.abort(true);
        }
        sessions.clear();
    }

    public void onPackStatus(Player player, PlayerResourcePackStatusEvent.Status status) {
        IntroSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.onPackStatus(status);
        }
    }

    private IntroSession of(Player player) {
        return sessions.get(player.getUniqueId());
    }

    // ── Persistência da posição original ────────────────────────

    static String encode(Location location, GameMode mode) {
        return String.join(";", location.getWorld().getName(), Double.toString(location.getX()),
                Double.toString(location.getY()), Double.toString(location.getZ()),
                Float.toString(location.getYaw()), Float.toString(location.getPitch()), mode.name());
    }

    /** Desfaz uma cerimônia interrompida (crash/queda) encontrada no login. */
    private void restoreStray(Player player) {
        String data = player.getPersistentDataContainer().get(ORIGIN, PersistentDataType.STRING);
        if (data == null || sessions.containsKey(player.getUniqueId())) {
            return;
        }
        player.getPersistentDataContainer().remove(ORIGIN);
        try {
            String[] parts = data.split(";");
            World world = Bukkit.getWorld(parts[0]);
            if (world != null) {
                player.teleport(new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5])));
            }
            player.setGameMode(GameMode.valueOf(parts[6]));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Posição de cerimônia ilegível para " + player.getName() + ": " + e);
        }
        stripSelector(player, null, player.getInventory().getHeldItemSlot());
        player.resetPlayerTime();
        player.resetPlayerWeather();
    }

    /** Remove o Cetro do Escolhido do inventário e devolve o item que estava no slot 4. */
    static void stripSelector(Player player, ItemStack savedSlotItem, int heldSlot) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.hasItemMeta()
                    && item.getItemMeta().getPersistentDataContainer().has(SELECTOR_TAG, PersistentDataType.BYTE)) {
                inventory.setItem(slot, null);
            }
        }
        if (savedSlotItem != null && inventory.getItem(4) == null) {
            inventory.setItem(4, savedSlotItem);
        }
        inventory.setHeldItemSlot(Math.max(0, Math.min(8, heldSlot)));
    }

    // ── Eventos ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        restoreStray(joined);
        for (IntroSession session : sessions.values()) {
            joined.hidePlayer(plugin, session.player());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        starting.remove(player.getUniqueId());
        lastRightClick.remove(player.getUniqueId());
        IntroSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.abort(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        IntroSession session = of(event.getPlayer());
        if (session == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        event.setCancelled(true);
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            lastRightClick.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
            session.move(1);
        }
    }

    /** O clique esquerdo no ar chega como animação de braço. */
    @EventHandler
    public void onAnimation(PlayerAnimationEvent event) {
        IntroSession session = of(event.getPlayer());
        if (session == null || event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Integer right = lastRightClick.get(event.getPlayer().getUniqueId());
        if (right != null && Bukkit.getCurrentTick() - right <= 2) {
            return; // o braço balançou por causa do clique direito
        }
        session.move(-1);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        IntroSession session = of(event.getPlayer());
        if (session != null && event.isSneaking()) {
            session.confirm();
        }
    }

    /** Agachar quando montado tenta desmontar: vira o gesto de confirmação. */
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        IntroSession session = of(player);
        if (session == null || !session.isSeat(event.getDismounted()) || session.dismountAllowed()) {
            return;
        }
        if (event.isCancellable()) {
            event.setCancelled(true);
        }
        session.confirm();
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        if (of(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (of(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && of(player) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && of(player) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && of(player) != null) {
            event.setCancelled(true);
        }
    }
}
