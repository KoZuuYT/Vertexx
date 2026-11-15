package dev.vertexx.intro;

import dev.vertexx.core.Msg;
import dev.vertexx.kingdom.Kingdom;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.persistence.PersistentDataType;

/** Nomes e recursos compartilhados entre o resource pack (gerado em tools/pack) e o código da cerimônia. */
final class IntroAssets {

    /** Fonte do pack com sobreposições de tela cheia, emblemas e ícones de teclas. */
    static final Key FONT = Key.key("vertexx", "cinematic");
    private static final String OVERLAY = "";
    private static final char EMBLEM_BASE = '';
    private static final String KEY_LMB = "";
    private static final String KEY_RMB = "";
    private static final String KEY_SHIFT = "";

    private IntroAssets() {
    }

    /** Tela cheia na cor dada: o fade do título controla a opacidade (preto, flash, tinta do reino). */
    static Component overlay(TextColor color) {
        return Component.text(OVERLAY, color).font(FONT);
    }

    static Component emblem(Kingdom kingdom) {
        return Component.text(String.valueOf((char) (EMBLEM_BASE + kingdom.ordinal()))).font(FONT);
    }

    static TextColor color(Kingdom kingdom) {
        return TextColor.fromHexString(kingdom.color());
    }

    static Color dust(Kingdom kingdom) {
        TextColor c = color(kingdom);
        return Color.fromRGB(c.red(), c.green(), c.blue());
    }

    /** Dica fixa no action bar: ícones de mouse e a fileira de emblemas com o reino atual aceso. */
    static Component hint(Kingdom selected, boolean pack) {
        if (!pack) {
            return Msg.mm("<gray>◀ <white>Botão esquerdo</white>   <dark_gray>|</dark_gray>   <white>Botão direito</white> ▶"
                    + "   <dark_gray>|</dark_gray>   <yellow>Agache</yellow> <gray>para confirmar");
        }
        StringBuilder row = new StringBuilder();
        for (Kingdom kingdom : Kingdom.values()) {
            String tint = kingdom == selected ? "#ffffff" : "#3a3a44";
            row.append("<font:vertexx:cinematic><color:").append(tint).append(">")
                    .append((char) (EMBLEM_BASE + kingdom.ordinal())).append("</color></font> ");
        }
        return Msg.mm("<font:vertexx:cinematic>" + KEY_LMB + "</font> <gray>◀  " + row.toString().stripTrailing()
                + "  <gray>▶ <font:vertexx:cinematic>" + KEY_RMB + "</font>"
                + "     <font:vertexx:cinematic>" + KEY_SHIFT + "</font> <gray>confirmar");
    }

    // ── Sons: com o pacote, os do pack; sem ele, equivalentes vanilla ──

    /** Evento do pack + som vanilla de reserva. */
    record Cue(String event, org.bukkit.Sound fallback, float fallbackPitch) {
        Key key() {
            return Key.key("vertexx", event);
        }

        Sound custom(float volume, float pitch) {
            return Sound.sound(key(), Sound.Source.MASTER, volume, pitch);
        }
    }

    static final Cue DRONE = new Cue("intro.drone", null, 1f);
    static final Cue RISER = new Cue("intro.riser", org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.6f);
    static final Cue BOOM = new Cue("intro.boom", org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.55f);
    static final Cue WHOOSH = new Cue("intro.whoosh", org.bukkit.Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f);
    static final Cue TICK = new Cue("intro.tick", org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 1.4f);
    static final Cue CONFIRM = new Cue("intro.confirm", org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
    static final Cue TITLE = new Cue("intro.title", org.bukkit.Sound.ITEM_TOTEM_USE, 0.9f);

    static Cue stinger(Kingdom kingdom) {
        return switch (kingdom) {
            case ENTERNAIS -> new Cue("intro.stinger.enternais", org.bukkit.Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f);
            case AURUDOS -> new Cue("intro.stinger.aurudos", org.bukkit.Sound.BLOCK_BELL_USE, 0.9f);
            case CAFETOES -> new Cue("intro.stinger.cafetoes", org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f);
        };
    }

    // ── Itens ──

    static final NamespacedKey MODEL_SELECTOR = NamespacedKey.fromString("vertexx:selector");

    static NamespacedKey bannerModel(Kingdom kingdom) {
        return NamespacedKey.fromString("vertexx:banner_" + kingdom.id());
    }

    /** O bandeirão: modelo customizado do pack, ou um estandarte vanilla bordado como reserva. */
    static ItemStack banner(Kingdom kingdom, boolean pack) {
        if (pack) {
            ItemStack item = new ItemStack(Material.PAPER);
            item.editMeta(meta -> meta.setItemModel(bannerModel(kingdom)));
            return item;
        }
        Material material;
        List<Pattern> patterns;
        switch (kingdom) {
            case ENTERNAIS -> {
                material = Material.PURPLE_BANNER;
                patterns = List.of(new Pattern(DyeColor.MAGENTA, PatternType.CIRCLE),
                        new Pattern(DyeColor.WHITE, PatternType.BORDER));
            }
            case AURUDOS -> {
                material = Material.YELLOW_BANNER;
                patterns = List.of(new Pattern(DyeColor.ORANGE, PatternType.CIRCLE),
                        new Pattern(DyeColor.ORANGE, PatternType.BORDER));
            }
            default -> {
                material = Material.RED_BANNER;
                patterns = List.of(new Pattern(DyeColor.BLACK, PatternType.CROSS),
                        new Pattern(DyeColor.BLACK, PatternType.BORDER));
            }
        }
        ItemStack item = new ItemStack(material);
        item.editMeta(BannerMeta.class, meta -> meta.setPatterns(patterns));
        return item;
    }

    /** O item de escolha na mão do jogador, marcado para ser recolhido caso algo dê errado. */
    static ItemStack selector(boolean pack, NamespacedKey tag) {
        ItemStack item = new ItemStack(pack ? Material.STICK : Material.AMETHYST_SHARD);
        item.editMeta(meta -> {
            meta.displayName(Msg.item("<gradient:#c4a7ff:#ffe082><bold>Cetro do Escolhido"));
            meta.lore(List.of(
                    Msg.item("<gray>Botão esquerdo: <white>◀ reino anterior"),
                    Msg.item("<gray>Botão direito: <white>próximo reino ▶"),
                    Msg.item("<gray>Agachar: <yellow>confirmar a escolha")));
            meta.setEnchantmentGlintOverride(true);
            if (pack) {
                meta.setItemModel(MODEL_SELECTOR);
            }
            meta.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }
}
