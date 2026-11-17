package dev.vertexx.boss;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Cajado do Soberano Silvestre (drop do Root Sovereign). Botão direito (recarga de 6 s): um feixe de sementes cruza até 12
 * blocos e, ao tocar o solo, faz brotar um emaranhado de vinhas e cipós por 8 s; mobs (nunca jogadores) que o atravessarem
 * ficam presos por 3 s e levam 5 de dano de espinhos.
 */
public final class SovereignStaff implements Listener {

    private static final String ID = "sovereign_staff";
    private static final long COOLDOWN_MS = 6000;
    private static final double RANGE = 12;
    private static final int LIFE = 160;
    private static final double RADIUS = 2.6;
    private static final Particle.DustOptions SEED = new Particle.DustOptions(Color.fromRGB(140, 220, 80), 1.2f);
    private static final Particle.DustOptions PINK = new Particle.DustOptions(Color.fromRGB(240, 130, 190), 1.2f);

    private final PluginMain plugin;
    private final NamespacedKey key;
    private final NamespacedKey partKey;
    private final NamespacedKey bodyKey;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public SovereignStaff(PluginMain plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, ID);
        this.partKey = new NamespacedKey(plugin, "boss_part");
        this.bodyKey = new NamespacedKey(plugin, "boss_body");
    }

    /** O item (drop garantido do Soberano). */
    public static ItemStack create(PluginMain plugin) {
        NamespacedKey marker = new NamespacedKey(plugin, ID);
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        item.editMeta(meta -> {
            meta.itemName(Msg.mm("<green>Cajado do Soberano Silvestre"));
            List<Component> lore = new ArrayList<>();
            lore.add(Msg.item("<gray>Cajado de raízes do Root Sovereign."));
            lore.add(Msg.item("<green>◆ Emaranhado de Vinhas <gray>(botão direito, 6 s)"));
            lore.add(Msg.item("<dark_gray>Um feixe de sementes atinge o solo a até 12 blocos"));
            lore.add(Msg.item("<dark_gray>e faz brotar vinhas: quem passar fica preso 3 s"));
            lore.add(Msg.item("<dark_gray>e sofre 5 de dano de espinhos."));
            meta.lore(lore);
            meta.setTooltipStyle(dev.vertexx.core.Items.TOOLTIP);
            NamespacedKey model = NamespacedKey.fromString(plugin.getConfig().getString("end.items." + ID + ".model",
                    "vertexx:" + ID));
            if (model != null) {
                meta.setItemModel(model);
            }
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of(1051f));
            meta.setCustomModelDataComponent(component);
            meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public boolean is(ItemStack item) {
        return item != null && item.getType() == Material.BLAZE_ROD && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!is(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = cooldown.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            Msg.actionBar(player, "<gray>O cajado ainda recarrega: <white>" + ((COOLDOWN_MS - (now - last)) / 1000 + 1) + " s");
            return;
        }
        cooldown.put(player.getUniqueId(), now);
        cast(player);
    }

    private void cast(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, dir, RANGE, org.bukkit.FluidCollisionMode.NEVER, true);
        Location end;
        if (hit != null && hit.getHitPosition() != null) {
            end = hit.getHitPosition().toLocation(player.getWorld());
        } else {
            end = eye.clone().add(dir.clone().multiply(RANGE));
            // o feixe cai até o solo (no máximo 8 blocos abaixo do ponto final)
            for (int i = 0; i < 8 && !end.getBlock().getType().isSolid(); i++) {
                end.subtract(0, 1, 0);
            }
        }
        // o emaranhado nasce sobre o solo: sobe até achar ar
        Location spot = end.clone();
        for (int i = 0; i < 3 && spot.getBlock().getType().isSolid(); i++) {
            spot.add(0, 1, 0);
        }
        spot.setX(end.getX());
        spot.setZ(end.getZ());
        spot.setY(Math.floor(spot.getY()));
        player.swingMainHand();
        Fx.worldSound(player.getLocation(), Sound.BLOCK_BIG_DRIPLEAF_TILT_DOWN, 1.4f, 0.6f);
        Fx.worldSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1.0f, 1.4f);
        // feixe de sementes
        Location from = eye.clone().add(dir.clone().multiply(0.8)).subtract(0, 0.25, 0);
        Vector line = spot.toVector().subtract(from.toVector());
        double length = line.length();
        Vector step = length < 0.01 ? new Vector(0, 0, 0) : line.clone().normalize().multiply(0.6);
        int points = (int) Math.max(1, length / 0.6);
        for (int i = 0; i < points; i++) {
            Location at = from.clone().add(step.clone().multiply(i));
            player.getWorld().spawnParticle(Particle.DUST, at, 2, 0.05, 0.05, 0.05, 0, SEED);
            if (i % 3 == 0) {
                player.getWorld().spawnParticle(Particle.COMPOSTER, at, 1, 0.05, 0.05, 0.05, 0);
            }
        }
        sprout(player, spot);
    }

    private void sprout(Player owner, Location spot) {
        org.bukkit.World world = spot.getWorld();
        List<BlockDisplay> parts = new ArrayList<>();
        Fx.worldSound(spot, Sound.BLOCK_MANGROVE_ROOTS_PLACE, 1.6f, 0.7f);
        Fx.worldSound(spot, Sound.BLOCK_AZALEA_LEAVES_BREAK, 1.4f, 0.6f);
        world.spawnParticle(Particle.BLOCK, spot, 40, 1.2, 0.2, 1.2, 0.1, Material.MOSS_BLOCK.createBlockData());
        for (int i = 0; i < 9; i++) {
            double a = Math.PI * 2 * i / 9;
            double r = i == 0 ? 0 : 0.8 + (i % 3) * 0.7;
            Location at = spot.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            boolean root = i % 3 == 1;
            float height = root ? 1.0f : 1.6f;
            BlockDisplay display = world.spawn(at, BlockDisplay.class, d -> {
                d.setBlock((root ? Material.MANGROVE_ROOTS : Material.CAVE_VINES_PLANT).createBlockData());
                d.setPersistent(false);
                d.setBrightness(new org.bukkit.entity.Display.Brightness(12, 12));
                d.setTransformation(new Transformation(new Vector3f(-0.6f, 0, -0.6f), new Quaternionf(),
                        new Vector3f(1.2f, height, 1.2f), new Quaternionf()));
                d.getPersistentDataContainer().set(partKey, PersistentDataType.BYTE, (byte) 1);
            });
            parts.add(display);
        }
        Map<UUID, Integer> trapped = new HashMap<>();
        new BukkitRunnable() {
            private int age;

            @Override
            public void run() {
                age++;
                if (age % 6 == 0) {
                    world.spawnParticle(Particle.DUST, spot.clone().add(0, 0.5, 0), 5, RADIUS * 0.6, 0.4, RADIUS * 0.6, 0, SEED);
                    world.spawnParticle(Particle.DUST, spot.clone().add(0, 0.9, 0), 2, RADIUS * 0.5, 0.4, RADIUS * 0.5, 0, PINK);
                }
                for (Entity entity : world.getNearbyEntities(spot.clone().add(0, 1, 0), RADIUS, 1.6, RADIUS)) {
                    if (!(entity instanceof LivingEntity living) || entity instanceof Player || entity instanceof ArmorStand
                            || entity instanceof Tameable tamed && tamed.getOwner() != null
                            || entity.getPersistentDataContainer().has(partKey, PersistentDataType.BYTE)) {
                        continue;
                    }
                    boolean boss = living.getPersistentDataContainer().has(bodyKey, PersistentDataType.STRING);
                    if (!trapped.containsKey(entity.getUniqueId())) {
                        trapped.put(entity.getUniqueId(), age);
                        living.damage(5, owner);
                        Fx.worldSound(living.getLocation(), Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.4f, 0.6f);
                        world.spawnParticle(Particle.BLOCK, living.getLocation().add(0, 1, 0), 14, 0.3, 0.5, 0.3, 0.1,
                                Material.MANGROVE_ROOTS.createBlockData());
                    }
                    if (!boss && age - trapped.get(entity.getUniqueId()) <= 60) {     // preso por 3 s desde o primeiro contato
                        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 6, 6, false, false));
                        living.setVelocity(new Vector(0, Math.min(0, living.getVelocity().getY()), 0));
                    }
                }
                if (age >= LIFE) {
                    cancel();
                    parts.forEach(Entity::remove);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
