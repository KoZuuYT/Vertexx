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
        stepper(14, "Rotação", yaw + "°", "Passo: 15°", 15, d -> {
            int value = Math.floorMod(hooks.num(display, hooks.kYaw, 0) + d + 180, 360) - 180;
            hooks.put(display, hooks.kYaw, value);
        });
        stepper(19, "Inclinação", tilt + "°", "Passo: 15°", 15, d -> bump(hooks.kTilt, 0, d, -90, 90));
        stepper(23, "Altura", String.format("%+.2f", up / 100.0), "Passo: 0,05", 5, d -> bump(hooks.kUp, -10, d, -60, 60));
        stepper(28, "Distância", String.format("%.2f", out / 100.0), "Passo: 0,05", 5, d -> bump(hooks.kOut, 22, d, -10, 60));

        HookManager.Mode mode = hooks.mode(display);
        button(32, Material.ITEM_FRAME, "<white>Modo: <yellow>" + mode.label,
                List.of("<gray>Moldura: item achatado, como num quadro.", "<gray>Bloco: o item em 3D, como na cabeça.",
                        "<gray>Solto: como um item largado no chão.", "<yellow>▶ Clique para trocar"),
                c -> hooks.put(display, hooks.kMode, mode.next().ordinal()));
        boolean spin = hooks.spins(display);
        button(33, spin ? Material.CLOCK : Material.BARRIER, spin ? "<green>Giro: ligado" : "<gray>Giro: desligado",
                List.of("<gray>O item gira devagar.", "<yellow>▶ Clique para alternar"),
                c -> hooks.put(display, hooks.kSpin, spin ? 0 : 1));
        button(34, Material.SUGAR, "<aqua>Restaurar padrão", List.of("<gray>Volta tamanho, posição e giro ao início."), c -> {
            hooks.put(display, hooks.kScale, 70);
            hooks.put(display, hooks.kYaw, 0);
            hooks.put(display, hooks.kTilt, 0);
            hooks.put(display, hooks.kUp, -10);
            hooks.put(display, hooks.kOut, 22);
            hooks.put(display, hooks.kMode, 0);
            hooks.put(display, hooks.kSpin, 0);
        });
        set(43, Items.of(Material.HOPPER, "<red>Retirar item", List.of("<gray>Devolve o item ao seu inventário.",
                "<dark_gray>Agache + clique vazio faz o mesmo.")), c -> hooks.take(player, display));
    }
}
