package dev.vertexx.rpg;

import org.bukkit.Material;

/**
 * Habilidades da árvore. {@code slot} é a posição no menu; {@code requirement} é o nível mínimo do
 * status e {@code parent} a habilidade que precisa estar desbloqueada antes.
 */
public enum Skill {
    HASTE("Mãos Rápidas", Material.GOLDEN_PICKAXE, 10, 3, Stat.MINING, 5, null, false,
            "Quebra blocos mais rápido.", "+8% de velocidade de quebra por nível"),
    AREA_MINER("Pico Largo", Material.DIAMOND_PICKAXE, 11, 2, Stat.MINING, 15, HASTE, true,
            "Ativa a mineração em área com picareta ou pá.",
            "Nv.1: área 2x2 por 20s (recarga 90s)", "Nv.2: área 3x3 por 25s (recarga 75s)"),
    VEIN_MINER("Veeiro", Material.RAW_IRON, 12, 3, Stat.MINING, 10, null, false,
            "Modo do Foco: ligado, quebra o veio de minério inteiro.", "Até 8 / 16 / 32 minérios por veio", "Agachado quebra um bloco só."),
    LUCKY("Garimpeiro", Material.EMERALD, 13, 3, Stat.MINING, 25, VEIN_MINER, false,
            "Chance de dobrar os drops de minérios.", "+8% de chance por nível"),
    CAPITATOR("Machadeiro", Material.DIAMOND_AXE, 15, 2, Stat.WOODCUTTING, 10, null, false,
            "Modo do Foco: ligado, o machado derruba a árvore toda.", "Até 40 / 120 troncos", "Agachado quebra um bloco só."),

    HARVEST("Colheita Farta", Material.DIAMOND_HOE, 19, 3, Stat.FARMING, 10, null, false,
            "Chance de dobrar a colheita madura.", "+10% de chance por nível"),
    REPLANT("Replantio", Material.WHEAT_SEEDS, 20, 1, Stat.FARMING, 5, null, false,
            "Plantações maduras são replantadas sozinhas."),
    STUN("Golpe Atordoante", Material.IRON_SWORD, 22, 3, Stat.COMBAT, 10, null, true,
            "Seu próximo golpe de espada ou machado atordoa o mob.",
            "Atordoa por 2 / 3 / 4s (recarga 20 / 16 / 12s)", "Só afeta mobs, nunca jogadores."),
    FRENZY("Frenesi", Material.BLAZE_POWDER, 23, 2, Stat.COMBAT, 20, STUN, false,
            "Ao derrotar um mob, ganha Velocidade e Força.", "Dura 4 / 6s"),
    MAGNET("Ímã", Material.LODESTONE, 25, 2, Stat.EXPLORATION, 10, null, false,
            "Atrai itens soltos próximos.", "Raio de 4 / 7 blocos"),

    DASH("Investida", Material.FEATHER, 28, 3, Stat.EXPLORATION, 5, null, true,
            "Impulso rápido para a frente.", "Recarga de 9 / 7 / 5s"),
    FEATHER("Pés Leves", Material.LEATHER_BOOTS, 29, 3, Stat.ACROBATICS, 10, null, false,
            "Reduz o dano de queda.", "-20% por nível"),
    THICK("Pele Grossa", Material.IRON_CHESTPLATE, 30, 3, Stat.ENDURANCE, 10, null, false,
            "Mais armadura natural.", "+1 de armadura por nível"),
    VITALITY("Vitalidade", Material.GOLDEN_APPLE, 31, 3, Stat.ENDURANCE, 20, THICK, false,
            "Mais vida máxima.", "+2 de vida por nível"),
    RESTOCK("Reposição", Material.CHEST, 33, 1, Stat.EXCAVATION, 5, null, false,
            "Ao acabar os blocos na mão, repõe do inventário."),
    REACH("Alcance do Construtor", Material.SCAFFOLDING, 34, 3, Stat.EXCAVATION, 10, RESTOCK, false,
            "Alcança blocos mais distantes.", "+1 bloco de alcance por nível");

    private final String display;
    private final Material icon;
    private final int slot;
    private final int maxRank;
    private final Stat stat;
    private final int requiredLevel;
    private final Skill parent;
    private final boolean active;
    private final String description;
    private final String[] details;

    Skill(String display, Material icon, int slot, int maxRank, Stat stat, int requiredLevel, Skill parent,
          boolean active, String description, String... details) {
        this.display = display;
        this.icon = icon;
        this.slot = slot;
        this.maxRank = maxRank;
        this.stat = stat;
        this.requiredLevel = requiredLevel;
        this.parent = parent;
        this.active = active;
        this.description = description;
        this.details = details;
    }

    public String display() {
        return display;
    }

    public Material icon() {
        return icon;
    }

    public int slot() {
        return slot;
    }

    public int maxRank() {
        return maxRank;
    }

    public Stat stat() {
        return stat;
    }

    public int requiredLevel() {
        return requiredLevel;
    }

    public Skill parent() {
        return parent;
    }

    public boolean active() {
        return active;
    }

    /** Modo que se liga e desliga pelo Foco de Habilidades (em vez de agachar para quebrar). */
    public boolean toggle() {
        return this == VEIN_MINER || this == CAPITATOR;
    }

    /** Aparece no Foco de Habilidades: ativas e modos. */
    public boolean usable() {
        return active || toggle();
    }

    public String description() {
        return description;
    }

    public String[] details() {
        return details;
    }
}
