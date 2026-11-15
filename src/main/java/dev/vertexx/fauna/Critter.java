package dev.vertexx.fauna;

import dev.vertexx.PluginMain;
import dev.vertexx.model.ModelInstance;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Mob;

/**
 * Uma criatura rejeitada em jogo: um mob de verdade (invisível) cuida de vida, colisão e navegação, e um
 * {@link ModelInstance} (modelo bbmodel animado) desenha a criatura. A lógica roda a cada 2 ticks e o desenho só é atualizado
 * quando há jogadores por perto.
 */
abstract class Critter {

    protected final FaunaManager manager;
    protected final PluginMain plugin;
    protected final Mob mob;
    protected final ModelInstance model;
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
        this.model = plugin.entityModels().spawn(modelId(), manager.partKey(), mob.getLocation(), modelScale());
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

    /** Arquivo do modelo em {@code models/src} (sem extensão). */
    protected String modelId() {
        return kind().name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Multiplicador sobre a escala padrão do modelo. */
    protected float modelScale() {
        return 1f;
    }

    /** Ajustes depois que o modelo existe (ossos escondidos, tinta…). */
    protected void build() {
    }

    protected abstract void think();

    protected abstract void pose();

    final void start() {
        build();
        pose();
        model.update(mob.getLocation(), yaw(), 0);
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
            model.advance(2);
            model.update(mob.getLocation(), yaw(), 2);
        }
        return true;
    }

    /** Amplitude do balanço das pernas: zero parado, cheia andando. */
    protected float gait() {
        return (float) Math.min(1.0, speed * 7.0);
    }

    /** Chamado ao remover a criatura do mundo (descarregar, morrer, sumir). */
    void remove() {
        model.remove();
        cleanup();
    }

    protected void cleanup() {
    }

    /** Dano/interação específicos; devolve verdadeiro se tratou o evento. */
    boolean interact(org.bukkit.entity.Player player, org.bukkit.inventory.ItemStack hand) {
        return false;
    }
}
