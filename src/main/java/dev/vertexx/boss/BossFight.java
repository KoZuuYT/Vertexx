package dev.vertexx.boss;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Base de uma luta de boss. O "corpo" real é um golem de ferro invisível, sem IA, que guarda vida e
 * hitbox; o visual é um {@link Rig} de displays e todo o movimento é calculado aqui, dentro da sala.
 */
abstract class BossFight {

    enum State { DORMANT, WAKING, ACTIVE, DYING }

    protected final PluginMain plugin;
    protected final BossManager manager;
    protected final Arena arena;
    protected final BossKind kind;
    protected final World world;
    protected IronGolem hitbox;
    protected Rig rig;
    protected dev.vertexx.model.ModelInstance model;
    protected State state = State.DORMANT;
    protected int age;
    protected int stateTicks;
    protected Player target;
    protected final Location pos;
    protected float yaw;
    protected boolean enraged;
    protected boolean exposed;
    protected int exposedTicks;
    protected int hurtTicks;
    protected final Set<UUID> participants = new HashSet<>();
    private BossBar bar;
    private int emptyTicks;
    private int targetTicks;
    private final NamespacedKey bossKey;
    private final List<org.bukkit.entity.Entity> loose = new ArrayList<>();
    protected double maxHealth;

    BossFight(BossManager manager, Arena arena) {
        this.manager = manager;
        this.plugin = manager.plugin();
        this.arena = arena;
        this.kind = arena.kind;
        this.world = arena.bukkitWorld();
        this.bossKey = manager.bossKey();
        this.pos = arena.center().add(0, startHeight(), 0);
        this.yaw = yawFor(arena.fx, arena.fz);
    }

