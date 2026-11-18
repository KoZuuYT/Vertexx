package dev.vertexx.decor;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Items;
import dev.vertexx.core.Menu;
import dev.vertexx.core.Ui;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Configuração de um gancho de exposição: tamanho, giro, inclinação, altura, distância e modo. */
final class HookMenu extends Menu {

    private final HookManager hooks;
    private final ItemDisplay display;

    HookMenu(PluginMain plugin, Player player, HookManager hooks, ItemDisplay display) {
        super(plugin, player, Ui.Theme.INFO, "<gradient:#c4a7ff:#2cb67d><bold>Gancho de exposição</bold></gradient>");
        this.hooks = hooks;
        this.display = display;
    }

    private void button(int slot, Material material, String name, List<String> lore, Consumer<ClickType> action) {
        set(slot, Items.of(material, name, lore), click -> {
            if (!display.isValid()) {
                player.closeInventory();
                return;
            }
            action.accept(click);
            hooks.apply(display, 4);
            refresh();
        });
    }

    /** Três botões seguidos: menos, valor e mais. Shift multiplica o passo por 5. */
    private void stepper(int slot, String label, String value, String hint, int step, java.util.function.IntConsumer change) {
        button(slot, Material.RED_CONCRETE, "<red>− " + label, List.of("<dark_gray>" + hint, "<dark_gray>Shift: passo maior"),
                c -> change.accept(-(c.isShiftClick() ? step * 5 : step)));
        set(slot + 1, Items.of(Material.PAPER, "<white>" + label + ": <yellow>" + value, List.of()));
        button(slot + 2, Material.LIME_CONCRETE, "<green>+ " + label, List.of("<dark_gray>" + hint, "<dark_gray>Shift: passo maior"),
                c -> change.accept(c.isShiftClick() ? step * 5 : step));
    }

    private void bump(org.bukkit.NamespacedKey key, int fallback, int delta, int min, int max) {
        hooks.put(display, key, Math.max(min, Math.min(max, hooks.num(display, key, fallback) + delta)));
    }

    /** Ângulo com volta completa: passa de 180° para -180° e continua girando (360° no total). */
    private void wrap(org.bukkit.NamespacedKey key, int delta) {
        hooks.put(display, key, Math.floorMod(hooks.num(display, key, 0) + delta + 180, 360) - 180);
    }

    @Override
    protected void render() {
        frame(null);
        if (!display.isValid()) {
            return;
        }
        ItemStack shown = display.getItemStack();
        if (shown != null && !shown.getType().isAir()) {
            set(HEADER_SLOT, shown.clone());
        }
        int scale = hooks.num(display, hooks.kScale, 70);
        int yaw = hooks.num(display, hooks.kYaw, 0);
        int tilt = hooks.num(display, hooks.kTilt, 0);
        int up = hooks.num(display, hooks.kUp, -10);
        int out = hooks.num(display, hooks.kOut, 22);

        stepper(10, "Tamanho", scale + "%", "Passo: 5%", 5, d -> bump(hooks.kScale, 70, d, 20, 250));
        stepper(14, "Rotação", yaw + "°", "Passo: 15°", 15, d -> wrap(hooks.kYaw, d));
        stepper(19, "Inclinação", tilt + "°", "Passo: 15°", 15, d -> wrap(hooks.kTilt, d));
        stepper(23, "Altura", String.format("%+.2f", up / 100.0), "Passo: 0,05", 5, d -> bump(hooks.kUp, -10, d, -60, 60));
        stepper(28, "Distância", String.format("%.2f", out / 100.0), "Passo: 0,05", 5, d -> bump(hooks.kOut, 22, d, -10, 60));
        int side = hooks.num(display, hooks.kSide, 0);
        int roll = hooks.num(display, hooks.kRoll, 0);
        stepper(32, "Lateral", String.format("%+.2f", side / 100.0), "Passo: 0,05", 5, d -> bump(hooks.kSide, 0, d, -60, 60));
        stepper(37, "Rolagem", roll + "°", "Passo: 15°", 15, d -> wrap(hooks.kRoll, d));

        HookManager.Mode mode = hooks.mode(display);
        button(41, Material.ITEM_FRAME, "<white>Modo: <yellow>" + mode.label,
                List.of("<gray>Moldura: item achatado, como num quadro.", "<gray>Bloco: o item em 3D, como na cabeça.",
                        "<gray>Solto: como um item largado no chão.", "<yellow>▶ Clique para trocar"),
                c -> hooks.put(display, hooks.kMode, mode.next().ordinal()));
        boolean spin = hooks.spins(display);
        button(42, spin ? Material.CLOCK : Material.BARRIER, spin ? "<green>Giro: ligado" : "<gray>Giro: desligado",
                List.of("<gray>O item gira devagar.", "<yellow>▶ Clique para alternar"),
                c -> hooks.put(display, hooks.kSpin, spin ? 0 : 1));
        button(47, Material.WRITABLE_BOOK, "<green>Copiar posição",
                List.of("<gray>Guarda tamanho, rotação, altura e demais", "<gray>ajustes deste gancho.",
                        "<dark_gray>Depois cole em qualquer outro gancho."), c -> hooks.copy(player, display));
        boolean has = hooks.hasCopy(player);
        button(48, has ? Material.FILLED_MAP : Material.MAP, has ? "<aqua>Colar posição" : "<gray>Colar posição",
                List.of(has ? "<gray>Aplica a posição copiada a este gancho." : "<gray>Copie a posição de outro gancho primeiro."),
                c -> hooks.paste(player, display));
        button(50, Material.SUGAR, "<aqua>Restaurar padrão", List.of("<gray>Volta tamanho, posição e giro ao início."),
                c -> hooks.reset(display));
        set(51, Items.of(Material.HOPPER, "<red>Retirar item", List.of("<gray>Devolve o item ao seu inventário.",
                "<dark_gray>Agache + clique vazio faz o mesmo.")), c -> hooks.take(player, display));
    }
}
