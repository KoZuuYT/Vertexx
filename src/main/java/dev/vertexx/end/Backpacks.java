package dev.vertexx.end;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Tab;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;

/**
 * Mochilas de três níveis. O conteúdo é serializado no PersistentDataContainer do próprio item
 * ({@code ItemStack#serializeItemsAsBytes}): soltar, guardar em baú ou perder a mochila preserva o conteúdo.
 * Cada mochila recebe um UUID (na primeira abertura) que serve de trava: a mesma mochila nunca abre em duas janelas.
 */
public final class Backpacks implements Listener, TabExecutor {

    private static final int[] SLOTS = {9, 18, 27};

    private final PluginMain plugin;
    private final NamespacedKey idKey;
    private final NamespacedKey dataKey;
    private final Map<String, Session> sessions = new HashMap<>();

    private static final class Session {
        final String id;
        final Player player;
        final Inventory inventory;
        final int tier;
        boolean closing;

        Session(String id, Player player, Inventory inventory, int tier) {
            this.id = id;
            this.player = player;
            this.inventory = inventory;
            this.tier = tier;
        }
    }

    /** Dono da janela da mochila (identifica o inventário nos eventos). */
    private static final class Holder implements InventoryHolder {
        private Session session;
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public Backpacks(PluginMain plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "backpack_id");
        this.dataKey = new NamespacedKey(plugin, "backpack_data");
    }

    // ── Receitas ───────────────────────────────────────────────

    public void start() {
        ShapedRecipe leather = new ShapedRecipe(new NamespacedKey(plugin, "leather_backpack"), EndItem.BACKPACK_LEATHER.create(plugin));
        leather.shape("LLL", "LCL", "LSL");
        leather.setIngredient('L', Material.LEATHER);
        leather.setIngredient('C', Material.CHEST);
        leather.setIngredient('S', Material.STRING);
        Bukkit.addRecipe(leather);

        ShapelessRecipe iron = new ShapelessRecipe(new NamespacedKey(plugin, "iron_backpack"), EndItem.BACKPACK_IRON.create(plugin));
        iron.addIngredient(new RecipeChoice.MaterialChoice(Material.PAPER));
        iron.addIngredient(4, Material.IRON_BARS);
        iron.addIngredient(2, Material.COPPER_BARS);
        Bukkit.addRecipe(iron);

        ShapelessRecipe voidBag = new ShapelessRecipe(new NamespacedKey(plugin, "void_backpack"), EndItem.BACKPACK_VOID.create(plugin));
        voidBag.addIngredient(new RecipeChoice.MaterialChoice(Material.PAPER));
        voidBag.addIngredient(2, Material.SHULKER_SHELL);
        for (int i = 0; i < 2; i++) {
            voidBag.addIngredient(new RecipeChoice.ExactChoice(EndItem.VOID_CHITIN.create(plugin)));
        }
        voidBag.addIngredient(Material.ENDER_PEARL);
        Bukkit.addRecipe(voidBag);

        Bukkit.getScheduler().runTaskTimer(plugin, this::magnet, 40L, 10L);
    }

    public void shutdown() {
        for (Session session : new ArrayList<>(sessions.values())) {
            save(session);
        }
        sessions.clear();
    }

    private boolean quality(String key) {
        return plugin.getConfig().getBoolean("end.quality." + key, true);
    }

