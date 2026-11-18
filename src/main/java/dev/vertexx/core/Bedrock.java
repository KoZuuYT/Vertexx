package dev.vertexx.core;

import dev.vertexx.PluginMain;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Suporte a jogadores Bedrock (Geyser/Floodgate).
 *
 * <p>O Geyser não desenha ItemDisplay nem BlockDisplay (os modelos custom do Vertexx) e não repassa o resource pack
 * Java. Por isso:
 * <ul>
 *   <li>{@link #is(Player)} detecta o jogador Bedrock (Floodgate, depois Geyser, depois UUID/prefixo);</li>
 *   <li>o pacote Java não é oferecido a eles (os menus caem no visual vanilla, que já existe);</li>
 *   <li>cada mob com modelo custom (o mob-base fica invisível) ganha um <b>duplo vanilla</b> do mesmo tipo, visível só
 *       para jogadores Bedrock por perto: segue o mob-base, e dano e clique direito nele são repassados ao mob de verdade.</li>
 * </ul>
 * Nada disso roda enquanto não há Bedrock online.
 */
public final class Bedrock implements Listener {

    private static final String TAG = "vx_bedrock";
    private static final double RANGE = 64;

    private static final class Tracked {
        final Mob mob;
        LivingEntity proxy;
        final Set<UUID> shown = new HashSet<>();

        Tracked(Mob mob) {
            this.mob = mob;
        }
    }

    private final PluginMain plugin;
    private final Set<UUID> bedrock = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Tracked> tracked = new HashMap<>();
    /** Duplo -> mob de verdade. */
    private final Map<UUID, Mob> owners = new HashMap<>();
    private BukkitTask task;
    private Method floodgateIs;
    private Object floodgate;
    private Method geyserIs;
    private Object geyser;

    public Bedrock(PluginMain plugin) {
        this.plugin = plugin;
        hook();
    }

    private void hook() {
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgate = api.getMethod("getInstance").invoke(null);
            floodgateIs = api.getMethod("isFloodgatePlayer", UUID.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            floodgate = null;
        }
        try {
            Class<?> api = Class.forName("org.geysermc.geyser.api.GeyserApi");
            geyser = api.getMethod("api").invoke(null);
            geyserIs = api.getMethod("isBedrockPlayer", UUID.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            geyser = null;
        }
    }

    public void start() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::cycle, 20L, 2L);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            detect(player);
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Tracked t : tracked.values()) {
            drop(t);
        }
        tracked.clear();
        owners.clear();
    }

    // ── Detecção ───────────────────────────────────────────────

    private boolean detect(Player player) {
        UUID id = player.getUniqueId();
        boolean result = false;
        try {
            if (floodgate != null) {
                result = (Boolean) floodgateIs.invoke(floodgate, id);
            }
            if (!result && geyser != null) {
                result = (Boolean) geyserIs.invoke(geyser, id);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // API indisponível neste instante: cai nas heurísticas
        }
        // UUID do Floodgate (0000…) e o prefixo padrão "." do nome
        result = result || id.getMostSignificantBits() == 0L || player.getName().startsWith(".");
        if (result) {
            bedrock.add(id);
        } else {
            bedrock.remove(id);
        }
        return result;
    }

    /** Verdadeiro se o jogador entrou pelo Bedrock (Geyser/Floodgate). */
    public boolean is(Player player) {
        return bedrock.contains(player.getUniqueId()) || (player.isOnline() && detect(player));
    }

    public boolean anyOnline() {
        return !bedrock.isEmpty();
    }

    public int count() {
        return bedrock.size();
    }

    public boolean geyserPresent() {
        return floodgate != null || geyser != null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        detect(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        bedrock.remove(id);
        for (Tracked t : tracked.values()) {
            t.shown.remove(id);
        }
    }

    // ── Duplo vanilla dos mobs com modelo ──────────────────────

    /** Passa a cuidar de um mob-base invisível (o modelo custom dele não aparece no Bedrock). */
    public void track(Mob mob) {
        tracked.putIfAbsent(mob.getUniqueId(), new Tracked(mob));
    }

    private List<Player> viewers(Mob mob) {
        List<Player> list = new ArrayList<>();
        Location at = mob.getLocation();
        for (Player player : mob.getWorld().getPlayers()) {
            if (bedrock.contains(player.getUniqueId()) && player.getLocation().distanceSquared(at) <= RANGE * RANGE) {
                list.add(player);
            }
        }
        return list;
    }

    private void cycle() {
        if (tracked.isEmpty()) {
            return;
        }
        boolean any = !bedrock.isEmpty();
        var iterator = tracked.values().iterator();
        while (iterator.hasNext()) {
            Tracked t = iterator.next();
            if (!t.mob.isValid()) {
                drop(t);
                iterator.remove();
                continue;
            }
            List<Player> watching = any ? viewers(t.mob) : List.of();
            if (watching.isEmpty()) {
                drop(t);
                continue;
            }
            if (t.proxy == null || !t.proxy.isValid()) {
                t.shown.clear();
                t.proxy = spawnProxy(t.mob);
                if (t.proxy == null) {
                    continue;
                }
                owners.put(t.proxy.getUniqueId(), t.mob);
            }
            follow(t);
            Set<UUID> now = new HashSet<>();
            for (Player player : watching) {
                now.add(player.getUniqueId());
                if (t.shown.add(player.getUniqueId())) {
                    player.showEntity(plugin, t.proxy);
                }
            }
            for (UUID id : new ArrayList<>(t.shown)) {
                if (!now.contains(id)) {
                    t.shown.remove(id);
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) {
                        player.hideEntity(plugin, t.proxy);
                    }
                }
            }
        }
    }

    private void follow(Tracked t) {
        Location at = t.mob.getLocation();
        if (t.proxy.getLocation().distanceSquared(at) > 0.0004 || t.proxy.getLocation().getYaw() != at.getYaw()) {
            t.proxy.teleport(at);
        }
        t.proxy.setBodyYaw(t.mob.getBodyYaw());
        t.proxy.setRotation(t.mob.getLocation().getYaw(), t.mob.getLocation().getPitch());
    }

    private LivingEntity spawnProxy(Mob mob) {
        Class<? extends Entity> type = mob.getType().getEntityClass();
        if (type == null) {
            return null;
        }
        Entity entity = mob.getWorld().spawn(mob.getLocation(), type, e -> {
            e.addScoreboardTag(TAG);
            e.setPersistent(false);
            e.setVisibleByDefault(false);
            e.setSilent(true);
            e.setGravity(false);
            if (e instanceof Mob m) {
                m.setAI(false);
                m.setRemoveWhenFarAway(false);
                m.setCanPickupItems(false);
                m.setAware(false);
            }
            if (e instanceof LivingEntity living) {
                living.setCollidable(false);
                living.setInvisible(false);
                AttributeInstance scale = living.getAttribute(Attribute.SCALE);
                AttributeInstance from = mob.getAttribute(Attribute.SCALE);
                if (scale != null && from != null) {
                    scale.setBaseValue(from.getBaseValue());
                }
            }
            if (e instanceof Slime slime && mob instanceof Slime base) {
                slime.setSize(base.getSize());
            }
        });
        return entity instanceof LivingEntity living ? living : null;
    }

    private void drop(Tracked t) {
        if (t.proxy != null) {
            owners.remove(t.proxy.getUniqueId());
            t.proxy.remove();
            t.proxy = null;
        }
        t.shown.clear();
    }

    // ── O duplo é só um desenho: dano e cliques vão para o mob de verdade ──

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        Mob owner = owners.get(event.getEntity().getUniqueId());
        if (owner == null) {
            return;
        }
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent byEntity && owner.isValid()) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }
            owner.damage(event.getDamage(), damager);
            if (event.getEntity() instanceof LivingEntity living) {
                living.playHurtAnimation(0);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        Mob owner = owners.get(event.getRightClicked().getUniqueId());
        if (owner == null) {
            return;
        }
        event.setCancelled(true);
        if (owner.isValid()) {
            Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(event.getPlayer(), owner, event.getHand()));
        }
    }

    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (owners.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTarget(EntityTargetEvent event) {
        if (owners.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        } else if (event.getTarget() != null && owners.containsKey(event.getTarget().getUniqueId())) {
            Mob owner = owners.get(event.getTarget().getUniqueId());
            event.setTarget(null);
            if (owner != null && event.getEntity() instanceof Mob attacker) {
                attacker.setTarget(owner);
            }
        }
    }
}
