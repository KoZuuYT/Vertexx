package dev.vertexx.block;

import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Blocos customizados do Vertexx. Cada um é um estado reservado do note block
 * ({@code instrument=custom_head,note=<n>,powered=false}); o pack troca o modelo desse estado.
 * O índice {@code note} tem de bater com tools/pack/blocks.py.
 */
public enum CustomBlock {
    ENDERITE_ORE("enderite_ore", "Minério de Enderita", "<light_purple>", 0, 30.0, 3,
            "<gray>Veios roxos nas ilhas externas do End.", "<dark_gray>Exige picareta de diamante ou melhor."),
    ENDERITE_BLOCK("enderite_block", "Bloco de Enderita", "<light_purple>", 1, 40.0, 3,
            "<gray>Nove Fragmentos de Enderita compactados.");

    private static final NamespacedKey KEY = new NamespacedKey("vertexx", "block_id");

    private final String id;
    private final String display;
    private final String color;
    private final int note;
    private final double hardness;
    private final int tier;
    private final List<String> lore;

    CustomBlock(String id, String display, String color, int note, double hardness, int tier, String... lore) {
        this.id = id;
        this.display = display;
        this.color = color;
        this.note = note;
        this.hardness = hardness;
        this.tier = tier;
        this.lore = List.of(lore);
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public double hardness() {
        return hardness;
    }

    /** Nível mínimo da picareta para dropar (0 madeira/ouro, 1 pedra, 2 ferro, 3 diamante, 4 netherite). */
    public int tier() {
        return tier;
    }

    public BlockData data() {
        return Bukkit.createBlockData("minecraft:note_block[instrument=custom_head,note=" + note + ",powered=false]");
    }

    /** O item que coloca o bloco: um note block marcado, com o item_model do pack. */
    public ItemStack item(int amount) {
        ItemStack stack = new ItemStack(Material.NOTE_BLOCK, Math.max(1, amount));
        stack.editMeta(meta -> {
            meta.itemName(Msg.mm(color + display));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Msg.item(line));
            }
            meta.lore(lines);
            meta.setTooltipStyle(dev.vertexx.core.Items.TOOLTIP);
            meta.setItemModel(NamespacedKey.fromString("vertexx:" + id));
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);
        });
        return stack;
    }

    public static CustomBlock of(ItemStack item) {
        if (item == null || item.getType() != Material.NOTE_BLOCK || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        return raw == null ? null : byId(raw);
    }

    public static CustomBlock byId(String id) {
        for (CustomBlock block : values()) {
            if (block.id.equals(id)) {
                return block;
            }
        }
        return null;
    }
}
