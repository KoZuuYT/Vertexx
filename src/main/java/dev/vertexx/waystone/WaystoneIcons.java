package dev.vertexx.waystone;

import java.util.List;
import org.bukkit.Material;

/** Ícones que o dono pode flutuar sobre o Waystone. */
public final class WaystoneIcons {

    public static final Material DEFAULT = Material.ENDER_EYE;

    public static final List<Material> ALL = List.of(
            Material.ENDER_EYE, Material.NETHER_STAR, Material.AMETHYST_SHARD, Material.ECHO_SHARD,
            Material.HEART_OF_THE_SEA, Material.TOTEM_OF_UNDYING, Material.DIAMOND, Material.EMERALD,
            Material.RECOVERY_COMPASS, Material.NAUTILUS_SHELL, Material.TRIDENT, Material.GOLDEN_APPLE,
            Material.ENDER_PEARL, Material.RESIN_CLUMP, Material.DRAGON_HEAD, Material.WITHER_SKELETON_SKULL,
            Material.BELL, Material.CLOCK);

    private WaystoneIcons() {
    }

    public static Material parse(String name) {
        Material material = name == null ? null : Material.matchMaterial(name);
        return material != null && ALL.contains(material) ? material : DEFAULT;
    }
}
