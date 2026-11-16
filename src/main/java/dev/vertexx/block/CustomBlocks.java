package dev.vertexx.block;

import dev.vertexx.PluginMain;
import dev.vertexx.end.EndItem;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Blocos customizados (note block em estados reservados, no estilo do CreatorCore): reconhece, coloca, quebra e
 * dropa, e blinda o note block contra o comportamento vanilla (trocar nota, tocar, atualizar por vizinhos).
 * A dureza do note block é fixa no cliente, então enquanto o jogador bate num bloco customizado ele recebe
 * Fadiga de Mineração (invisível) e o servidor acumula o progresso, mostra as rachaduras e quebra o bloco.
 */
public final class CustomBlocks implements Listener {

    private static final int FATIGUE_AMPLIFIER = 4;

    private static final class Session {
        final Block block;
        final CustomBlock type;
        final PotionEffect previous;
        BukkitTask task;
        double progress;

        Session(Block block, CustomBlock type, PotionEffect previous) {
            this.block = block;
            this.type = type;
            this.previous = previous;
        }
    }

    private final PluginMain plugin;
    private final Map<String, CustomBlock> byState = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();

    public CustomBlocks(PluginMain plugin) {
        this.plugin = plugin;
        for (CustomBlock block : CustomBlock.values()) {
            byState.put(block.data().getAsString(), block);
        }
    }

    /** Receitas: 9 Fragmentos de Enderita fazem o bloco, e o bloco volta a 9 fragmentos. */
    public void start() {
        ItemStack fragment = EndItem.ENDERITE_FRAGMENT.create(plugin);
        NamespacedKey toBlock = new NamespacedKey(plugin, "enderite_block");
        NamespacedKey toFragments = new NamespacedKey(plugin, "enderite_fragments");
        plugin.getServer().removeRecipe(toBlock);
        plugin.getServer().removeRecipe(toFragments);
        ShapedRecipe block = new ShapedRecipe(toBlock, CustomBlock.ENDERITE_BLOCK.item(1));
        block.shape("FFF", "FFF", "FFF");
        block.setIngredient('F', new RecipeChoice.ExactChoice(fragment));
        plugin.getServer().addRecipe(block);
        ItemStack nine = fragment.clone();
        nine.setAmount(9);
        ShapelessRecipe back = new ShapelessRecipe(toFragments, nine);
        back.addIngredient(new RecipeChoice.ExactChoice(CustomBlock.ENDERITE_BLOCK.item(1)));
        plugin.getServer().addRecipe(back);
    }

    public void shutdown() {
        for (UUID id : List.copyOf(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                stop(player);
            }
        }
        sessions.clear();
    }

    /** O bloco customizado em {@code block}, ou nulo. */
    public CustomBlock at(Block block) {
        if (block.getType() != Material.NOTE_BLOCK) {
            return null;
        }
        return byState.get(block.getBlockData().getAsString());
    }

    /** Coloca o bloco sem atualizar vizinhos (o estado do note block não pode ser recalculado). */
    public static void place(Block block, CustomBlock type) {
        block.setBlockData(type.data(), false);
    }

