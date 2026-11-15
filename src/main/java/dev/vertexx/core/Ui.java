package dev.vertexx.core;

import dev.vertexx.kingdom.Kingdom;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Visual dos menus. Com o resource pack carregado, o título do inventário leva um glifo que desenha o
 * fundo do menu (moldura, grade e ornamentos do tema) e os painéis decorativos viram itens invisíveis;
 * sem o pacote, o mesmo menu aparece com painéis de vidro vanilla. A funcionalidade é idêntica.
 */
public final class Ui {

    /** Temas do fundo; a ordem define o caractere do glifo (U+E100 + ordinal). */
    public enum Theme {
        VERTEXX, ENTERNAIS, AURUDOS, CAFETOES, OUTLAW;

        public static Theme of(Kingdom kingdom) {
            if (kingdom == null) {
                return VERTEXX;
            }
            return switch (kingdom) {
                case ENTERNAIS -> ENTERNAIS;
                case AURUDOS -> AURUDOS;
                case CAFETOES -> CAFETOES;
            };
        }

        public Material pane() {
            return switch (this) {
                case VERTEXX -> Material.PURPLE_STAINED_GLASS_PANE;
                case ENTERNAIS -> Kingdom.ENTERNAIS.pane();
                case AURUDOS -> Kingdom.AURUDOS.pane();
                case CAFETOES -> Kingdom.CAFETOES.pane();
                case OUTLAW -> Material.RED_STAINED_GLASS_PANE;
            };
        }
    }

    private static final Key MENU_FONT = Key.key("vertexx", "menu");
    private static final String BACK_UP = "";
    private static final String BACK_DOWN = "";
    private static final NamespacedKey BLANK = NamespacedKey.fromString("vertexx:blank");

    private Ui() {
    }

    /**
     * Título do inventário. Com o pacote: glifo de fundo na fonte {@code vertexx:menu}, recuo até a origem
     * do título e depois o texto normal. Sem o pacote: só o texto.
     */
    public static Component title(boolean pack, Theme theme, String mini) {
        Component text = Msg.mm(mini);
        if (!pack) {
            return text;
        }
        char glyph = (char) (0xE100 + theme.ordinal());
        return Component.text()
                .color(NamedTextColor.WHITE)
                .append(Component.text(BACK_UP).font(MENU_FONT))
                .append(Component.text(String.valueOf(glyph)).font(MENU_FONT))
                .append(Component.text(BACK_DOWN).font(MENU_FONT))
                .append(text)
                .build();
    }

    /** Aplica um modelo do pacote ao item (se o jogador o tem). */
    public static ItemStack model(boolean pack, ItemStack item, String key) {
        if (pack) {
            item.editMeta(meta -> meta.setItemModel(NamespacedKey.fromString("vertexx:" + key)));
        }
        return item;
    }

    /** Emblema do reino no lugar do ícone vanilla (só com o pacote). */
    public static ItemStack emblem(boolean pack, ItemStack item, Kingdom kingdom) {
        return model(pack, item, "emblem_" + kingdom.id());
    }

    /**
     * Passada final de um menu: com o pacote, painéis decorativos ficam invisíveis (o fundo do glifo
     * aparece) e os botões padrão ganham ícones próprios.
     */
    public static void skin(boolean pack, Inventory inventory) {
        if (!pack) {
            return;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            Material type = item.getType();
            if (type.name().endsWith("_STAINED_GLASS_PANE") && item.hasItemMeta() && item.getItemMeta().isHideTooltip()) {
                item.editMeta(meta -> meta.setItemModel(BLANK));
            } else if (type == Material.BARRIER) {
                model(true, item, "ui_close");
            } else if (type == Material.ARROW && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                String name = Msg.plain(item.getItemMeta().displayName());
                if (name.contains("◀")) {
                    model(true, item, "ui_back");
                } else if (name.contains("▶")) {
                    model(true, item, "ui_next");
                }
            }
        }
    }
}
