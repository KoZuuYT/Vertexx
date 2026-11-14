package dev.vertexx.honor;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import dev.vertexx.honor.HonorManager.Tier;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * PvP entre reinos: bloqueia fogo amigo (mesmo reino e aliados) e julga cada morte.
 * Um "confronto" lembra quem deu o primeiro golpe; matar sendo o agressor é injustificado,
 * matar quem atacou primeiro é legítima defesa.
 */
public final class CombatListener implements Listener {

    private static final long ENGAGEMENT_TIMEOUT = 30_000L;

    private static final class Engagement {
        private final UUID aggressor;
        private long lastHit;

        private Engagement(UUID aggressor, long lastHit) {
            this.aggressor = aggressor;
            this.lastHit = lastHit;
        }
    }

    private final PluginMain plugin;
    private final Map<String, Engagement> engagements = new HashMap<>();
    private final Map<UUID, Long> lastCombat = new HashMap<>();
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    public CombatListener(PluginMain plugin) {
        this.plugin = plugin;
    }

    public boolean inCombat(Player player) {
        Long last = lastCombat.get(player.getUniqueId());
        long tag = plugin.getConfig().getLong("combat.combat-tag-seconds", 10) * 1000L;
        return last != null && System.currentTimeMillis() - last < tag;
    }

    private static String pair(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    /** Jogador responsável pelo dano: direto, por projétil ou por animal domado. */
    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (damager instanceof Tameable pet && pet.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean friendly = plugin.kingdoms().friendly(attacker.getUniqueId(), victim.getUniqueId());
        if (friendly && !plugin.getConfig().getBoolean("combat.friendly-fire", false)) {
            event.setCancelled(true);
            Long warned = lastWarning.get(attacker.getUniqueId());
            if (warned == null || now - warned > 1500) {
                lastWarning.put(attacker.getUniqueId(), now);
                Msg.actionBar(attacker, "<yellow>⚔ Fogo amigo desativado <dark_gray>— <gray>" + victim.getName()
                        + " é do seu reino ou de um aliado");
            }
            return;
        }

        lastCombat.put(attacker.getUniqueId(), now);
        lastCombat.put(victim.getUniqueId(), now);
        String key = pair(attacker.getUniqueId(), victim.getUniqueId());
        Engagement engagement = engagements.get(key);
        if (engagement == null || now - engagement.lastHit > ENGAGEMENT_TIMEOUT) {
            engagements.put(key, new Engagement(attacker.getUniqueId(), now));
            // primeiro golpe de um novo confronto: atacar um desonrado não é punido
            if (plugin.honor().tier(victim.getUniqueId()) != Tier.DESONRADO) {
                plugin.honor().add(attacker, plugin.getConfig().getInt("honor.unjustified-attack", -1), "ataque injustificado");
            }
        } else {
            engagement.lastHit = now;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        lastCombat.remove(victim.getUniqueId());
        long now = System.currentTimeMillis();
        engagements.values().removeIf(e -> now - e.lastHit > ENGAGEMENT_TIMEOUT);
        if (killer == null || killer.equals(victim)) {
            return;
        }
        Engagement engagement = engagements.remove(pair(killer.getUniqueId(), victim.getUniqueId()));
        HonorManager honor = plugin.honor();
        if (plugin.kingdoms().friendly(killer.getUniqueId(), victim.getUniqueId())) {
            honor.add(killer, plugin.getConfig().getInt("honor.betrayal-kill", -25), "traição");
        } else if (honor.tier(victim.getUniqueId()) == Tier.DESONRADO) {
            honor.add(killer, plugin.getConfig().getInt("honor.dishonored-kill", 5), "justiça");
        } else if (engagement == null || engagement.aggressor.equals(killer.getUniqueId())) {
            honor.add(killer, plugin.getConfig().getInt("honor.unjustified-kill", -10), "morte injustificada");
        } else {
            honor.add(killer, plugin.getConfig().getInt("honor.defense-kill", 3), "legítima defesa");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastCombat.remove(id);
        lastWarning.remove(id);
    }
}
