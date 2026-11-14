package dev.vertexx.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Atalhos de MiniMessage usados por todo o plugin. */
public final class Msg {

    public static final String PREFIX =
            "<gradient:#7f5af0:#2cb67d><bold>Vertexx</bold></gradient> <dark_gray>»</dark_gray> ";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Msg() {
    }

    public static Component mm(String text, TagResolver... resolvers) {
        return MM.deserialize(text, resolvers);
    }

    /** Igual a {@link #mm}, mas sem o itálico padrão de nomes/lore de itens. */
    public static Component item(String text, TagResolver... resolvers) {
        return mm(text, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static void send(CommandSender to, String text, TagResolver... resolvers) {
        to.sendMessage(mm(PREFIX + text, resolvers));
    }

    public static void actionBar(Player to, String text, TagResolver... resolvers) {
        to.sendActionBar(mm(text, resolvers));
    }

    /** Escapa tags MiniMessage de textos vindos de jogadores. */
    public static String esc(String text) {
        return MM.escapeTags(text);
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
