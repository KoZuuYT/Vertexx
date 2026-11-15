package dev.vertexx.upgrade;

import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Janela de um baú melhorado: páginas de 45 espaços com navegação na última linha.
 * Os primeiros 27 espaços são os do próprio baú (funis continuam funcionando com eles quando ninguém
 * está usando); os demais ficam no PersistentDataContainer do bloco.
 */
final class StorageMenu implements InventoryHolder {

    private static final int NAV_PREV = 45;
    private static final int NAV_INFO = 49;
    private static final int NAV_NEXT = 53;

    private final UpgradeManager manager;
    private final Block block;
    private final Player viewer;
    private final int total;
    private final int pages;
    private final ItemStack[] all;
    private final Inventory inventory;
    private int page;
    private boolean closed;

    StorageMenu(UpgradeManager manager, Block block, int tier, Player viewer) {
        this.manager = manager;
        this.block = block;
        this.viewer = viewer;
        List<UpgradeManager.Tier> tiers = manager.tiers(UpgradeManager.Kind.CHEST);
        int slots = tiers.isEmpty() ? UpgradeManager.PAGE_SLOTS : (int) tiers.get(Math.min(tier, tiers.size()) - 1).value();
        this.total = Math.max(UpgradeManager.PAGE_SLOTS, slots);
        this.pages = total / UpgradeManager.PAGE_SLOTS;
        this.all = new ItemStack[total];
        load();
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text("Baú melhorado nv." + tier).color(net.kyori.adventure.text.format.NamedTextColor.GOLD));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void load() {
        if (!(block.getState() instanceof Container container)) {
            return;
        }
        Inventory real = container.getInventory();
        for (int i = 0; i < UpgradeManager.VANILLA_SLOTS && i < real.getSize(); i++) {
            all[i] = clean(real.getItem(i));
        }
        byte[] bytes = container.getPersistentDataContainer().get(manager.storeKey, PersistentDataType.BYTE_ARRAY);
        if (bytes != null) {
            ItemStack[] extra = ItemStack.deserializeItemsFromBytes(bytes);
            for (int i = 0; i < extra.length && UpgradeManager.VANILLA_SLOTS + i < total; i++) {
                all[UpgradeManager.VANILLA_SLOTS + i] = clean(extra[i]);
            }
        }
    }

    private static ItemStack clean(ItemStack item) {
        return item == null || item.isEmpty() ? null : item;
    }

    void open() {
        render();
        viewer.openInventory(inventory);
        Fx.sound(viewer, org.bukkit.Sound.BLOCK_CHEST_OPEN, 0.6f, 1.1f);
    }

    boolean isReserved(int slot) {
        return slot >= UpgradeManager.PAGE_SLOTS || page * UpgradeManager.PAGE_SLOTS + slot >= total;
    }

    private void render() {
        for (int slot = 0; slot < UpgradeManager.PAGE_SLOTS; slot++) {
            int global = page * UpgradeManager.PAGE_SLOTS + slot;
            inventory.setItem(slot, global < total ? all[global] : Items.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        ItemStack filler = Items.filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = UpgradeManager.PAGE_SLOTS; slot < 54; slot++) {
            inventory.setItem(slot, filler);
        }
        if (page > 0) {
            inventory.setItem(NAV_PREV, Items.of(Material.ARROW, "<yellow>◀ Página anterior", List.of()));
        }
        if (page < pages - 1) {
            inventory.setItem(NAV_NEXT, Items.of(Material.ARROW, "<yellow>Próxima página ▶", List.of()));
        }
        inventory.setItem(NAV_INFO, Items.of(Material.PAPER, "<white>Página " + (page + 1) + "<dark_gray>/<white>" + pages,
                List.of("<gray>Capacidade: <white>" + total + " <gray>espaços")));
    }

    void handleNav(int slot) {
        if (slot == NAV_PREV && page > 0) {
            capture();
            page--;
            render();
            Fx.click(viewer);
        } else if (slot == NAV_NEXT && page < pages - 1) {
            capture();
            page++;
            render();
            Fx.click(viewer);
        }
    }

    /** Copia a página visível para a memória (depois de qualquer clique). */
    private void capture() {
        for (int slot = 0; slot < UpgradeManager.PAGE_SLOTS; slot++) {
            int global = page * UpgradeManager.PAGE_SLOTS + slot;
            if (global < total) {
                ItemStack item = inventory.getItem(slot);
                all[global] = clean(item) == null ? null : item.clone();
            }
        }
    }

    void syncLater() {
        Bukkit.getScheduler().runTask(manager.plugin(), () -> {
            if (!closed) {
                capture();
                flush();
            }
        });
    }

    /** Grava tudo no bloco: espaços vanilla no inventário real e o restante no PDC. */
    private void flush() {
        if (!(block.getState() instanceof Container container)) {
            return;
        }
        ItemStack[] extra = Arrays.copyOfRange(all, UpgradeManager.VANILLA_SLOTS, total);
        container.getPersistentDataContainer().set(manager.storeKey, PersistentDataType.BYTE_ARRAY,
                ItemStack.serializeItemsAsBytes(extra));
        container.update(true, false);
        if (block.getState() instanceof Container fresh) {
            Inventory real = fresh.getInventory();
            for (int i = 0; i < UpgradeManager.VANILLA_SLOTS && i < real.getSize(); i++) {
                real.setItem(i, all[i]);
            }
        }
    }

    void close() {
        if (closed) {
            return;
        }
        capture();
        flush();
        closed = true;
        manager.unlock(block);
        Fx.sound(viewer, org.bukkit.Sound.BLOCK_CHEST_CLOSE, 0.6f, 1.1f);
    }
}
