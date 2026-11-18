package dev.vertexx.intro;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import dev.vertexx.core.YamlStore;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryType;

/**
 * Recado de boas-vindas com as regras do SMP. Quem entra sem ter confirmado recebe, no chat, o aviso para baixar o
 * pacote de recursos ({@code /pacote aceitar}); em seguida abre o menu de introdução (features, skills, bosses e
 * regras) e só depois de confirmar que leu tudo e quer participar o jogador é liberado. Jogadores novos são levados
 * ao spawn ao confirmar; quem já jogava continua onde estava. Enquanto não confirma, o jogador fica parado e
 * protegido. A confirmação fica em {@code onboarding.yml}.
 */
public final class IntroManager implements Listener {

    private final PluginMain plugin;
    private final YamlStore store;
    /** Quem já confirmou o recado. */
    private final Set<UUID> confirmed = ConcurrentHashMap.newKeySet();
    /** Jogadores que entraram pela primeira vez e ainda não confirmaram (serão levados ao spawn). */
    private final Set<UUID> newcomers = ConcurrentHashMap.newKeySet();
    /** Jogadores com o recado pendente (parados e protegidos). */
    private final Set<UUID> pending = new HashSet<>();
    /** Quem já teve o menu aberto nesta sessão. */
    private final Set<UUID> opened = new HashSet<>();
    private final Map<UUID, Integer> pages = new HashMap<>();
    /** Menus que estão sendo trocados de página (o fechamento não conta como saída). */
    private final Set<UUID> switching = new HashSet<>();
    private volatile boolean loaded;

