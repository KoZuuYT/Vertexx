package dev.vertexx.mercenary;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import dev.vertexx.kingdom.Kingdom;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Consequências das mortes entre jogadores: recompensas cobradas, reputação dos mercenários,
 * honra dos reinos atingidos e a perda de fama de quem morre.
 */
public final class MercenaryListener implements Listener {

    private final PluginMain plugin;

    public MercenaryListener(PluginMain plugin) {
        this.plugin = plugin;
    }

    private MercenaryManager mercs() {
        return plugin.mercenaries();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        var config = plugin.getConfig();
        boolean collected = false;

        if (killer != null && !killer.equals(victim)) {
            int bounty = mercs().total(victim.getUniqueId());
            if (bounty > 0) {
                collect(killer, victim, bounty);
                collected = true;
            }
            if (mercs().isOutlaw(killer.getUniqueId())) {
                if (collected) {
                    mercs().addRep(killer, config.getInt("mercenary.reputation.bounty-kill", 8), "cabeça cobrada");
                } else if (mercs().killCounts(killer.getUniqueId(), victim.getUniqueId())) {
                    mercs().addRep(killer, config.getInt("mercenary.reputation.kill", 3), "abate");
                }
                Kingdom hit = plugin.kingdoms().kingdomOf(victim.getUniqueId());
                if (hit != null) {
                    plugin.membership().shake(hit, config.getInt("kingdom-honor.mercenary-kill", -3),
                            "mercenário matou " + victim.getName());
                }
            } else if (collected) {
                plugin.honor().add(killer, config.getInt("bounty.hunter-honor", 3), "caçador de recompensas");
            }
        }

        if (collected) {
            mercs().wipe(victim, "cabeça cobrada");
        } else {
            mercs().punishDeath(victim);
        }
    }

    /** Paga a recompensa (com o bônus de fama do caçador, se ele for mercenário) e anuncia. */
    private void collect(Player killer, Player victim, int bounty) {
        mercs().claim(victim.getUniqueId());
        int bonus = bounty * mercs().activeTier(killer.getUniqueId()).bountyBonusPercent() / 100;
        mercs().give(killer, bounty + bonus);
        String currency = mercs().currencyName();
        Bukkit.broadcast(Msg.mm(Msg.PREFIX + "<gold>Recompensa cobrada! <white><killer></white> <gray>entregou a cabeça de "
                        + "<white><victim></white> <gray>e recebeu <yellow>" + (bounty + bonus) + " " + currency
                        + (bonus > 0 ? " <dark_gray>(+" + bonus + " de fama)" : "") + "<gray>.",
                Placeholder.unparsed("killer", killer.getName()), Placeholder.unparsed("victim", victim.getName())));
        Fx.sound(killer, Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 0.8f);
        Fx.sound(killer, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.2f);
        Fx.burst(killer.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 40, 0.4, 0.3);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!mercs().isOutlaw(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            int rep = mercs().rep(player.getUniqueId());
            Msg.send(player, "<red>Você é um fora da lei. <gray>Reputação: <white>" + rep + " <dark_gray>("
                    + mercs().tier(player.getUniqueId()).styled() + "<dark_gray>)<gray>. "
                    + "Veja <white>/recompensa</white> e <white>/reino entrar</white>.");
        }, 80L);
    }
}
