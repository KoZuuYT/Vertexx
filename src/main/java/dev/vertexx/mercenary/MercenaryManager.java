package dev.vertexx.mercenary;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import dev.vertexx.core.YamlStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Foras da lei (jogadores sem reino), reputação de mercenário, recompensas e marcas de traição,
 * persistidos em {@code mercenaries.yml}.
 * <p>
 * A reputação vai de 0 a 100 e sobe com mortes, traições e roubos; os benefícios só valem enquanto o
 * jogador está sem reino. Recompensas são pagas em itens (ver {@code bounty.currency}) a quem matar o alvo.
 */
public final class MercenaryManager {

    public static final int MAX_REP = 100;

    public enum Tier {
        DESCONHECIDO("Desconhecido", "<gray>", 0, 1.0, 0),
        NOTORIO("Notório", "<yellow>", 20, 0.75, 10),
        TEMIDO("Temido", "<gold>", 50, 0.5, 25),
        LENDARIO("Lendário", "<light_purple>", 80, 0.0, 50);

        private final String displayName;
        private final String color;
        private final int min;
        private final double travelMultiplier;
        private final int bountyBonusPercent;

        Tier(String displayName, String color, int min, double travelMultiplier, int bountyBonusPercent) {
            this.displayName = displayName;
            this.color = color;
            this.min = min;
            this.travelMultiplier = travelMultiplier;
            this.bountyBonusPercent = bountyBonusPercent;
        }

        public String displayName() {
            return displayName;
        }

        public String styled() {
            return color + displayName;
        }

        public String color() {
            return color;
        }

        public int min() {
            return min;
        }

        /** Multiplicador do custo nos Waystones (0 = viagem gratuita). */
        public double travelMultiplier() {
            return travelMultiplier;
        }

        /** Bônus, em porcentagem, sobre recompensas recebidas. */
        public int bountyBonusPercent() {
            return bountyBonusPercent;
        }

        public static Tier of(int reputation) {
            Tier result = DESCONHECIDO;
            for (Tier tier : values()) {
                if (reputation >= tier.min) {
                    result = tier;
                }
            }
            return result;
        }

        public Tier next() {
            return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
        }
    }

    public record Outlaw(String name, long since, String previousKingdom) {
    }

    public record Pledge(UUID placer, String placerName, int amount, long time) {
    }

    /** Uma recompensa em aberto, com o nome do alvo (que pode estar offline). */
    public record Wanted(UUID target, String name, int total, List<Pledge> pledges) {
    }

    private final PluginMain plugin;
    private final YamlStore store;
    // lidos também pela thread do chat (assíncrona): mapas concorrentes
    private final Map<UUID, Outlaw> outlaws = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Integer> reputation = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> traitors = new HashMap<>();
    private final Map<UUID, Long> lastSwitch = new HashMap<>();
    private final Map<UUID, String> targetNames = new HashMap<>();
    private final Map<UUID, List<Pledge>> bounties = new HashMap<>();
    /** Evita farmar reputação matando a mesma vítima em sequência: (assassino|vítima) → instante. */
    private final Map<String, Long> killCooldown = new HashMap<>();
    private boolean dirty;

