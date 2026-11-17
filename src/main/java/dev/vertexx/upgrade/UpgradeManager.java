package dev.vertexx.upgrade;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Upgrades de fornalhas (velocidade de cozimento) e baús (capacidade em páginas). O nível fica no
 * PersistentDataContainer do bloco e acompanha o item quando o bloco é quebrado e colocado de novo.
 */
public final class UpgradeManager implements Listener {

    public enum Kind {
        FURNACE("Fornalha", "velocidade"), CHEST("Baú", "capacidade");

        private final String display;
        private final String effect;

        Kind(String display, String effect) {
            this.display = display;
            this.effect = effect;
        }

        public String display() {
            return display;
        }

        public String effect() {
            return effect;
        }
    }

    /** Um nível de upgrade; {@code value} é o divisor de tempo (fornalha) ou o total de espaços (baú). */
    public record Tier(Material item, int amount, int essence, double value) {
    }

    static final int PAGE_SLOTS = 45;
    static final int VANILLA_SLOTS = 27;

    private final PluginMain plugin;
    final NamespacedKey tierKey;
    final NamespacedKey storeKey;
    private final Map<String, UUID> locks = new HashMap<>();

    public UpgradeManager(PluginMain plugin) {
        this.plugin = plugin;
        this.tierKey = new NamespacedKey(plugin, "upgrade_tier");
        this.storeKey = new NamespacedKey(plugin, "upgrade_store");
    }

    PluginMain plugin() {
        return plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("upgrades.enabled", true);
    }

    public static Kind kindOf(Material material) {
        return switch (material) {
            case FURNACE, BLAST_FURNACE, SMOKER -> Kind.FURNACE;
            case CHEST, TRAPPED_CHEST, BARREL -> Kind.CHEST;
            default -> null;
        };
    }

    // ── Configuração ───────────────────────────────────────────

    public List<Tier> tiers(Kind kind) {
        String path = kind == Kind.FURNACE ? "upgrades.furnace.tiers" : "upgrades.chest.tiers";
        String valueKey = kind == Kind.FURNACE ? "speed" : "slots";
        List<Tier> list = new ArrayList<>();
        for (Map<?, ?> map : plugin.getConfig().getMapList(path)) {
            Material material = Material.matchMaterial(String.valueOf(map.get("item")));
            if (material == null) {
                continue;
            }
            double value = number(map.get(valueKey), kind == Kind.FURNACE ? 1.0 : PAGE_SLOTS);
            if (kind == Kind.CHEST) {
                value = Math.max(PAGE_SLOTS, Math.round(value / PAGE_SLOTS) * PAGE_SLOTS);
            }
            list.add(new Tier(material, (int) number(map.get("amount"), 1), (int) number(map.get("essence"), 0), value));
        }
        return list;
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    // ── Nível do bloco ─────────────────────────────────────────

    public int tierOf(Block block) {
        BlockState state = block.getState(false);
        if (state instanceof TileState tile) {
            return tile.getPersistentDataContainer().getOrDefault(tierKey, PersistentDataType.INTEGER, 0);
        }
        return 0;
    }

    private void setTier(Block block, int tier) {
        if (block.getState() instanceof TileState tile) {
            tile.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);
            tile.update(true, false);
        }
    }

