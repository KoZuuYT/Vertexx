package dev.vertexx.end;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Equipamentos do Gloom Stalker: Coração da Penumbra (Passo Sombrio: invisível e silencioso para sensores de Sculk por 8 s,
 * 32 usos, recarga com Echo Shards na bigorna), Garras de Ardósia Sônica (2 Garras + espada de diamante ou netherite; o
 * terceiro golpe seguido lança um pulso sônico) e o Peitoral de Carapaça Sculk (mesa de ferraria: −65% de detecção sonora e
 * resistência extra a dano sônico e explosões).
 */
public final class GloomGear implements Listener {

    private static final int HEART_MAX = 32;
    private static final long STEP_COOLDOWN_MS = 10_000;
    private static final int STEP_TICKS = 160;

    private final PluginMain plugin;
    private final NamespacedKey clawsKey;
    private final NamespacedKey chestKey;
    private final NamespacedKey chargesKey;
    private final NamespacedKey stealthKey;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Map<UUID, long[]> combo = new HashMap<>();

    public GloomGear(PluginMain plugin) {
        this.plugin = plugin;
        this.clawsKey = new NamespacedKey(plugin, "sonic_claws");
        this.chestKey = new NamespacedKey(plugin, "sculk_chest");
        this.chargesKey = new NamespacedKey(plugin, "gloom_charges");
        this.stealthKey = new NamespacedKey(plugin, "gloom_stealth_active");
    }

    public void start() {
        ShapelessRecipe claws = new ShapelessRecipe(new NamespacedKey(plugin, "sonic_claws"), sonicClaws());
        claws.addIngredient(new RecipeChoice.ExactChoice(EndItem.STALKER_CLAW.create(plugin)));
        claws.addIngredient(new RecipeChoice.ExactChoice(EndItem.STALKER_CLAW.create(plugin)));
        claws.addIngredient(new RecipeChoice.MaterialChoice(Material.DIAMOND_SWORD, Material.NETHERITE_SWORD));
        Bukkit.addRecipe(claws);
        Bukkit.addRecipe(new SmithingTransformRecipe(new NamespacedKey(plugin, "sculk_chest"),
                new ItemStack(Material.NETHERITE_CHESTPLATE), new RecipeChoice.ExactChoice(EndItem.SCULK_CARAPACE.create(plugin)),
                new RecipeChoice.MaterialChoice(Material.NETHERITE_CHESTPLATE), new RecipeChoice.MaterialChoice(Material.ECHO_SHARD)));
    }

    // ── Itens ──────────────────────────────────────────────────

