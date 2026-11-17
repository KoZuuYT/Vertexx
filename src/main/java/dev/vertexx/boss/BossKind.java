package dev.vertexx.boss;

import java.util.Locale;
import net.kyori.adventure.bossbar.BossBar;

/** Bosses do Vertexx. Cada um tem a sua sala (estrutura própria) e seus ataques. */
public enum BossKind {
    WILDFIRE("Wildfire", "<gold>", BossBar.Color.RED, "Câmara do Wildfire"),
    ICEOLOGER("Iceologer", "<aqua>", BossBar.Color.BLUE, "Castelo de Gelo"),
    VOID_SCARAB("Void-Scarab", "<dark_purple>", BossBar.Color.PURPLE, "Pináculo de Quitina"),
    PARCHED_TITAN("Parched Titan", "<gold>", BossBar.Color.YELLOW, "Cidadela Soterrada"),
    ROOT_SOVEREIGN("Root Sovereign", "<green>", BossBar.Color.GREEN, "Gruta dos Esporos Ancestrais"),
    GLOOM_STALKER("Gloom Stalker", "<dark_aqua>", BossBar.Color.BLUE, "Covil Ressonante");

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

    /** Salas circulares (Wildfire e Void-Scarab); o castelo é quadrado. */
    public boolean round() {
        return this != ICEOLOGER;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static BossKind parse(String text) {
        if (text == null) {
            return null;
        }
        for (BossKind kind : values()) {
            if (kind.key().equalsIgnoreCase(text) || kind.key().replace("_", "").equalsIgnoreCase(text)
                    || kind == VOID_SCARAB && text.equalsIgnoreCase("scarab")
                    || kind == PARCHED_TITAN && (text.equalsIgnoreCase("titan") || text.equalsIgnoreCase("parched"))
                    || kind == ROOT_SOVEREIGN && (text.equalsIgnoreCase("sovereign") || text.equalsIgnoreCase("root"))
                    || kind == GLOOM_STALKER && (text.equalsIgnoreCase("stalker") || text.equalsIgnoreCase("gloom"))) {
                return kind;
            }
        }
        return null;
    }
}