    // ── Colocar ─────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        CustomBlock type = CustomBlock.of(event.getItemInHand());
        if (type != null) {
            place(event.getBlockPlaced(), type);
            Location at = event.getBlockPlaced().getLocation().add(0.5, 0.5, 0.5);
            at.getWorld().playSound(at, Sound.BLOCK_DEEPSLATE_PLACE, 1f, 0.8f);
        }
    }

    // ── Quebrar e dropar ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        CustomBlock type = at(event.getBlock());
        if (type == null) {
            return;
        }
        Player player = event.getPlayer();
        stop(player);
        event.setDropItems(false);
        event.setExpToDrop(0);
        Location center = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        center.getWorld().playSound(center, Sound.BLOCK_DEEPSLATE_BREAK, 1f, 0.8f);
        center.getWorld().spawnParticle(Particle.PORTAL, center, 12, 0.3, 0.3, 0.3, 0.3);
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (player.getGameMode() == GameMode.CREATIVE || tier(tool) < type.tier()) {
            return;
        }
        drop(type, center, tool.getEnchantmentLevel(Enchantment.FORTUNE));
        if (type == CustomBlock.ENDERITE_ORE) {
            center.getWorld().spawn(center, org.bukkit.entity.ExperienceOrb.class, orb -> orb.setExperience(4));
        }
    }

    private void drop(CustomBlock type, Location at, int fortune) {
        ItemStack stack = type == CustomBlock.ENDERITE_ORE
                ? EndItem.ENDERITE_FRAGMENT.create(plugin) : type.item(1);
        if (type == CustomBlock.ENDERITE_ORE) {
            stack.setAmount(1 + ThreadLocalRandom.current().nextInt(2 + fortune));
        }
        at.getWorld().dropItemNaturally(at, stack);
    }

    // ── Explosões: resistem como obsidiana (somem sem dropar) ────

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        protect(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        protect(event.blockList());
    }

    private void protect(List<Block> list) {
        for (Iterator<Block> it = list.iterator(); it.hasNext();) {
            if (at(it.next()) != null) {
                it.remove();
            }
        }
    }

    // ── Blindagem contra o comportamento vanilla do note block ──

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && at(event.getClickedBlock()) != null) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.ALLOW);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onNote(NotePlayEvent event) {
        if (at(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (event.getBlock().getType() == Material.NOTE_BLOCK && at(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> at(b) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> at(b) != null)) {
            event.setCancelled(true);
        }
    }

    // ── Mineração com dureza própria ────────────────────────────

    /** Nível da picareta na mão (0 madeira/ouro ... 4 netherite); -1 se não for picareta. */
    static int tier(ItemStack held) {
        String name = held.getType().name();
        if (!name.endsWith("_PICKAXE")) {
            return -1;
        }
        return switch (name.substring(0, name.length() - "_PICKAXE".length())) {
            case "NETHERITE" -> 4;
            case "DIAMOND" -> 3;
            case "IRON", "COPPER" -> 2;
            case "STONE" -> 1;
            default -> 0;
        };
    }

    private static double speed(ItemStack held) {
        String name = held.getType().name();
        if (!name.endsWith("_PICKAXE")) {
            return 1;
        }
        double base = switch (name.substring(0, name.length() - "_PICKAXE".length())) {
            case "NETHERITE" -> 9;
            case "DIAMOND" -> 8;
            case "IRON" -> 6;
            case "COPPER" -> 5;
            case "STONE" -> 4;
            case "GOLDEN" -> 12;
            default -> 2;
        };
        int efficiency = held.getEnchantmentLevel(Enchantment.EFFICIENCY);
        return efficiency > 0 ? base + efficiency * efficiency + 1 : base;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        CustomBlock type = at(event.getBlock());
        if (type == null) {
            return;
        }
        stop(player);
        Session session = new Session(event.getBlock(), type, player.getPotionEffect(PotionEffectType.MINING_FATIGUE));
        sessions.put(player.getUniqueId(), session);
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, PotionEffect.INFINITE_DURATION,
                FATIGUE_AMPLIFIER, false, false, false));
        session.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(player, session), 1L, 1L);
    }

    @EventHandler
    public void onAbort(BlockDamageAbortEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer());
    }

    private void tick(Player player, Session session) {
        if (!player.isOnline() || player.getGameMode() != GameMode.SURVIVAL
                || sessions.get(player.getUniqueId()) != session
                || at(session.block) != session.type
                || !session.block.equals(player.getTargetBlockExact(6))) {
            stop(player);
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean ground = player.getWorld().hasCollisionsIn(player.getBoundingBox().shift(0, -0.05, 0));
        double speed = speed(held) / (ground ? 1 : 5);
        boolean right = tier(held) >= session.type.tier();
        session.progress += speed / session.type.hardness() / (right ? 30 : 100);
        if (session.progress >= 1) {
            Block block = session.block;
            stop(player);
            player.breakBlock(block);
            return;
        }
        player.sendBlockDamage(session.block.getLocation(), (float) session.progress);
    }

    public void stop(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.task != null) {
            session.task.cancel();
        }
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        if (session.previous != null) {
            player.addPotionEffect(session.previous);
        }
        player.sendBlockDamage(session.block.getLocation(), 0f);
    }
}
