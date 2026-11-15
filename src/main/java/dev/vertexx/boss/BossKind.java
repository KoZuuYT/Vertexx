package dev.vertexx.boss;

import java.util.Locale;
import net.kyori.adventure.bossbar.BossBar;

/** Bosses do Vertexx. Cada um tem a sua sala (estrutura própria) e seus ataques. */
public enum BossKind {
    WILDFIRE("Wildfire", "<gold>", BossBar.Color.RED, "Câmara do Wildfire"),
    ICEOLOGER("Iceologer", "<aqua>", BossBar.Color.BLUE, "Castelo de Gelo");

    private final String display;
    private final String color;
    private final BossBar.Color barColor;
    private final String place;

    BossKind(String display, String color, BossBar.Color barColor, String place) {
        this.display = display;
        this.color = color;
        this.barColor = barColor;
        this.place = place;
    }

    public String display() {
        return display;
    }

    public String color() {
        return color;
    }

    public BossBar.Color barColor() {
        return barColor;
    }

    /** Nome da estrutura onde o boss vive. */
    public String place() {
        return place;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static BossKind parse(String text) {
        if (text == null) {
            return null;
        }
        for (BossKind kind : values()) {
            if (kind.key().equalsIgnoreCase(text)) {
                return kind;
            }
        }
        return null;
    }
}