    public MercenaryManager(PluginMain plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "mercenaries.yml");
    }

    // ── Persistência ────────────────────────────────────────────

    public YamlConfiguration read() {
        return store.load();
    }

    public void apply(YamlConfiguration data) {
        outlaws.clear();
        reputation.clear();
        traitors.clear();
        lastSwitch.clear();
        targetNames.clear();
        bounties.clear();
        ConfigurationSection out = data.getConfigurationSection("outlaws");
        if (out != null) {
            for (String key : out.getKeys(false)) {
                try {
                    outlaws.put(UUID.fromString(key), new Outlaw(out.getString(key + ".name", "?"),
                            out.getLong(key + ".since"), out.getString(key + ".previous", "")));
                } catch (IllegalArgumentException ignored) {
                    // entrada inválida: ignora
                }
            }
        }
        readInts(data.getConfigurationSection("reputation"), reputation);
        readLongs(data.getConfigurationSection("traitors"), traitors);
        readLongs(data.getConfigurationSection("last-switch"), lastSwitch);
        ConfigurationSection bounty = data.getConfigurationSection("bounties");
        if (bounty != null) {
            for (String key : bounty.getKeys(false)) {
                try {
                    UUID target = UUID.fromString(key);
                    targetNames.put(target, bounty.getString(key + ".name", "?"));
                    List<Pledge> list = new ArrayList<>();
                    for (String line : bounty.getStringList(key + ".pledges")) {
                        String[] parts = line.split("\\|", 4);
                        list.add(new Pledge(UUID.fromString(parts[0]), parts[3], Integer.parseInt(parts[1]),
                                Long.parseLong(parts[2])));
                    }
                    if (!list.isEmpty()) {
                        bounties.put(target, list);
                    }
                } catch (RuntimeException ignored) {
                    // entrada inválida: ignora
                }
            }
        }
    }

    private static void readInts(ConfigurationSection section, Map<UUID, Integer> into) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                into.put(UUID.fromString(key), section.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // entrada inválida: ignora
            }
        }
    }

    private static void readLongs(ConfigurationSection section, Map<UUID, Long> into) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                into.put(UUID.fromString(key), section.getLong(key));
            } catch (IllegalArgumentException ignored) {
                // entrada inválida: ignora
            }
        }
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration data = new YamlConfiguration();
        outlaws.forEach((id, outlaw) -> {
            data.set("outlaws." + id + ".name", outlaw.name());
            data.set("outlaws." + id + ".since", outlaw.since());
            data.set("outlaws." + id + ".previous", outlaw.previousKingdom());
        });
        reputation.forEach((id, value) -> {
            if (value > 0) {
                data.set("reputation." + id, value);
            }
        });
        long now = System.currentTimeMillis();
        traitors.forEach((id, until) -> {
            if (until > now) {
                data.set("traitors." + id, until);
            }
        });
        lastSwitch.forEach((id, at) -> data.set("last-switch." + id, at));
        bounties.forEach((target, list) -> {
            data.set("bounties." + target + ".name", targetNames.getOrDefault(target, "?"));
            List<String> lines = new ArrayList<>();
            for (Pledge pledge : list) {
                lines.add(pledge.placer() + "|" + pledge.amount() + "|" + pledge.time() + "|" + pledge.placerName());
            }
            data.set("bounties." + target + ".pledges", lines);
        });
        return data;
    }

    public void saveNow() {
        store.saveNow(snapshot());
        dirty = false;
    }

    private void save() {
        dirty = true;
    }

    /** Salva alterações pendentes a cada minuto. */
    public void startTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                dirty = false;
                store.saveAsync(snapshot());
            }
        }, 20L * 60, 20L * 60);
    }

    // ── Fora da lei ─────────────────────────────────────────────

    public boolean isOutlaw(UUID id) {
        return outlaws.containsKey(id);
    }

    public Outlaw outlaw(UUID id) {
        return outlaws.get(id);
    }

    public List<UUID> outlawIds() {
        return new ArrayList<>(outlaws.keySet());
    }

    public void makeOutlaw(UUID id, String name, String previousKingdom) {
        outlaws.put(id, new Outlaw(name, System.currentTimeMillis(), previousKingdom == null ? "" : previousKingdom));
        save();
    }

    public void clearOutlaw(UUID id) {
        if (outlaws.remove(id) != null) {
            save();
        }
    }

    /** Alvo legítimo de caça: fora da lei ou traidor marcado. Matá-lo não é punido. */
    public boolean hunted(UUID id) {
        return isOutlaw(id) || isTraitor(id);
    }

    public void markTraitor(UUID id, int days) {
        traitors.put(id, System.currentTimeMillis() + days * 86_400_000L);
        save();
    }

    public boolean isTraitor(UUID id) {
        Long until = traitors.get(id);
        return until != null && until > System.currentTimeMillis();
    }

    // ── Troca de reino: carência ────────────────────────────────

    /** Milissegundos restantes até o jogador poder trocar de reino de novo. */
    public long cooldownLeft(UUID id) {
        Long last = lastSwitch.get(id);
        if (last == null) {
            return 0;
        }
        long total = plugin.getConfig().getLong("kingdoms.switch-cooldown-hours", 24) * 3_600_000L;
        return Math.max(0, last + total - System.currentTimeMillis());
    }

    public void startCooldown(UUID id) {
        lastSwitch.put(id, System.currentTimeMillis());
        save();
    }

    public static String formatDuration(long millis) {
        long minutes = Math.max(1, millis / 60_000L);
        if (minutes >= 60) {
            return (minutes / 60) + " h " + (minutes % 60) + " min";
        }
        return minutes + " min";
    }

    // ── Reputação ───────────────────────────────────────────────

    public int rep(UUID id) {
        return reputation.getOrDefault(id, 0);
    }

    public Tier tier(UUID id) {
        return Tier.of(rep(id));
    }

    /** Nível que vale na prática: os benefícios só existem enquanto o jogador está fora da lei. */
    public Tier activeTier(UUID id) {
        return isOutlaw(id) ? tier(id) : Tier.DESCONHECIDO;
    }

    public void setRep(UUID id, int value) {
        reputation.put(id, Math.max(0, Math.min(MAX_REP, value)));
        save();
    }

    /** Ajusta a reputação e avisa o jogador, com som e mudança de nível. */
    public void addRep(Player player, int delta, String reason) {
        if (delta == 0) {
            return;
        }
        int before = rep(player.getUniqueId());
        int after = Math.max(0, Math.min(MAX_REP, before + delta));
        if (after == before) {
            return;
        }
        reputation.put(player.getUniqueId(), after);
        save();
        boolean gain = after > before;
        Msg.actionBar(player, (gain ? "<gold>+" : "<red>") + (after - before) + " Reputação <dark_gray>(" + reason
                + ") <gray>" + after + " — " + Tier.of(after).styled());
        player.playSound(player.getLocation(), gain ? Sound.BLOCK_AMETHYST_BLOCK_CHIME : Sound.ENTITY_WITHER_HURT,
                gain ? 0.7f : 0.5f, gain ? 1.4f : 0.7f);
        Tier old = Tier.of(before);
        Tier now = Tier.of(after);
        if (old != now) {
            Msg.send(player, gain ? "<gold>Sua fama cresce: <gray>agora você é " + now.styled() + "<gray>."
                    : "<gray>Sua fama diminui: agora você é " + now.styled() + "<gray>.");
        }
    }

    /** Perda ao morrer: uma porcentagem da reputação. */
    public void punishDeath(Player player) {
        int current = rep(player.getUniqueId());
        if (current <= 0) {
            return;
        }
        int percent = plugin.getConfig().getInt("mercenary.reputation.death-loss-percent", 25);
        int loss = Math.max(1, current * percent / 100);
        addRep(player, -loss, "morte");
    }

    /** Zera a reputação (cabeça cobrada por um caçador). */
    public void wipe(Player player, String reason) {
        int current = rep(player.getUniqueId());
        if (current > 0) {
            addRep(player, -current, reason);
        }
    }

    /** Controle contra repetição: {@code true} se o assassino ainda pode ganhar fama matando esta vítima. */
    public boolean killCounts(UUID killer, UUID victim) {
        long now = System.currentTimeMillis();
        killCooldown.values().removeIf(at -> now - at > 3_600_000L);
        String key = killer + "|" + victim;
        long window = plugin.getConfig().getLong("mercenary.reputation.victim-cooldown-minutes", 10) * 60_000L;
        Long last = killCooldown.get(key);
        if (last != null && now - last < window) {
            return false;
        }
        killCooldown.put(key, now);
        return true;
    }

    // ── Recompensas ─────────────────────────────────────────────

    /** Item usado como moeda das recompensas ({@code bounty.currency}). */
    public Material currency() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("bounty.currency", "EMERALD"));
        return material == null || !material.isItem() ? Material.EMERALD : material;
    }

    /** Nome traduzido da moeda, em MiniMessage. */
    public String currencyName() {
        return "<lang:" + currency().translationKey() + ">";
    }

    /** Quantas unidades da moeda o jogador carrega. */
    public int carried(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == currency() && !item.hasItemMeta()) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /** Retira a moeda do inventário; {@code false} (sem alterar nada) se não houver o suficiente. */
    public boolean take(Player player, int amount) {
        if (carried(player) < amount) {
            return false;
        }
        int left = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != currency() || item.hasItemMeta()) {
                continue;
            }
            int used = Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - used);
            left -= used;
        }
        player.getInventory().setStorageContents(contents);
        return true;
    }

    /** Entrega a moeda; o que não couber cai aos pés do jogador. */
    public void give(Player player, int amount) {
        int left = amount;
        while (left > 0) {
            int stack = Math.min(left, currency().getMaxStackSize());
            left -= stack;
            for (ItemStack extra : player.getInventory().addItem(new ItemStack(currency(), stack)).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
        }
    }

    public int total(UUID target) {
        int total = 0;
        for (Pledge pledge : bounties.getOrDefault(target, List.of())) {
            total += pledge.amount();
        }
        return total;
    }

    public List<Pledge> pledges(UUID target) {
        return bounties.getOrDefault(target, List.of());
    }

    /** Soma uma recompensa; o alvo ganha notoriedade (reputação) proporcional ao valor. */
    public void place(Player placer, UUID target, String targetName, int amount) {
        bounties.computeIfAbsent(target, k -> new ArrayList<>())
                .add(new Pledge(placer.getUniqueId(), placer.getName(), amount, System.currentTimeMillis()));
        targetNames.put(target, targetName);
        save();
        int per = Math.max(1, plugin.getConfig().getInt("bounty.rep-per-units", 8));
        int cap = Math.max(0, plugin.getConfig().getInt("bounty.rep-cap-per-placement", 15));
        int gain = Math.min(cap, amount / per);
        Player online = Bukkit.getPlayer(target);
        if (online != null && gain > 0) {
            addRep(online, gain, "cabeça a prêmio");
        } else if (gain > 0) {
            setRep(target, rep(target) + gain);
        }
    }

    /** Remove e devolve o total pago por uma cabeça; 0 se não havia recompensa. */
    public int claim(UUID target) {
        int total = total(target);
        if (bounties.remove(target) != null) {
            save();
        }
        return total;
    }

    /** Todas as recompensas em aberto, da maior para a menor. */
    public List<Wanted> wanted() {
        List<Wanted> list = new ArrayList<>();
        for (Map.Entry<UUID, List<Pledge>> entry : bounties.entrySet()) {
            list.add(new Wanted(entry.getKey(), targetNames.getOrDefault(entry.getKey(), "?"),
                    total(entry.getKey()), entry.getValue()));
        }
        list.sort(Comparator.comparingInt(Wanted::total).reversed());
        return list;
    }

    /** Nome conhecido de um jogador (online, fora da lei ou com recompensa). */
    public String nameOf(UUID id) {
        Outlaw outlaw = outlaws.get(id);
        if (outlaw != null) {
            return outlaw.name();
        }
        return targetNames.get(id);
    }

    public UUID findOutlaw(String name) {
        for (Map.Entry<UUID, Outlaw> entry : outlaws.entrySet()) {
            if (entry.getValue().name().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        for (Map.Entry<UUID, String> entry : targetNames.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
