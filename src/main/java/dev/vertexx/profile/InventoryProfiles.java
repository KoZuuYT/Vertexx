package dev.vertexx.profile;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Items;
import dev.vertexx.core.Msg;
import dev.vertexx.core.YamlStore;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Perfis de inventário dos administradores: guarda tudo o que o administrador carrega (inventário, armadura e mão
 * secundária, com todos os dados dos itens) num perfil, deixando só o Guia do Vertexx. Os perfis podem ser
 * editados, renomeados, duplicados, resgatados e atualizados pelo menu. Cada administrador vê os seus.
 */
public final class InventoryProfiles implements Listener {

    /** 36 do inventário, 4 de armadura (botas a capacete) e 1 da mão secundária. */
    public static final int SIZE = 41;

    /** Um perfil guardado. */
    public static final class Profile {

        private final UUID id;
        private String name;
        private final long created;
        private long updated;
        private ItemStack[] items;

        Profile(UUID id, String name, long created, long updated, ItemStack[] items) {
            this.id = id;
            this.name = name;
            this.created = created;
            this.updated = updated;
            this.items = items;
        }

        public UUID id() {
            return id;
        }

        public String name() {
            return name;
        }

        public long created() {
            return created;
        }

        public long updated() {
            return updated;
        }

        /** Cópia dos itens (41 posições, nulas quando vazias). */
        public ItemStack[] items() {
            return copy(items);
        }

        public int count() {
            int total = 0;
            for (ItemStack item : items) {
                if (item != null && !item.isEmpty()) {
                    total++;
                }
            }
            return total;
        }
    }

    private final PluginMain plugin;
    private final YamlStore store;
    private final Map<UUID, List<Profile>> byOwner = new HashMap<>();
    private volatile boolean loaded;

