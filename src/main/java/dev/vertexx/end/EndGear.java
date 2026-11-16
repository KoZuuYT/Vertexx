package dev.vertexx.end;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import io.papermc.paper.potion.PotionMix;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

/**
 * Usos dos materiais da End Update: Fragmento de Enderita (tempera armaduras contra o Vazio, na mesa de ferraria
 * ou na bigorna), Quitina do Vazio (reforça escudos) e as poções de gravidade (Núcleo de Levitação).
 * A Membrana Etérea é uma membrana de fantasma de verdade: repara Elytras na bigorna e vai na poção de Queda Lenta
 * como a vanilla.
 */
public final class EndGear implements Listener {

    private final PluginMain plugin;
    private final NamespacedKey temperedKey;

    public EndGear(PluginMain plugin) {
        this.plugin = plugin;
        this.temperedKey = new NamespacedKey(plugin, "void_tempered");
    }

    public void start() {
        ItemStack potion = new ItemStack(Material.POTION);
        potion.editMeta(PotionMeta.class, meta -> {
            meta.addCustomEffect(new PotionEffect(PotionEffectType.LEVITATION, 20 * 45, 0), true);
            meta.setColor(Color.fromRGB(0xC8A2FF));
            meta.displayName(Msg.item("<light_purple>Poção de Levitação"));
        });
        RecipeChoice awkward = PotionMix.createPredicateChoice(item -> item.getType() == Material.POTION
                && item.getItemMeta() instanceof PotionMeta meta && meta.getBasePotionType() == PotionType.AWKWARD);
        Bukkit.getPotionBrewer().addPotionMix(new PotionMix(new NamespacedKey(plugin, "levitation_potion"), potion, awkward,
                new RecipeChoice.ExactChoice(EndItem.LEVITATION_CORE.create(plugin))));
    }

    static boolean armor(ItemStack item) {
        if (item == null) {
            return false;
        }
        String name = item.getType().name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    boolean tempered(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(temperedKey, PersistentDataType.BYTE);
    }

    private ItemStack temper(ItemStack base) {
        ItemStack out = base.clone();
        out.editMeta(meta -> {
            meta.getPersistentDataContainer().set(temperedKey, PersistentDataType.BYTE, (byte) 1);
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Msg.item("<light_purple>◆ Temperada no Vazio"));
            lore.add(Msg.item("<dark_gray>−25% de dano do Vazio por peça;"));
            lore.add(Msg.item("<dark_gray>as botas anulam a levitação das bordas."));
            meta.lore(lore);
        });
        return out;
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getFirstItem();
        ItemStack second = event.getInventory().getSecondItem();
        if (first == null || second == null) {
            return;
        }
        if (armor(first) && EndItem.ENDERITE_FRAGMENT.matches(second) && !tempered(first)) {
            event.setResult(temper(first));
            event.getView().setRepairCost(3);
            event.getView().setRepairItemCountCost(1);
        } else if (first.getType() == Material.SHIELD && EndItem.VOID_CHITIN.matches(second)) {
            ItemStack shield = first.clone();
            shield.editMeta(meta -> {
                meta.addEnchant(Enchantment.UNBREAKING, Math.max(3, meta.getEnchantLevel(Enchantment.UNBREAKING)), true);
                List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                if (lore.stream().noneMatch(line -> Msg.plain(line).contains("Reforçado"))) {
                    lore.add(Msg.item("<dark_purple>◆ Reforçado com Quitina do Vazio"));
                }
                meta.lore(lore);
            });
            if (shield.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable) {
                int max = shield.getType().getMaxDurability();
                damageable.setDamage(Math.max(0, damageable.getDamage() - max / 4));
                shield.setItemMeta(damageable);
            }
            event.setResult(shield);
            event.getView().setRepairCost(3);
            event.getView().setRepairItemCountCost(1);
        }
    }

    @EventHandler
    public void onSmithing(PrepareSmithingEvent event) {
        ItemStack mineral = event.getInventory().getInputMineral();
        if (!EndItem.ENDERITE_FRAGMENT.matches(mineral)) {
            return;
        }
        ItemStack base = event.getInventory().getInputEquipment();
        event.setResult(armor(base) && !tempered(base) ? temper(base) : null);
    }

    /** Armaduras temperadas: −25% de dano do Vazio por peça. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVoid(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID || !(event.getEntity() instanceof Player player)) {
            return;
        }
        int pieces = temperedPieces(player);
        if (pieces > 0) {
            event.setDamage(event.getDamage() * (1 - 0.25 * pieces));
        }
    }

    public int temperedPieces(Player player) {
        int n = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (tempered(piece)) {
                n++;
            }
        }
        return n;
    }

    public boolean temperedBoots(Player player) {
        return tempered(player.getInventory().getBoots());
    }
}
