package dev.vertexx.fauna;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Breeze;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Squid;
import org.bukkit.entity.Turtle;

/** Criaturas "rejeitadas" da votação de mobs da Mojang que o Vertexx traz para o jogo. */
public enum FaunaKind {
    RASCAL("rascal", "Rascal", "<aqua>", Material.CYAN_WOOL, Cow.class),
    TUFF_GOLEM("tuffgolem", "Golem de Tufo", "<gray>", Material.TUFF, Cow.class),
    MOOBLOOM("moobloom", "Moobloom", "<yellow>", Material.DANDELION, Cow.class),
    GLARE("glare", "Glare", "<green>", Material.MOSS_BLOCK, Parrot.class),
    CRAB("crab", "Caranguejo", "<blue>", Material.MANGROVE_ROOTS, Cow.class),
    PENGUIN("penguin", "Pinguim", "<dark_aqua>", Material.COD, Turtle.class),
    GREAT_HUNGER("greathunger", "Great Hunger", "<dark_green>", Material.COARSE_DIRT, Cow.class),
    BARNACLE("barnacle", "Barnacle", "<dark_aqua>", Material.PRISMARINE, Squid.class),
    VOID_RAY("voidray", "Arraia do Vazio", "<light_purple>", Material.PHANTOM_MEMBRANE, Allay.class),
    END_CRAWLER("endcrawler", "Rastejador do Fim", "<dark_purple>", Material.OBSIDIAN, Silverfish.class),
    SHULKER_SENTINEL("shulkersentinel", "Sentinela de Shulker", "<light_purple>", Material.SHULKER_SHELL, Breeze.class),
    AMETHYST_GOLEM("amethystgolem", "Golem de Ametista", "<light_purple>", Material.AMETHYST_BLOCK, Allay.class);

    private final String key;
    private final String display;
    private final String color;
    private final Material icon;
    private final Class<? extends Mob> base;

    FaunaKind(String key, String display, String color, Material icon, Class<? extends Mob> base) {
        this.key = key;
        this.display = display;
        this.color = color;
        this.icon = icon;
        this.base = base;
    }

    public String key() {
        return key;
    }

    public String display() {
        return display;
    }

    public String color() {
        return color;
    }

    public Material icon() {
        return icon;
    }

    Class<? extends Mob> base() {
        return base;
    }

    public static FaunaKind parse(String text) {
        if (text == null) {
            return null;
        }
        String clean = text.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        for (FaunaKind kind : values()) {
            if (kind.key.equals(clean)) {
                return kind;
            }
        }
        return switch (clean) {
            case "golem" -> TUFF_GOLEM;
            case "caranguejo" -> CRAB;
            case "pinguim" -> PENGUIN;
            case "hunger", "fome" -> GREAT_HUNGER;
            case "monstro", "lula" -> BARNACLE;
            case "arraia", "ray" -> VOID_RAY;
            case "rastejador", "crawler" -> END_CRAWLER;
            case "sentinela", "sentinel" -> SHULKER_SENTINEL;
            case "ametista" -> AMETHYST_GOLEM;
            default -> null;
        };
    }
}
