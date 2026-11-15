package dev.vertexx.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Autocomplete: filtra sugestões pelo que já foi digitado. */
public final class Tab {

    private Tab() {
    }

    public static List<String> of(String typed, Collection<String> options) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(option);
            }
        }
        return result;
    }

    public static List<String> of(String typed, String... options) {
        return of(typed, List.of(options));
    }

    public static List<String> players(String typed) {
        return of(typed, Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
    }

    /** Nomes de enum em minúsculas, como o jogador costuma digitar. */
    public static <E extends Enum<E>> List<String> names(String typed, E[] values) {
        List<String> all = new ArrayList<>(values.length);
        for (E value : values) {
            all.add(value.name().toLowerCase(Locale.ROOT));
        }
        return of(typed, all);
    }
}
