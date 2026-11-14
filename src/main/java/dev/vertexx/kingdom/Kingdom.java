package dev.vertexx.kingdom;

import dev.vertexx.core.Msg;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Material;

/**
 * Os 3 reinos fixos do servidor. Os nomes de exibição são definidos pela comunidade
 * e devem ser mantidos exatamente como estão.
 */
public enum Kingdom {

    ENTERNAIS(
            "enternais", "Enternais", "✦", "#c4a7ff", "#7c4dff",
            Material.END_CRYSTAL, Material.PURPLE_STAINED_GLASS_PANE,
            List.of(
                    "<gray>Guardiões do que não se apaga.",
                    "<gray>Erguem obras feitas para durar",
                    "<gray>mais do que os próprios construtores.")),

    AURUDOS(
            "aurudos", "Reino dos aurudos+67+ego", "♛", "#ffe082", "#ff8f00",
            Material.TOTEM_OF_UNDYING, Material.YELLOW_STAINED_GLASS_PANE,
            List.of(
                    "<gray>Uma corte movida a presença.",
                    "<gray>Aqui a aura é medida, o ego é",
                    "<gray>estandarte e a glória é dever.")),

    CAFETOES(
            "cafetoes", "cafetões", "♠", "#ff8a80", "#c62828",
            Material.EMERALD, Material.RED_STAINED_GLASS_PANE,
            List.of(
                    "<gray>Senhores do comércio e da influência.",
                    "<gray>Todo acordo tem um preço, e todo",
                    "<gray>preço passa primeiro por eles."));

    private final String id;
    private final String displayName;
    private final String symbol;
    private final String colorFrom;
    private final String colorTo;
    private final Material icon;
    private final Material pane;
    private final List<String> description;

    Kingdom(String id, String displayName, String symbol, String colorFrom, String colorTo,
            Material icon, Material pane, List<String> description) {
        this.id = id;
        this.displayName = displayName;
        this.symbol = symbol;
        this.colorFrom = colorFrom;
        this.colorTo = colorTo;
        this.icon = icon;
        this.pane = pane;
        this.description = description;
    }

    public String id() {
        return id;
    }

    /** Nome oficial, sem formatação. */
    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public Material pane() {
        return pane;
    }

    public List<String> description() {
        return description;
    }

    public String color() {
        return colorFrom;
    }

    /** Nome oficial em MiniMessage, com o gradiente do reino. O nome não contém tags. */
    public String styled() {
        return "<gradient:" + colorFrom + ":" + colorTo + ">" + displayName + "</gradient>";
    }

    /** Símbolo colorido com o nome do reino ao passar o mouse. */
    public Component tag() {
        return Msg.mm("<" + colorFrom + ">" + symbol + "</" + colorFrom + ">")
                .hoverEvent(HoverEvent.showText(Msg.mm(styled())));
    }

    public static Kingdom byId(String id) {
        if (id == null) {
            return null;
        }
        String key = id.toLowerCase(Locale.ROOT);
        for (Kingdom kingdom : values()) {
            if (kingdom.id.equals(key)) {
                return kingdom;
            }
        }
        return null;
    }
}
