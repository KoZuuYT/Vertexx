package dev.vertexx.kingdom;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import dev.vertexx.mercenary.MercenaryManager;
import java.time.Duration;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Saída e troca de reino. Quatro caminhos:
 * <ul>
 *   <li>{@code ALLIANCE}: vai para um reino aliado, sem punição;</li>
 *   <li>{@code BETRAYAL}: muda de lado para um reino que não é aliado — perde honra, o reino de origem
 *       também, e o jogador fica marcado como traidor;</li>
 *   <li>{@code OUTLAW}: abandona o reino e vira fora da lei (mercenário);</li>
 *   <li>{@code JOIN}: um fora da lei entra em um reino.</li>
 * </ul>
 */
public final class Membership {

    public enum Mode {
        ALLIANCE, BETRAYAL, OUTLAW, JOIN
    }

    private final PluginMain plugin;

    public Membership(PluginMain plugin) {
        this.plugin = plugin;
    }

    /** {@code null} se o jogador pode fazer a troca; senão o motivo (MiniMessage). */
    public String validate(Player player, Mode mode, Kingdom target) {
        UUID id = player.getUniqueId();
        KingdomManager kingdoms = plugin.kingdoms();
        MercenaryManager mercs = plugin.mercenaries();
        Kingdom from = kingdoms.kingdomOf(id);
        long wait = mercs.cooldownLeft(id);
        if (wait > 0) {
            return "<red>Você mudou de lado há pouco. Aguarde <white>" + MercenaryManager.formatDuration(wait) + "</white>.";
        }
        if (plugin.inCombat(player)) {
            return "<red>Não é possível mudar de reino em combate.";
        }
        switch (mode) {
            case ALLIANCE -> {
                if (from == null || target == null || target == from) {
                    return "<red>Escolha um reino aliado.";
                }
                if (!kingdoms.allied(from, target)) {
                    return "<red>Só é possível se transferir para um reino <white>aliado</white>.";
                }
            }
            case BETRAYAL -> {
                if (from == null || target == null || target == from) {
                    return "<red>Escolha outro reino.";
                }
                if (kingdoms.allied(from, target)) {
                    return "<red>Seu reino é aliado deste: use a <white>transferência por aliança</white>.";
                }
            }
            case OUTLAW -> {
                if (from == null) {
                    return "<red>Você já está sem reino.";
                }
            }
            case JOIN -> {
                if (from != null || !mercs.isOutlaw(id) || target == null) {
                    return "<red>Apenas foras da lei podem entrar em um reino por aqui.";
                }
            }
        }
        return null;
    }

