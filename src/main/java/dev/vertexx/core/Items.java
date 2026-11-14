package dev.vertexx.core;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/** Construção de itens de interface. */
public final class Items {

    private Items() {
    }

    public static ItemStack of(Material material, String name, List<String> lore) {
        return of(material, name, lore, false);
    }

    public static ItemStack of(Material material, String name, List<String> lore, boolean glint) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Msg.item(name));
            List<Component> lines = new ArrayList<>(lore.size());
            for (String line : lore) {
                lines.add(Msg.item(line));
            }
            meta.lore(lines);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        return item;
    }

    /** Painel decorativo sem tooltip. */
    public static ItemStack filler(Material material) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.setHideTooltip(true));
        return item;
    }
}