    public IntroManager(PluginMain plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "onboarding.yml");
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("onboarding.enabled", true);
    }

    /** O recado está pendente para o jogador (parado, ainda não confirmou). */
    public boolean isPending(Player player) {
        return pending.contains(player.getUniqueId());
    }

    // ── Persistência ────────────────────────────────────────────

    /** Leitura do disco: chame fora da thread principal. */
    public YamlConfiguration read() {
        return store.load();
    }

    /** Aplica os dados lidos: chame na thread principal. */
    public void apply(YamlConfiguration data) {
        confirmed.clear();
        newcomers.clear();
        for (String id : data.getStringList("confirmed")) {
            addUuid(confirmed, id);
        }
        for (String id : data.getStringList("newcomers")) {
            addUuid(newcomers, id);
        }
        loaded = true;
        // jogadores que entraram antes do carregamento terminar
        Bukkit.getOnlinePlayers().forEach(this::begin);
    }

    private static void addUuid(Set<UUID> into, String text) {
        try {
            into.add(UUID.fromString(text));
        } catch (IllegalArgumentException ignored) {
            // entrada inválida: ignora
        }
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("confirmed", confirmed.stream().map(UUID::toString).toList());
        data.set("newcomers", newcomers.stream().map(UUID::toString).toList());
        return data;
    }

    private void save() {
        store.saveAsync(snapshot());
    }

    public void saveNow() {
        store.saveNow(snapshot());
    }

    // ── Fluxo ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPlayedBefore() && !confirmed.contains(player.getUniqueId())) {
            newcomers.add(player.getUniqueId());
            save();
        }
        begin(player);
    }

    /** Deixa o jogador parado e inicia a sequência: aviso do pacote no chat e, depois, o menu. */
    private void begin(Player player) {
        UUID id = player.getUniqueId();
        if (!enabled() || !loaded || confirmed.contains(id) || !pending.add(id)) {
            return;
        }
        opened.remove(id);
        pages.put(id, 0);
        ResourcePackNote note = packNote(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !pending.contains(id)) {
                return;
            }
            if (note == ResourcePackNote.WARN) {
                sendPackWarning(player);
            }
        }, 20L);
        // espera o pacote carregar (o menu depende dele); sem pacote a espera é curta
        long wait = note == ResourcePackNote.NONE ? 60L : plugin.resourcePack().isLoaded(player) ? 30L : 300L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> openFirst(player), wait);
    }

    private enum ResourcePackNote { WARN, WAIT, NONE }

    private ResourcePackNote packNote(Player player) {
        if (!plugin.resourcePack().isConfigured() || plugin.bedrock().is(player)) {
            return ResourcePackNote.NONE;
        }
        return plugin.resourcePack().isLoaded(player) || plugin.resourcePack().accepted(player)
                ? ResourcePackNote.WAIT : ResourcePackNote.WARN;
    }

    private void sendPackWarning(Player player) {
        player.sendMessage(Msg.mm(""));
        player.sendMessage(Msg.mm(" <gradient:#f7b32b:#f72585><bold>⚠ Antes de começar: baixe o pacote de recursos</bold></gradient>"));
        player.sendMessage(Msg.mm(" <gray>O menu de introdução e todas as novidades do Vertexx (itens, criaturas,"));
        player.sendMessage(Msg.mm(" <gray>menus e sons) <white>dependem</white> do pacote de recursos do servidor."));
        player.sendMessage(Msg.mm(" <gray>Digite <white><bold>/pacote aceitar</bold></white> ou clique no botão abaixo."));
        player.sendMessage(Msg.mm(""));
        player.sendMessage(Msg.mm("   <click:run_command:'/pacote aceitar'><hover:show_text:'<green>Baixar e carregar o pacote agora'>"
                + "<green><bold>[ ✔ Baixar o pacote ]</bold></hover></click>"));
        player.sendMessage(Msg.mm(""));
        Fx.sound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
    }

    private void openFirst(Player player) {
        UUID id = player.getUniqueId();
        if (player.isOnline() && pending.contains(id) && opened.add(id)) {
            show(player, pages.getOrDefault(id, 0));
        }
    }

    /** Chamado pelo gerenciador do pacote quando o jogador termina de carregá-lo. */
    public void onPackLoaded(Player player) {
        UUID id = player.getUniqueId();
        if (!pending.contains(id)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !pending.contains(id)) {
                return;
            }
            opened.add(id);
            show(player, pages.getOrDefault(id, 0)); // abre (ou redesenha com o visual do pacote)
        }, 20L);
    }

    void show(Player player, int page) {
        UUID id = player.getUniqueId();
        pages.put(id, page);
        switching.add(id);
        try {
            new IntroMenu(plugin, this, player, page, !pending.contains(id)).open();
        } finally {
            switching.remove(id);
        }
    }

    /** {@code /vertexx recado}: reabre o recado (pendente) ou o rele (já confirmado, só leitura). */
    public void open(Player player) {
        UUID id = player.getUniqueId();
        if (pending.contains(id)) {
            opened.add(id);
            show(player, pages.getOrDefault(id, 0));
        } else {
            show(player, 0);
        }
    }

    /** O jogador confirmou que leu tudo e quer participar. */
    void confirm(Player player) {
        UUID id = player.getUniqueId();
        if (!pending.remove(id)) {
            player.closeInventory();
            return;
        }
        opened.remove(id);
        pages.remove(id);
        confirmed.add(id);
        boolean teleport = newcomers.remove(id) || plugin.getConfig().getBoolean("onboarding.teleport-veterans", false);
        save();
        player.closeInventory();
        player.showTitle(Title.title(Msg.mm("<gradient:#7f5af0:#2cb67d><bold>Bem-vindo ao SMP!</bold></gradient>"),
                Msg.mm("<gray>Boa jornada, <white>" + Msg.esc(player.getName())),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2200), Duration.ofMillis(700))));
        Fx.sound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Msg.send(player, "<green>Recado confirmado!</green> <gray>Você pode relê-lo quando quiser com <white>/vertexx recado</white>.");
        if (teleport) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> toSpawn(player), 30L);
        }
    }

    /** O jogador não quer participar: sai do servidor (o recado volta quando ele entrar de novo). */
    void decline(Player player) {
        player.kick(Msg.mm("<red>Você escolheu não participar do SMP.\n<gray>Volte quando quiser: o recado aparece de novo ao entrar."));
    }

    private void toSpawn(Player player) {
        if (!player.isOnline()) {
            return;
        }
        String name = plugin.getConfig().getString("onboarding.spawn-world", "");
        World world = name == null || name.isBlank() ? Bukkit.getWorlds().get(0) : Bukkit.getWorld(name);
        if (world == null) {
            world = Bukkit.getWorlds().get(0);
        }
        Location spawn = world.getSpawnLocation().add(0.5, 0, 0.5);
        player.teleportAsync(spawn).thenAccept(ok -> {
            if (ok) {
                Fx.sound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
            }
        });
    }

    // ── Eventos ─────────────────────────────────────────────────

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !(event.getInventory().getHolder(false) instanceof IntroMenu)) {
            return;
        }
        UUID id = player.getUniqueId();
        if (!pending.contains(id) || switching.contains(id) || event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW
                || event.getReason() == InventoryCloseEvent.Reason.DISCONNECT) {
            return;
        }
        player.sendMessage(Msg.mm(""));
        player.sendMessage(Msg.mm(" <gray>Você ainda não confirmou o recado do SMP. "
                + "<click:run_command:'/vertexx recado'><hover:show_text:'<green>Reabrir o recado'>"
                + "<green><bold>[ Abrir o recado ]</bold></hover></click>"));
        player.sendMessage(Msg.mm(""));
        // reabre sozinho em instantes (a menos que o jogador esteja em outra tela, como o download do pacote)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && pending.contains(id) && player.getOpenInventory().getType() == InventoryType.CRAFTING) {
                show(player, pages.getOrDefault(id, 0));
            }
        }, 100L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pending.remove(id);
        opened.remove(id);
        pages.remove(id);
        switching.remove(id);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (pending.contains(event.getPlayer().getUniqueId()) && event.hasChangedPosition()) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY()) {
                to.setX(from.getX());
                to.setZ(from.getZ());
                if (to.getY() > from.getY()) {
                    to.setY(from.getY());
                }
                event.setTo(to);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && pending.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && pending.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (pending.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (pending.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (pending.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (pending.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Textos das páginas do recado, em um só lugar para facilitar a edição. */
    static List<String> rules() {
        return List.of(
                "<gold><bold>1. Conviva</bold></gold>|<gray>Esteja perto dos outros jogadores e|<gray>participe da vida do servidor. Não se|<gray>isole só para manter o seu survival:|<gray>o SMP é feito de comunidade.",
                "<red><bold>2. Não roube</bold></red>|<gray>Nada de pegar itens de baús, túmulos|<gray>ou bases alheias sem permissão.|<gray>O que é dos outros fica com eles.",
                "<dark_red><bold>3. Não mate</bold></dark_red>|<gray>Sem PvP: não ataque nem mate|<gray>outros jogadores, nem com armadilhas|<gray>ou truques. Aqui o inimigo são os mobs.",
                "<yellow><bold>4. Sem griefing</bold></yellow>|<gray>Não destrua, altere nem estrague|<gray>construções e terrenos de outros|<gray>jogadores (nem a natureza ao redor).");
    }
}
