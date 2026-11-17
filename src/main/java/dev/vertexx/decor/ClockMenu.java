package dev.vertexx.decor;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Menu;
import dev.vertexx.core.Ui;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Configuração de um relógio decorativo: alarme, timer, sinal de redstone, som e horário flutuante. */
final class ClockMenu extends Menu {

    private final ClockManager clocks;
    private final ItemDisplay clock;

    ClockMenu(PluginMain plugin, Player player, ClockManager clocks, ItemDisplay clock) {
        super(plugin, player, Ui.Theme.INFO, "<gradient:#ffe08a:#ff9be8><bold>Relógio</bold></gradient>");
        this.clocks = clocks;
        this.clock = clock;
    }

    private void button(int slot, Material material, String name, List<String> lore, Consumer<ClickType> action) {
        set(slot, Items.of(material, name, lore), click -> {
            if (!clock.isValid()) {
                player.closeInventory();
                return;
            }
            action.accept(click);
            refresh();
        });
    }

    @Override
    protected void render() {
        frame(null);
        if (!clock.isValid()) {
            return;
        }
        World world = clock.getWorld();
        set(HEADER_SLOT, Items.of(Material.CLOCK, "<yellow>" + ClockManager.hhmm(world) + " <gray>• Dia <white>" + ClockManager.day(world),
                List.of("<gray>Horário exato do mundo.", "<dark_gray>Agache + clique vazio para recolher."), true));

        // alarme: dispara todo dia no horário escolhido
        boolean alarm = clocks.flag(clock, clocks.kAlarmOn, false);
        int minute = clocks.num(clock, clocks.kAlarmMin, 360);
        button(19, alarm ? Material.LIME_DYE : Material.GRAY_DYE, alarm ? "<green>Alarme ligado" : "<gray>Alarme desligado",
                List.of("<gray>Toca e envia redstone todo dia", "<gray>no horário escolhido.", "<yellow>▶ Clique para alternar"),
                c -> clocks.put(clock, clocks.kAlarmOn, !alarm));
        button(20, Material.RED_CONCRETE, "<red>− 1 hora", List.of("<dark_gray>Direito: −1 minuto"),
                c -> shiftAlarm(c.isRightClick() ? -1 : -60));
        button(21, Material.RED_TERRACOTTA, "<red>− 10 min", List.of("<dark_gray>Direito: −5 minutos"),
                c -> shiftAlarm(c.isRightClick() ? -5 : -10));
        set(22, Items.of(Material.CLOCK, "<white>Hora do alarme: <yellow>" + ClockManager.clockText(minute),
                List.of("<gray>Horário do Minecraft (6:00 = amanhecer)."), alarm));
        button(23, Material.LIME_TERRACOTTA, "<green>+ 10 min", List.of("<dark_gray>Direito: +5 minutos"),
                c -> shiftAlarm(c.isRightClick() ? 5 : 10));
        button(24, Material.LIME_CONCRETE, "<green>+ 1 hora", List.of("<dark_gray>Direito: +1 minuto"),
                c -> shiftAlarm(c.isRightClick() ? 1 : 60));
        button(25, Material.COMPASS, "<aqua>Usar a hora de agora", List.of("<gray>Define o alarme para o horário atual."),
                c -> clocks.put(clock, clocks.kAlarmMin, (int) ((world.getTime() * 60 / 1000 + 360) % 1440)));

        // timer: contagem regressiva em tempo real
        int left = clocks.num(clock, clocks.kTimerLeft, -1);
        int seconds = clocks.num(clock, clocks.kTimerSec, 60);
        boolean running = left >= 0;
        button(28, running ? Material.REDSTONE_TORCH : Material.LEVER, running ? "<red>Parar timer" : "<green>Iniciar timer",
                List.of(running ? "<gray>Restam <white>" + format((left + 19) / 20) : "<gray>Duração: <white>" + format(seconds),
                        "<yellow>▶ Clique para alternar"),
                c -> clocks.put(clock, clocks.kTimerLeft, running ? -1 : seconds * 20));
        button(29, Material.RED_CONCRETE, "<red>− 1 min", List.of(), c -> shiftTimer(-60));
        button(30, Material.RED_TERRACOTTA, "<red>− 10 s", List.of("<dark_gray>Direito: −1 s"), c -> shiftTimer(c.isRightClick() ? -1 : -10));
        set(31, Items.of(Material.HOPPER, "<white>Duração: <yellow>" + format(seconds),
                List.of("<gray>De 1 segundo a " + format(clocks.maxTimer()) + "."), running));
        button(32, Material.LIME_TERRACOTTA, "<green>+ 10 s", List.of("<dark_gray>Direito: +1 s"), c -> shiftTimer(c.isRightClick() ? 1 : 10));
        button(33, Material.LIME_CONCRETE, "<green>+ 1 min", List.of(), c -> shiftTimer(60));
        boolean repeat = clocks.flag(clock, clocks.kRepeat, false);
        button(34, repeat ? Material.REPEATER : Material.COMPARATOR, repeat ? "<green>Repetir: ligado" : "<gray>Repetir: desligado",
                List.of("<gray>Reinicia o timer sozinho ao acabar.", "<yellow>▶ Clique para alternar"),
                c -> clocks.put(clock, clocks.kRepeat, !repeat));

        // saída
        boolean redstone = clocks.flag(clock, clocks.kRedstone, true);
        button(37, redstone ? Material.REDSTONE : Material.GUNPOWDER, redstone ? "<red>Redstone: ligado" : "<gray>Redstone: desligado",
                List.of("<gray>Um pulso de sinal no bloco do relógio", "<gray>quando o alarme ou o timer disparam.", "<yellow>▶ Clique para alternar"),
                c -> clocks.put(clock, clocks.kRedstone, !redstone));
        int pulse = clocks.num(clock, clocks.kPulse, 20);
        button(38, Material.REPEATER, "<white>Duração do pulso: <yellow>" + (pulse / 20.0) + " s",
                List.of("<gray>Quanto tempo o sinal fica ligado.", "<yellow>▶ Clique para mudar"), c -> {
                    int index = 0;
                    for (int i = 0; i < ClockManager.PULSES.length; i++) {
                        if (ClockManager.PULSES[i] == pulse) {
                            index = i;
                        }
                    }
                    clocks.put(clock, clocks.kPulse, ClockManager.PULSES[(index + 1) % ClockManager.PULSES.length]);
                });
        int sound = Math.floorMod(clocks.num(clock, clocks.kSound, 0), ClockManager.SOUNDS.length);
        button(39, Material.NOTE_BLOCK, "<white>Som: <yellow>" + ClockManager.SOUND_NAMES[sound],
                List.of("<yellow>▶ Esquerdo: próximo som", "<yellow>▶ Direito: ouvir"), c -> {
                    if (c.isRightClick()) {
                        clocks.testRing(clock);
                    } else {
                        clocks.put(clock, clocks.kSound, (sound + 1) % ClockManager.SOUNDS.length);
                        player.playSound(player.getLocation(), ClockManager.SOUNDS[(sound + 1) % ClockManager.SOUNDS.length], 1f, 1f);
                    }
                });
        boolean text = clocks.flag(clock, clocks.kText, false);
        button(40, text ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME, text ? "<green>Horário flutuante: ligado" : "<gray>Horário flutuante: desligado",
                List.of("<gray>Mostra hora e dia sobre o relógio.", "<yellow>▶ Clique para alternar"),
                c -> clocks.put(clock, clocks.kText, !text));
        button(43, Material.HOPPER, "<red>Recolher relógio", List.of("<gray>Devolve o relógio ao seu inventário."),
                c -> clocks.pickUp(player, clock));
    }

    private void shiftAlarm(int minutes) {
        int value = clocks.num(clock, clocks.kAlarmMin, 360);
        clocks.put(clock, clocks.kAlarmMin, Math.floorMod(value + minutes, 1440));
    }

    private void shiftTimer(int seconds) {
        int value = clocks.num(clock, clocks.kTimerSec, 60) + seconds;
        clocks.put(clock, clocks.kTimerSec, Math.max(1, Math.min(clocks.maxTimer(), value)));
    }

    private static String format(int seconds) {
        return seconds >= 3600 ? seconds / 3600 + " h " + seconds % 3600 / 60 + " min"
                : seconds >= 60 ? seconds / 60 + " min " + seconds % 60 + " s" : seconds + " s";
    }
}
