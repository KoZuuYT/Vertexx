package dev.vertexx.kingdom;

import java.util.Locale;

/** Cargos dentro de um reino. Todo jogador começa como Membro. */
public enum Rank {

    MEMBRO("Membro"),
    OFICIAL("Oficial"),
    LIDER("Líder");

    private final String displayName;

    Rank(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static Rank parse(String text) {
        if (text == null) {
            return null;
        }
        try {
            return valueOf(text.toUpperCase(Locale.ROOT).replace('Í', 'I'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
