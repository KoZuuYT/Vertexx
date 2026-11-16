package dev.vertexx.overworld;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Encantamentos de maldição com benefício: livros raros (baús de estruturas e Bibliotecários Mestres) que trazem um
 * bônus forte e uma desvantagem tática clara. O encantamento fica no PDC do item e aparece na lore.
 */
public final class Curses implements Listener {

    public enum Curse {
        BLOODTHIRST("bloodthirst", "Lâmina Faminta", "<gradient:#a64dff:#ffb347>", "Espadas e machados",
                "+45% de dano corpo a corpo", "Cada golpe gasta 1,5 de fome"),
        SHATTER("shatter", "Toque da Ruína", "<gradient:#a64dff:#ffb347>", "Picaretas e pás",
                "Haste IV ao segurar", "Blocos minerados não dropam itens"),
        FRAGILE_STRIDE("fragile_stride", "Passo Etéreo", "<gradient:#a64dff:#ffb347>", "Botas",
                "+30% de velocidade e sem dano de queda", "+50% de dano de projéteis e explosões"),
        GLASS_CANNON("glass_cannon", "Olho de Vidro", "<gradient:#a64dff:#ffb347>", "Arcos e bestas",
                "Flechas retas, sem gravidade e críticas", "-5 de durabilidade e Brilho por 4s ao atirar");

        private final String id;
        private final String display;
        private final String color;
        private final String applies;
        private final String bonus;
        private final String malus;

        Curse(String id, String display, String color, String applies, String bonus, String malus) {
            this.id = id;
            this.display = display;
            this.color = color;
            this.applies = applies;
            this.bonus = bonus;
            this.malus = malus;
        }

        public String id() {
            return id;
        }

        public String display() {
            return display;
        }

        public boolean accepts(Material m) {
            String n = m.name();
            return switch (this) {
                case BLOODTHIRST -> n.endsWith("_SWORD") || n.endsWith("_AXE");
                case SHATTER -> n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL");
                case FRAGILE_STRIDE -> n.endsWith("_BOOTS");
                case GLASS_CANNON -> m == Material.BOW || m == Material.CROSSBOW;
            };
        }

        public static Curse parse(String text) {
            for (Curse c : values()) {
                if (c.id.equalsIgnoreCase(text) || c.name().equalsIgnoreCase(text)) {
                    return c;
                }
            }
            return null;
        }
    }

    private final PluginMain plugin;
    private final NamespacedKey curseKey;
    private final NamespacedKey bookKey;
    private final NamespacedKey speedKey;
    private BukkitTask task;

    public Curses(PluginMain plugin) {
        this.plugin = plugin;
        this.curseKey = new NamespacedKey(plugin, "curse");
        this.bookKey = new NamespacedKey(plugin, "curse_book");
        this.speedKey = new NamespacedKey(plugin, "fragile_stride_speed");
    }

