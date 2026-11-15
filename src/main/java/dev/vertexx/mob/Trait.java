package dev.vertexx.mob;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Traços especiais de mobs. Os de elite só aparecem em versões elite. */
public enum Trait {
    SWIFT("Veloz", "⚡", "<aqua>", false, "Move-se 25% mais rápido."),
    TOUGH("Robusto", "♥", "<red>", false, "Tem 30% mais vida."),
    BRUTE("Bruto", "⚔", "<dark_red>", false, "Causa 25% mais dano."),
    ARMORED("Blindado", "▣", "<gray>", false, "Tem +4 de armadura."),
    VAMPIRIC("Vampírico", "☽", "<dark_purple>", false, "Cura 40% do dano que causa."),
    THORNY("Espinhoso", "✱", "<green>", false, "Reflete 20% do dano corpo a corpo recebido."),
    VENOMOUS("Peçonhento", "☣", "<dark_green>", false, "Seus golpes envenenam."),
    FROSTY("Gélido", "❄", "<blue>", false, "Seus golpes causam lentidão."),
    BLAZING("Flamejante", "♨", "<gold>", false, "Seus golpes incendeiam."),
    REGENERATING("Regenerador", "✚", "<light_purple>", false, "Recupera vida aos poucos."),
    VOLATILE("Explosivo", "✹", "<yellow>", false, "Explode ao morrer, sem destruir blocos."),
    BLINKER("Fantasma", "◌", "<white>", false, "Ao ser atingido pode se teletransportar para perto de você."),

    TITAN("Titã", "♛", "<gold>", true, "Tem 60% mais vida e não sofre empurrão."),
    FURIOUS("Furioso", "☠", "<dark_red>", true, "Abaixo de 50% de vida ganha velocidade e 40% de dano."),
    WARDEN("Guardião", "◆", "<aqua>", true, "Recebe 25% menos dano."),
    STORMCALLER("Tempestuoso", "☈", "<yellow>", true, "Seus golpes chamam raios (dano extra, sem fogo)."),
    SANGUINE("Sanguinário", "☥", "<red>", true, "Cura 80% do dano que causa e causa 15% mais dano.");

    private final String display;
    private final String symbol;
    private final String color;
    private final boolean elite;
    private final String description;

    Trait(String display, String symbol, String color, boolean elite, String description) {
        this.display = display;
        this.symbol = symbol;
        this.color = color;
        this.elite = elite;
        this.description = description;
    }

    public String display() {
        return display;
    }

    public String symbol() {
        return symbol;
    }

    /** Tag MiniMessage de abertura da cor. */
    public String color() {
        return color;
    }

    public boolean elite() {
        return elite;
    }

    public String description() {
        return description;
    }

    public String colored() {
        return color + display;
    }

    public static List<Trait> normal() {
        return Arrays.stream(values()).filter(t -> !t.elite).toList();
    }

    public static List<Trait> eliteOnly() {
        return Arrays.stream(values()).filter(t -> t.elite).toList();
    }

    public static Trait parse(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
