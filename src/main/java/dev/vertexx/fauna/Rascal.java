package dev.vertexx.fauna;

import dev.vertexx.core.Msg;
import dev.vertexx.rpg.Stat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Criatura tímida das minas abandonadas. Brinca de esconde-esconde: quando um jogador a encontra
 * (olhando para ela de perto) ela fica contente, foge e se esconde de novo. Na terceira vez dá um
 * presente e vai embora. Não pode ser ferida.
 */
final class Rascal extends Critter {

    private enum State { HIDING, FLEEING }

    private State state = State.HIDING;
    private int stateTicks;
    private int finds;
    private int life;
    private int quiet;
    private Location goal;

    Rascal(FaunaManager manager, Mob mob) {
        super(manager, mob);
        mob.setInvulnerable(true);
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.RASCAL;
    }

    @Override
    protected float modelScale() {
        return 1.15f;
    }

    // ── Comportamento ──────────────────────────────────────────

    @Override
    protected void think() {
        life += 2;
        World world = mob.getWorld();
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player player : world.getPlayers()) {
            double d = player.getLocation().distanceSquared(mob.getLocation());
            if (d < best && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                best = d;
                nearest = player;
            }
        }
        quiet = nearest == null || best > 48 * 48 ? quiet + 2 : 0;
        if (life > 6000 || quiet > 600) {
            vanish(false);
            return;
        }
        stateTicks += 2;
        if (state == State.FLEEING) {
            flee();
            return;
        }
        mob.getPathfinder().stopPathfinding();
        if (nearest == null) {
            return;
        }
        if (age % 4 == 0) {
            for (Player player : world.getPlayers()) {
                if (found(player)) {
                    onFound(player);
                    return;
                }
            }
        }
        if (age % 70 == 0 && best < 8 * 8) {
            // risadinha abafada: pista sonora de que ele está por perto
            world.playSound(mob.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.35f, 1.9f);
            world.spawnParticle(Particle.NOTE, mob.getLocation().add(0, 0.9, 0), 1, 0.2, 0.2, 0.2, 1);
        }
    }

    /** Achado = o jogador olha para ele de perto, sem blocos no caminho (ou quase esbarra nele). */
    private boolean found(Player player) {
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR || !player.getWorld().equals(mob.getWorld())) {
            return false;
        }
        Location eye = player.getEyeLocation();
        Location me = mob.getLocation().add(0, 0.4, 0);
        double d2 = eye.distanceSquared(me);
        if (d2 > 9 * 9) {
            return false;
        }
        if (d2 < 1.6 * 1.6) {
            return true;
        }
        Vector to = me.toVector().subtract(eye.toVector()).normalize();
        return eye.getDirection().dot(to) > 0.88 && player.hasLineOfSight(mob);
    }

    private void onFound(Player player) {
        finds++;
        World world = mob.getWorld();
        int needed = Math.max(1, plugin.getConfig().getInt("fauna.rascal.finds-needed", 3));
        world.spawnParticle(Particle.HAPPY_VILLAGER, mob.getLocation().add(0, 0.9, 0), 14, 0.4, 0.4, 0.4, 0);
        world.spawnParticle(Particle.NOTE, mob.getLocation().add(0, 1.1, 0), 5, 0.4, 0.3, 0.4, 1);
        world.playSound(mob.getLocation(), Sound.ENTITY_ALLAY_ITEM_GIVEN, 1f, 1.7f);
        if (finds >= needed) {
            reward(player);
            return;
        }
        player.sendActionBar(Msg.mm("<aqua><bold>Achou!</bold> <gray>O Rascal foi encontrado <white>" + finds + "/" + needed
                + "<gray>. Ele vai se esconder de novo..."));
        goal = hideSpot(player);
        state = State.FLEEING;
        stateTicks = 0;
        world.playSound(mob.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.8f, 2f);
    }

    private void flee() {
        if (goal == null) {
            hideAgain();
            return;
        }
        mob.getPathfinder().moveTo(goal, 1.9);
        double d2 = mob.getLocation().distanceSquared(goal);
        if (d2 < 2.5 || stateTicks > 120) {
            if (d2 > 16) { // preso: some num estalo e reaparece no esconderijo
                mob.getWorld().spawnParticle(Particle.POOF, mob.getLocation().add(0, 0.5, 0), 12, 0.3, 0.3, 0.3, 0.03);
                mob.teleport(goal);
                mob.getWorld().spawnParticle(Particle.POOF, goal.clone().add(0, 0.5, 0), 12, 0.3, 0.3, 0.3, 0.03);
            }
            hideAgain();
        }
    }

    private void hideAgain() {
        state = State.HIDING;
        stateTicks = 0;
        goal = null;
        mob.getPathfinder().stopPathfinding();
    }

    /** Ponto de 8 a 16 blocos dali, onde o chão é firme e o jogador não tem linha de visão. */
    private Location hideSpot(Player player) {
        World world = mob.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location fallback = null;
        for (int i = 0; i < 40; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 8 + random.nextDouble() * 8;
            int x = (int) Math.floor(mob.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(mob.getZ() + Math.sin(angle) * dist);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            for (int dy = 3; dy >= -4; dy--) {
                Block b = world.getBlockAt(x, mob.getLocation().getBlockY() + dy, z);
                if (b.isPassable() && !b.isLiquid() && b.getRelative(0, 1, 0).isPassable()
                        && !b.getRelative(0, -1, 0).isPassable() && !b.getRelative(0, -1, 0).isLiquid()) {
                    Location spot = b.getLocation().add(0.5, 0, 0.5);
                    if (fallback == null) {
                        fallback = spot;
                    }
                    Location eye = player.getEyeLocation();
                    Vector dir = spot.clone().add(0, 0.5, 0).toVector().subtract(eye.toVector());
                    double length = dir.length();
                    if (length > 0.1 && world.rayTraceBlocks(eye, dir.normalize(), length, FluidCollisionMode.NEVER, true) != null) {
                        return spot;
                    }
                    break;
                }
            }
        }
        return fallback;
    }

    private void reward(Player player) {
        World world = mob.getWorld();
        ItemStack pick = new ItemStack(Material.IRON_PICKAXE);
        pick.editMeta(meta -> {
            meta.displayName(Msg.item("<aqua><bold>Picareta do Rascal"));
            meta.lore(List.of(Msg.item("<gray>Presente de um amigo das minas.")));
            meta.addEnchant(Enchantment.EFFICIENCY, 3, true);
            meta.addEnchant(Enchantment.UNBREAKING, 2, true);
        });
        world.dropItemNaturally(player.getLocation(), pick);
        plugin.rpg().addXp(player, Stat.MINING, 150);
        player.sendMessage(Msg.mm("<aqua><bold>Rascal</bold> <gray>» Você me achou três vezes! Tome, <white>um presente</white>."));
        world.playSound(mob.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, mob.getLocation().add(0, 1, 0), 30, 0.5, 0.6, 0.5, 0.3);
        vanish(true);
    }

    private void vanish(boolean happy) {
        World world = mob.getWorld();
        world.spawnParticle(happy ? Particle.HAPPY_VILLAGER : Particle.POOF, mob.getLocation().add(0, 0.6, 0), 16, 0.4, 0.4, 0.4, 0.02);
        world.playSound(mob.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.7f, 1.2f);
        mob.remove();
    }

    // ── Desenho ────────────────────────────────────────────────

    @Override
    protected void pose() {
        if (state == State.FLEEING) {
            model.play("flee");
            model.speed(1f);
        } else if (gait() > 0.1f) {
            model.play("walk");
            model.speed(0.8f + gait() * 0.6f);
        } else {
            // escondido: agachado, só a caixa balançando; espia de vez em quando
            model.play((age / 120) % 3 == 2 ? "idle" : "hide");
            model.speed(1f);
        }
    }
}
