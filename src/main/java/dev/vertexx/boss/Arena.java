package dev.vertexx.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Sala de um boss: posição, forma, portão de selo e estado de renascimento. */
public final class Arena {

    final String id;
    final BossKind kind;
    final String world;
    final int cx;
    final int fy;
    final int cz;
    /** Direção da entrada (um de {@code (0,1)}, {@code (1,0)}, {@code (0,-1)}, {@code (-1,0)}). */
    final int fx;
    final int fz;
    final int half;
    final int height;
    final List<int[]> seals = new ArrayList<>();
    Material sealMaterial = Material.BLUE_ICE;
    /** Instante (ms) em que o boss volta; 0 = disponível. */
    long readyAt;
    int kills;
    /** Arena provisória criada por comando de teste: não é salva. */
    final boolean temp;
    boolean sealed;
    /** Altar de ativação (blocos), quando a sala tem um. */
    int[] altar;
    /** Portão do salão dos guardiões (Cidadela Soterrada): fecha enquanto os guardiões vivem. */
    final List<int[]> gate = new ArrayList<>();

    Arena(String id, BossKind kind, String world, int cx, int fy, int cz, int fx, int fz, int half, int height,
          boolean temp) {
        this.id = id;
        this.kind = kind;
        this.world = world;
        this.cx = cx;
        this.fy = fy;
        this.cz = cz;
        this.fx = fx;
        this.fz = fz;
        this.half = half;
        this.height = height;
        this.temp = temp;
    }

    static Arena create(BossKind kind, World world, int cx, int fy, int cz, int fx, int fz, boolean temp) {
        int half = kind == BossKind.VOID_SCARAB ? 25 : kind == BossKind.PARCHED_TITAN ? 17 : kind == BossKind.ROOT_SOVEREIGN ? 18 : kind == BossKind.WILDFIRE ? 13 : 9;
        int height = kind == BossKind.VOID_SCARAB ? 38 : kind == BossKind.PARCHED_TITAN ? 16 : kind == BossKind.ROOT_SOVEREIGN ? 18 : kind == BossKind.WILDFIRE ? 20 : 16;
        return new Arena(UUID.randomUUID().toString().substring(0, 8), kind, world.getName(), cx, fy, cz, fx, fz,
                half, height, temp);
    }

    World bukkitWorld() {
        return Bukkit.getWorld(world);
    }

    Location center() {
        World w = bukkitWorld();
        return w == null ? null : new Location(w, cx + 0.5, fy, cz + 0.5);
    }

    boolean ready(long now) {
        return readyAt <= now;
    }

    /** Dentro do volume da sala (a câmara do Wildfire é circular; o castelo é quadrado). */
    boolean contains(Location at, double pad) {
        if (!at.getWorld().getName().equals(world)) {
            return false;
        }
        double dx = at.getX() - (cx + 0.5);
        double dz = at.getZ() - (cz + 0.5);
        double limit = half + pad;
        boolean flat = kind.round() ? dx * dx + dz * dz <= limit * limit
                : Math.abs(dx) <= limit && Math.abs(dz) <= limit;
        return flat && at.getY() >= fy - 2 - pad && at.getY() <= fy + height + pad;
    }

    boolean contains(Player player) {
        return contains(player.getLocation(), 0);
    }

    double distanceSquared(Location at) {
        double dx = at.getX() - (cx + 0.5);
        double dz = at.getZ() - (cz + 0.5);
        return dx * dx + dz * dz;
    }

    void save(YamlConfiguration yaml, String path) {
        yaml.set(path + ".kind", kind.key());
        yaml.set(path + ".world", world);
        yaml.set(path + ".x", cx);
        yaml.set(path + ".y", fy);
        yaml.set(path + ".z", cz);
        yaml.set(path + ".fx", fx);
        yaml.set(path + ".fz", fz);
        yaml.set(path + ".half", half);
        yaml.set(path + ".height", height);
        yaml.set(path + ".ready-at", readyAt);
        yaml.set(path + ".kills", kills);
        yaml.set(path + ".sealed", sealed);
        yaml.set(path + ".seal-material", sealMaterial.name());
        List<String> list = new ArrayList<>();
        for (int[] pos : seals) {
            list.add(pos[0] + "," + pos[1] + "," + pos[2]);
        }
        yaml.set(path + ".seals", list);
        List<String> gates = new ArrayList<>();
        for (int[] pos : gate) {
            gates.add(pos[0] + "," + pos[1] + "," + pos[2]);
        }
        yaml.set(path + ".gate", gates);
        if (altar != null) {
            yaml.set(path + ".altar", altar[0] + "," + altar[1] + "," + altar[2]);
        }
    }

    static Arena load(String id, ConfigurationSection section) {
        BossKind kind = BossKind.parse(section.getString("kind"));
        if (kind == null || section.getString("world") == null) {
            return null;
        }
        Arena arena = new Arena(id, kind, section.getString("world"), section.getInt("x"), section.getInt("y"),
                section.getInt("z"), section.getInt("fx", 0), section.getInt("fz", 1), section.getInt("half", 9),
                section.getInt("height", 16), false);
        arena.readyAt = section.getLong("ready-at");
        arena.kills = section.getInt("kills");
        arena.sealed = section.getBoolean("sealed");
        Material material = Material.matchMaterial(section.getString("seal-material", "BLUE_ICE"));
        arena.sealMaterial = material == null ? Material.BLUE_ICE : material;
        String altarText = section.getString("altar");
        if (altarText != null && altarText.split(",").length == 3) {
            String[] a = altarText.split(",");
            try {
                arena.altar = new int[] {Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2])};
            } catch (NumberFormatException ignored) {
                arena.altar = null;
            }
        }
        for (String text : section.getStringList("seals")) {
            String[] parts = text.split(",");
            if (parts.length == 3) {
                try {
                    arena.seals.add(new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])});
                } catch (NumberFormatException ignored) {
                    // posição inválida: ignora
                }
            }
        }
        for (String text : section.getStringList("gate")) {
            String[] parts = text.split(",");
            if (parts.length == 3) {
                try {
                    arena.gate.add(new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])});
                } catch (NumberFormatException ignored) {
                    // posição inválida: ignora
                }
            }
        }
        return arena;
    }
}