    /** Receitas: o upgrade preserva o conteúdo; nenhuma receita comum aceita item customizado da End Update. */
    @EventHandler
    public void onPrepare(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe == null) {
            return;
        }
        String key = recipe instanceof org.bukkit.Keyed keyed ? keyed.getKey().getKey() : "";
        boolean ours = recipe instanceof org.bukkit.Keyed keyed && keyed.getKey().getNamespace().equals(plugin.getName().toLowerCase());
        ItemStack[] matrix = event.getInventory().getMatrix();
        if (!ours) {
            for (ItemStack item : matrix) {
                if (EndItem.of(item) != null) {
                    event.getInventory().setResult(null);
                    return;
                }
            }
            return;
        }
        if (key.equals("leather_backpack")) {
            for (ItemStack item : matrix) {
                if (EndItem.of(item) != null) {
                    event.getInventory().setResult(null);
                    return;
                }
            }
            return;
        }
        int needed = key.equals("iron_backpack") ? 1 : key.equals("void_backpack") ? 2 : 0;
        if (needed == 0) {
            return;
        }
        ItemStack source = null;
        for (ItemStack item : matrix) {
            if (item != null && item.getType() == Material.PAPER) {
                EndItem type = EndItem.of(item);
                if (type != null && type.tier() == needed) {
                    source = item;
                }
            }
        }
        if (source == null) {
            event.getInventory().setResult(null);
            return;
        }
        ItemStack result = create(needed + 1);
        ItemStack[] kept = read(source, SLOTS[needed - 1]);
        String id = source.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (id != null) {
            result.editMeta(meta -> meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id));
        }
        event.getInventory().setResult(write(result, kept, SLOTS[needed]));
    }

    // ── Itens ──────────────────────────────────────────────────

    /** Mochila vazia do nível (1 a 3). */
    public ItemStack create(int tier) {
        ItemStack item = EndItem.backpack(tier).create(plugin);
        return write(item, new ItemStack[SLOTS[tier - 1]], SLOTS[tier - 1]);
    }

    static int tierOf(ItemStack item) {
        EndItem type = EndItem.of(item);
        return type == null ? 0 : type.tier();
    }

    private ItemStack[] read(ItemStack backpack, int size) {
        ItemStack[] out = new ItemStack[size];
        byte[] bytes = backpack.getItemMeta().getPersistentDataContainer().get(dataKey, PersistentDataType.BYTE_ARRAY);
        if (bytes != null && bytes.length > 0) {
            ItemStack[] stored = ItemStack.deserializeItemsFromBytes(bytes);
            for (int i = 0; i < stored.length && i < size; i++) {
                out[i] = stored[i] == null || stored[i].isEmpty() ? null : stored[i];
            }
        }
        return out;
    }

    /** Grava o conteúdo no item e atualiza o resumo na lore. */
    private ItemStack write(ItemStack backpack, ItemStack[] contents, int size) {
        ItemStack[] clean = new ItemStack[size];
        int used = 0;
        for (int i = 0; i < size && i < contents.length; i++) {
            if (contents[i] != null && !contents[i].isEmpty()) {
                clean[i] = contents[i];
                used++;
            }
        }
        int tier = tierOf(backpack);
        int usedFinal = used;
        byte[] bytes = ItemStack.serializeItemsAsBytes(clean);
        backpack.editMeta(meta -> {
            meta.getPersistentDataContainer().set(dataKey, PersistentDataType.BYTE_ARRAY, bytes);
            List<Component> lore = new ArrayList<>();
            EndItem type = EndItem.backpack(Math.max(1, tier));
            for (String line : List.of(type.tier() == 1 ? "<gray>9 espaços." : type.tier() == 2 ? "<gray>18 espaços." : "<gray>27 espaços.")) {
                lore.add(Msg.item(line));
            }
            if (type.tier() == 3) {
                lore.add(Msg.item("<dark_purple>Coleta magnética de itens do chão."));
            }
            lore.add(Msg.item("<gray>Ocupados: <white>" + usedFinal + "<dark_gray>/<white>" + size));
            lore.add(Msg.item("<dark_gray>Botão direito: abrir"));
            meta.lore(lore);
        });
        return backpack;
    }

    private String idOf(ItemStack backpack) {
        return backpack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    // ── Janela ─────────────────────────────────────────────────

    /** Abre a mochila que está em {@code item} (da mão ou do peito). */
    public boolean open(Player player, ItemStack item) {
        int tier = tierOf(item);
        if (tier == 0) {
            return false;
        }
        if (sessions.values().stream().anyMatch(s -> s.player.equals(player))) {
            return false;
        }
        String id = idOf(item);
        if (id == null) {
            id = UUID.randomUUID().toString();
            String fresh = id;
            item.editMeta(meta -> meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, fresh));
        }
        Session busy = sessions.get(id);
        if (busy != null) {
            Msg.actionBar(player, "<red>Esta mochila já está aberta.");
            Fx.deny(player);
            return true;
        }
        int size = SLOTS[tier - 1];
        EndItem type = EndItem.backpack(tier);
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, size, Msg.mm(type.color() + type.display()));
        holder.inventory = inventory;
        Session session = new Session(id, player, inventory, tier);
        holder.session = session;
        inventory.setContents(read(item, size));
        sessions.put(id, session);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1.1f);
        return true;
    }

    private Session sessionOf(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof Holder holder ? holder.session : null;
    }

    /** Procura a mochila com esse id no inventário (mãos, barra, mochila e peito) e a substitui pelo conteúdo novo. */
    private void save(Session session) {
        Player player = session.player;
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = session.inventory.getContents();
        for (int slot = 0; slot < 41; slot++) {
            ItemStack item = inv.getItem(slot);
            if (tierOf(item) == session.tier && session.id.equals(idOf(item))) {
                inv.setItem(slot, write(item, contents, SLOTS[session.tier - 1]));
                return;
            }
        }
        // a mochila não está mais com o jogador (não deveria acontecer: ela fica travada com a janela aberta)
        ItemStack lost = create(session.tier);
        lost.editMeta(meta -> meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, session.id));
        write(lost, contents, SLOTS[session.tier - 1]);
        player.getWorld().dropItemNaturally(player.getLocation(), lost);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Session session = sessionOf(event.getInventory());
        if (session == null) {
            return;
        }
        save(session);
        sessions.remove(session.id);
        ((Player) event.getPlayer()).playSound(event.getPlayer().getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.6f, 0.9f);
    }

    /** Morrendo com a janela aberta, o conteúdo vai para o item antes de o inventário ser despejado. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        for (Session session : new ArrayList<>(sessions.values())) {
            if (session.player.equals(event.getPlayer())) {
                save(session);
            }
        }
    }

    // ── Abrir ──────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        if (held != null && tierOf(held) > 0) {
            event.setCancelled(true);
            if (event.getHand() != null) {
                ItemStack real = player.getInventory().getItem(event.getHand());
                open(player, real);
            }
            return;
        }
        // com o peito equipado: Shift + botão direito com a mão vazia
        if ((held == null || held.getType().isAir()) && player.isSneaking() && event.getHand() == EquipmentSlot.HAND) {
            if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null && event.getClickedBlock().getType().isInteractable()) {
                return;
            }
            ItemStack worn = player.getInventory().getChestplate();
            if (tierOf(worn) > 0) {
                event.setCancelled(true);
                open(player, worn);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "<red>Só jogadores.");
            return true;
        }
        ItemStack worn = player.getInventory().getChestplate();
        ItemStack target = tierOf(worn) > 0 ? worn : null;
        if (target == null) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (tierOf(item) > 0) {
                    target = item;
                    break;
                }
            }
        }
        if (target == null) {
            Msg.send(player, "<red>Você não tem uma mochila. <gray>Fabrique a Mochila de Couro: 7 couros, 1 baú e 1 linha.");
            return true;
        }
        open(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Tab.of(args.length > 0 ? args[0] : "");
    }

    // ── Proteções (duplicação e recursão) ─────────────────────

    private boolean sameBackpack(Session session, ItemStack item) {
        return item != null && tierOf(item) == session.tier && session.id.equals(idOf(item));
    }

    private Session openOf(Player player) {
        for (Session session : sessions.values()) {
            if (session.player.equals(player)) {
                return session;
            }
        }
        return null;
    }

    private static boolean forbidsBackpacks(Inventory inventory) {
        return inventory.getType() == InventoryType.SHULKER_BOX || inventory.getHolder() instanceof Holder;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Session open = openOf(player);
        Inventory top = event.getView().getTopInventory();
        ItemStack hotbar = event.getHotbarButton() >= 0 ? player.getInventory().getItem(event.getHotbarButton()) : null;
        ItemStack offhand = event.getClick() == ClickType.SWAP_OFFHAND ? player.getInventory().getItemInOffHand() : null;
        if (open != null) {      // a mochila aberta fica imóvel até a janela fechar
            if (sameBackpack(open, event.getCurrentItem()) || sameBackpack(open, event.getCursor())
                    || sameBackpack(open, hotbar) || sameBackpack(open, offhand)) {
                event.setCancelled(true);
                return;
            }
        }
        if (forbidsBackpacks(top)) {
            boolean intoTop = event.getRawSlot() < top.getSize();
            ItemStack moving = null;
            switch (event.getAction()) {
                case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> moving = intoTop ? event.getCursor() : null;
                case MOVE_TO_OTHER_INVENTORY -> moving = intoTop ? null : event.getCurrentItem();
                case HOTBAR_SWAP -> moving = intoTop ? (hotbar != null ? hotbar : offhand) : null;
                default -> {
                }
            }
            if (event.getAction() == InventoryAction.HOTBAR_SWAP && intoTop) {
                moving = hotbar != null ? hotbar : offhand;
            }
            if (EndItem.isBackpack(moving)) {
                event.setCancelled(true);
                Msg.actionBar(player, "<red>Uma mochila não cabe aqui dentro.");
                Fx.deny(player);
                return;
            }
        }
        Session session = sessionOf(top);
        if (session != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sessions.containsKey(session.id)) {
                    save(session);
                }
            });
        }
        if (event.getClick() == ClickType.MIDDLE && quality("sorter") && player.getGameMode() != GameMode.CREATIVE
                && event.getRawSlot() < top.getSize() && sortable(top)) {
            event.setCancelled(true);
            sort(top);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            if (session != null) {
                save(session);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        Player player = (Player) event.getWhoClicked();
        Session open = openOf(player);
        if (open != null && (sameBackpack(open, event.getOldCursor()) || sameBackpack(open, event.getCursor()))) {
            event.setCancelled(true);
            return;
        }
        if (forbidsBackpacks(top) && EndItem.isBackpack(event.getOldCursor())) {
            for (int raw : event.getRawSlots()) {
                if (raw < top.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        Session session = sessionOf(top);
        if (session != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sessions.containsKey(session.id)) {
                    save(session);
                }
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (event.getDestination().getType() == InventoryType.SHULKER_BOX && EndItem.isBackpack(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Session open = openOf(event.getPlayer());
        if (open != null && sameBackpack(open, event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    // ── Organizador (botão do meio) ────────────────────────────

    private static boolean sortable(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof Holder || holder instanceof org.bukkit.block.Container
                || holder instanceof org.bukkit.block.DoubleChest || holder instanceof org.bukkit.entity.minecart.StorageMinecart
                || holder instanceof org.bukkit.entity.ChestBoat
                || inventory.getType() == InventoryType.ENDER_CHEST;
    }

    /** Junta pilhas incompletas e ordena por material (depois pelo nome). */
    static void sort(Inventory inventory) {
        List<ItemStack> all = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            boolean merged = false;
            if (item.getMaxStackSize() > 1) {
                for (ItemStack other : all) {
                    if (other.isSimilar(item) && other.getAmount() < other.getMaxStackSize()) {
                        int move = Math.min(item.getAmount(), other.getMaxStackSize() - other.getAmount());
                        other.setAmount(other.getAmount() + move);
                        item.setAmount(item.getAmount() - move);
                        if (item.getAmount() <= 0) {
                            merged = true;
                            break;
                        }
                    }
                }
            }
            if (!merged) {
                all.add(item.clone());
            }
        }
        all.sort(Comparator.comparing((ItemStack i) -> i.getType().name()).thenComparing(i -> -i.getAmount()));
        ItemStack[] sorted = new ItemStack[inventory.getSize()];
        for (int i = 0; i < all.size() && i < sorted.length; i++) {
            sorted[i] = all.get(i);
        }
        inventory.setContents(sorted);
    }

    // ── Reabastecimento ao construir ───────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!quality("auto-refill") || event.getItemInHand().getAmount() != 1 || event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        ItemStack used = event.getItemInHand().clone();
        if (EndItem.of(used) != null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerInventory inv = player.getInventory();
            ItemStack now = inv.getItem(hand);
            if (now != null && !now.isEmpty()) {
                return;
            }
            for (int slot = 0; slot < 36; slot++) {
                ItemStack candidate = inv.getItem(slot);
                if (candidate != null && candidate.isSimilar(used)) {
                    if (hand == EquipmentSlot.HAND && slot == inv.getHeldItemSlot()) {
                        continue;
                    }
                    inv.setItem(hand, candidate);
                    inv.setItem(slot, null);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.3f, 1.6f);
                    return;
                }
            }
        });
    }

    // ── Coleta magnética (Mochila do Vazio) ────────────────────

    private ItemStack voidBackpackOf(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack worn = inv.getChestplate();
        if (tierOf(worn) == 3) {
            return worn;
        }
        for (ItemStack item : inv.getContents()) {
            if (tierOf(item) == 3) {
                return item;
            }
        }
        return null;
    }

    private void magnet() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
                continue;
            }
            ItemStack bag = voidBackpackOf(player);
            if (bag == null) {
                continue;
            }
            List<Item> drops = new ArrayList<>();
            for (var entity : player.getNearbyEntities(4, 2.5, 4)) {
                if (entity instanceof Item item && item.isValid() && item.getPickupDelay() <= 0
                        && !EndItem.isBackpack(item.getItemStack())) {
                    drops.add(item);
                }
            }
            if (drops.isEmpty()) {
                continue;
            }
            String id = idOf(bag);
            Session open = id == null ? null : sessions.get(id);
            Inventory work = open != null ? open.inventory : Bukkit.createInventory(null, SLOTS[2]);
            if (open == null) {
                work.setContents(read(bag, SLOTS[2]));
            }
            boolean any = false;
            for (Item drop : drops) {
                ItemStack stack = drop.getItemStack();
                boolean known = false;
                for (ItemStack have : work.getContents()) {
                    if (have != null && have.isSimilar(stack)) {
                        known = true;
                        break;
                    }
                }
                if (!known) {
                    continue;
                }
                int before = stack.getAmount();
                Map<Integer, ItemStack> left = work.addItem(stack.clone());
                int remaining = left.isEmpty() ? 0 : left.values().iterator().next().getAmount();
                if (remaining == before) {
                    continue;
                }
                any = true;
                if (remaining == 0) {
                    drop.remove();
                } else {
                    stack.setAmount(remaining);
                    drop.setItemStack(stack);
                }
                player.getWorld().spawnParticle(Particle.PORTAL, drop.getLocation().add(0, 0.3, 0), 6, 0.1, 0.1, 0.1, 0.4);
            }
            if (any) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.15f, 2f);
                if (open == null) {
                    PlayerInventory inv = player.getInventory();
                    ItemStack[] result = work.getContents();
                    for (int slot = 0; slot < 41; slot++) {
                        ItemStack item = inv.getItem(slot);
                        if (item != null && item.equals(bag)) {
                            inv.setItem(slot, write(item, result, SLOTS[2]));
                            break;
                        }
                    }
                } else {
                    save(open);
                }
            }
        }
    }
}