    private int itemTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(tierKey, PersistentDataType.INTEGER, 0);
    }

    static String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    // ── Custos ─────────────────────────────────────────────────

    public int countEssence(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (plugin.mobs().isEssence(stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public int countItem(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material && !plugin.mobs().isEssence(stack)
                    && stack.getItemMeta() != null && !stack.getItemMeta().hasDisplayName()) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public boolean canAfford(Player player, Tier tier) {
        return countItem(player, tier.item()) >= tier.amount() && countEssence(player) >= tier.essence();
    }

    private void consume(Player player, Tier tier) {
        remove(player, tier.amount(), stack -> stack.getType() == tier.item() && !plugin.mobs().isEssence(stack)
                && !stack.getItemMeta().hasDisplayName());
        remove(player, tier.essence(), plugin.mobs()::isEssence);
    }

    private void remove(Player player, int amount, java.util.function.Predicate<ItemStack> match) {
        int left = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.isEmpty() || !match.test(stack)) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            player.getInventory().setItem(i, stack.getAmount() <= 0 ? null : stack);
            left -= take;
        }
    }

    // ── Melhorar ───────────────────────────────────────────────

    /** Tenta subir o bloco um nível; devolve o motivo da recusa ou {@code null} em caso de sucesso. */
    public String upgrade(Player player, Block block) {
        Kind kind = kindOf(block.getType());
        if (kind == null || !enabled()) {
            return "Este bloco não pode ser melhorado.";
        }
        if (kind == Kind.CHEST && block.getState() instanceof Container container
                && container.getInventory() instanceof DoubleChestInventory) {
            return "Separe o baú duplo antes de melhorá-lo.";
        }
        List<Tier> tiers = tiers(kind);
        int current = tierOf(block);
        if (current >= tiers.size()) {
            return "Já está no nível máximo.";
        }
        Tier next = tiers.get(current);
        if (!canAfford(player, next)) {
            return "Faltam materiais para o próximo nível.";
        }
        consume(player, next);
        setTier(block, current + 1);
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        Fx.ring(block.getLocation().add(0.5, 0.1, 0.5), Particle.END_ROD, 0.9, 24);
        Fx.burst(at.clone().add(0, 0.6, 0), Particle.FLAME, 25, 0.35, 0.04);
        Fx.worldSound(at, Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        Fx.worldSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.5f);
        if (kind == Kind.CHEST) {
            badge(block, current + 1, true);
        }
        Msg.actionBar(player, "<gold>" + kind.display() + " melhorado <white>nível " + (current + 1));
        return null;
    }

    // ── Interação ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled() || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Block block = event.getClickedBlock();
        Kind kind = kindOf(block.getType());
        if (kind == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            if (!player.getInventory().getItemInMainHand().isEmpty()) {
                return; // agachado com item: coloca o bloco normalmente
            }
            event.setCancelled(true);
            new UpgradeGUI(this, player, block, kind).open();
        } else if (kind == Kind.CHEST && tierOf(block) > 0) {
            event.setCancelled(true);
            openStorage(player, block);
        }
    }

    private void openStorage(Player player, Block block) {
        if (!(block.getState() instanceof Container container)) {
            return;
        }
        if (container.getInventory() instanceof DoubleChestInventory) {
            player.openInventory(container.getInventory());
            return;
        }
        String key = key(block);
        UUID viewer = locks.get(key);
        if (viewer != null && !viewer.equals(player.getUniqueId()) && Bukkit.getPlayer(viewer) != null) {
            Fx.deny(player);
            Msg.actionBar(player, "<red>Este baú está em uso por outro jogador.");
            return;
        }
        locks.put(key, player.getUniqueId());
        new StorageMenu(this, block, tierOf(block), player).open();
    }

    void unlock(Block block) {
        locks.remove(key(block));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        locks.values().removeIf(id -> id.equals(event.getPlayer().getUniqueId()));
    }

    // ── Menu de armazenamento ──────────────────────────────────

    @EventHandler
    public void onStorageClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof StorageMenu menu)) {
            return;
        }
        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            if (menu.isReserved(event.getSlot())) {
                event.setCancelled(true);
                menu.handleNav(event.getSlot());
                return;
            }
        }
        menu.syncLater();
    }

    @EventHandler
    public void onStorageDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof StorageMenu menu)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize() && menu.isReserved(slot)) {
                event.setCancelled(true);
                return;
            }
        }
        menu.syncLater();
    }

    @EventHandler
    public void onStorageClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof StorageMenu menu) {
            menu.close();
        }
    }

    /** Enquanto alguém usa o baú melhorado, funis não mexem nele (evita perder ou duplicar itens). */
    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (locks.isEmpty()) {
            return;
        }
        for (var inventory : new org.bukkit.inventory.Inventory[]{event.getSource(), event.getDestination()}) {
            if (inventory.getHolder(false) instanceof BlockInventoryHolder holder
                    && locks.containsKey(key(holder.getBlock()))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ── Fornalhas ──────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onSmelt(FurnaceStartSmeltEvent event) {
        if (!enabled()) {
            return;
        }
        int tier = tierOf(event.getBlock());
        if (tier <= 0) {
            return;
        }
        List<Tier> tiers = tiers(Kind.FURNACE);
        if (tier > tiers.size()) {
            return;
        }
        double speed = Math.max(1.0, tiers.get(tier - 1).value());
        event.setTotalCookTime(Math.max(1, (int) Math.round(event.getTotalCookTime() / speed)));
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !enabled()
                || !(event.getInventory().getHolder(false) instanceof org.bukkit.block.Furnace furnace)) {
            return;
        }
        int tier = tierOf(furnace.getBlock());
        List<Tier> tiers = tiers(Kind.FURNACE);
        if (tier > 0 && tier <= tiers.size()) {
            Msg.actionBar(player, "<gold>Fornalha melhorada <white>nível " + tier + " <dark_gray>• <gray>"
                    + trim(tiers.get(tier - 1).value()) + "x mais rápida");
        }
    }

    // ── Quebrar, colocar, explodir ─────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (kindOf(block.getType()) != Kind.CHEST || tierOf(block) <= 0) {
            return;
        }
        closeViewer(block);
        removeBadge(block);
        if (event.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
            dropExtras(block);
        }
    }

    private void closeViewer(Block block) {
        UUID viewer = locks.get(key(block));
        Player player = viewer == null ? null : Bukkit.getPlayer(viewer);
        if (player != null) {
            player.closeInventory();
        }
    }

    /** Solta os itens guardados além dos 27 espaços vanilla (que o jogo já solta sozinho). */
    private void dropExtras(Block block) {
        if (!(block.getState() instanceof TileState tile)) {
            return;
        }
        byte[] bytes = tile.getPersistentDataContainer().get(storeKey, PersistentDataType.BYTE_ARRAY);
        if (bytes == null) {
            return;
        }
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack stack : ItemStack.deserializeItemsFromBytes(bytes)) {
            if (stack != null && !stack.isEmpty()) {
                block.getWorld().dropItemNaturally(at, stack);
            }
        }
        tile.getPersistentDataContainer().remove(storeKey);
        tile.update(true, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        BlockState state = event.getBlockState();
        if (kindOf(state.getType()) == null || !(state instanceof TileState tile)) {
            return;
        }
        int tier = tile.getPersistentDataContainer().getOrDefault(tierKey, PersistentDataType.INTEGER, 0);
        if (tier <= 0) {
            return;
        }
        Kind kind = kindOf(state.getType());
        for (Item drop : event.getItems()) {
            ItemStack stack = drop.getItemStack();
            if (stack.getType() == state.getType()) {
                stack.editMeta(meta -> {
                    meta.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);
                    meta.lore(List.of(Msg.item("<gold>" + kind.display() + " melhorado <white>nível " + tier)));
                    meta.setEnchantmentGlintOverride(true);
                    meta.setTooltipStyle(Items.TOOLTIP);
                });
                drop.setItemStack(stack);
                break;
            }
        }
    }

    /**
     * Baús melhorados ficam lado a lado sem virar baú duplo: depois de colocar, o baú novo e o vizinho
     * voltam ao tipo SINGLE (o jogo só junta os dois se nenhum deles for melhorado).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        int tier = itemTier(event.getItemInHand());
        Block placed = event.getBlockPlaced();
        if (tier > 0 && kindOf(placed.getType()) != null) {
            setTier(placed, tier);
            if (kindOf(placed.getType()) == Kind.CHEST) {
                badge(placed, tier, true);
            }
        }
        Material type = placed.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) {
            return;
        }
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block neighbor = placed.getRelative(face);
            if (neighbor.getType() == type && (tier > 0 || tierOf(neighbor) > 0)) {
                single(placed);
                single(neighbor);
            }
        }
    }

    // ── Faixa visual dos baús melhorados ───────────────────────

    private static final String BADGE = "vx_badge";

    /** Uma faixa de metal em volta do baú (modelo do pack, uma cor por nível); fica salva no mundo. */
    private void badge(Block block, int tier, boolean animate) {
        Material type = block.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST && type != Material.BARREL) {
            return;
        }
        removeBadge(block);
        if (tier <= 0) {
            return;
        }
        float width = type == Material.BARREL ? 1.0f : 0.9f;
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.editMeta(meta -> meta.setItemModel(NamespacedKey.fromString("vertexx:chest_band_" + Math.min(tier, 6))));
        Transformation full = new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(width, 1f, width), new Quaternionf());
        Transformation small = new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(width * 0.2f, 0.2f, width * 0.2f), new Quaternionf());
        ItemDisplay display = block.getWorld().spawn(block.getLocation().add(0.5, 0.5, 0.5), ItemDisplay.class, entity -> {
            entity.setItemStack(stack);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.addScoreboardTag(BADGE);
            entity.setPersistent(true);
            entity.setViewRange(0.5f);
            entity.setTransformation(animate ? small : full);
        });
        if (animate) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (display.isValid()) {
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(10);
                    display.setTransformation(full);
                }
            }, 2L);
        }
    }

    private static List<Entity> badgesAt(Block block) {
        List<Entity> found = new ArrayList<>();
        for (Entity entity : block.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 0.3, 0.3, 0.3)) {
            if (entity.getScoreboardTags().contains(BADGE)) {
                found.add(entity);
            }
        }
        return found;
    }

    private void removeBadge(Block block) {
        badgesAt(block).forEach(Entity::remove);
    }

    /** Baús melhorados de versões antigas (ou de faixas perdidas) ganham a faixa ao carregar o chunk. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!enabled()) {
            return;
        }
        var chunk = event.getChunk();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!chunk.isLoaded()) {
                return;
            }
            for (BlockState state : chunk.getTileEntities(b -> kindOf(b.getType()) == Kind.CHEST, false)) {
                int tier = tierOf(state.getBlock());
                if (tier > 0 && badgesAt(state.getBlock()).isEmpty()) {
                    badge(state.getBlock(), tier, false);
                }
            }
        }, 40L);
    }

    private static void single(Block block) {
        if (block.getBlockData() instanceof org.bukkit.block.data.type.Chest data
                && data.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE) {
            data.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
            block.setBlockData(data, false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::explodeDrop);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::explodeDrop);
    }

    private void explodeDrop(Block block) {
        if (kindOf(block.getType()) == Kind.CHEST && tierOf(block) > 0) {
            closeViewer(block);
            removeBadge(block);
            dropExtras(block);
        }
    }

    // ── Textos ─────────────────────────────────────────────────

    static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    public static String itemName(Material material) {
        return switch (material) {
            case COPPER_INGOT -> "Lingote de Cobre";
            case IRON_INGOT -> "Lingote de Ferro";
            case GOLD_INGOT -> "Lingote de Ouro";
            case AMETHYST_SHARD -> "Fragmento de Ametista";
            case DIAMOND -> "Diamante";
            case EMERALD -> "Esmeralda";
            case OBSIDIAN -> "Obsidiana";
            case NETHERITE_INGOT -> "Lingote de Netherite";
            case NETHER_STAR -> "Estrela do Nether";
            default -> {
                StringBuilder out = new StringBuilder();
                for (String part : material.name().split("_")) {
                    out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase()).append(' ');
                }
                yield out.toString().trim();
            }
        };
    }

    /** Linhas de custo de um nível, com o que o jogador tem (verde) ou não (vermelho). */
    List<String> costLines(Player player, Tier tier) {
        int have = countItem(player, tier.item());
        List<String> lines = new ArrayList<>();
        lines.add((have >= tier.amount() ? "<green>" : "<red>") + have + "<dark_gray>/<white>" + tier.amount()
                + " <gray>" + itemName(tier.item()));
        if (tier.essence() > 0) {
            int essence = countEssence(player);
            lines.add((essence >= tier.essence() ? "<green>" : "<red>") + essence + "<dark_gray>/<white>"
                    + tier.essence() + " <gradient:#c4a7ff:#2cb67d>Essência Elite");
        }
        return lines;
    }

    String effectText(Kind kind, Tier tier) {
        return kind == Kind.FURNACE ? "<gray>Cozinha <white>" + trim(tier.value()) + "x <gray>mais rápido"
                : "<gray>Capacidade: <white>" + (int) tier.value() + " <gray>espaços";
    }

    /** Preenche a página de informações do menu de status com os níveis de upgrade. */
    public void describe(BiConsumer<Integer, ItemStack> setter) {
        int[][] rows = {{10, 11}, {19, 20}};
        Kind[] kinds = {Kind.FURNACE, Kind.CHEST};
        for (int r = 0; r < 2; r++) {
            Kind kind = kinds[r];
            setter.accept(rows[r][0], Items.of(kind == Kind.FURNACE ? Material.BLAST_FURNACE : Material.CHEST,
                    "<gold><bold>" + kind.display() + "s", List.of("<gray>Melhora a " + kind.effect() + ".",
                            "<gray>Agache e clique com a <white>mão vazia<gray>.")));
            List<Tier> tiers = tiers(kind);
            for (int i = 0; i < tiers.size() && i < 6; i++) {
                Tier tier = tiers.get(i);
                List<String> lore = new ArrayList<>();
                lore.add(effectText(kind, tier));
                lore.add("");
                lore.add("<gray>Custo: <white>" + tier.amount() + "x " + itemName(tier.item()));
                if (tier.essence() > 0) {
                    lore.add("<gray>        <white>" + tier.essence() + "x <gradient:#c4a7ff:#2cb67d>Essência Elite");
                }
                setter.accept(rows[r][1] + i, Items.of(tier.item(), "<yellow>Nível " + (i + 1), lore));
            }
        }
        setter.accept(31, Items.of(Material.ECHO_SHARD, "<gradient:#c4a7ff:#2cb67d>Essência Elite",
                List.of("<gray>Dropa de mobs <gold>Elite<gray>.", "<gray>Exigida nos níveis mais altos.")));
    }
}
