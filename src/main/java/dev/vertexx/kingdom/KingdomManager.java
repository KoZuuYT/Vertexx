package dev.vertexx.kingdom;

import dev.vertexx.core.YamlStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Membros, cargos e alianças dos reinos, persistidos em {@code kingdoms.yml}. */
public final class KingdomManager {

    public record Member(Kingdom kingdom, Rank rank, String name) {
    }

    public enum ProposalResult {
        ALREADY_ALLIED, FORMED, PROPOSED, ALREADY_PROPOSED
    }

    private final YamlStore store;
    private final Map<UUID, Member> members = new ConcurrentHashMap<>();
    private final Set<String> alliances = new HashSet<>();
    private final Set<String> proposals = new HashSet<>();

    public KingdomManager(Plugin plugin) {
        this.store = new YamlStore(plugin, "kingdoms.yml");
    }

    // ── Persistência ────────────────────────────────────────────

    /** Leitura do disco: chame fora da thread principal. */
    public YamlConfiguration read() {
        return store.load();
    }

    /** Aplica os dados lidos: chame na thread principal. */
    public void apply(YamlConfiguration data) {
        members.clear();
        alliances.clear();
        proposals.clear();
        ConfigurationSection section = data.getConfigurationSection("members");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    Kingdom kingdom = Kingdom.byId(section.getString(key + ".kingdom"));
                    Rank rank = Rank.parse(section.getString(key + ".rank"));
                    if (kingdom != null) {
                        members.put(id, new Member(kingdom, rank == null ? Rank.MEMBRO : rank,
                                section.getString(key + ".name", "?")));
                    }
                } catch (IllegalArgumentException ignored) {
                    // entrada inválida no arquivo: ignora
                }
            }
        }
        alliances.addAll(data.getStringList("alliances"));
        proposals.addAll(data.getStringList("proposals"));
    }

    public void save() {
        store.saveAsync(snapshot());
    }

    public void saveNow() {
        store.saveNow(snapshot());
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, Member> entry : members.entrySet()) {
            String base = "members." + entry.getKey();
            data.set(base + ".kingdom", entry.getValue().kingdom().id());
            data.set(base + ".rank", entry.getValue().rank().name());
            data.set(base + ".name", entry.getValue().name());
        }
        data.set("alliances", new ArrayList<>(alliances));
        data.set("proposals", new ArrayList<>(proposals));
        return data;
    }

    // ── Membros ─────────────────────────────────────────────────

    public Member member(UUID id) {
        return members.get(id);
    }

    public Kingdom kingdomOf(UUID id) {
        Member member = members.get(id);
        return member == null ? null : member.kingdom();
    }

    public Rank rankOf(UUID id) {
        Member member = members.get(id);
        return member == null ? null : member.rank();
    }

    /** Coloca o jogador no reino com o cargo inicial de Membro. */
    public void assign(Player player, Kingdom kingdom) {
        set(player.getUniqueId(), player.getName(), kingdom, Rank.MEMBRO);
    }

    public void set(UUID id, String name, Kingdom kingdom, Rank rank) {
        members.put(id, new Member(kingdom, rank, name));
        save();
    }

    /** Mantém o nome salvo em dia quando o jogador troca de nick. */
    public void refreshName(Player player) {
        Member member = members.get(player.getUniqueId());
        if (member != null && !member.name().equals(player.getName())) {
            members.put(player.getUniqueId(), new Member(member.kingdom(), member.rank(), player.getName()));
            save();
        }
    }

    public UUID findByName(String name) {
        for (Map.Entry<UUID, Member> entry : members.entrySet()) {
            if (entry.getValue().name().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Map<UUID, Member> membersOf(Kingdom kingdom) {
        Map<UUID, Member> result = new HashMap<>();
        for (Map.Entry<UUID, Member> entry : members.entrySet()) {
            if (entry.getValue().kingdom() == kingdom) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    public int count(Kingdom kingdom) {
        int total = 0;
        for (Member member : members.values()) {
            if (member.kingdom() == kingdom) {
                total++;
            }
        }
        return total;
    }

    // ── Alianças ────────────────────────────────────────────────

    private static String pair(Kingdom a, Kingdom b) {
        return a.id().compareTo(b.id()) < 0 ? a.id() + "|" + b.id() : b.id() + "|" + a.id();
    }

    public boolean allied(Kingdom a, Kingdom b) {
        return a != b && alliances.contains(pair(a, b));
    }

    public Set<Kingdom> alliesOf(Kingdom kingdom) {
        Set<Kingdom> result = EnumSet.noneOf(Kingdom.class);
        for (Kingdom other : Kingdom.values()) {
            if (allied(kingdom, other)) {
                result.add(other);
            }
        }
        return result;
    }

    /** Mesmo reino ou reinos aliados: fogo amigo desativado entre eles. */
    public boolean friendly(UUID a, UUID b) {
        Kingdom ka = kingdomOf(a);
        Kingdom kb = kingdomOf(b);
        return ka != null && kb != null && (ka == kb || allied(ka, kb));
    }

    /** Propõe aliança; se o outro reino já tinha proposto, a aliança é firmada. */
    public ProposalResult propose(Kingdom from, Kingdom to) {
        if (allied(from, to)) {
            return ProposalResult.ALREADY_ALLIED;
        }
        if (proposals.remove(to.id() + ">" + from.id())) {
            alliances.add(pair(from, to));
            save();
            return ProposalResult.FORMED;
        }
        if (!proposals.add(from.id() + ">" + to.id())) {
            return ProposalResult.ALREADY_PROPOSED;
        }
        save();
        return ProposalResult.PROPOSED;
    }

    public boolean breakAlliance(Kingdom a, Kingdom b) {
        boolean removed = alliances.remove(pair(a, b));
        boolean withdrawn = proposals.remove(a.id() + ">" + b.id());
        if (removed || withdrawn) {
            save();
        }
        return removed;
    }

    public List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (Kingdom kingdom : Kingdom.values()) {
            ids.add(kingdom.id());
        }
        return Collections.unmodifiableList(ids);
    }
}
