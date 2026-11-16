package dev.vertexx.overworld;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Climas extremos localizados: tempestade de areia (deserto e badlands), nevasca (planícies nevadas, espinhos de gelo,
 * bosque) e ventania de altitude (picos). Cada clima tem um relógio por mundo, mas só afeta quem está no bioma certo;
 * as partículas vão só para o jogador afetado (raio de 16 blocos). Contramedidas: Óculos de Explorador, couro ou tocha,
 * botas pesadas.
 */
public final class Climates implements Listener {

    enum Type {
        SANDSTORM("Tempestade de areia", "<gold>"),
        BLIZZARD("Nevasca intensa", "<aqua>"),
        GALE("Ventania de altitude", "<white>");

        final String label;
        final String color;

        Type(String label, String color) {
            this.label = label;
            this.color = color;
        }
    }

    private static final class Active {
        long until;
        Vector wind;
        long gustUntil;
        long nextGust;
    }

    private final PluginMain plugin;
    private final NamespacedKey gogglesKey;
    private final NamespacedKey recipeKey;
    private final Map<World, Map<Type, Active>> events = new HashMap<>();
    private final List<Block> snow = new ArrayList<>();
    private final List<Player> announced = new ArrayList<>();
    private BukkitTask task;
    private long tick;

    public Climates(PluginMain plugin) {
        this.plugin = plugin;
        this.gogglesKey = new NamespacedKey(plugin, "explorer_goggles");
        this.recipeKey = new NamespacedKey(plugin, "explorer_goggles_recipe");
    }

