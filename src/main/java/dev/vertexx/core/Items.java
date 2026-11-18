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
     * Ajusta dano e velocidade de ataque de uma arma partindo dos valores do material e mostra o tooltip como o
     * vanilla. O bônus é SOMADO ao modificador base do material (mesmo id {@code minecraft:base_attack_damage} /
     * {@code minecraft:base_attack_speed}), em vez de entrar como um segundo modificador. Assim o cliente soma o dano
     * e a velocidade base do jogador (1 e 4) e mostra uma única linha verde com o valor final, por exemplo
     * {@code 10 Attack Damage} e {@code 1 Attack Speed}, sem as linhas azul/vermelha de "+2" e "-0.6".
     * <p>
     * Conta: valor final = base do jogador + dano da arma + bônus (espada de netherite: 1 + 7 + 2 = 10) e
     * velocidade final = 4 + (-2,4) + bônus (netherite: 4 - 2,4 - 0,6 = 1,0).
     *
     * @param damageBonus dano adicional em relação ao padrão da arma
     * @param speedBonus velocidade de ataque adicional em relação ao padrão (negativo = mais lenta)
     */
    public static void weaponStats(org.bukkit.plugin.Plugin plugin, org.bukkit.inventory.meta.ItemMeta meta,
                                   Material type, double damageBonus, double speedBonus) {
        var damage = org.bukkit.attribute.Attribute.ATTACK_DAMAGE;
        var speed = org.bukkit.attribute.Attribute.ATTACK_SPEED;
        boolean damageDone = false;
        boolean speedDone = false;
        var defaults = type.getDefaultAttributeModifiers(org.bukkit.inventory.EquipmentSlot.HAND);
        for (var entry : defaults.entries()) {
            var modifier = entry.getValue();
            double bonus = entry.getKey() == damage ? damageBonus : entry.getKey() == speed ? speedBonus : 0;
            if (bonus != 0) {
                modifier = new org.bukkit.attribute.AttributeModifier(modifier.getKey(), modifier.getAmount() + bonus,
                        modifier.getOperation(), modifier.getSlotGroup());
            }
            damageDone |= entry.getKey() == damage;
            speedDone |= entry.getKey() == speed;
            meta.addAttributeModifier(entry.getKey(), modifier);
        }
        // material sem padrão (ex.: item que não é arma): cria o modificador base do jogador do zero
        if (!damageDone && damageBonus != 0) {
            meta.addAttributeModifier(damage, new org.bukkit.attribute.AttributeModifier(
                    org.bukkit.NamespacedKey.minecraft("base_attack_damage"), damageBonus,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        }
        if (!speedDone && speedBonus != 0) {
            meta.addAttributeModifier(speed, new org.bukkit.attribute.AttributeModifier(
                    org.bukkit.NamespacedKey.minecraft("base_attack_speed"), speedBonus,
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
