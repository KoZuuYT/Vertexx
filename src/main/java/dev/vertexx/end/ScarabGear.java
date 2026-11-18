package dev.vertexx.end;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Equipamentos do Void-Scarab: Segadora de Mandíbula (avanço), Élitros de Asa do Vazio (+6 de armadura), Peitoral de
 * Quitina Abissal (−35% de projéteis e explosões, reflete espinhos) e o Coração de Cristal do Vazio (âncora de retorno).
 * As receitas usam a mesa de ferraria: modelo = 2x Placas de Quitina, peça-base e o material do boss.
 */
public final class ScarabGear implements Listener {

    private static final int HEART_MAX = 4;

    private final PluginMain plugin;
    private final NamespacedKey bladeKey;
    private final NamespacedKey wingKey;
    private final NamespacedKey chestKey;
    private final NamespacedKey chargesKey;
    private final Map<UUID, Location> safe = new HashMap<>();
    private final Map<UUID, Location> anchor = new HashMap<>();
    private final Map<UUID, Long> dashCooldown = new HashMap<>();

    public ScarabGear(PluginMain plugin) {
        this.plugin = plugin;
        this.bladeKey = new NamespacedKey(plugin, "scarab_blade");
        this.wingKey = new NamespacedKey(plugin, "void_wing");
        this.chestKey = new NamespacedKey(plugin, "chitin_chest");
        this.chargesKey = new NamespacedKey(plugin, "heart_charges");
    }

    public void start() {
        recipe("scarab_blade", new ItemStack(Material.NETHERITE_SWORD), Material.NETHERITE_SWORD, EndItem.SCARAB_MANDIBLE);
        recipe("void_wing", new ItemStack(Material.ELYTRA), Material.ELYTRA, EndItem.ETHEREAL_MEMBRANE);
        recipe("chitin_chest", new ItemStack(Material.NETHERITE_CHESTPLATE), Material.NETHERITE_CHESTPLATE,
                EndItem.ENDERITE_FRAGMENT);
        Bukkit.getScheduler().runTaskTimer(plugin, this::trackSafe, 20L, 10L);
    }

    private void recipe(String id, ItemStack result, Material base, EndItem addition) {
        SmithingTransformRecipe recipe = new SmithingTransformRecipe(new NamespacedKey(plugin, id), result,
                new RecipeChoice.ExactChoice(EndItem.CHITIN_PLATE.create(plugin)), new RecipeChoice.MaterialChoice(base),
                new RecipeChoice.ExactChoice(addition.create(plugin)));
        Bukkit.addRecipe(recipe);
    }

