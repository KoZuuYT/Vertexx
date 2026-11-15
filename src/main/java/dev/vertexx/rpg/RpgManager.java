package dev.vertexx.rpg;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Cinematics;
import dev.vertexx.core.Msg;
import dev.vertexx.core.YamlStore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.scheduler.BukkitTask;

/**
 * Núcleo do sistema de progressão: dados dos jogadores (status, nível geral, pontos, árvore),
 * ganho de XP, bônus passivos e persistência em {@code rpg.yml}.
 */
public final class RpgManager implements Listener {

    private final PluginMain plugin;
    private final YamlStore store;
    private final Cinematics cinematics;
    private final Map<UUID, PlayerData> data = new HashMap<>();
    private final Map<UUID, Long> lastToast = new HashMap<>();
    private final Map<UUID, Location> lastPosition = new HashMap<>();
    private BukkitTask task;
    private boolean dirty;
    private int beat;

    public RpgManager(PluginMain plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "rpg.yml");
        this.cinematics = new Cinematics(plugin);
    }

    public Cinematics cinematics() {
        return cinematics;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("rpg.enabled", true);
    }

    // ── Persistência ───────────────────────────────────────────

    public YamlConfiguration read() {
        return store.load();
    }

    public void apply(YamlConfiguration yaml) {
        data.clear();
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection s = players.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            PlayerData d = new PlayerData();
            d.name = s.getString("name", "");
            d.level = Math.max(1, s.getInt("level", 1));
            d.xp = s.getDouble("xp", 0);
            d.points = Math.max(0, s.getInt("points", 0));
            d.selected = parseSkill(s.getString("selected"));
            for (Stat stat : Stat.values()) {
                d.statLevel[stat.ordinal()] = Math.max(1, s.getInt("stats." + stat.name() + ".level", 1));
                d.statXp[stat.ordinal()] = s.getDouble("stats." + stat.name() + ".xp", 0);
            }
            ConfigurationSection skills = s.getConfigurationSection("skills");
            if (skills != null) {
                for (String name : skills.getKeys(false)) {
                    Skill skill = parseSkill(name);
                    if (skill != null) {
                        d.skills.put(skill, Math.min(skill.maxRank(), skills.getInt(name)));
                    }
                }
            }
            data.put(id, d);
        }
    }

    private static Skill parseSkill(String name) {
        if (name == null) {
            return null;
        }
        try {
            return Skill.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        data.forEach((id, d) -> {
            String base = "players." + id;
            yaml.set(base + ".name", d.name);
            yaml.set(base + ".level", d.level);
            yaml.set(base + ".xp", Math.round(d.xp * 100) / 100.0);
            yaml.set(base + ".points", d.points);
            yaml.set(base + ".selected", d.selected == null ? null : d.selected.name());
            for (Stat stat : Stat.values()) {
                yaml.set(base + ".stats." + stat.name() + ".level", d.statLevel[stat.ordinal()]);
                yaml.set(base + ".stats." + stat.name() + ".xp", Math.round(d.statXp[stat.ordinal()] * 100) / 100.0);
            }
            d.skills.forEach((skill, rank) -> yaml.set(base + ".skills." + skill.name(), rank));
        });
        return yaml;
    }

    public void save() {
        dirty = false;
        store.saveAsync(snapshot());
    }

    public void saveNow() {
        store.saveNow(snapshot());
    }

    // ── Ciclo de vida ──────────────────────────────────────────

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
    }

    /** Aplica os bônus passivos a quem já estava online (após carregar os dados ou /reload). */
    public void refreshOnline() {
        Bukkit.getOnlinePlayers().forEach(this::applyPassives);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData d = data(player);
        d.name = player.getName();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                applyPassives(player);
            }
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastPosition.remove(event.getPlayer().getUniqueId());
        lastToast.remove(event.getPlayer().getUniqueId());
        if (dirty) {
            save();
        }
    }

    private void tick() {
        beat++;
        boolean second = beat % 2 == 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!enabled() || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            plugin.skills().tick(player, second);
            if (second) {
                trackMovement(player);
            }
        }
        if (beat % 600 == 0 && dirty) {
            save();
        }
    }

    private void trackMovement(Player player) {
        Location now = player.getLocation();
        Location before = lastPosition.put(player.getUniqueId(), now);
        if (before == null || before.getWorld() != now.getWorld() || player.isInsideVehicle()
                || player.isGliding() || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        double distance = Math.hypot(now.getX() - before.getX(), now.getZ() - before.getZ());
        if (distance > 0.5 && distance < 20) {
            addXp(player, Stat.EXPLORATION, distance * 0.12);
        }
        if (player.isSprinting() && distance > 2) {
            addXp(player, Stat.ENDURANCE, 0.7);
        }
    }

    // ── Dados ──────────────────────────────────────────────────

    public PlayerData data(Player player) {
        return data.computeIfAbsent(player.getUniqueId(), id -> {
            PlayerData d = new PlayerData();
            d.name = player.getName();
            return d;
        });
    }

    public void markDirty() {
        dirty = true;
    }

    // ── XP ─────────────────────────────────────────────────────

    public void addXp(Player player, Stat stat, double amount) {
        if (!enabled() || amount <= 0 || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("rpg");
        amount *= c.getDouble("xp-multiplier", 1.0);
        PlayerData d = data(player);
        int i = stat.ordinal();
        int cap = c.getInt("max-stat-level", 100);
        boolean leveled = false;
        if (d.statLevel[i] < cap) {
            d.statXp[i] += amount;
            while (d.statLevel[i] < cap && d.statXp[i] >= PlayerData.statNeed(d.statLevel[i])) {
                d.statXp[i] -= PlayerData.statNeed(d.statLevel[i]);
                d.statLevel[i]++;
                leveled = true;
                int level = d.statLevel[i];
                cinematics.statLevelUp(player, stat.display(), level, level % 10 == 0);
            }
            if (leveled) {
                applyPassives(player);
            }
        }
        d.xp += amount * c.getDouble("general-share", 0.8);
        int generalCap = c.getInt("max-level", 100);
        while (d.level < generalCap && d.xp >= PlayerData.generalNeed(d.level)) {
            d.xp -= PlayerData.generalNeed(d.level);
            d.level++;
            int points = PlayerData.pointsFor(d.level);
            d.points += points;
            leveled = true;
            cinematics.generalLevelUp(player, d.level, points);
            Msg.send(player, "<gold>Você alcançou o nível <white>" + d.level + "</white>! Gaste seus pontos "
                    + "na árvore de habilidades (<white>/vstats</white>).");
        }
        dirty = true;
        if (!leveled && c.getBoolean("xp-toast", true)) {
            toast(player, stat, d, amount);
        }
    }

    private void toast(Player player, Stat stat, PlayerData d, double amount) {
        long now = System.currentTimeMillis();
        if (now - lastToast.getOrDefault(player.getUniqueId(), 0L) < 1200) {
            return;
        }
        lastToast.put(player.getUniqueId(), now);
        double frac = Math.min(1, d.xp(stat) / PlayerData.statNeed(d.level(stat)));
        Msg.actionBar(player, stat.color() + stat.display() + " <white>" + d.level(stat) + " " + bar(frac, 12)
                + " <gray>+" + (amount < 10 ? String.format("%.1f", amount) : String.valueOf(Math.round(amount))));
    }

    public static String bar(double fraction, int length) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * length);
        return "<green>" + "▮".repeat(filled) + "<dark_gray>" + "▯".repeat(length - filled);
    }

    // ── Habilidades ────────────────────────────────────────────

    /** Gasta 1 ponto no próximo nível da habilidade; devolve o motivo da recusa ou {@code null}. */
    public String buy(Player player, Skill skill) {
        PlayerData d = data(player);
        int rank = d.rank(skill);
        if (rank >= skill.maxRank()) {
            return "Essa habilidade já está no nível máximo.";
        }
        if (d.level(skill.stat()) < skill.requiredLevel()) {
            return "Requer " + skill.stat().display() + " nível " + skill.requiredLevel() + ".";
        }
        if (skill.parent() != null && d.rank(skill.parent()) < 1) {
            return "Requer a habilidade " + skill.parent().display() + ".";
        }
        if (d.points < 1) {
            return "Você não tem pontos de habilidade.";
        }
        d.points--;
        d.skills.put(skill, rank + 1);
        if (skill.active() && d.selected == null) {
            d.selected = skill;
        }
        applyPassives(player);
        dirty = true;
        cinematics.skillUnlocked(player, skill.display(), rank + 1);
        return null;
    }

    /** Devolve todos os pontos gastos mediante um custo em níveis de XP do Minecraft. */
    public String reset(Player player) {
        PlayerData d = data(player);
        int refund = d.skills.values().stream().mapToInt(Integer::intValue).sum();
        if (refund == 0) {
            return "Você não tem habilidades para redefinir.";
        }
        int cost = plugin.getConfig().getInt("rpg.respec-cost-levels", 5);
        if (player.getLevel() < cost) {
            return "Redefinir custa " + cost + " níveis de XP.";
        }
        player.setLevel(player.getLevel() - cost);
        d.points += refund;
        d.skills.clear();
        d.selected = null;
        applyPassives(player);
        dirty = true;
        return null;
    }

    public void select(Player player, Skill skill) {
        PlayerData d = data(player);
        if (skill.active() && d.rank(skill) > 0) {
            d.selected = skill;
            dirty = true;
        }
    }

    // ── Bônus passivos ─────────────────────────────────────────

    public void applyPassives(Player player) {
        PlayerData d = data(player);
        boolean on = enabled();
        double dig = (d.level(Stat.MINING) - 1 + d.level(Stat.EXCAVATION) - 1 + d.level(Stat.WOODCUTTING) - 1) * 0.004
                + d.rank(Skill.HASTE) * 0.08;
        mod(player, Attribute.BLOCK_BREAK_SPEED, "rpg_dig", on ? dig : 0, AttributeModifier.Operation.ADD_SCALAR);
        mod(player, Attribute.ATTACK_DAMAGE, "rpg_combat", on ? (d.level(Stat.COMBAT) - 1) * 0.004 : 0,
                AttributeModifier.Operation.ADD_SCALAR);
        mod(player, Attribute.MAX_HEALTH, "rpg_health",
                on ? (d.level(Stat.ENDURANCE) - 1) * 0.1 + d.rank(Skill.VITALITY) * 2 : 0,
                AttributeModifier.Operation.ADD_NUMBER);
        mod(player, Attribute.SAFE_FALL_DISTANCE, "rpg_fall", on ? (d.level(Stat.ACROBATICS) - 1) * 0.04 : 0,
                AttributeModifier.Operation.ADD_NUMBER);
        mod(player, Attribute.MOVEMENT_SPEED, "rpg_speed", on ? (d.level(Stat.EXPLORATION) - 1) * 0.001 : 0,
                AttributeModifier.Operation.ADD_SCALAR);
        mod(player, Attribute.LUCK, "rpg_luck", on ? (d.level(Stat.FISHING) - 1) * 0.05 : 0,
                AttributeModifier.Operation.ADD_NUMBER);
        mod(player, Attribute.ARMOR, "rpg_armor", on ? d.rank(Skill.THICK) : 0, AttributeModifier.Operation.ADD_NUMBER);
        mod(player, Attribute.BLOCK_INTERACTION_RANGE, "rpg_reach", on ? d.rank(Skill.REACH) : 0,
                AttributeModifier.Operation.ADD_NUMBER);
    }

    private void mod(Player player, Attribute attribute, String id, double amount, AttributeModifier.Operation op) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, id);
        AttributeModifier existing = instance.getModifier(key);
        if (existing != null) {
            if (existing.getAmount() == amount) {
                return;
            }
            instance.removeModifier(existing);
        }
        if (amount != 0) {
            instance.addModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
        }
    }
}