    public void start() {
        task = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.removeModifier(speedKey);
            }
        }
    }

    private double cfg(String path, double def) {
        return plugin.getConfig().getDouble("overworld.curses." + path, def);
    }

    // ── Itens ──────────────────────────────────────────────────

    public ItemStack book(Curse curse) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        book.editMeta(meta -> {
            meta.displayName(Msg.item(curse.color + "<bold>" + curse.display + "</bold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Msg.item("<gray>Aplica em: <white>" + curse.applies));
            lore.add(Msg.item("<green>+ " + curse.bonus));
            lore.add(Msg.item("<red>- " + curse.malus));
            lore.add(Msg.item("<dark_gray>Una na bigorna ao item."));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(bookKey, PersistentDataType.STRING, curse.id);
        });
        return book;
    }

    public Curse curseOfBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(bookKey, PersistentDataType.STRING);
        return id == null ? null : Curse.parse(id);
    }

    public Curse curseOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(curseKey, PersistentDataType.STRING);
        return id == null ? null : Curse.parse(id);
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getFirstItem();
        Curse curse = curseOfBook(event.getInventory().getSecondItem());
        if (first == null || curse == null || !curse.accepts(first.getType()) || curseOf(first) != null) {
            return;
        }
        ItemStack result = first.clone();
        result.editMeta(meta -> {
            meta.getPersistentDataContainer().set(curseKey, PersistentDataType.STRING, curse.id);
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Msg.item(curse.color + curse.display + " I"));
            lore.add(Msg.item("<green>+ " + curse.bonus));
            lore.add(Msg.item("<red>- " + curse.malus));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
        });
        event.setResult(result);
        event.getView().setRepairCost(4);
    }

    // ── Aquisição ──────────────────────────────────────────────

    @EventHandler
    public void onLoot(LootGenerateEvent event) {
        if (!plugin.getConfig().getBoolean("overworld.curses.enabled", true)) {
            return;
        }
        String key = event.getLootTable().getKey().getKey();
        if (!key.startsWith("chests/") || event.getLootTable().getKey().getKey().contains("spawn_bonus")) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() < cfg("chest-chance", 0.06)) {
            Curse[] all = Curse.values();
            event.getLoot().add(book(all[r.nextInt(all.length)]));
        }
    }

    @EventHandler
    public void onTrade(VillagerAcquireTradeEvent event) {
        if (!plugin.getConfig().getBoolean("overworld.curses.enabled", true)
                || !(event.getEntity() instanceof Villager villager)
                || villager.getProfession() != Villager.Profession.LIBRARIAN || villager.getVillagerLevel() < 4) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() >= cfg("librarian-chance", 0.25)) {
            return;
        }
        Curse[] all = Curse.values();
        MerchantRecipe recipe = new MerchantRecipe(book(all[r.nextInt(all.length)]), 1);
        recipe.addIngredient(new ItemStack(Material.EMERALD, 40));
        recipe.addIngredient(new ItemStack(Material.BOOK));
        recipe.setExperienceReward(true);
        event.setRecipe(recipe);
    }

    // ── Efeitos ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && curseOf(player.getInventory().getItemInMainHand()) == Curse.BLOODTHIRST) {
            event.setDamage(event.getDamage() * cfg("bloodthirst.damage-multiplier", 1.45));
            drain(player, cfg("bloodthirst.hunger-cost", 1.5));
        }
    }

    private void drain(Player player, double points) {
        float saturation = player.getSaturation();
        if (saturation >= points) {
            player.setSaturation((float) (saturation - points));
            return;
        }
        double rest = points - saturation;
        player.setSaturation(0);
        player.setFoodLevel(Math.max(0, player.getFoodLevel() - (int) Math.ceil(rest)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (curseOf(event.getPlayer().getInventory().getItemInMainHand()) == Curse.SHATTER) {
            event.setDropItems(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || curseOf(player.getInventory().getBoots()) != Curse.FRAGILE_STRIDE) {
            return;
        }
        switch (event.getCause()) {
            case FALL -> event.setCancelled(true);
            case PROJECTILE, ENTITY_EXPLOSION, BLOCK_EXPLOSION ->
                    event.setDamage(event.getDamage() * cfg("fragile-stride.damage-multiplier", 1.5));
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || curseOf(event.getBow()) != Curse.GLASS_CANNON
                || !(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        arrow.setGravity(false);
        arrow.setCritical(true);
        Entity projectile = arrow;
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (projectile.isValid()) {
                projectile.remove();            // sem gravidade, a flecha voaria para sempre
            }
        }, 60L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0, false, false));
        ItemStack bow = event.getBow();
        if (bow != null && bow.getItemMeta() instanceof Damageable damageable) {
            int wear = (int) cfg("glass-cannon.durability-cost", 5);
            int damage = damageable.getDamage() + wear;
            if (damage >= bow.getType().getMaxDurability()) {
                bow.setAmount(0);
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            } else {
                damageable.setDamage(damage);
                bow.setItemMeta(damageable);
            }
        }
    }

    private void tick() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (curseOf(player.getInventory().getItemInMainHand()) == Curse.SHATTER) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 30, 3, true, false, false));
            }
            AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed == null) {
                continue;
            }
            boolean worn = curseOf(player.getInventory().getBoots()) == Curse.FRAGILE_STRIDE;
            AttributeModifier existing = speed.getModifier(speedKey);
            if (worn && existing == null) {
                speed.addModifier(new AttributeModifier(speedKey, cfg("fragile-stride.speed-bonus", 0.3),
                        AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.FEET));
            } else if (!worn && existing != null) {
                speed.removeModifier(speedKey);
            }
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        AttributeInstance speed = event.getPlayer().getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(speedKey);
        }
    }
}
