package dev.vertexx.rpg;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Uso das habilidades: ativas (Shift+botão direito usa, Shift+botão esquerdo troca), mineração em área, veio, derrubada de árvores,
 * golpe atordoante, colheita, reposição, ímã e frenesi. Nenhuma habilidade afeta outros jogadores.
 */
public final class SkillManager implements Listener {

    private static final int[][] NEIGHBORS = neighbors();

    private final PluginMain plugin;
    private final Map<UUID, EnumMap<Skill, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> areaUntil = new HashMap<>();
    private final Map<UUID, Long> stunArmed = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();

    public SkillManager(PluginMain plugin) {
        this.plugin = plugin;
    }

    private RpgManager rpg() {
        return plugin.rpg();
    }

    private static int[][] neighbors() {
        List<int[]> list = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        list.add(new int[]{x, y, z});
                    }
                }
            }
        }
        return list.toArray(new int[0][]);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        areaUntil.remove(id);
        stunArmed.remove(id);
        cooldowns.remove(id);
    }

    // ── Ativas ─────────────────────────────────────────────────

    public static int cooldownSeconds(Skill skill, int rank) {
        return switch (skill) {
            case DASH -> new int[]{9, 7, 5}[Math.min(rank, 3) - 1];
            case AREA_MINER -> rank >= 2 ? 75 : 90;
            case STUN -> new int[]{20, 16, 12}[Math.min(rank, 3) - 1];
            default -> 0;
        };
    }

    private List<Skill> unlockedActives(PlayerData d) {
        List<Skill> list = new ArrayList<>();
        for (Skill skill : Skill.values()) {
            if (skill.active() && d.rank(skill) > 0) {
                list.add(skill);
            }
        }
        return list;
    }

    /** Shift + botão direito usa a habilidade escolhida; Shift + botão esquerdo (no ar) troca a habilidade. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !player.isSneaking()
                || !rpg().enabled() || !plugin.getConfig().getBoolean("rpg.cast-with-shift-click", true)) {
            return;
        }
        Action action = event.getAction();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!right && action != Action.LEFT_CLICK_AIR) {
            return;
        }
        if (right) {
            if (event.getItem() != null && event.getItem().getType().isBlock()) {
                return;                                   // Shift + clique direito com bloco continua colocando
            }
            if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                    && event.getClickedBlock().getType().isInteractable()) {
                return;
            }
        }
        PlayerData d = rpg().data(player);
        List<Skill> actives = unlockedActives(d);
        if (actives.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (!right) {
            int next = (actives.indexOf(d.selected) + 1) % actives.size();
            d.selected = actives.get(next);
            rpg().markDirty();
            Fx.select(player);
            Msg.actionBar(player, "<aqua>Habilidade: <white>" + d.selected.display()
                    + " <dark_gray>(Shift + botão direito para usar)");
        } else if (System.currentTimeMillis() - lastCast.getOrDefault(player.getUniqueId(), 0L) > 250) {
            lastCast.put(player.getUniqueId(), System.currentTimeMillis());
            cast(player);
        }
    }

    private final java.util.Map<java.util.UUID, Long> lastCast = new java.util.HashMap<>();

    private long remaining(Player player, Skill skill) {
        EnumMap<Skill, Long> map = cooldowns.get(player.getUniqueId());
        Long until = map == null ? null : map.get(skill);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    private void startCooldown(Player player, Skill skill, int rank) {
        cooldowns.computeIfAbsent(player.getUniqueId(), id -> new EnumMap<>(Skill.class))
                .put(skill, System.currentTimeMillis() + cooldownSeconds(skill, rank) * 1000L);
    }

    public void cast(Player player) {
        PlayerData d = rpg().data(player);
        Skill skill = d.selected;
        if (skill == null || d.rank(skill) < 1) {
            Fx.deny(player);
            return;
        }
        long left = remaining(player, skill);
        if (left > 0) {
            Fx.deny(player);
            Msg.actionBar(player, "<red>" + skill.display() + " <gray>em recarga: <white>" + (left / 1000 + 1) + "s");
            return;
        }
        int rank = d.rank(skill);
        switch (skill) {
            case DASH -> dash(player, rank);
            case AREA_MINER -> {
                long duration = rank >= 2 ? 25_000 : 20_000;
                areaUntil.put(player.getUniqueId(), System.currentTimeMillis() + duration);
                Fx.worldSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.6f);
                Fx.burst(player.getLocation().add(0, 1, 0), Particle.ENCHANT, 40, 0.6, 0.6);
                Msg.actionBar(player, "<aqua>Pico Largo ativo <white>" + (rank >= 2 ? "3x3" : "2x2")
                        + " <gray>por " + duration / 1000 + "s");
            }
            case STUN -> {
                stunArmed.put(player.getUniqueId(), System.currentTimeMillis() + 8000);
                Fx.worldSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.7f, 1.4f);
                Fx.burst(player.getLocation().add(0, 1, 0), Particle.CRIT, 25, 0.5, 0.3);
                Msg.actionBar(player, "<red>Golpe Atordoante armado <gray>(próximo golpe em 8s)");
            }
            default -> {
                return;
            }
        }
        startCooldown(player, skill, rank);
    }

    private void dash(Player player, int rank) {
        Vector look = player.getLocation().getDirection();
        Vector flat = new Vector(look.getX(), 0, look.getZ());
        if (flat.lengthSquared() < 1.0E-4) {
            flat = new Vector(0, 0, 1);
        }
        Vector velocity = flat.normalize().multiply(1.0 + 0.3 * rank).setY(Math.max(0.2, look.getY() * 0.5));
        player.setVelocity(velocity);
        player.setFallDistance(0);
        Fx.burst(player.getLocation().add(0, 0.3, 0), Particle.CLOUD, 18, 0.3, 0.05);
        Fx.worldSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 1.6f);
    }

    /** Chamado a cada 0,5s para cada jogador: ímã, tempo da área, recargas prontas. */
    public void tick(Player player, boolean second) {
        PlayerData d = rpg().data(player);
        int magnet = d.rank(Skill.MAGNET);
        if (magnet > 0 && player.getGameMode() == GameMode.SURVIVAL) {
            double radius = magnet >= 2 ? 7 : 4;
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof Item item && item.getPickupDelay() <= 0
                        && item.getLocation().distanceSquared(player.getLocation()) > 2.25) {
                    Vector pull = player.getLocation().add(0, 0.6, 0).toVector().subtract(item.getLocation().toVector());
                    item.setVelocity(pull.normalize().multiply(0.45));
                }
            }
        }
        if (!second) {
            return;
        }
        UUID id = player.getUniqueId();
        Long area = areaUntil.get(id);
        if (area != null) {
            long left = area - System.currentTimeMillis();
            if (left <= 0) {
                areaUntil.remove(id);
                Msg.actionBar(player, "<gray>Pico Largo terminou.");
            } else {
                int rank = d.rank(Skill.AREA_MINER);
                Msg.actionBar(player, "<aqua>Pico Largo " + (rank >= 2 ? "3x3" : "2x2") + " <white>" + (left / 1000 + 1) + "s");
            }
        }
        Long armed = stunArmed.get(id);
        if (armed != null && armed < System.currentTimeMillis()) {
            stunArmed.remove(id);
        }
        EnumMap<Skill, Long> map = cooldowns.get(id);
        if (map != null) {
            Iterator<Map.Entry<Skill, Long>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Skill, Long> entry = it.next();
                if (entry.getValue() <= System.currentTimeMillis()) {
                    it.remove();
                    Msg.actionBar(player, "<green>✔ <white>" + entry.getKey().display() + " <green>pronta");
                    Fx.sound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.5f);
                }
            }
        }
    }

    private boolean areaActive(Player player) {
        Long until = areaUntil.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    // ── Mineração, veio e árvores ──────────────────────────────

    private static final org.bukkit.NamespacedKey PLACED_KEY = new org.bukkit.NamespacedKey("vertexx", "placed_logs");

    private static int pack(Block b) {
        return ((b.getY() + 2048) << 8) | ((b.getX() & 15) << 4) | (b.getZ() & 15);
    }

    private boolean isPlayerPlaced(Block b) {
        int[] list = b.getChunk().getPersistentDataContainer().get(PLACED_KEY,
                org.bukkit.persistence.PersistentDataType.INTEGER_ARRAY);
        if (list == null) {
            return false;
        }
        int key = pack(b);
        for (int v : list) {
            if (v == key) {
                return true;
            }
        }
        return false;
    }

    private void markPlaced(Block b, boolean placed) {
        var pdc = b.getChunk().getPersistentDataContainer();
        int[] list = pdc.get(PLACED_KEY, org.bukkit.persistence.PersistentDataType.INTEGER_ARRAY);
        int key = pack(b);
        List<Integer> out = new ArrayList<>();
        boolean had = false;
        if (list != null) {
            for (int v : list) {
                if (v == key) {
                    had = true;
                } else {
                    out.add(v);
                }
            }
        }
        if (placed) {
            out.add(key);
        } else if (!had) {
            return;
        }
        if (out.isEmpty()) {
            pdc.remove(PLACED_KEY);
        } else {
            pdc.set(PLACED_KEY, org.bukkit.persistence.PersistentDataType.INTEGER_ARRAY,
                    out.stream().mapToInt(Integer::intValue).toArray());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLogPlaced(BlockPlaceEvent event) {
        if (Tag.LOGS.isTagged(event.getBlockPlaced().getType())) {
            markPlaced(event.getBlockPlaced(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLogBroken(BlockBreakEvent event) {
        if (Tag.LOGS.isTagged(event.getBlock().getType())) {
            markPlaced(event.getBlock(), false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!rpg().enabled() || busy.contains(id) || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        Block block = event.getBlock();
        Material type = block.getType();
        PlayerData d = rpg().data(player);

        // replantio de plantações maduras
        if (d.rank(Skill.REPLANT) > 0 && block.getBlockData() instanceof Ageable age
                && age.getAge() >= age.getMaximumAge() && Ores.cropXp(type) > 0 && type != Material.MELON
                && type != Material.PUMPKIN) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (block.getType().isAir()) {
                    block.setType(type);
                    if (block.getBlockData() instanceof Ageable fresh) {
                        fresh.setAge(0);
                        block.setBlockData(fresh);
                    }
                }
            });
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        String toolName = tool.getType().name();
        boolean pick = toolName.endsWith("_PICKAXE");
        boolean shovel = toolName.endsWith("_SHOVEL");
        boolean axe = toolName.endsWith("_AXE");

        List<Block> targets = null;
        if (areaActive(player) && (pick || shovel)) {
            targets = areaTargets(player, block, tool, d.rank(Skill.AREA_MINER) >= 2 ? 3 : 2);
        } else if (player.isSneaking()) {
            int veinRank = d.rank(Skill.VEIN_MINER);
            int treeRank = d.rank(Skill.CAPITATOR);
            if (pick && veinRank > 0 && Ores.isOre(type)) {
                targets = connected(block, veinRank * 8 + (veinRank == 3 ? 8 : 0), b -> Ores.isOre(b.getType())
                        && Ores.group(b.getType()).equals(Ores.group(type)));
            } else if (axe && treeRank > 0 && Tag.LOGS.isTagged(type)) {
                if (isPlayerPlaced(block)) {
                    return;                                   // tora de construção: quebra normal
                }
                targets = connected(block, treeRank >= 2 ? 120 : 40,
                        bl -> Tag.LOGS.isTagged(bl.getType()) && !isPlayerPlaced(bl));
            }
        }
        if (targets == null || targets.isEmpty()) {
            return;
        }
        List<Block> finalTargets = targets;
        Bukkit.getScheduler().runTask(plugin, () -> breakAll(player, finalTargets));
    }

    private void breakAll(Player player, List<Block> blocks) {
        UUID id = player.getUniqueId();
        busy.add(id);
        try {
            for (Block target : blocks) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (!player.isOnline() || hand.isEmpty()) {
                    break;
                }
                if (!target.getType().isAir()) {
                    player.breakBlock(target);
                }
            }
        } finally {
            busy.remove(id);
        }
    }

    private List<Block> connected(Block origin, int limit, java.util.function.Predicate<Block> match) {
        List<Block> result = new ArrayList<>();
        Set<Block> seen = new LinkedHashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && result.size() < limit) {
            Block current = queue.poll();
            for (int[] n : NEIGHBORS) {
                Block next = current.getRelative(n[0], n[1], n[2]);
                if (seen.add(next) && match.test(next)) {
                    result.add(next);
                    queue.add(next);
                    if (result.size() >= limit) {
                        break;
                    }
                }
            }
        }
        return result;
    }

    private List<Block> areaTargets(Player player, Block origin, ItemStack tool, int size) {
        RayTraceResult ray = player.rayTraceBlocks(7.0);
        BlockFace face = ray != null && ray.getHitBlock() != null && ray.getHitBlock().equals(origin)
                && ray.getHitBlockFace() != null ? ray.getHitBlockFace() : BlockFace.UP;
        int[] u;
        int[] v;
        switch (face) {
            case UP, DOWN -> {
                u = new int[]{1, 0, 0};
                v = new int[]{0, 0, 1};
            }
            case NORTH, SOUTH -> {
                u = new int[]{1, 0, 0};
                v = new int[]{0, 1, 0};
            }
            default -> {
                u = new int[]{0, 0, 1};
                v = new int[]{0, 1, 0};
            }
        }
        Vector look = player.getLocation().getDirection();
        int su = (u[0] != 0 ? look.getX() : look.getZ()) >= 0 ? 1 : -1;
        int sv = v[1] != 0 ? (look.getY() >= 0 ? 1 : -1) : (look.getZ() >= 0 ? 1 : -1);
        List<Block> list = new ArrayList<>();
        for (int i = size == 3 ? -1 : 0; i <= 1; i++) {
            for (int j = size == 3 ? -1 : 0; j <= 1; j++) {
                if (i == 0 && j == 0) {
                    continue;
                }
                int a = size == 3 ? i : i * su;
                int b = size == 3 ? j : j * sv;
                Block target = origin.getRelative(u[0] * a + v[0] * b, u[1] * a + v[1] * b, u[2] * a + v[2] * b);
                if (breakable(target, tool)) {
                    list.add(target);
                }
            }
        }
        return list;
    }

    private static boolean breakable(Block block, ItemStack tool) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid() || type.getHardness() < 0 || type.getHardness() > 25) {
            return false;
        }
        BlockState state = block.getState();
        return !(state instanceof Container) && block.isPreferredTool(tool);
    }

    // ── Drops extras: Garimpeiro e Colheita Farta ──────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (!rpg().enabled() || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        PlayerData d = rpg().data(player);
        BlockState state = event.getBlockState();
        double chance = 0;
        if (Ores.isOre(state.getType())) {
            chance = d.rank(Skill.LUCKY) * 0.08;
        } else if (state.getBlockData() instanceof Ageable age && age.getAge() >= age.getMaximumAge()
                && Ores.cropXp(state.getType()) > 0) {
            chance = d.rank(Skill.HARVEST) * 0.10;
        }
        if (chance > 0 && ThreadLocalRandom.current().nextDouble() < chance) {
            for (Item item : new ArrayList<>(event.getItems())) {
                ItemStack copy = item.getItemStack().clone();
                Bukkit.getScheduler().runTask(plugin, () -> state.getWorld().dropItemNaturally(
                        state.getLocation().add(0.5, 0.5, 0.5), copy));
            }
            Fx.burst(state.getLocation().add(0.5, 0.7, 0.5), Particle.HAPPY_VILLAGER, 8, 0.3, 0);
        }
    }

    // ── Reposição de blocos ────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!rpg().enabled() || rpg().data(player).rank(Skill.RESTOCK) < 1 || event.getItemInHand().getAmount() != 1) {
            return;
        }
        ItemStack model = event.getItemInHand().clone();
        EquipmentSlot hand = event.getHand();
        Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerInventory inventory = player.getInventory();
            ItemStack now = inventory.getItem(hand);
            if (!player.isOnline() || (now != null && !now.isEmpty())) {
                return;
            }
            for (int slot = 0; slot < 36; slot++) {
                ItemStack candidate = inventory.getItem(slot);
                if (candidate != null && candidate.isSimilar(model)) {
                    inventory.setItem(hand, candidate.clone());
                    inventory.setItem(slot, null);
                    Fx.sound(player, Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.5f, 1.4f);
                    return;
                }
            }
        });
    }

    // ── Golpe atordoante ───────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStrike(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        Long armed = stunArmed.get(player.getUniqueId());
        if (armed == null || armed < System.currentTimeMillis()) {
            return;
        }
        String held = player.getInventory().getItemInMainHand().getType().name();
        if (!held.endsWith("_SWORD") && !held.endsWith("_AXE")) {
            return;
        }
        stunArmed.remove(player.getUniqueId());
        int rank = rpg().data(player).rank(Skill.STUN);
        int ticks = (1 + rank) * 20;
        mob.setAware(false);
        mob.getWorld().spawnParticle(Particle.CRIT, mob.getLocation().add(0, mob.getHeight() + 0.3, 0), 20, 0.3, 0.1, 0.3, 0.1);
        Fx.worldSound(mob.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1f, 0.6f);
        new org.bukkit.scheduler.BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!mob.isValid() || t >= ticks) {
                    if (mob.isValid()) {
                        mob.setAware(true);
                    }
                    cancel();
                    return;
                }
                if (t % 6 == 0) {
                    mob.getWorld().spawnParticle(Particle.ENCHANTED_HIT,
                            mob.getLocation().add(0, mob.getHeight() + 0.4, 0), 4, 0.2, 0.05, 0.2, 0);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Morte de mobs: XP de combate e Frenesi ─────────────────

    public void onMobKilled(Player killer, LivingEntity mob, int level, boolean elite) {
        if (!rpg().enabled()) {
            return;
        }
        double xp = 6 + level * 1.4;
        if (elite) {
            xp *= 3;
        }
        boolean ranged = mob.getLastDamageCause() instanceof EntityDamageByEntityEvent hit
                && hit.getDamager() instanceof org.bukkit.entity.Projectile;
        rpg().addXp(killer, ranged ? Stat.ARCHERY : Stat.COMBAT, xp);
        int frenzy = rpg().data(killer).rank(Skill.FRENZY);
        if (frenzy > 0) {
            int ticks = frenzy >= 2 ? 120 : 80;
            killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 1, false, true));
            killer.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 0, false, true));
        }
        if (elite) {
            rpg().cinematics().eliteSlain(killer, pretty(mob.getType().getKey().getKey()));
        }
    }

    private static String pretty(String key) {
        StringBuilder out = new StringBuilder();
        for (String part : key.split("_")) {
            if (!part.isEmpty()) {
                out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT)).append(' ');
            }
        }
        return out.toString().trim();
    }
}
