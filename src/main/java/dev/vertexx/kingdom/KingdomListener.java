package dev.vertexx.kingdom;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Onboarding (escolha obrigatória de reino) e identificação do reino no chat/TAB.
 * Jogadores sem reino ficam "pendentes": não se movem, não sofrem dano e o menu reabre se fechado.
 */
public final class KingdomListener implements Listener {

    private static final int TASK_PERIOD = 5;

    private final PluginMain plugin;
    /** Jogador pendente → ticks restantes até (re)abrir o menu. */
    private final Map<UUID, Integer> pending = new HashMap<>();
    private BukkitTask task;
    private int phase;

    public KingdomListener(PluginMain plugin) {
        this.plugin = plugin;
    }

    public boolean isPending(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    // ── Fluxo de onboarding ─────────────────────────────────────

    public void begin(Player player) {
        pending.put(player.getUniqueId(), plugin.getConfig().getInt("onboarding.open-delay-ticks", 40));
        if (plugin.intro().enabled()) {
            plugin.intro().schedule(player); // cerimônia cinematográfica; o menu só entra como reserva
        }
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TASK_PERIOD, TASK_PERIOD);
        }
    }

    /** Reabre o menu quando necessário e anima as partículas ao redor de quem está escolhendo. */
    private void tick() {
        phase++;
        Iterator<Map.Entry<UUID, Integer>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (plugin.intro().isActive(player)) {
                continue; // a cerimônia cuida da câmera, dos efeitos e da escolha
            }
            boolean open = player.getOpenInventory().getTopInventory().getHolder(false) instanceof KingdomGUI;
            if (!open) {
                int remaining = entry.getValue() - TASK_PERIOD;
                if (remaining <= 0 && !player.isDead()) {
                    new KingdomGUI(plugin, player).open();
                    remaining = 20;
                }
                entry.setValue(remaining);
            }
            orbit(player);
        }
        if (pending.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void orbit(Player player) {
        Location base = player.getLocation();
        double angle = phase * 0.35;
        for (int arm = 0; arm < 2; arm++) {
            double a = angle + Math.PI * arm;
            double y = base.getY() + 1.0 + Math.sin(angle * 0.5 + arm * Math.PI) * 0.6;
            player.spawnParticle(Particle.END_ROD,
                    base.getX() + Math.cos(a) * 1.4, y, base.getZ() + Math.sin(a) * 1.4, 1, 0, 0, 0, 0);
        }
        player.spawnParticle(Particle.ENCHANT, base.clone().add(0, 1.8, 0), 4, 0.8, 0.4, 0.8, 0.2);
    }

    /** Chamado pelo {@link KingdomGUI} quando o jogador confirma a escolha. */
    public void complete(Player player, Kingdom kingdom) {
        complete(player, kingdom, false);
    }

    /**
     * Atribui o reino. Com {@code cinematic}, a cerimônia já exibiu o título e os efeitos,
     * então aqui só ficam a atribuição, o anúncio e os ganchos seguintes.
     */
    public void complete(Player player, Kingdom kingdom, boolean cinematic) {
        if (pending.remove(player.getUniqueId()) == null) {
            return;
        }
        plugin.kingdoms().assign(player, kingdom);
        player.closeInventory();
        updateTab(player);

        if (!cinematic) {
            player.showTitle(Title.title(
                    Msg.mm("<bold>" + kingdom.styled()),
                    Msg.mm("<gray>Você agora é <white>" + Rank.MEMBRO.displayName() + "</white> do reino"),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofMillis(900))));
            Fx.sound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
            Location center = player.getLocation().add(0, 1, 0);
            Fx.burst(center, Particle.TOTEM_OF_UNDYING, 60, 0.4, 0.45);
            Fx.ring(player.getLocation().add(0, 0.1, 0), Particle.END_ROD, 1.6, 28);
        }

        Bukkit.broadcast(Msg.mm(Msg.PREFIX + "<white><player></white> <gray>juntou-se ao reino " + kingdom.styled() + "<gray>.",
                Placeholder.unparsed("player", player.getName())));
        plugin.afterOnboarding(player);
    }

    /** Um administrador definiu o reino do jogador manualmente. */
    public void adminAssigned(Player player) {
        boolean wasPending = pending.remove(player.getUniqueId()) != null;
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof KingdomGUI) {
            player.closeInventory();
        }
        updateTab(player);
        if (wasPending) {
            plugin.afterOnboarding(player);
        }
    }

    // ── Eventos de entrada/saída ────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!plugin.isReady()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Msg.mm("<gray>O servidor ainda está iniciando. Tente novamente em instantes."));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        handleJoin(event.getPlayer());
    }

    /** Também usado para jogadores já online quando o plugin termina de carregar. */
    public void handleJoin(Player player) {
        plugin.kingdoms().refreshName(player);
        updateTab(player);
        if (plugin.kingdoms().kingdomOf(player.getUniqueId()) == null) {
            begin(player);
        } else {
            plugin.afterOnboarding(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    // ── Menu ────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof KingdomGUI gui)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            gui.handleClick(event.getSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof KingdomGUI) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof KingdomGUI)) {
            return;
        }
        if (event.getPlayer() instanceof Player player && isPending(player)) {
            pending.put(player.getUniqueId(), TASK_PERIOD); // o tick reabre o menu
            Fx.deny(player);
            Msg.actionBar(player, "<red>Escolha um reino para começar a jogar.");
        }
    }

    // ── Bloqueio de ações enquanto pendente ─────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (pending.isEmpty() || !isPending(event.getPlayer()) || plugin.intro().isActive(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
            // trava o deslocamento horizontal, mas deixa cair e olhar em volta
            Location fixed = to.clone();
            fixed.setX(from.getX());
            fixed.setZ(from.getZ());
            event.setTo(fixed);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!pending.isEmpty() && event.getEntity() instanceof Player player && isPending(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!pending.isEmpty() && event.getTarget() instanceof Player player && isPending(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!pending.isEmpty() && isPending(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!pending.isEmpty() && isPending(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!pending.isEmpty() && isPending(event.getPlayer())) {
            event.setCancelled(true);
            Msg.send(event.getPlayer(), "<red>Escolha um reino antes de usar comandos.");
        }
    }

    // ── Identificação no chat e no TAB ──────────────────────────

    public void updateTab(Player player) {
        Kingdom kingdom = plugin.kingdoms().kingdomOf(player.getUniqueId());
        if (kingdom == null || !plugin.getConfig().getBoolean("chat.kingdom-tag", true)) {
            player.playerListName(null);
            return;
        }
        player.playerListName(Component.text().append(kingdom.tag()).append(Component.space())
                .append(Component.text(player.getName())).build());
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.kingdom-tag", true)) {
            return;
        }
        // evento assíncrono: o mapa de membros é concorrente, a leitura é segura
        Kingdom kingdom = plugin.kingdoms().kingdomOf(event.getPlayer().getUniqueId());
        if (kingdom == null) {
            return;
        }
        Component tag = kingdom.tag();
        event.renderer((source, displayName, message, viewer) -> Component.text()
                .append(tag).append(Component.space())
                .append(displayName).append(Msg.mm("<dark_gray>: </dark_gray>"))
                .append(message).build());
    }
}
