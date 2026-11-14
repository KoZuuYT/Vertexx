package dev.vertexx.kingdom;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Menu obrigatório de escolha de reino (onboarding).
 * A escolha é feita em dois passos: selecionar o reino e confirmar.
 */
public final class KingdomGUI implements InventoryHolder {

    private static final int SIZE = 45;
    private static final int INFO_SLOT = 4;
    private static final int CONFIRM_SLOT = 40;
    private static final int[] KINGDOM_SLOTS = {20, 22, 24};

    private final PluginMain plugin;
    private final Player player;
    private final Inventory inventory;
    private Kingdom selected;

    public KingdomGUI(PluginMain plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, SIZE,
                Msg.mm("<gradient:#7f5af0:#2cb67d><bold>Escolha o seu Reino</bold></gradient>"));
        render();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        player.openInventory(inventory);
        Fx.sound(player, Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.2f);
    }

    public void handleClick(int slot) {
        Kingdom[] kingdoms = Kingdom.values();
        for (int i = 0; i < KINGDOM_SLOTS.length; i++) {
            if (KINGDOM_SLOTS[i] != slot) {
                continue;
            }
            if (selected == kingdoms[i]) {
                confirm();
            } else {
                selected = kingdoms[i];
                Fx.select(player);
                render();
            }
            return;
        }
        if (slot == CONFIRM_SLOT) {
            if (selected == null) {
                Fx.deny(player);
            } else {
                confirm();
            }
            return;
        }
        Fx.click(player);
    }

    private void confirm() {
        plugin.onboarding().complete(player, selected);
    }

    private void render() {
        Material ring = selected == null ? Material.BLACK_STAINED_GLASS_PANE : selected.pane();
        ItemStack outer = Items.filler(ring);
        ItemStack inner = Items.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            boolean border = row == 0 || row == 4 || column == 0 || column == 8;
            inventory.setItem(slot, border ? outer : inner);
        }

        inventory.setItem(INFO_SLOT, Items.of(Material.NETHER_STAR,
                "<gradient:#7f5af0:#2cb67d><bold>Bem-vindo ao servidor</bold></gradient>",
                List.of(
                        "<gray>Todo jogador pertence a um dos",
                        "<gray>três reinos. Escolha com calma:",
                        "<gray>a decisão é <white>permanente</white>.",
                        "",
                        "<dark_gray>1. Clique em um reino para selecioná-lo",
                        "<dark_gray>2. Confirme a escolha")));

        Kingdom[] kingdoms = Kingdom.values();
        for (int i = 0; i < KINGDOM_SLOTS.length; i++) {
            inventory.setItem(KINGDOM_SLOTS[i], kingdomItem(kingdoms[i]));
        }

        if (selected == null) {
            inventory.setItem(CONFIRM_SLOT, Items.of(Material.GRAY_DYE,
                    "<gray>Selecione um reino",
                    List.of("<dark_gray>Nenhum reino selecionado ainda.")));
        } else {
            inventory.setItem(CONFIRM_SLOT, Items.of(Material.LIME_DYE,
                    "<green><bold>Confirmar escolha",
                    List.of(
                            "<gray>Reino: " + selected.styled(),
                            "<gray>Cargo inicial: <white>" + Rank.MEMBRO.displayName(),
                            "",
                            "<red>Esta decisão é permanente."), true));
        }
    }

    private ItemStack kingdomItem(Kingdom kingdom) {
        boolean isSelected = kingdom == selected;
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(kingdom.description());
        lore.add("");
        lore.add("<gray>Membros: <white>" + plugin.kingdoms().count(kingdom));
        lore.add("<gray>Cargo inicial: <white>" + Rank.MEMBRO.displayName());
        lore.add("");
        lore.add(isSelected
                ? "<green>✔ Selecionado <dark_gray>— clique de novo para confirmar"
                : "<yellow>▶ Clique para selecionar");
        return Items.of(kingdom.icon(), "<bold>" + kingdom.styled(), lore, isSelected);
    }
}
