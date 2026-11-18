package dev.vertexx.core;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/** Construção de itens de interface. */
public final class Items {

    /** Moldura de tooltip do Vertexx (sprites tooltip/special_* do pack). */
    public static final org.bukkit.NamespacedKey TOOLTIP = new org.bukkit.NamespacedKey("vertexx", "special");

    private Items() {
    }

    /**
     * Ajusta dano e velocidade de ataque de uma arma SEM perder os valores padrão do material. Sem isto, qualquer
     * modificador de atributo no item substitui os padrões do vanilla (dano e velocidade viram quase zero).
     *
     * @param damageBonus soma ao dano padrão da arma
     * @param speedBonus soma à velocidade de ataque padrão (negativo = mais lenta)
     */
    public static void weaponStats(org.bukkit.plugin.Plugin plugin, org.bukkit.inventory.meta.ItemMeta meta,
                                   Material type, double damageBonus, double speedBonus) {
        var defaults = type.getDefaultAttributeModifiers(org.bukkit.inventory.EquipmentSlot.HAND);
        for (var entry : defaults.entries()) {
            meta.addAttributeModifier(entry.getKey(), entry.getValue());
        }
        if (damageBonus != 0) {
            meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_DAMAGE, new org.bukkit.attribute.AttributeModifier(
                    new org.bukkit.NamespacedKey(plugin, "weapon_damage_bonus"), damageBonus,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        }
        if (speedBonus != 0) {
            meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_SPEED, new org.bukkit.attribute.AttributeModifier(
                    new org.bukkit.NamespacedKey(plugin, "weapon_speed_bonus"), speedBonus,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        }
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
            meta.setTooltipStyle(TOOLTIP);
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