    /** As Garras de Ardósia Sônica: base de espada de netherite (dano 9,0), mais rápidas. */
    public ItemStack sonicClaws() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        item.editMeta(meta -> {
            meta.itemName(Msg.mm("<dark_aqua>Garras de Ardósia Sônica"));
            List<Component> lore = new ArrayList<>();
            lore.add(Msg.item("<gray>Garras do Espreitador forjadas numa lâmina."));
            lore.add(Msg.item("<dark_aqua>◆ Pulso Sônico <gray>(3º golpe seguido)"));
            lore.add(Msg.item("<dark_gray>Dispara um pulso de 5 blocos à frente que"));
            lore.add(Msg.item("<dark_gray>atravessa escudos. Arma rápida (dano 9, velocidade 2,2)."));
            meta.lore(lore);
            meta.setTooltipStyle(dev.vertexx.core.Items.TOOLTIP);
            NamespacedKey model = NamespacedKey.fromString(plugin.getConfig().getString("end.items.sonic_claws.model",
                    "vertexx:sonic_claws"));
            if (model != null) {
                meta.setItemModel(model);
            }
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of(1063f));
            meta.setCustomModelDataComponent(component);
            dev.vertexx.core.Items.weaponStats(plugin, meta, Material.NETHERITE_SWORD, 1.0, 0.6);
            meta.getPersistentDataContainer().set(clawsKey, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    private ItemStack chestplate(ItemStack base) {
        ItemStack out = base.clone();
        out.editMeta(meta -> {
            meta.displayName(Msg.item("<dark_aqua>Peitoral de Carapaça Sculk"));
            meta.getPersistentDataContainer().set(chestKey, PersistentDataType.BYTE, (byte) 1);
            lore(meta, "<dark_aqua>◆ −65% de alcance de detecção sonora",
                    "<dark_gray>Mobs e Wardens te ouvem de muito mais perto.",
                    "<dark_gray>Resistência extra a dano sônico e explosões.");
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

    public boolean isClaws(ItemStack item) {
        return item != null && item.getType() == Material.NETHERITE_SWORD && has(item, clawsKey);
    }

    private boolean wearsCarapace(Player player) {
        return has(player.getInventory().getChestplate(), chestKey);
    }

    // ── Coração da Penumbra ────────────────────────────────────

    private int charges(ItemStack heart) {
        Integer value = heart.getItemMeta().getPersistentDataContainer().get(chargesKey, PersistentDataType.INTEGER);
        return value == null ? HEART_MAX : value;
    }

    private void setCharges(ItemStack heart, int value) {
        int clamped = Math.max(0, Math.min(HEART_MAX, value));
        heart.editMeta(meta -> {
            meta.getPersistentDataContainer().set(chargesKey, PersistentDataType.INTEGER, clamped);
            List<Component> lore = new ArrayList<>();
            for (String line : EndItem.GLOOM_HEART.loreLines()) {
                lore.add(Msg.item(line));
            }
            lore.add(Msg.item("<aqua>Usos: <white>" + clamped + "/" + HEART_MAX));
            meta.lore(lore);
        });
    }

    private boolean stealthy(Player player) {
        Long until = player.getPersistentDataContainer().get(stealthKey, PersistentDataType.LONG);
        return until != null && until > System.currentTimeMillis();
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!EndItem.GLOOM_HEART.matches(held)) {
            return;
        }
        event.setCancelled(true);
        int left = charges(held);
        if (left <= 0) {
            Msg.actionBar(player, "<red>O Coração está vazio. <gray>Recarregue com Echo Shards na bigorna.");
            return;
        }
        long now = System.currentTimeMillis();
        long ready = cooldown.getOrDefault(player.getUniqueId(), 0L);
        if (ready > now) {
            Msg.actionBar(player, "<gray>O Coração ainda pulsa: <white>" + ((ready - now) / 1000 + 1) + " s");
            return;
        }
        cooldown.put(player.getUniqueId(), now + STEP_COOLDOWN_MS);
        setCharges(held, left - 1);
        player.getInventory().setItemInMainHand(held);
        player.getPersistentDataContainer().set(stealthKey, PersistentDataType.LONG, now + STEP_TICKS * 50L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, STEP_TICKS, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, STEP_TICKS, 1, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, STEP_TICKS, 0, false, false));
        Location at = player.getLocation();
        Fx.worldSound(at, Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.2f, 0.5f);
        at.getWorld().spawnParticle(Particle.SCULK_SOUL, at.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.04);
        Msg.actionBar(player, "<dark_aqua>Passo Sombrio: <gray>invisível e silencioso por 8 s. Usos: <white>" + (left - 1) + "/" + HEART_MAX);
    }

    /** Sensores de Sculk, Catalisadores e Wardens não percebem quem está no Passo Sombrio; o Peitoral reduz 65%. */
    @EventHandler(ignoreCancelled = true)
    public void onSensor(BlockReceiveGameEvent event) {
        if (event.getEntity() instanceof Player player && muffled(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameEvent(GenericGameEvent event) {
        if (event.getEntity() instanceof Player player && muffled(player)) {
            event.setCancelled(true);
        }
    }

    private boolean muffled(Player player) {
        if (stealthy(player)) {
            return true;
        }
        return wearsCarapace(player) && java.util.concurrent.ThreadLocalRandom.current().nextDouble() < 0.65;
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getFirstItem();
        ItemStack second = event.getInventory().getSecondItem();
        if (!EndItem.GLOOM_HEART.matches(first) || second == null || second.getType() != Material.ECHO_SHARD) {
            return;
        }
        int have = charges(first);
        int need = HEART_MAX - have;
        if (need <= 0) {
            event.setResult(null);
            return;
        }
        int use = Math.min((need + 7) / 8, second.getAmount());
        ItemStack out = first.clone();
        setCharges(out, have + use * 8);
        event.setResult(out);
        event.getView().setRepairCost(2);
        event.getView().setRepairItemCountCost(use);
    }

    // ── Peitoral de Carapaça Sculk ─────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onSmithing(PrepareSmithingEvent event) {
        SmithingInventory inv = event.getInventory();
        ItemStack base = inv.getInputEquipment();
        if (base == null || base.getType() != Material.NETHERITE_CHESTPLATE || !EndItem.SCULK_CARAPACE.matches(inv.getInputTemplate())) {
            return;
        }
        ItemStack addition = inv.getInputMineral();
        event.setResult(addition != null && addition.getType() == Material.ECHO_SHARD ? chestplate(base) : null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !wearsCarapace(player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.SONIC_BOOM || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            event.setDamage(event.getDamage() * 0.65);
        }
    }

    // ── Garras de Ardósia Sônica ───────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity victim)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (!isClaws(player.getInventory().getItemInMainHand())) {
            return;
        }
        long now = System.currentTimeMillis();
        long[] state = combo.computeIfAbsent(player.getUniqueId(), id -> new long[2]);
        state[0] = now - state[1] <= 1500 ? state[0] + 1 : 1;
        state[1] = now;
        if (state[0] < 3) {
            return;
        }
        state[0] = 0;
        pulse(player, victim);
    }

    /** O terceiro golpe seguido: pulso sônico de 5 blocos à frente (nunca fere jogadores). */
    private void pulse(Player player, LivingEntity victim) {
        Vector dir = player.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            return;
        }
        dir.normalize();
        Location eye = player.getEyeLocation();
        Fx.worldSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.4f);
        for (int i = 1; i <= 5; i++) {
            Location p = eye.clone().add(dir.clone().multiply(i)).subtract(0, 0.3, 0);
            p.getWorld().spawnParticle(Particle.SONIC_BOOM, p, 1, 0, 0, 0, 0);
        }
        DamageSource source = DamageSource.builder(DamageType.SONIC_BOOM).withCausingEntity(player).withDirectEntity(player).build();
        for (Entity entity : player.getWorld().getNearbyEntities(eye.clone().add(dir.clone().multiply(2.8)), 3.2, 2.2, 3.2)) {
            if (!(entity instanceof LivingEntity living) || entity instanceof Player || entity.equals(victim)
                    || entity instanceof org.bukkit.entity.ArmorStand || entity instanceof org.bukkit.entity.Tameable tamed && tamed.getOwner() != null) {
                continue;
            }
            Vector to = living.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
            if (to.lengthSquared() > 1.0E-4 && to.normalize().dot(dir) > 0.5) {
                living.damage(6, source);
            }
        }
        if (!victim.isDead()) {
            victim.damage(6, source);
        }
    }
}
