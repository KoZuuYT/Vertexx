package dev.vertexx.rpg;

import dev.vertexx.PluginMain;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * O cliente não envia clique direito no ar com a mão vazia. Para as habilidades funcionarem assim, enquanto o jogador
 * mira o ar de mão vazia, uma entidade Interaction invisível (visível só para ele, sem hitbox para projéteis) fica
 * à frente dele: o clique nela vira o clique direito (ou o Shift + esquerdo que troca de habilidade).
 */
final class EmptyHandCatcher implements Listener {

    private static final double REACH = 2.6;
    private static final double SIZE = 3.0;

    private final PluginMain plugin;
    private final SkillManager skills;
    private final Map<UUID, Interaction> catchers = new HashMap<>();

    EmptyHandCatcher(PluginMain plugin, SkillManager skills) {
        this.plugin = plugin;
        this.skills = skills;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
    }

    private void tick() {
        boolean on = skills.castEnabled();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (on && eligible(player)) {
                place(player);
            } else {
                drop(player.getUniqueId());
            }
        }
    }

    private boolean eligible(Player player) {
        if (player.isDead() || player.isInsideVehicle() || player.isGliding() || player.isSleeping()
                || player.getGameMode() == GameMode.SPECTATOR
                || !player.getInventory().getItemInMainHand().getType().isAir() || !skills.hasActives(player)) {
            return false;
        }
        Location eye = player.getEyeLocation();
        Entity mine = catchers.get(player.getUniqueId());
        RayTraceResult hit = player.getWorld().rayTrace(eye, eye.getDirection(), 5.0, FluidCollisionMode.NEVER, true, 0.1,
                e -> e != player && e != mine && !(e instanceof Interaction));
        return hit == null;
    }

    private void place(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        Location at = eye.clone().add(dir.multiply(REACH));
        at.subtract(0, SIZE / 2, 0);
        at.setYaw(0);
        at.setPitch(0);
        Interaction e = catchers.get(player.getUniqueId());
        if (e != null && e.isValid() && e.getWorld().equals(at.getWorld())) {
            e.teleport(at);
            return;
        }
        if (e != null) {
            e.remove();
        }
        Interaction created = player.getWorld().spawn(at, Interaction.class, i -> {
            i.setInteractionWidth((float) SIZE);
            i.setInteractionHeight((float) SIZE);
            i.setResponsive(true);
            i.setPersistent(false);
            i.setInvulnerable(true);
        });
        catchers.put(player.getUniqueId(), created);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.hideEntity(plugin, created);
            }
        }
    }

    private void drop(UUID id) {
        Interaction e = catchers.remove(id);
        if (e != null) {
            e.remove();
        }
    }

    private boolean mine(Player player, Entity entity) {
        return entity instanceof Interaction && catchers.get(player.getUniqueId()) == entity;
    }

    @EventHandler
    public void onUse(PlayerInteractEntityEvent event) {
        if (!mine(event.getPlayer(), event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            skills.doubleRight(event.getPlayer());
        }
    }

    @EventHandler
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (!mine(event.getPlayer(), event.getAttacked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer().isSneaking()) {
            skills.cycle(event.getPlayer());
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && mine(player, event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (Interaction e : catchers.values()) {
            event.getPlayer().hideEntity(plugin, e);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        drop(event.getPlayer().getUniqueId());
    }
}