    public InventoryProfiles(PluginMain plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "profiles.yml");
    }

    // ── Persistência ────────────────────────────────────────────

    public void start() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            YamlConfiguration data = store.load();
            Bukkit.getScheduler().runTask(plugin, () -> apply(data));
        });
    }

    private void apply(YamlConfiguration data) {
        ConfigurationSection owners = data.getConfigurationSection("owners");
        if (owners != null) {
            for (String ownerKey : owners.getKeys(false)) {
                ConfigurationSection section = owners.getConfigurationSection(ownerKey);
                if (section == null) {
                    continue;
                }
                UUID owner;
                try {
                    owner = UUID.fromString(ownerKey);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                List<Profile> list = new ArrayList<>();
                for (String key : section.getKeys(false)) {
                    ConfigurationSection p = section.getConfigurationSection(key);
                    if (p == null) {
                        continue;
                    }
                    try {
                        ItemStack[] items = normalize(ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(p.getString("data", ""))));
                        list.add(new Profile(UUID.fromString(key), p.getString("name", "Perfil"), p.getLong("created"),
                                p.getLong("updated"), items));
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.WARNING, "Perfil de inventário ilegível ignorado: " + key, e);
                    }
                }
                list.sort((a, b) -> Long.compare(a.created, b.created));
                byOwner.put(owner, list);
            }
        }
        loaded = true;
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, List<Profile>> entry : byOwner.entrySet()) {
            for (Profile p : entry.getValue()) {
                String path = "owners." + entry.getKey() + "." + p.id;
                data.set(path + ".name", p.name);
                data.set(path + ".created", p.created);
                data.set(path + ".updated", p.updated);
                data.set(path + ".data", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(p.items)));
            }
        }
        return data;
    }

    public void save() {
        if (loaded) {
            store.saveAsync(snapshot());
        }
    }

    public void saveNow() {
        if (!loaded) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Editor editor) {
                capture(editor);
            }
        }
        store.saveNow(snapshot());
    }

    public boolean isLoaded() {
        return loaded;
    }

    // ── Perfis ──────────────────────────────────────────────────

    public List<Profile> list(UUID owner) {
        return byOwner.getOrDefault(owner, List.of());
    }

    public Profile find(UUID owner, UUID id) {
        for (Profile p : list(owner)) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    public Profile add(UUID owner, String name, ItemStack[] items) {
        long now = System.currentTimeMillis();
        Profile profile = new Profile(UUID.randomUUID(), name, now, now, normalize(items));
        byOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(profile);
        save();
        return profile;
    }

    public void remove(UUID owner, Profile profile) {
        List<Profile> list = byOwner.get(owner);
        if (list != null) {
            list.remove(profile);
        }
        save();
    }

    public void rename(Profile profile, String name) {
        profile.name = name;
        profile.updated = System.currentTimeMillis();
        save();
    }

    public Profile duplicate(UUID owner, Profile profile) {
        String name = profile.name + " (cópia)";
        return add(owner, name.length() > 40 ? name.substring(0, 40) : name, profile.items);
    }

    public void replaceItems(Profile profile, ItemStack[] items) {
        profile.items = normalize(items);
        profile.updated = System.currentTimeMillis();
        save();
    }

    // ── Inventário do administrador ─────────────────────────────

    /** O que o jogador carrega agora (sem o Guia do Vertexx), em 41 posições. */
    public ItemStack[] snapshot(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] out = new ItemStack[SIZE];
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < 36 && i < storage.length; i++) {
            out[i] = storage[i];
        }
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < 4 && i < armor.length; i++) {
            out[36 + i] = armor[i];
        }
        out[40] = inv.getItemInOffHand();
        for (int i = 0; i < SIZE; i++) {
            ItemStack item = out[i];
            out[i] = item == null || item.isEmpty() || plugin.panel().isGuide(item) ? null : item.clone();
        }
        return out;
    }

    /** Há algo além do Guia no inventário? */
    public boolean hasContent(Player player) {
        for (ItemStack item : snapshot(player)) {
            if (item != null) {
                return true;
            }
        }
        return false;
    }

    /** Esvazia tudo e deixa só o Guia do Vertexx. */
    public void clear(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.setStorageContents(new ItemStack[36]);
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);
        giveGuide(player);
    }

    /** Coloca os itens do perfil no inventário (substitui o atual) e garante o Guia. */
    public void restore(Player player, Profile profile) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] items = profile.items();
        ItemStack[] storage = new ItemStack[36];
        ItemStack[] armor = new ItemStack[4];
        System.arraycopy(items, 0, storage, 0, 36);
        System.arraycopy(items, 36, armor, 0, 4);
        inv.setStorageContents(storage);
        inv.setArmorContents(armor);
        inv.setItemInOffHand(items[40]);
        giveGuide(player);
    }

    private void giveGuide(Player player) {
        PlayerInventory inv = player.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (item != null && plugin.panel().isGuide(item)) {
                return;
            }
        }
        inv.addItem(plugin.panel().createGuide()).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    // ── Editor de itens ─────────────────────────────────────────

    /** Janela editável com o conteúdo de um perfil (move, pega, coloca, apaga). */
    static final class Editor implements InventoryHolder {

        private final UUID owner;
        private final UUID profile;
        private final Runnable back;
        private Inventory inventory;

        Editor(UUID owner, UUID profile, Runnable back) {
            this.owner = owner;
            this.profile = profile;
            this.back = back;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int BACK = 45;
    private static final int CLOSE = 49;

    /** Posição do perfil que o espaço da janela representa, ou -1 se for enfeite. */
    private static int index(int slot) {
        if (slot < 27) {
            return 9 + slot;
        }
        if (slot < 36) {
            return slot - 27;
        }
        if (slot >= 36 && slot <= 39) {
            return slot;
        }
        return slot == 41 ? 40 : -1;
    }

    private static int slotOf(int index) {
        if (index >= 9 && index < 36) {
            return index - 9;
        }
        if (index < 9) {
            return index + 27;
        }
        return index == 40 ? 41 : index;
    }

    /** Abre o editor de itens do perfil. */
    public void edit(Player player, Profile profile, Runnable back) {
        Editor editor = new Editor(player.getUniqueId(), profile.id, back);
        Inventory inv = Bukkit.createInventory(editor, 54,
                Msg.mm("<gradient:#c4a7ff:#2cb67d><bold>Itens: " + Msg.esc(profile.name) + "</bold></gradient>"));
        editor.inventory = inv;
        ItemStack dark = Items.filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 54; slot++) {
            inv.setItem(slot, index(slot) < 0 ? dark : null);
        }
        ItemStack[] items = profile.items();
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(slotOf(i), items[i]);
        }
        inv.setItem(43, Items.of(Material.PAPER, "<white><bold>Armadura e mão secundária",
                List.of("<gray>Linha de baixo: botas, calças, peitoral,", "<gray>capacete e (depois do vidro) a mão secundária.",
                        "<gray>Mexa à vontade: salva ao fechar.")));
        inv.setItem(BACK, Items.of(Material.ARROW, "<yellow>◀ Voltar", List.of()));
        inv.setItem(CLOSE, Items.of(Material.BARRIER, "<red>Fechar", List.of()));
        player.openInventory(inv);
    }

    /** Copia a janela para o perfil. */
    private void capture(Editor editor) {
        Profile profile = find(editor.owner, editor.profile);
        if (profile == null || editor.inventory == null) {
            return;
        }
        ItemStack[] items = new ItemStack[SIZE];
        for (int i = 0; i < SIZE; i++) {
            ItemStack item = editor.inventory.getItem(slotOf(i));
            items[i] = item == null || item.isEmpty() ? null : item.clone();
        }
        profile.items = items;
        profile.updated = System.currentTimeMillis();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof Editor editor)) {
            return;
        }
        int raw = event.getRawSlot();
        if (event.getClickedInventory() == top) {
            if (raw == BACK || raw == CLOSE) {
                event.setCancelled(true);
                capture(editor);
                save();
                Player player = (Player) event.getWhoClicked();
                if (raw == CLOSE) {
                    player.closeInventory();
                } else {
                    editor.back.run();
                }
                return;
            }
            if (index(raw) < 0 || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
                return;
            }
        } else if (event.getClickedInventory() != null) {
            if (event.getClick() == ClickType.DOUBLE_CLICK || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
                return;
            }
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack moving = event.getCurrentItem();
                if (moving != null && !moving.isEmpty() && place(top, moving.clone())) {
                    event.setCurrentItem(null);
                }
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> capture(editor));
    }

    /** Põe o item no primeiro espaço livre do inventário do editor (juntando pilhas iguais antes). */
    private static boolean place(Inventory top, ItemStack item) {
        for (int i = 0; i < 36; i++) {
            ItemStack there = top.getItem(slotOf(i));
            if (there != null && there.isSimilar(item) && there.getAmount() + item.getAmount() <= there.getMaxStackSize()) {
                there.setAmount(there.getAmount() + item.getAmount());
                return true;
            }
        }
        for (int i = 0; i < 36; i++) {
            if (top.getItem(slotOf(i)) == null) {
                top.setItem(slotOf(i), item);
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof Editor editor)) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize() && index(raw) < 0) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> capture(editor));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof Editor editor) {
            capture(editor);
            save();
        }
    }

    // ── Textos ──────────────────────────────────────────────────

    /** Nome de exibição de um item, já escapado para MiniMessage. */
    public static String label(ItemStack item) {
        Component name = item.effectiveName();
        return Msg.esc(Msg.plain(name));
    }

    /** Linhas de resumo dos itens do perfil (até {@code max}). */
    public static List<String> summary(Profile profile, int max) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack item : profile.items) {
            if (item != null && !item.isEmpty()) {
                counts.merge(label(item), item.getAmount(), Integer::sum);
            }
        }
        List<String> lines = new ArrayList<>();
        int shown = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (shown++ >= max) {
                lines.add("<dark_gray>… e mais " + (counts.size() - max));
                break;
            }
            lines.add("<gray>• <white>" + entry.getValue() + "x " + entry.getKey());
        }
        if (lines.isEmpty()) {
            lines.add("<dark_gray>Perfil vazio.");
        }
        return lines;
    }

    private static ItemStack[] normalize(ItemStack[] items) {
        ItemStack[] out = new ItemStack[SIZE];
        for (int i = 0; i < SIZE && items != null && i < items.length; i++) {
            ItemStack item = items[i];
            out[i] = item == null || item.isEmpty() ? null : item.clone();
        }
        return out;
    }

    private static ItemStack[] copy(ItemStack[] items) {
        return normalize(items);
    }
}
