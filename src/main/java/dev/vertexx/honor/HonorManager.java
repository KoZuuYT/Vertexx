package dev.vertexx.honor;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import dev.vertexx.core.YamlStore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Honra: atributo flutuante de -100 a 100, salvo em {@code honor.yml}.
 * Sobe com atividade e combate justo; cai com ataques injustificados e traições.
 */
public final class HonorManager {

    public static final int MIN = -100;
    public static final int MAX = 100;

    public enum Tier {
        DESONRADO("Desonrado", "<red>", 2.0),
        NEUTRO("Neutro", "<gray>", 1.0),
        HONRADO("Honrado", "<green>", 1.0),
        GLORIOSO("Glorioso", "<gold>", 0.0);

        private final String displayName;
        private final String color;
        private final double travelMultiplier;

        Tier(String displayName, String color, double travelMultiplier) {
            this.displayName = displayName;
            this.color = color;
            this.travelMultiplier = travelMultiplier;
        }

        public String styled() {
            return color + displayName;
        }

        /** Desonrados pagam o dobro nos Waystones; Gloriosos viajam de graça. */
        public double travelMultiplier() {
            return travelMultiplier;
        }

        public static Tier of(int honor) {
            if (honor <= -30) {
                return DESONRADO;
            }
            if (honor >= 70) {
                return GLORIOSO;
            }
            return honor >= 30 ? HONRADO : NEUTRO;
        }
    }

    private final PluginMain plugin;
    private final YamlStore store;
    private final Map<UUID, Integer> honor = new HashMap<>();
    private final Map<UUID, Location> lastSeen = new HashMap<>();
    private boolean dirty;

    public HonorManager(PluginMain plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "honor.yml");
    }

    // ── Persistência ────────────────────────────────────────────

    /** Leitura do disco: chame fora da thread principal. */
    public YamlConfiguration read() {
        return store.load();
    }

    /** Aplica os dados lidos: chame na thread principal. */
    public void apply(YamlConfiguration data) {
        honor.clear();
        ConfigurationSection section = data.getConfigurationSection("honor");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                honor.put(UUID.fromString(key), clamp(section.getInt(key)));
            } catch (IllegalArgumentException ignored) {
                // entrada inválida: ignora
            }
        }
    }

    public void saveNow() {
        store.saveNow(snapshot());
        dirty = false;
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : honor.entrySet()) {
            data.set("honor." + entry.getKey(), entry.getValue());
        }
        return data;
    }

    /** Salvamento periódico (a honra muda com frequência) e bônus de atividade. */
    public void startTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                dirty = false;
                store.saveAsync(snapshot());
            }
        }, 20L * 60, 20L * 60);

        long interval = Math.max(1, plugin.getConfig().getLong("honor.activity.interval-minutes", 10)) * 60 * 20;
        Bukkit.getScheduler().runTaskTimer(plugin, this::rewardActivity, interval, interval);
    }

    /** Bonifica quem está jogando de verdade: quem não saiu do lugar desde a última checagem não recebe. */
    private void rewardActivity() {
        int amount = plugin.getConfig().getInt("honor.activity.amount", 1);
        lastSeen.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location now = player.getLocation();
            Location before = lastSeen.put(player.getUniqueId(), now);
            boolean moved = before == null || before.getWorld() != now.getWorld() || before.distanceSquared(now) > 4.0;
            if (moved && amount != 0 && plugin.kingdoms().kingdomOf(player.getUniqueId()) != null) {
                add(player, amount, "atividade");
            }
        }
    }

    // ── Valor ───────────────────────────────────────────────────

    private static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    public int get(UUID id) {
        return honor.getOrDefault(id, 0);
    }

    public Tier tier(UUID id) {
        return Tier.of(get(id));
    }

    public void set(UUID id, int value) {
        honor.put(id, clamp(value));
        dirty = true;
    }

    /** Altera a honra e mostra o motivo na actionbar, com som correspondente. */
    public void add(Player player, int delta, String reason) {
        if (delta == 0) {
            return;
        }
        int before = get(player.getUniqueId());
        int after = clamp(before + delta);
        if (after == before) {
            return;
        }
        set(player.getUniqueId(), after);
        boolean gain = after > before;
        Msg.actionBar(player, (gain ? "<green>+" : "<red>") + (after - before) + " Honra <dark_gray>(" + reason + ") <gray>"
                + after + " — " + Tier.of(after).styled());
        if (gain) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.6f);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.8f, 0.7f);
        }
        Tier oldTier = Tier.of(before);
        Tier newTier = Tier.of(after);
        if (oldTier != newTier) {
            Msg.send(player, "<gray>Sua reputação agora é " + newTier.styled() + "<gray>.");
        }
    }
}
