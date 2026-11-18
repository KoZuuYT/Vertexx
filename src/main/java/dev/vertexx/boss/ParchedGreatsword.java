package dev.vertexx.boss;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Grande Espada do Deserto (drop do Parched Titan): base de espada de netherite com 9,5 de dano, ataque pesado (1,25) e
 * +1 de alcance. Botão direito (recarga de 7 s): um corte projeta um pequeno tornado de areia por 14 blocos em 3,5 s;
 * quem for atingido (mobs, nunca jogadores) leva 7 de dano, sobe 3,5 blocos e fica com Cegueira leve. Com Fire
 * Aspect o tornado é incandescente e incendeia os alvos.
 */
public final class ParchedGreatsword implements Listener {

    private static final String ID = "parched_greatsword";
    private static final long COOLDOWN_MS = 7000;
    private static final int LIFE = 70;
    private static final double RANGE = 14;

    private final PluginMain plugin;
    private final NamespacedKey key;
    private final NamespacedKey partKey;
    private final NamespacedKey bodyKey;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public ParchedGreatsword(PluginMain plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, ID);
        this.partKey = new NamespacedKey(plugin, "boss_part");
        this.bodyKey = new NamespacedKey(plugin, "boss_body");
    }

    /** O item (drop garantido do Titã). */
    public static ItemStack create(PluginMain plugin) {
        NamespacedKey marker = new NamespacedKey(plugin, ID);
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        item.editMeta(meta -> {
            meta.itemName(Msg.mm("<gold>Grande Espada do Deserto"));
            List<Component> lore = new ArrayList<>();
            lore.add(Msg.item("<gray>Lâmina larga de arenito do Parched Titan."));
            lore.add(Msg.item("<gold>◆ Pequeno Tornado de Areia <gray>(botão direito, 7 s)"));
            lore.add(Msg.item("<dark_gray>Um corte lança um tornado por 14 blocos:"));
            lore.add(Msg.item("<dark_gray>7 de dano, ergue o alvo e o cega um instante."));
            lore.add(Msg.item("<dark_gray>Com Fire Aspect, o tornado fica incandescente."));
            lore.add(Msg.item("<dark_gray>Ataque pesado, alcance +1."));
            meta.lore(lore);
            meta.setTooltipStyle(dev.vertexx.core.Items.TOOLTIP);
            NamespacedKey model = NamespacedKey.fromString(plugin.getConfig().getString("end.items." + ID + ".model",
                    "vertexx:" + ID));
            if (model != null) {
                meta.setItemModel(model);
            }
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of(1050f));
            meta.setCustomModelDataComponent(component);
            dev.vertexx.core.Items.weaponStats(plugin, meta, Material.NETHERITE_SWORD, 2.0, -0.6);
            meta.addAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE, new AttributeModifier(
                    new NamespacedKey(plugin, "greatsword_reach"), 1.0, AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND));
            meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public boolean is(ItemStack item) {
        return item != null && item.getType() == Material.NETHERITE_SWORD && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!is(held)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = cooldown.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            Msg.actionBar(player, "<gray>A espada ainda recarrega: <white>" + ((COOLDOWN_MS - (now - last)) / 1000 + 1) + " s");
            return;
        }
        cooldown.put(player.getUniqueId(), now);
        cast(player, held.containsEnchantment(Enchantment.FIRE_ASPECT));
    }

    private void cast(Player player, boolean fire) {
        Location start = player.getLocation();
        Vector dir = start.getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        }
        final Vector heading = dir.normalize();
        Location origin = start.clone().add(heading.clone().multiply(1.6));
        origin.setY(start.getY());
        player.swingMainHand();
        Fx.worldSound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.6f);
        Fx.worldSound(origin, Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 0.9f);
        SandTornado tornado = new SandTornado(origin, fire, partKey);
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            private int age;

            @Override
            public void run() {
                age++;
                Location next = tornado.location().add(heading.clone().multiply(RANGE / LIFE));
                // acompanha o relevo: sobe/desce até 1 bloco por tick
                int floor = next.getWorld().getHighestBlockYAt(next.getBlockX(), next.getBlockZ(),
                        org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
                double y = Math.max(next.getY() - 0.6, Math.min(next.getY() + 0.6, floor + 1.0));
                next.setY(Math.abs(y - player.getLocation().getY()) < 6 ? y : next.getY());
                if (next.getBlock().getType().isSolid() && next.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                    finish(tornado);
                    return;
                }
                tornado.moveTo(next);
                tornado.tick(age);
                for (Entity entity : next.getWorld().getNearbyEntities(next.clone().add(0, 1.75, 0), 1.6, 1.75, 1.6)) {
                    if (!(entity instanceof LivingEntity living) || entity instanceof Player || entity instanceof ArmorStand
                            || entity instanceof Tameable tamed && tamed.getOwner() != null || !hit.add(entity.getUniqueId())) {
                        continue;
                    }
                    living.damage(7, player);
                    boolean boss = living.getPersistentDataContainer().has(bodyKey, PersistentDataType.STRING);
                    if (!boss) {
                        living.setVelocity(new Vector(0, 0.85, 0));
                    }
                    living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0));
                    if (fire) {
                        living.setFireTicks(80);
                    }
                    Fx.worldSound(living.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1.3f);
                }
                if (age >= LIFE) {
                    finish(tornado);
                }
            }

            private void finish(SandTornado t) {
                cancel();
                t.remove();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