    /** Yaw do Bukkit para olhar na direção (dx, dz). */
    static float yawFor(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    // ── Criação ────────────────────────────────────────────────

    protected abstract double startHeight();

    protected abstract double baseHealth();

    protected abstract double hitboxScale();

    /** Modelo (bbmodel compilado) que desenha o corpo do boss. */
    protected abstract String modelId();

    protected float modelScale() {
        return 1f;
    }

    /** Peças extras (efeitos, aura, gelo) em BlockDisplays; o corpo vem do modelo. */
    protected abstract void buildRig();

    protected abstract void animate();

    protected abstract void aiTick();

    protected abstract void wakeTick(int t);

    protected abstract int wakeDuration();

    protected abstract String title();

    protected abstract String subtitle();

    /** Aplica a pose de repouso (animação do estado dormente e do despertar). */
    protected abstract void idlePose();

    protected abstract void onEnrage();

    /** Força um ataque pelo nome (comando de teste). */
    abstract boolean forceMove(String name);

    abstract List<String> moves();

    void spawn() {
        Location feet = pos.clone();
        feet.setYaw(yaw);
        hitbox = world.spawn(feet, IronGolem.class, golem -> {
            golem.setAI(false);
            golem.setGravity(false);
            golem.setSilent(true);
            golem.setInvisible(true);
            golem.setPersistent(false);
            golem.setRemoveWhenFarAway(false);
            golem.setCanPickupItems(false);
            golem.setPlayerCreated(false);
            golem.setInvulnerable(true);
            golem.setCollidable(false);
            // nome próprio: mensagens de morte ("morto por Wildfire"), legendas etc. não citam o golem de ferro-base
            golem.customName(dev.vertexx.core.Msg.mm(kind.color() + kind.display()));
            golem.setCustomNameVisible(false);
            golem.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, arena.id);
            AttributeInstance scale = golem.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(hitboxScale());
            }
            AttributeInstance max = golem.getAttribute(Attribute.MAX_HEALTH);
            maxHealth = baseHealth();
            if (max != null) {
                max.setBaseValue(maxHealth);
            }
            AttributeInstance knock = golem.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (knock != null) {
                knock.setBaseValue(1.0);
            }
            golem.setHealth(maxHealth);
        });
        plugin.bedrock().track(hitbox);
        model = plugin.entityModels().spawn(modelId(), manager.partKey(), feet, modelScale());
        rig = new Rig(manager.partKey(), feet);
        buildRig();
        idlePose();
        animate();
        model.update(pos, yaw, 0);
        rig.update(pos, Rig.yaw(yaw), 2);
    }

    boolean valid() {
        return hitbox != null && hitbox.isValid();
    }

    Player target() {
        return target;
    }

    boolean ownsHitbox(org.bukkit.entity.Entity entity) {
        return hitbox != null && hitbox.getUniqueId().equals(entity.getUniqueId());
    }

    double health() {
        return hitbox == null ? 0 : hitbox.getHealth();
    }

    double maxHealth() {
        return maxHealth;
    }

    boolean dormant() {
        return state == State.DORMANT;
    }

    boolean active() {
        return state == State.ACTIVE;
    }

    boolean dying() {
        return state == State.DYING;
    }

    boolean vulnerable() {
        return state == State.ACTIVE;
    }

    // ── Ciclo ──────────────────────────────────────────────────

    /** Chamado a cada tick pelo gerenciador. */
    void tick() {
        if (state != State.DYING && !valid()) {
            if (state != State.DYING) {
                manager.endFight(this, false);
            }
            return;
        }
        age++;
        stateTicks++;
        double prevX = pos.getX();
        double prevY = pos.getY();
        double prevZ = pos.getZ();
        if (hurtTicks > 0) {
            hurtTicks--;
        }
        switch (state) {
            case DORMANT -> {
                idlePose();
                dormantTick();
                if (age % 10 == 0) {
                    checkAwaken();
                }
            }
            case WAKING -> {
                wakeTick(stateTicks);
                if (stateTicks >= wakeDuration()) {
                    state = State.ACTIVE;
                    stateTicks = 0;
                    hitbox.setInvulnerable(false);
                }
            }
            case ACTIVE -> activeTick();
            case DYING -> deathTick();
        }
        if (state != State.DYING && state != State.DORMANT) {
            collide(prevX, prevY, prevZ);
        }
        if (age % 2 == 0) {
            animate();
        }
        if (age % 2 == 0 && rig != null) {
            Location at = pos.clone();
            at.setYaw(yaw);
            rig.update(at, Rig.yaw(yaw), 2);
            if (model != null) {
                model.advance(2);
                model.update(at, yaw, 2);
            }
        }
        syncHitbox();
        if (age % 5 == 0) {
            updateBar();
        }
    }

    /** Chamado a cada tick enquanto o boss dorme (armadilhas e guardiões da Cidadela Soterrada). */
    protected void dormantTick() {
    }

    protected void checkAwaken() {
        for (Player player : insidePlayers()) {
            if (arena.contains(player.getLocation(), -3)) {
                awaken();
                return;
            }
        }
    }

    /** Desperta o boss: cinemática, selo da sala e vida ajustada ao número de jogadores. */
    void awaken() {
        if (state != State.DORMANT) {
            return;
        }
        state = State.WAKING;
        stateTicks = 0;
        List<Player> players = insidePlayers();
        int count = Math.max(1, Math.min(4, players.size()));
        double scaled = baseHealth() * (1 + 0.6 * (count - 1));
        maxHealth = scaled;
        AttributeInstance max = hitbox.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(scaled);
        }
        hitbox.setHealth(scaled);
        for (Player player : players) {
            participants.add(player.getUniqueId());
            manager.plugin().rpg().cinematics().bossAwakens(player, kind.color() + "<bold>" + kind.display().toUpperCase(),
                    subtitle());
        }
        manager.seal(arena, true);
        bar = BossBar.bossBar(Msg.mm(kind.color() + "<bold>" + kind.display() + " <dark_gray>• <white>" + title()),
                1f, kind.barColor(), BossBar.Overlay.NOTCHED_10);
    }

    private void activeTick() {
        List<Player> inside = insidePlayers();
        for (Player player : inside) {
            participants.add(player.getUniqueId());
        }
        if (inside.isEmpty()) {
            emptyTicks++;
            if (emptyTicks > 240 && !arena.temp) {
                manager.endFight(this, false);
                return;
            }
        } else {
            emptyTicks = 0;
        }
        if (target == null || !validTarget(target) || ++targetTicks > 60) {
            targetTicks = 0;
            target = nearest(inside);
        }
        if (exposedTicks > 0 && --exposedTicks == 0) {
            exposed = false;
        }
        if (!enraged && hitbox.getHealth() < maxHealth * 0.5) {
            enraged = true;
            onEnrage();
        }
        aiTick();
    }

    private void deathTick() {
        if (stateTicks == 1) {
            deathStart();
        }
        deathAnimate(stateTicks);
        if (stateTicks >= 50) {
            manager.endFight(this, true);
        }
    }

    protected void deathStart() {
    }

    protected void deathAnimate(int t) {
    }

    /** Chamado pelo gerenciador quando o corpo morre. */
    void onDeath(EntityDeathEvent event) {
        event.getDrops().clear();
        event.setDroppedExp(0);
        state = State.DYING;
        stateTicks = 0;
        hitbox.setInvulnerable(true);
        manager.victory(this);
    }

    /** Versão com o evento (para saber quem atacou); por padrão usa só o valor. */
    double damageTaken(org.bukkit.event.entity.EntityDamageEvent event, double base) {
        return damageTaken(base);
    }

    /** Dano recebido pelo corpo; devolve o multiplicador (exposto = mais dano). */
    double damageTaken(double base) {
        hurtTicks = 6;
        return exposed ? base * 1.5 : base;
    }

    abstract boolean blocksProjectile(org.bukkit.entity.Projectile projectile);

    // ── Remoção ────────────────────────────────────────────────

    void track(org.bukkit.entity.Entity entity) {
        loose.add(entity);
    }

    void remove() {
        if (rig != null) {
            rig.remove();
        }
        if (model != null) {
            model.remove();
        }
        if (hitbox != null) {
            hitbox.remove();
        }
        for (org.bukkit.entity.Entity entity : loose) {
            entity.remove();
        }
        loose.clear();
        hideBar();
        cleanup();
    }

    protected void cleanup() {
    }

    // ── Barra de chefe ─────────────────────────────────────────

    private void updateBar() {
        if (bar == null) {
            return;
        }
        bar.progress((float) Math.max(0, Math.min(1, hitbox.getHealth() / maxHealth)));
        bar.color(exposed ? BossBar.Color.WHITE : enraged ? BossBar.Color.PURPLE : kind.barColor());
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean near = player.getWorld().equals(world) && arena.contains(player.getLocation(), 14);
            if (near) {
                player.showBossBar(bar);
            } else {
                player.hideBossBar(bar);
            }
        }
    }

    void hideBar() {
        if (bar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(bar);
            }
        }
    }

    // ── Utilidades de combate ──────────────────────────────────

    protected boolean validTarget(Player player) {
        return player.isOnline() && !player.isDead() && player.getWorld().equals(world)
                && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                && arena.contains(player.getLocation(), 1);
    }

    protected List<Player> insidePlayers() {
        List<Player> list = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (validTarget(player)) {
                list.add(player);
            }
        }
        return list;
    }

    private Player nearest(List<Player> players) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : players) {
            double d = player.getLocation().distanceSquared(pos);
            if (d < bestDistance) {
                bestDistance = d;
                best = player;
            }
        }
        return best;
    }

    /** Ponto de mira: o alvo atual ou, sem jogadores (testes), o centro da sala. */
    protected Location aim() {
        return target != null && target.isOnline() ? target.getLocation().add(0, 1, 0)
                : arena.center().add(0, 1, 0);
    }

    protected void damage(Player player, double amount) {
        player.damage(amount * manager.damageMultiplier(), hitbox);
    }

    protected void warn(String text) {
        for (Player player : insidePlayers()) {
            player.sendActionBar(Msg.mm(text));
        }
    }

    protected void faceTowards(Location at, float turn) {
        float want = yawFor(at.getX() - pos.getX(), at.getZ() - pos.getZ());
        float diff = ((want - yaw + 540) % 360) - 180;
        yaw += Math.max(-turn, Math.min(turn, diff));
    }

    /** Move a posição lógica em direção a um ponto, sem passar de {@code speed} blocos por tick. */
    protected void moveToward(double x, double y, double z, double speed) {
        Vector delta = new Vector(x - pos.getX(), y - pos.getY(), z - pos.getZ());
        double length = delta.length();
        if (length > speed) {
            delta.multiply(speed / length);
        }
        pos.add(delta);
        clamp();
    }

    protected void clamp() {
        double limit = arena.half - 2.2;
        double dx = pos.getX() - (arena.cx + 0.5);
        double dz = pos.getZ() - (arena.cz + 0.5);
        double d = Math.hypot(dx, dz);
        if (kind.round() ? d > limit : Math.abs(dx) > limit || Math.abs(dz) > limit) {
            if (kind.round()) {
                pos.setX(arena.cx + 0.5 + dx / d * limit);
                pos.setZ(arena.cz + 0.5 + dz / d * limit);
            } else {
                pos.setX(arena.cx + 0.5 + Math.max(-limit, Math.min(limit, dx)));
                pos.setZ(arena.cz + 0.5 + Math.max(-limit, Math.min(limit, dz)));
            }
        }
        pos.setY(Math.max(arena.fy, Math.min(arena.fy + arena.height - 5, pos.getY())));
    }

    /** Meia largura e altura do corpo, usadas para impedir que o boss entre em blocos. */
    protected double bodyRadius() {
        return 0.8;
    }

    protected double bodyHeight() {
        return 2.6;
    }

    protected boolean blocked(double x, double y, double z) {
        double r = bodyRadius();
        // degraus baixos (tapetes, lajes, neve, 0,6) não travam o corpo
        return world.hasCollisionsIn(new org.bukkit.util.BoundingBox(x - r, y + 0.6, z - r, x + r,
                y + bodyHeight(), z + r));
    }

    /**
     * Colisão com blocos: se o novo ponto está dentro de uma parede e o anterior não estava, o eixo
     * correspondente volta atrás (o boss desliza ao longo da parede em vez de atravessá-la).
     */
    private void collide(double px, double py, double pz) {
        if (!blocked(pos.getX(), pos.getY(), pos.getZ()) || blocked(px, py, pz)) {
            return;
        }
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        double ax = blocked(x, py, pz) ? px : x;
        double az = blocked(ax, py, z) ? pz : z;
        double ay = blocked(ax, y, az) ? py : y;
        if (blocked(ax, ay, az)) {
            ax = px;
            ay = py;
            az = pz;
        }
        pos.setX(ax);
        pos.setY(ay);
        pos.setZ(az);
    }

    private void syncHitbox() {
        if (valid() && state != State.DYING) {
            Location at = pos.clone();
            at.setYaw(yaw);
            hitbox.teleport(at);
        }
    }

    protected void particles(Particle particle, Location at, int count, double dx, double dy, double dz, double extra) {
        world.spawnParticle(particle, at, count, dx, dy, dz, extra);
    }

    protected static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
