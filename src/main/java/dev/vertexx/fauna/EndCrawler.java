package dev.vertexx.fauna;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Rastejador do Fim: besouro de obsidiana neutro. Ignora os jogadores até alguém minerar cristais de ametista por
 * perto; resiste bem a projéteis e se enrola (quase invulnerável) ao receber um golpe crítico.
 */
final class EndCrawler extends Critter {

    private Player target;
    private int aggroUntil;
    private int curlUntil;
    private int biteCooldown;
    private int wander;

    EndCrawler(FaunaManager manager, Mob mob) {
        super(manager, mob);
        mob.setSilent(true);
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.END_CRAWLER;
    }

    /** Alguém mexeu nos cristais (ou bateu nele): persegue o jogador por 30 segundos. */
    void provoke(Player player) {
        target = player;
        aggroUntil = age + 600;
    }

    private boolean curled() {
        return age < curlUntil;
    }

    @Override
    protected void think() {
        if (biteCooldown > 0) {
            biteCooldown -= 2;
        }
        if (curled()) {
            mob.getPathfinder().stopPathfinding();
            return;
        }
        if (target != null && (age >= aggroUntil || !target.isValid() || target.getWorld() != mob.getWorld()
                || target.getGameMode() == GameMode.CREATIVE || target.getGameMode() == GameMode.SPECTATOR)) {
            target = null;
        }
        if (target != null) {
            double d2 = target.getLocation().distanceSquared(mob.getLocation());
            if (d2 > 28 * 28) {
                target = null;
            } else if (d2 < 2.2 * 2.2) {
                if (biteCooldown <= 0) {
                    biteCooldown = 24;
                    model.restart("bite");
                    mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_SILVERFISH_AMBIENT, 1f, 0.5f);
                    target.damage(4, mob);
                }
            } else {
                mob.getPathfinder().moveTo(target.getLocation(), 1.25);
            }
            return;
        }
        if (wander-- <= 0) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            wander = 50 + r.nextInt(100);
            if (r.nextInt(3) != 0) {
                Location to = mob.getLocation().add(r.nextDouble(-6, 6), 0, r.nextDouble(-6, 6));
                mob.getPathfinder().moveTo(to, 0.8);
            }
        }
    }

    @Override
    protected void onHurt(Entity attacker) {
        if (attacker instanceof Player player) {
            provoke(player);
            boolean critical = player.getFallDistance() > 0 && !player.isOnGround() && !player.isSprinting();
            if (critical) {
                curlUntil = age + 70;
                mob.getWorld().playSound(mob.getLocation(), Sound.BLOCK_DEEPSLATE_BRICKS_PLACE, 1f, 0.6f);
            }
        }
    }

    @Override
    double resist(EntityDamageEvent event) {
        double k = 1;
        if (event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
            k *= 0.3;                 // carapaça de obsidiana
        }
        if (curled()) {
            k *= 0.25;
        }
        return k;
    }

    @Override
    protected void pose() {
        if ("bite".equals(model.playing()) && !model.finished()) {
            return;
        }
        model.play(curled() ? "curl" : speed > 0.01 ? "walk" : "idle");
        model.speed(curled() ? 1f : (float) Math.max(0.6, Math.min(1.6, speed * 8)));
    }
}