    public void execute(Player player, Mode mode, Kingdom target) {
        String problem = validate(player, mode, target);
        if (problem != null) {
            Msg.send(player, problem);
            Fx.deny(player);
            return;
        }
        UUID id = player.getUniqueId();
        KingdomManager kingdoms = plugin.kingdoms();
        MercenaryManager mercs = plugin.mercenaries();
        var config = plugin.getConfig();
        Kingdom from = kingdoms.kingdomOf(id);
        player.closeInventory();

        String successor = from == null ? null : kingdoms.remove(id);
        switch (mode) {
            case ALLIANCE -> {
                mercs.clearOutlaw(id);
                kingdoms.assign(player, target);
            }
            case BETRAYAL -> {
                plugin.honor().add(player, config.getInt("kingdoms.leave.betrayal-honor", -30), "traição ao reino");
                shake(from, config.getInt("kingdom-honor.betrayal", -12), "traição de " + player.getName());
                mercs.addRep(player, config.getInt("mercenary.reputation.betrayal", 10), "traição");
                mercs.markTraitor(id, config.getInt("kingdoms.traitor-days", 3));
                mercs.clearOutlaw(id);
                kingdoms.assign(player, target);
            }
            case OUTLAW -> {
                plugin.honor().add(player, config.getInt("kingdoms.leave.outlaw-honor", -10), "abandonou o reino");
                shake(from, config.getInt("kingdom-honor.desertion", -4), player.getName() + " abandonou o reino");
                mercs.makeOutlaw(id, player.getName(), from.id());
            }
            case JOIN -> {
                mercs.clearOutlaw(id);
                kingdoms.assign(player, target);
            }
        }
        mercs.startCooldown(id);
        plugin.onboarding().updateTab(player);

        if (successor != null && from != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (kingdoms.kingdomOf(online.getUniqueId()) == from) {
                    Msg.send(online, "<gray>" + Msg.esc(player.getName()) + " deixou o comando. <white>"
                            + Msg.esc(successor) + "</white> é o novo <white>Líder</white> de " + from.styled() + "<gray>.");
                }
            }
        }
        announce(player, mode, from, target);
        celebrate(player, mode, target);
    }

    private void announce(Player player, Mode mode, Kingdom from, Kingdom target) {
        String name = "<white><player></white>";
        var who = Placeholder.unparsed("player", player.getName());
        switch (mode) {
            case ALLIANCE -> Bukkit.broadcast(Msg.mm(Msg.PREFIX + name + " <gray>deixou " + from.styled()
                    + " <gray>e se juntou aos aliados de " + target.styled() + "<gray>.", who));
            case BETRAYAL -> Bukkit.broadcast(Msg.mm(Msg.PREFIX + "<dark_red>Traição! " + name + " <gray>abandonou "
                    + from.styled() + " <gray>e jurou lealdade a " + target.styled() + "<gray>. Está marcado como traidor.", who));
            case OUTLAW -> Bukkit.broadcast(Msg.mm(Msg.PREFIX + name + " <gray>abandonou " + from.styled()
                    + " <gray>e agora é um <red>fora da lei<gray>.", who));
            case JOIN -> Bukkit.broadcast(Msg.mm(Msg.PREFIX + name + " <gray>deixou a vida de mercenário e entrou em "
                    + target.styled() + "<gray>.", who));
        }
    }

    private void celebrate(Player player, Mode mode, Kingdom target) {
        Location at = player.getLocation().add(0, 1, 0);
        Title.Times times = Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(900));
        switch (mode) {
            case ALLIANCE, JOIN -> {
                player.showTitle(Title.title(Msg.mm("<bold>" + target.styled()),
                        Msg.mm("<gray>Você agora pertence a este reino"), times));
                Fx.sound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.0f);
                Fx.burst(at, Particle.TOTEM_OF_UNDYING, 50, 0.4, 0.4);
            }
            case BETRAYAL -> {
                player.showTitle(Title.title(Msg.mm("<dark_red><bold>TRAIDOR"),
                        Msg.mm("<gray>Você jurou lealdade a " + target.styled()), times));
                Fx.sound(player, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.4f);
                Fx.burst(at, Particle.SOUL_FIRE_FLAME, 40, 0.4, 0.05);
            }
            case OUTLAW -> {
                player.showTitle(Title.title(Msg.mm("<red><bold>FORA DA LEI"),
                        Msg.mm("<gray>Sem reino, sem lei. Faça seu nome."), times));
                Fx.sound(player, Sound.EVENT_RAID_HORN, 0.7f, 1.0f);
                Fx.burst(at, Particle.SMOKE, 60, 0.4, 0.05);
            }
        }
    }

    /**
     * Reduz (ou aumenta) a honra de um reino e avisa seus membros online. Chamado por traições,
     * deserções, mortes e roubos causados por mercenários.
     */
    public void shake(Kingdom kingdom, int delta, String reason) {
        if (kingdom == null || delta == 0) {
            return;
        }
        KingdomManager kingdoms = plugin.kingdoms();
        int before = kingdoms.honor(kingdom);
        int after = kingdoms.addHonor(kingdom, delta);
        if (after == before) {
            return;
        }
        String sign = after > before ? "<green>+" : "<red>";
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (kingdoms.kingdomOf(online.getUniqueId()) == kingdom) {
                Msg.actionBar(online, sign + (after - before) + " Honra do reino <dark_gray>(" + Msg.esc(reason)
                        + ") <gray>" + after);
                Fx.sound(online, after > before ? Sound.BLOCK_NOTE_BLOCK_CHIME : Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO,
                        0.5f, after > before ? 1.5f : 0.6f);
            }
        }
        String oldLabel = KingdomManager.honorLabel(before);
        String newLabel = KingdomManager.honorLabel(after);
        if (!oldLabel.equals(newLabel)) {
            Bukkit.broadcast(Msg.mm(Msg.PREFIX + kingdom.styled() + " <gray>agora está " + newLabel + "<gray> <dark_gray>("
                    + after + ")"));
        }
    }
}
