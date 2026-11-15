package dev.vertexx.rpg;

import org.bukkit.Material;

/** Classificação de minérios e plantações usada pelos status e habilidades. */
final class Ores {

    private Ores() {
    }

    static boolean isOre(Material material) {
        return material == Material.ANCIENT_DEBRIS || material.name().endsWith("_ORE");
    }

    /** Variantes de pedra e ardósia do mesmo minério pertencem ao mesmo veio. */
    static String group(Material material) {
        return material.name().replace("DEEPSLATE_", "");
    }

    static double oreXp(Material material) {
        return switch (group(material)) {
            case "COAL_ORE", "COPPER_ORE", "NETHER_QUARTZ_ORE", "NETHER_GOLD_ORE" -> 4;
            case "REDSTONE_ORE" -> 5;
            case "LAPIS_ORE" -> 6;
            case "IRON_ORE" -> 7;
            case "GOLD_ORE" -> 9;
            case "EMERALD_ORE" -> 22;
            case "DIAMOND_ORE" -> 25;
            case "ANCIENT_DEBRIS" -> 45;
            default -> 3;
        };
    }

    /** XP de colheita de plantações que só valem quando maduras (ou de melões e abóboras). */
    static double cropXp(Material material) {
        return switch (material) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, COCOA -> 3;
            case NETHER_WART -> 4;
            case SWEET_BERRY_BUSH -> 2;
            case MELON, PUMPKIN -> 3;
            default -> 0;
        };
    }
}
