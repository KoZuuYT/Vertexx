package dev.vertexx.waystone;

import java.util.Locale;
import org.bukkit.Material;

/** Aparência do corpo do Waystone: cada skin troca os blocos do pilar, do beiral e da base. */
public enum WaystoneSkin {

    STONE_BRICKS("Tijolos de pedra", Material.CHISELED_STONE_BRICKS, Material.STONE_BRICKS,
            Material.STONE_BRICKS, Material.SMOOTH_STONE, Material.STONE_BRICKS),
    SMOOTH_STONE("Pedra lisa", Material.SMOOTH_STONE, Material.SMOOTH_STONE,
            Material.SMOOTH_STONE, Material.STONE, Material.SMOOTH_STONE),
    STONE("Pedra", Material.STONE, Material.STONE,
            Material.COBBLESTONE, Material.STONE, Material.STONE),
    END_STONE("Endstone", Material.END_STONE_BRICKS, Material.END_STONE,
            Material.END_STONE_BRICKS, Material.END_STONE, Material.END_STONE_BRICKS),
    PURPUR("Tijolos de purpur", Material.PURPUR_PILLAR, Material.PURPUR_BLOCK,
            Material.PURPUR_BLOCK, Material.PURPUR_BLOCK, Material.PURPUR_PILLAR),
    DIORITE("Diorito", Material.POLISHED_DIORITE, Material.DIORITE,
            Material.DIORITE, Material.POLISHED_DIORITE, Material.POLISHED_DIORITE),
    ANDESITE("Andesito", Material.POLISHED_ANDESITE, Material.ANDESITE,
            Material.ANDESITE, Material.POLISHED_ANDESITE, Material.POLISHED_ANDESITE),
    BRICKS("Tijolos de barro", Material.BRICKS, Material.BRICKS,
            Material.BRICKS, Material.BRICKS, Material.BRICKS);

    public static final WaystoneSkin DEFAULT = STONE_BRICKS;

    private final String label;
    private final Material pillar;
    private final Material ledge;
    private final Material plate;
    private final Material rimInner;
    private final Material rim;

    WaystoneSkin(String label, Material pillar, Material ledge, Material plate, Material rimInner, Material rim) {
        this.label = label;
        this.pillar = pillar;
        this.ledge = ledge;
        this.plate = plate;
        this.rimInner = rimInner;
        this.rim = rim;
    }

    public String label() {
        return label;
    }

    /** Bloco do pilar central. */
    public Material pillar() {
        return pillar;
    }

    /** Bloco do beiral superior. */
    public Material ledge() {
        return ledge;
    }

    /** Bloco da placa de chão. */
    public Material plate() {
        return plate;
    }

    /** Bloco do piso rebaixado dentro do beiral. */
    public Material floor() {
        return rimInner;
    }

    /** Bloco da borda do beiral. */
    public Material rim() {
        return rim;
    }

    public static WaystoneSkin parse(String name) {
        if (name == null) {
            return DEFAULT;
        }
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }
}