    public void start() {
        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, goggles());
        recipe.addIngredient(Material.LEATHER_HELMET);
        recipe.addIngredient(Material.GLASS_PANE);
        recipe.addIngredient(Material.GLASS_PANE);
        recipe.addIngredient(Material.COPPER_INGOT);
        Bukkit.removeRecipe(recipeKey);
        Bukkit.addRecipe(recipe);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 4L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        clearSnow();
    }

    private boolean on() {
        return plugin.getConfig().getBoolean("overworld.climate.enabled", true);
    }

    public ItemStack goggles() {
        ItemStack item = new ItemStack(Material.LEATHER_HELMET);
        item.editMeta(LeatherArmorMeta.class, meta -> {
            meta.setColor(Color.fromRGB(0xB0833A));
            meta.displayName(Msg.item("<gold>Óculos de Explorador"));
            meta.lore(List.of(Msg.item("<gray>Protegem os olhos de tempestades de areia."),
                    Msg.item("<dark_gray>Também conta como armadura de couro.")));
            meta.getPersistentDataContainer().set(gogglesKey, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    private boolean hasGoggles(Player p) {
        ItemStack helmet = p.getInventory().getHelmet();
        return helmet != null && helmet.hasItemMeta()
                && helmet.getItemMeta().getPersistentDataContainer().has(gogglesKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        // o capacete usado na receita não pode já ser os óculos (evita clonagem inútil); nada a fazer além do padrão
    }

    // ── Relógio ────────────────────────────────────────────────

    private static boolean biome(Player p, String... names) {
        String key = p.getLocation().getBlock().getBiome().getKey().getKey();
        for (String n : names) {
            if (key.equals(n)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(Type type, Player p) {
        return switch (type) {
            case SANDSTORM -> biome(p, "desert", "badlands", "eroded_badlands", "wooded_badlands");
            case BLIZZARD -> biome(p, "snowy_plains", "ice_spikes", "grove");
            case GALE -> p.getLocation().getY() > 150 && biome(p, "jagged_peaks", "frozen_peaks");
        };
    }

    private void tick() {
        tick += 4;
        if (!on()) {
            if (!events.isEmpty()) {
                events.clear();
                clearSnow();
            }
            return;
        }
        boolean second = tick % 20 == 0;
        long now = tick;
        if (tick % 400 == 0) {
            roll(now);
        }
        for (var entry : new ArrayList<>(events.entrySet())) {
            World world = entry.getKey();
            for (var e : new ArrayList<>(entry.getValue().entrySet())) {
                Type type = e.getKey();
                Active active = e.getValue();
                if (now >= active.until) {
                    entry.getValue().remove(type);
                    if (type == Type.BLIZZARD) {
                        clearSnow();
                    }
                    continue;
                }
                for (Player p : world.getPlayers()) {
                    if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || !matches(type, p)) {
                        continue;
                    }
                    if (!announced.contains(p)) {
                        announced.add(p);
                        Msg.actionBar(p, type.color + "⚠ " + type.label + " se aproxima!");
                    }
                    apply(type, active, p, second, now);
                }
            }
        }
        announced.removeIf(p -> !p.isOnline() || events.values().stream().noneMatch(m -> !m.isEmpty()));
    }

    private void roll(long now) {
        double chance = plugin.getConfig().getDouble("overworld.climate.chance", 0.18);
        long duration = plugin.getConfig().getLong("overworld.climate.duration-seconds", 90) * 20L;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL || world.getPlayers().isEmpty()) {
                continue;
            }
            Map<Type, Active> map = events.computeIfAbsent(world, w -> new HashMap<>());
            for (Type type : Type.values()) {
                if (map.containsKey(type) || r.nextDouble() >= chance
                        || world.getPlayers().stream().noneMatch(p -> matches(type, p))) {
                    continue;
                }
                Active a = new Active();
                a.until = now + duration;
                double ang = r.nextDouble() * Math.PI * 2;
                a.wind = new Vector(Math.cos(ang), 0, Math.sin(ang));
                a.nextGust = now + 60;
                map.put(type, a);
            }
        }
    }

    private boolean sheltered(Player p) {
        return p.getLocation().getBlock().getLightFromSky() < 10;
    }

    private void apply(Type type, Active a, Player p, boolean second, long now) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (type) {
            case SANDSTORM -> {
                Vector dir = a.wind;
                for (int i = 0; i < 10; i++) {
                    org.bukkit.Location at = p.getLocation().add(r.nextDouble(-12, 12), r.nextDouble(0, 6), r.nextDouble(-12, 12));
                    p.spawnParticle(Particle.FALLING_DUST, at, 0, dir.getX(), -0.05, dir.getZ(), 0.9,
                            Material.SAND.createBlockData());
                }
                if (sheltered(p)) {
                    return;
                }
                if (!hasGoggles(p) && second) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, true, false, false));
                }
                double push = p.isSneaking() ? 0.015 : 0.035;
                p.setVelocity(p.getVelocity().add(dir.clone().multiply(push)));
            }
            case BLIZZARD -> {
                for (int i = 0; i < 12; i++) {
                    org.bukkit.Location at = p.getLocation().add(r.nextDouble(-12, 12), r.nextDouble(0, 7), r.nextDouble(-12, 12));
                    p.spawnParticle(Particle.SNOWFLAKE, at, 0, a.wind.getX(), -0.1, a.wind.getZ(), 0.6);
                }
                if (!second || sheltered(p)) {
                    return;
                }
                if (!warm(p)) {
                    p.setFreezeTicks(Math.min(p.getMaxFreezeTicks(), p.getFreezeTicks() + 14));
                }
                for (int i = 0; i < 3 && snow.size() < 600; i++) {
                    Block top = p.getWorld().getHighestBlockAt(p.getLocation().add(r.nextInt(-10, 11), 0, r.nextInt(-10, 11)));
                    Block above = top.getRelative(0, 1, 0);
                    if (above.getType().isAir() && top.getType().isSolid() && top.getType() != Material.SNOW_BLOCK
                            && top.getType() != Material.ICE && !top.isLiquid()) {
                        above.setType(Material.SNOW, false);
                        snow.add(above);
                    }
                }
            }
            case GALE -> {
                if (now >= a.nextGust) {
                    a.gustUntil = now + 50;
                    a.nextGust = now + 120 + r.nextInt(120);
                    p.getWorld().playSound(p.getLocation(), Sound.ITEM_ELYTRA_FLYING, 1.2f, 0.5f);
                }
                if (now >= a.gustUntil) {
                    return;
                }
                double k = heavy(p) ? 0.2 : 1.0;
                Vector push = a.wind.clone().multiply(0.09 * k);
                if (p.isGliding()) {
                    push.setY(0.06 * k);
                }
                p.setVelocity(p.getVelocity().add(push));
                p.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 3, 1.5, 0.8, 1.5, 0.02);
            }
        }
    }

    private boolean warm(Player p) {
        for (ItemStack piece : p.getInventory().getArmorContents()) {
            if (piece != null && piece.getType().name().startsWith("LEATHER_")) {
                return true;
            }
        }
        Material main = p.getInventory().getItemInMainHand().getType();
        Material off = p.getInventory().getItemInOffHand().getType();
        return isLight(main) || isLight(off);
    }

    private static boolean isLight(Material m) {
        return m == Material.TORCH || m == Material.SOUL_TORCH || m == Material.LANTERN || m == Material.SOUL_LANTERN;
    }

    private boolean heavy(Player p) {
        ItemStack boots = p.getInventory().getBoots();
        return boots != null && (boots.getType() == Material.NETHERITE_BOOTS
                || boots.containsEnchantment(Enchantment.FEATHER_FALLING));
    }

    private void clearSnow() {
        for (Block b : snow) {
            if (b.getType() == Material.SNOW) {
                b.setType(Material.AIR, false);
            }
        }
        snow.clear();
    }

    /** Força um clima agora (teste/admin). */
    public String force(String name, World world) {
        for (Type type : Type.values()) {
            if (type.name().equalsIgnoreCase(name)) {
                Active a = new Active();
                a.until = tick + plugin.getConfig().getLong("overworld.climate.duration-seconds", 90) * 20L;
                double ang = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
                a.wind = new Vector(Math.cos(ang), 0, Math.sin(ang));
                events.computeIfAbsent(world, w -> new HashMap<>()).put(type, a);
                return type.label;
            }
        }
        return null;
    }
}
