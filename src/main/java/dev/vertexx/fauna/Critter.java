package dev.vertexx.fauna;

import dev.vertexx.PluginMain;
import dev.vertexx.boss.Rig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Mob;

/**
 * Uma criatura rejeitada em jogo: um mob de verdade (invisível) cuida de vida, colisão e navegação, e um
 * {@link Rig} de displays desenha o modelo. A lógica roda a cada 2 ticks e o desenho só é atualizado
 * quando há jogadores por perto.
 */
abstract class Critter {

    protected final FaunaManager manager;
    protected final PluginMain plugin;
    protected final Mob mob;
    protected final Rig rig;
    protected int age;
    protected double speed;
    protected float phase;
    private double lastX;
    private double lastZ;
    private boolean near = true;

    Critter(FaunaManager manager, Mob mob) {
        this.manager = manager;
        this.plugin = manager.plugin();
        this.mob = mob;
        this.rig = new Rig(manager.partKey(), mob.getLocation());
        mob.setInvisible(true);
        if (wild()) {
            mob.setSilent(true);
            Bukkit.getMobGoals().removeAllGoals(mob);
            mob.getPathfinder().stopPathfinding();
        }
        lastX = mob.getX();
        lastZ = mob.getZ();
    }

    /** Criaturas "selvagens" não usam a IA original do mob-base: tudo é comandado pelo plugin. */
    protected boolean wild() {
        return true;
    }

    abstract FaunaKind kind();

    protected abstract void build();

    protected abstract void think();

    protected abstract void pose();

    final void start() {
        build();
        rig.update(mob.getLocation(), Rig.yaw(yaw()), 1);
    }

    protected float yaw() {
        return mob.getBodyYaw();
    }

    protected boolean near() {
        return near;
    }

    /** Devolve falso quando a criatura deve ser descartada. */
    final boolean tick() {
        if (!mob.isValid()) {
            return false;
        }
        age += 2;
        speed = Math.hypot(mob.getX() - lastX, mob.getZ() - lastZ) / 2.0;
        lastX = mob.getX();
        lastZ = mob.getZ();
        phase += (float) (speed * 2 * 3.4);
        think();
        if (!mob.isValid()) {
            return false;
        }
        if (age % 20 == 0) {
            near = !mob.getWorld().getNearbyPlayers(mob.getLocation(), 56).isEmpty();
        }
        if (near) {
            pose();
            Location at = mob.getLocation();
            rig.update(at, Rig.yaw(yaw()), 2);
        }
        return true;
    }

    /** Amplitude do balanço das pernas: zero parado, cheia andando. */
    protected float gait() {
        return (float) Math.min(1.0, speed * 7.0);
    }

    /** Posiciona uma perna (e o casco/filho) girando em torno do quadril. */
    protected static void swing(Rig.Part part, float x, float hipY, float hipZ, float length, float angle) {
        part.at(x, hipY - (float) Math.cos(angle) * length / 2f, hipZ - (float) Math.sin(angle) * length / 2f);
        part.rot.identity().rotateX(angle);
    }

    /** Chamado ao remover a criatura do mundo (descarregar, morrer, sumir). */
    void remove() {
        rig.remove();
        cleanup();
    }

    protected void cleanup() {
    }

    /** Dano/interação específicos; devolve verdadeiro se tratou o evento. */
    boolean interact(org.bukkit.entity.Player player, org.bukkit.inventory.ItemStack hand) {
        return false;
    }
}
