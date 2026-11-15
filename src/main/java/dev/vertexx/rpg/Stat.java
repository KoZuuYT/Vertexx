package dev.vertexx.rpg;

import org.bukkit.Material;

/** Status de atividade do jogador. Cada um sobe fazendo a atividade correspondente. */
public enum Stat {
    MINING("Mineração", Material.DIAMOND_PICKAXE, "<aqua>", "Quebrar pedra e minérios",
            "+0,4% de velocidade de quebra por nível"),
    EXCAVATION("Escavação", Material.IRON_SHOVEL, "<gold>", "Cavar terra, areia, cascalho e argila",
            "+0,4% de velocidade de quebra por nível"),
    WOODCUTTING("Lenhador", Material.IRON_AXE, "<green>", "Cortar troncos",
            "+0,4% de velocidade de quebra por nível"),
    FARMING("Agricultura", Material.WHEAT, "<yellow>", "Colher plantações maduras",
            "Libera habilidades da lavoura"),
    COMBAT("Luta", Material.IRON_SWORD, "<red>", "Derrotar mobs de perto",
            "+0,4% de dano de ataque por nível"),
    ARCHERY("Arquearia", Material.BOW, "<light_purple>", "Derrotar mobs com arco, besta ou tridente",
            "+0,5% de dano de projéteis por nível"),
    ENDURANCE("Resistência", Material.IRON_CHESTPLATE, "<dark_red>", "Receber dano e correr",
            "+0,1 de vida máxima por nível"),
    ACROBATICS("Acrobacia", Material.FEATHER, "<white>", "Sofrer quedas",
            "+0,04 bloco de queda segura por nível"),
    FISHING("Pesca", Material.FISHING_ROD, "<blue>", "Pescar",
            "+0,05 de sorte por nível"),
    EXPLORATION("Exploração", Material.COMPASS, "<dark_aqua>", "Percorrer distâncias a pé",
            "+0,1% de velocidade por nível");

    private final String display;
    private final Material icon;
    private final String color;
    private final String activity;
    private final String passive;

    Stat(String display, Material icon, String color, String activity, String passive) {
        this.display = display;
        this.icon = icon;
        this.color = color;
        this.activity = activity;
        this.passive = passive;
    }

    public String display() {
        return display;
    }

    public Material icon() {
        return icon;
    }

    public String color() {
        return color;
    }

    public String colored() {
        return color + display;
    }

    public String activity() {
        return activity;
    }

    public String passive() {
        return passive;
    }
}