    // ── Ferraria ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onSmithing(PrepareSmithingEvent event) {
        SmithingInventory inv = event.getInventory();
        ItemStack template = inv.getInputTemplate();
        ItemStack base = inv.getInputEquipment();
        ItemStack addition = inv.getInputMineral();
        if (base == null || !EndItem.CHITIN_PLATE.matches(template)) {
            return;
        }
        if (template.getAmount() < 2) {
            event.setResult(null);
            return;
        }
        ItemStack result = null;
        if (base.getType() == Material.NETHERITE_SWORD && EndItem.SCARAB_MANDIBLE.matches(addition)) {
            result = blade(base);
        } else if (base.getType() == Material.ELYTRA && EndItem.ETHEREAL_MEMBRANE.matches(addition)) {
            result = wings(base);
        } else if (base.getType() == Material.NETHERITE_CHESTPLATE && EndItem.ENDERITE_FRAGMENT.matches(addition)) {
            result = chestplate(base);
        }
        event.setResult(result);
    }

    /** A receita consome 1 placa; a segunda sai aqui. */
    @EventHandler
    public void onSmithed(SmithItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null || !(isBlade(result) || isWings(result) || isChest(result))) {
            return;
        }
        SmithingInventory inv = event.getInventory();
        ItemStack template = inv.getInputTemplate();
        if (template == null || template.getAmount() < 2) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack left = inv.getInputTemplate();
            if (left != null && left.getAmount() > 0) {
                left.setAmount(left.getAmount() - 1);
                inv.setInputTemplate(left);
            }
        });
    }

    private ItemStack blade(ItemStack base) {
        ItemStack out = base.clone();
        out.editMeta(meta -> {
            meta.displayName(Msg.item("<dark_purple>Segadora de Mandíbula do Vazio"));
            dev.vertexx.core.Items.weaponStats(plugin, meta, out.getType(), 2.5, 0);
            meta.getPersistentDataContainer().set(bladeKey, PersistentDataType.BYTE, (byte) 1);
            lore(meta, "<light_purple>◆ Avanço do Vazio <gray>(botão direito, 6 s)",
                    "<dark_gray>Corta tudo à frente e ignora 40% da armadura.");
        });
        return out;
    }

    private ItemStack wings(ItemStack base) {
        ItemStack out = base.clone();
        out.editMeta(meta -> {
            meta.displayName(Msg.item("<dark_purple>Élitros de Asa do Vazio"));
            meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(new NamespacedKey(plugin, "void_wing_armor"),
                    6.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST));
            meta.getPersistentDataContainer().set(wingKey, PersistentDataType.BYTE, (byte) 1);
            lore(meta, "<light_purple>◆ +6 de armadura sem impedir o voo", "<dark_gray>Durabilidade dobrada.");
        });
        int max = out.getType().getMaxDurability() > 0 ? out.getType().getMaxDurability() : 432;
        out.setData(DataComponentTypes.MAX_DAMAGE, max * 2);
        return out;
    }

    private ItemStack chestplate(ItemStack base) {
        ItemStack out = base.clone();
        out.editMeta(meta -> {
            meta.displayName(Msg.item("<dark_purple>Peitoral de Quitina Abissal"));
            meta.getPersistentDataContainer().set(chestKey, PersistentDataType.BYTE, (byte) 1);
            lore(meta, "<light_purple>◆ −35% de projéteis e explosões",
                    "<dark_gray>Reflete espinhos contra quem te ataca de perto.");
        });
        return out;
    }

    private static void lore(ItemMeta meta, String... lines) {
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        for (String line : lines) {
            lore.add(Msg.item(line));
        }
        meta.lore(lore);
    }

    private boolean has(ItemStack item, NamespacedKey key) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private boolean isBlade(ItemStack item) {
        return has(item, bladeKey);
    }

    private boolean isWings(ItemStack item) {
        return has(item, wingKey);
    }

    private boolean isChest(ItemStack item) {
        return has(item, chestKey);
    }

    // ── Segadora e âncora ──────────────────────────────────────

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (isBlade(held)) {
            dash(player);
            return;
        }
        if (EndItem.CRYSTAL_HEART.matches(held)) {
            event.setCancelled(true);
            Location here = player.getLocation();
            if (player.getWorld().getEnvironment() != World.Environment.THE_END || !player.isOnGround()) {
                Msg.actionBar(player, "<red>Firme os pés numa ilha do Fim para ancorar.");
                return;
            }
            anchor.put(player.getUniqueId(), here.clone());
            Fx.worldSound(here, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.8f);
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, here.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.1);
            Msg.actionBar(player, "<light_purple>Âncora dimensional fixada nesta ilha. <gray>Cargas: " + charges(held) + "/" + HEART_MAX);
        }
    }

    private void dash(Player player) {
        long now = System.currentTimeMillis();
        if (dashCooldown.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }
        dashCooldown.put(player.getUniqueId(), now + 6000);
        Vector look = player.getLocation().getDirection();
        Vector dir = new Vector(look.getX(), Math.max(-0.2, Math.min(0.3, look.getY())), look.getZ()).normalize();
        player.setVelocity(dir.clone().multiply(1.7).setY(Math.max(0.15, dir.getY() * 1.7)));
        Fx.worldSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
        Fx.worldSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.4f);
        Set<UUID> hit = new HashSet<>();
        for (int step = 1; step <= 5; step++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location at = player.getLocation().add(0, 1, 0);
                at.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at, 1, 0, 0, 0, 0);
                at.getWorld().spawnParticle(Particle.WITCH, at, 6, 0.3, 0.4, 0.3, 0.02);
                for (LivingEntity entity : at.getWorld().getNearbyLivingEntities(at, 1.8)) {
                    if (entity.equals(player) || entity instanceof Player || !hit.add(entity.getUniqueId())) {
                        continue;
                    }
                    AttributeInstance armorAttr = entity.getAttribute(Attribute.ARMOR);
                    double armor = armorAttr == null ? 0 : armorAttr.getValue();
                    entity.damage(10.5, player);
                    double extra = 10.5 * 0.4 * Math.min(20, armor) / 25.0;
                    if (extra > 0 && !entity.isDead()) {
                        entity.setHealth(Math.max(0.1, entity.getHealth() - extra));
                    }
                }
            }, step);
        }
    }

    // ── Peitoral ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isChest(player.getInventory().getChestplate())) {
            return;
        }
        switch (event.getCause()) {
            case PROJECTILE, ENTITY_EXPLOSION, BLOCK_EXPLOSION -> event.setDamage(event.getDamage() * 0.65);
            default -> { }
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && byEntity.getDamager() instanceof LivingEntity attacker && !(attacker instanceof Player)) {
            attacker.damage(3, player);
            attacker.getWorld().spawnParticle(Particle.WITCH, attacker.getLocation().add(0, 1, 0), 8, 0.3, 0.4,
                    0.3, 0.02);
        }
    }

    // ── Coração de Cristal ─────────────────────────────────────

    private int charges(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(chargesKey, PersistentDataType.INTEGER, HEART_MAX);
    }

    private void setCharges(ItemStack item, int value) {
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(chargesKey, PersistentDataType.INTEGER, value);
            List<Component> lore = new ArrayList<>();
            if (meta.hasLore()) {
                for (Component line : meta.lore()) {
                    if (!Msg.plain(line).startsWith("Cargas:")) {
                        lore.add(line);
                    }
                }
            }
            lore.add(Msg.item("<light_purple>Cargas: " + value + "/" + HEART_MAX));
            meta.lore(lore);
        });
    }

    private ItemStack heart(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (EndItem.CRYSTAL_HEART.matches(item) && charges(item) > 0) {
                return item;
            }
        }
        return null;
    }

    private void trackSafe() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() != World.Environment.THE_END) {
                continue;
            }
            Location at = player.getLocation();
            if (at.getY() < -10) {
                ItemStack heart = heart(player);
                if (heart != null) {
                    rescue(player, heart);
                }
            } else if (player.isOnGround() && !player.isFlying() && at.getY() > 5
                    && at.clone().subtract(0, 0.2, 0).getBlock().getType().isSolid()) {
                safe.put(player.getUniqueId(), at.clone());
            }
        }
    }

    private void rescue(Player player, ItemStack heart) {
        Location target = anchor.getOrDefault(player.getUniqueId(), safe.get(player.getUniqueId()));
        if (target == null) {
            return;
        }
        setCharges(heart, charges(heart) - 1);
        player.setFallDistance(0);
        player.teleport(target);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0));
        Fx.worldSound(target, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.2f, 0.7f);
        target.getWorld().spawnParticle(Particle.REVERSE_PORTAL, target.clone().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.2);
        Msg.actionBar(player, "<light_purple>O Coração de Cristal te trouxe de volta. <gray>Cargas: " + charges(heart) + "/" + HEART_MAX);
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getFirstItem();
        ItemStack second = event.getInventory().getSecondItem();
        if (!EndItem.CRYSTAL_HEART.matches(first) || second == null || second.getType() != Material.ENDER_PEARL) {
            return;
        }
        int have = charges(first);
        int need = HEART_MAX - have;
        if (need <= 0) {
            event.setResult(null);
            return;
        }
        int use = Math.min(need, second.getAmount());
        ItemStack out = first.clone();
        setCharges(out, have + use);
        event.setResult(out);
        event.getView().setRepairCost(2);
        event.getView().setRepairItemCountCost(use);
    }
}
