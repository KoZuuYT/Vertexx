package dev.vertexx.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Desenha no título do inventário (ver tools/pack/ui2.py): o painel largo de fundo e, por cima, textos, barras de
 * progresso e molduras em qualquer posição da janela. Coordenadas em pixels de GUI a partir do canto superior
 * esquerdo da janela do inventário (a janela tem 176 de largura; o painel se estende 92 para cada lado).
 */
public final class Canvas {

    public static final int PANEL_W = 360;
    public static final int WINDOW_X = 92;
    public static final int BAR_W = 40;
    public static final int BAR_STEPS = 20;
    public static final int FRAME_W = 19;
    private static final int[] WIDTHS = loadWidths();
    private static final Key PANEL = Key.key("vertexx", "panel");
    private static final Key SPACE = Key.key("vertexx", "sp");

    private final TextComponent.Builder out = Component.text().color(NamedTextColor.WHITE);
    private int x = 8;     // o texto do título começa em x=8

    private Canvas() {
    }

    private static int[] loadWidths() {
        int[] table = new int[256];
        java.util.Arrays.fill(table, 6);
        try (InputStream in = Canvas.class.getResourceAsStream("/ui-widths.txt")) {
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] part = line.trim().split(" ");
                    if (part.length == 2) {
                        table[Integer.parseInt(part[0]) & 255] = Integer.parseInt(part[1]);
                    }
                }
            }
        } catch (Exception ignored) {
            // larguras padrão
        }
        table[' '] = 4;
        return table;
    }

    /** Começa um painel; {@code panel} é o índice do glifo (U+E400 + índice). */
    public static Canvas panel(int panel) {
        Canvas canvas = new Canvas();
        canvas.move(-WINDOW_X);
        canvas.out.append(Component.text(String.valueOf((char) (0xE400 + panel))).font(PANEL));
        canvas.x += PANEL_W + 1;
        return canvas;
    }

    /** Texto em maiúsculas e sem acentos (a fonte de uma linha só tem ASCII). */
    public static String ascii(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder sb = new StringBuilder();
        for (char c : plain.toUpperCase(java.util.Locale.ROOT).toCharArray()) {
            sb.append(c < 128 && c >= 32 ? c : '?');
        }
        return sb.toString();
    }

    public static int width(String text) {
        int w = 0;
        for (char c : text.toCharArray()) {
            w += WIDTHS[c & 255];
        }
        return w;
    }

    /** Corta o texto (com ...) para caber em {@code max} pixels. */
    public static String fit(String text, int max) {
        if (width(text) <= max) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (width(sb.toString() + c + "...") > max) {
                break;
            }
            sb.append(c);
        }
        return sb + "...";
    }

    private static Key line(int y) {
        return Key.key("vertexx", "ln" + Math.max(6, Math.min(130, y)));
    }

    private void move(int to) {
        int dx = to - x;
        char base = dx >= 0 ? (char) 0xE200 : (char) 0xE210;
        int rest = Math.abs(dx);
        while (rest > 0) {
            int bit = Math.min(8, 31 - Integer.numberOfLeadingZeros(rest));
            out.append(Component.text(String.valueOf((char) (base + bit))).font(SPACE));
            rest -= 1 << bit;
        }
        x = to;
    }

    /** Texto com o topo em {@code y}; sem acentos e em maiúsculas. */
    public Canvas text(int px, int y, String text, TextColor color) {
        String s = ascii(text);
        if (s.isEmpty()) {
            return this;
        }
        move(px);
        out.append(Component.text(s).font(line(y)).color(color));
        x += width(s);
        return this;
    }

    /** Texto centralizado em torno de {@code cx}. */
    public Canvas centered(int cx, int y, String text, TextColor color) {
        return text(cx - width(ascii(text)) / 2, y, text, color);
    }

    /** Barra de progresso de 40 px; a cor tinge o trecho cheio e o trilho. */
    public Canvas bar(int px, int y, double fraction, TextColor color) {
        move(px);
        int step = (int) Math.round(Math.max(0, Math.min(1, fraction)) * BAR_STEPS);
        out.append(Component.text(String.valueOf((char) (0xE300 + step))).font(line(y)).color(color));
        x += BAR_W + 1;
        return this;
    }

    /** Moldura 18x18 (estado: 0 vazia, 1 aprendida, 2 disponível, 3 bloqueada, 4 selecionada, 5 ativa, 6 máxima). */
    public Canvas frame(int px, int y, int state) {
        move(px);
        out.append(Component.text(String.valueOf((char) (0xE340 + state))).font(line(y)).color(NamedTextColor.WHITE));
        x += FRAME_W;
        return this;
    }

    /** Moldura em volta do slot (coluna/linha do inventário). */
    public Canvas slotFrame(int slot, int state) {
        return frame(7 + 18 * (slot % 9), 17 + 18 * (slot / 9), state);
    }

    public Component build() {
        return out.build();
    }
}
