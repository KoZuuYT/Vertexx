package dev.vertexx.rpg;

import java.util.EnumMap;
import java.util.Map;

/** Progresso de um jogador: nível geral, pontos, status e árvore de habilidades. */
public final class PlayerData {

    public String name = "";
    public int level = 1;
    public double xp;
    public int points;
    public Skill selected;
    public final int[] statLevel = new int[Stat.values().length];
    public final double[] statXp = new double[Stat.values().length];
    public final Map<Skill, Integer> skills = new EnumMap<>(Skill.class);

    public PlayerData() {
        java.util.Arrays.fill(statLevel, 1);
    }

    public int level(Stat stat) {
        return statLevel[stat.ordinal()];
    }

    public double xp(Stat stat) {
        return statXp[stat.ordinal()];
    }

    public int rank(Skill skill) {
        return skills.getOrDefault(skill, 0);
    }

    /** XP necessário para sair do nível {@code level} de um status. */
    public static double statNeed(int level) {
        return 40 + 10.0 * level + 2.2 * level * level;
    }

    /** XP necessário para sair do nível geral {@code level}. */
    public static double generalNeed(int level) {
        return 150 + 40.0 * level + 6.0 * level * level;
    }

    /** Pontos de habilidade ganhos ao alcançar o nível geral {@code level}. */
    public static int pointsFor(int level) {
        return level % 5 == 0 ? 2 : 1;
    }
}
