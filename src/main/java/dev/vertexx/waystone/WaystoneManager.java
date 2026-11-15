package dev.vertexx.waystone;

import dev.vertexx.PluginMain;
import dev.vertexx.core.BlockPos;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import dev.vertexx.core.YamlStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Registro, persistência ({@code waystones.yml}) e teletransporte seguro entre Waystones.
 * Um Waystone é um Lodestone colocado a partir de um item renomeado; jogadores só veem
 * no menu os Waystones que já descobriram (clicando neles).
 */
public final class WaystoneManager {

    public enum CreateResult {
        CREATED, LIMIT_REACHED, NAME_TAKEN, INVALID_NAME
    }

    private static final Set<Material> HAZARDS = Set.of(
            Material.FIRE, Material.SOUL_FIRE, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE,
            Material.POWDER_SNOW, Material.COBWEB, Material.NETHER_PORTAL, Material.END_PORTAL);
    private static final Set<Material> BAD_FLOORS = Set.of(
            Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE);
    /** Vizinhos do Waystone, dos mais próximos (cardeais) aos mais distantes. */
    private static final int[][] OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
            {1, 2}, {-1, 2}, {1, -2}, {-1, -2}};

    private final PluginMain plugin;
    private final YamlStore store;
    private final Map<UUID, Waystone> byId = new LinkedHashMap<>();
    private final Map<BlockPos, Waystone> byPos = new HashMap<>();
    private final Map<UUID, Set<UUID>> discovered = new HashMap<>();
    private final Set<UUID> traveling = new HashSet<>();
    /** Item escolhido por cada jogador para pagar as viagens. */
    private final Map<UUID, Material> payment = new HashMap<>();

    public WaystoneManager(PluginMain plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "waystones.yml");
    }

    // ── Persistência ────────────────────────────────────────────

    /** Leitura do disco: chame fora da thread principal. */
    public YamlConfiguration read() {
        return store.load();
    }

    /** Aplica os dados lidos: chame na thread principal. */
    public void apply(YamlConfiguration data) {
        byId.clear();
        byPos.clear();
        discovered.clear();
        payment.clear();
        ConfigurationSection section = data.getConfigurationSection("waystones");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                try {
                    BlockPos pos = BlockPos.read(entry);
                    if (pos == null) {
                        continue;
                    }
                    Waystone waystone = new Waystone(UUID.fromString(key), entry.getString("name", "Waystone"), pos,
                            UUID.fromString(entry.getString("owner", "")), entry.getString("owner-name", "?"),
                            entry.getLong("created"));
                    waystone.icon(WaystoneIcons.parse(entry.getString("icon")));
                    waystone.showName(entry.getBoolean("show-name", true));
                    String route = entry.getString("route");
                    waystone.route(route == null || route.isBlank() ? null : UUID.fromString(route));
                    byId.put(waystone.id(), waystone);
                    byPos.put(pos, waystone);
                } catch (IllegalArgumentException | NullPointerException ignored) {
                    plugin.getLogger().warning("Waystone inválido ignorado em waystones.yml: " + key);
                }
            }
        }
        ConfigurationSection pay = data.getConfigurationSection("payment");
        if (pay != null) {
            for (String key : pay.getKeys(false)) {
                Material item = Material.matchMaterial(pay.getString(key, ""));
                try {
                    if (item != null) {
                        payment.put(UUID.fromString(key), item);
                    }
                } catch (IllegalArgumentException ignored) {
                    // entrada inválida: ignora
                }
            }
        }
        ConfigurationSection known = data.getConfigurationSection("discovered");
        if (known != null) {
            for (String key : known.getKeys(false)) {
                try {
                    Set<UUID> ids = new HashSet<>();
                    for (String id : known.getStringList(key)) {
                        ids.add(UUID.fromString(id));
                    }
                    discovered.put(UUID.fromString(key), ids);
                } catch (IllegalArgumentException ignored) {
                    // entrada inválida: ignora
                }
            }
        }
    }

    public void save() {
        store.saveAsync(snapshot());
    }

    public void saveNow() {
        store.saveNow(snapshot());
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration data = new YamlConfiguration();
        for (Waystone waystone : byId.values()) {
            ConfigurationSection entry = data.createSection("waystones." + waystone.id());
            entry.set("name", waystone.name());
            waystone.pos().write(entry);
            entry.set("owner", waystone.owner().toString());
            entry.set("owner-name", waystone.ownerName());
            entry.set("created", waystone.created());
            entry.set("icon", waystone.icon().name());
            entry.set("show-name", waystone.showName());
            entry.set("route", waystone.route() == null ? null : waystone.route().toString());
        }
        for (Map.Entry<UUID, Material> entry : payment.entrySet()) {
            data.set("payment." + entry.getKey(), entry.getValue().name());
        }
        for (Map.Entry<UUID, Set<UUID>> entry : discovered.entrySet()) {
            List<String> ids = new ArrayList<>();
            for (UUID id : entry.getValue()) {
                if (byId.containsKey(id)) {
                    ids.add(id.toString());
                }
            }
            if (!ids.isEmpty()) {
                data.set("discovered." + entry.getKey(), ids);
            }
        }
        return data;
    }

    // ── Registro ────────────────────────────────────────────────

    public Waystone at(Block block) {
        if (byPos.isEmpty() || block.getType() != Material.LODESTONE) {
            return null;
        }
        return byPos.get(BlockPos.of(block));
    }

    public Waystone byId(UUID id) {
        return id == null ? null : byId.get(id);
    }

    public Waystone atPos(BlockPos pos) {
        return byPos.get(pos);
    }

    public java.util.Collection<Waystone> all() {
        return java.util.Collections.unmodifiableCollection(byId.values());
    }

    public List<Waystone> ownedList(UUID owner) {
        List<Waystone> result = new ArrayList<>();
        for (Waystone waystone : byId.values()) {
            if (waystone.owner().equals(owner)) {
                result.add(waystone);
            }
        }
        return result;
    }

    public boolean exists(Waystone waystone) {
        return byId.containsKey(waystone.id());
    }

    public int ownedBy(UUID owner) {
        int total = 0;
        for (Waystone waystone : byId.values()) {
            if (waystone.owner().equals(owner)) {
                total++;
            }
        }
        return total;
    }

    public CreateResult create(Player owner, Block block, String rawName) {
        String name = rawName == null ? "" : rawName.strip();
        int maxLength = plugin.getConfig().getInt("waystones.name-max-length", 32);
        if (name.isEmpty()) {
            return CreateResult.INVALID_NAME;
        }
        if (name.length() > maxLength) {
            name = name.substring(0, maxLength).strip();
        }
        int limit = plugin.getConfig().getInt("waystones.max-per-player", 3);
        if (limit >= 0 && ownedBy(owner.getUniqueId()) >= limit && !owner.hasPermission("vertexx.admin")) {
            return CreateResult.LIMIT_REACHED;
        }
        for (Waystone existing : byId.values()) {
            if (existing.name().equalsIgnoreCase(name)) {
                return CreateResult.NAME_TAKEN;
            }
        }
        Waystone waystone = new Waystone(UUID.randomUUID(), name, BlockPos.of(block),
                owner.getUniqueId(), owner.getName(), System.currentTimeMillis());
        byId.put(waystone.id(), waystone);
        byPos.put(waystone.pos(), waystone);
        discovered.computeIfAbsent(owner.getUniqueId(), k -> new HashSet<>()).add(waystone.id());
        plugin.models().add(waystone, true);
        save();
        return CreateResult.CREATED;
    }

    public void remove(Waystone waystone) {
        byId.remove(waystone.id());
        byPos.remove(waystone.pos());
        for (Waystone other : byId.values()) {
            if (waystone.id().equals(other.route())) {
                other.route(null); // rotas que apontavam para ele deixam de existir
            }
        }
        plugin.models().remove(waystone);
        save();
    }

    /** Pode alterar a decoração e as rotas do Waystone. */
    public boolean canConfigure(Player player, Waystone waystone) {
        return waystone.owner().equals(player.getUniqueId()) || player.hasPermission("vertexx.admin");
    }

    /** Decoração alterada: persiste e atualiza o modelo. */
    public void changed(Waystone waystone) {
        plugin.models().refresh(waystone);
        save();
    }

    /** @return {@code true} se o jogador acabou de descobrir este Waystone. */
    public boolean discover(Player player, Waystone waystone) {
        boolean added = discovered.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(waystone.id());
        if (added) {
            save();
        }
        return added;
    }

    /** Waystones descobertos pelo jogador, exceto {@code except}, em ordem alfabética. */
    public List<Waystone> destinations(Player player, Waystone except) {
        List<Waystone> result = new ArrayList<>();
        for (UUID id : discovered.getOrDefault(player.getUniqueId(), Set.of())) {
            Waystone waystone = byId.get(id);
            if (waystone != null && !waystone.equals(except)) {
                result.add(waystone);
            }
        }
        result.sort(Comparator.comparing(w -> w.name().toLowerCase(Locale.ROOT)));
        return result;
    }

    // ── Custo ───────────────────────────────────────────────────

    /** Itens aceitos como pagamento e a quantidade base de cada um, na ordem do config. */
    public Map<Material, Integer> paymentOptions() {
        Map<Material, Integer> options = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("waystones.cost.options");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material item = Material.matchMaterial(key);
                if (item != null && item.isItem()) {
                    options.put(item, Math.max(0, section.getInt(key, 1)));
                }
            }
        }
        if (options.isEmpty()) {
            options.put(Material.AMETHYST_SHARD, 1);
        }
        return options;
    }

    /** Item que o jogador escolheu para pagar (ou o padrão, se a escolha não for mais aceita). */
    public Material chosenPayment(Player player) {
        Map<Material, Integer> options = paymentOptions();
        Material chosen = payment.get(player.getUniqueId());
        if (chosen != null && options.containsKey(chosen)) {
            return chosen;
        }
        Material fallback = Material.matchMaterial(plugin.getConfig().getString("waystones.cost.default", ""));
        return fallback != null && options.containsKey(fallback) ? fallback : options.keySet().iterator().next();
    }

    /** Passa para o próximo item de pagamento da lista. */
    public Material cyclePayment(Player player) {
        List<Material> items = new ArrayList<>(paymentOptions().keySet());
        Material next = items.get((items.indexOf(chosenPayment(player)) + 1) % items.size());
        payment.put(player.getUniqueId(), next);
        save();
        return next;
    }

    public TravelCost costFor(Player player) {
        TravelCost.Type type;
        try {
            type = TravelCost.Type.valueOf(
                    plugin.getConfig().getString("waystones.cost.type", "ITEM").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            type = TravelCost.Type.ITEM;
        }
        Material item = chosenPayment(player);
        int base = paymentOptions().getOrDefault(item, 1);
        if (type == TravelCost.Type.XP_LEVELS) {
            base = plugin.getConfig().getInt("waystones.cost.xp-levels", 1);
        }
        int amount = (int) Math.ceil(base * plugin.travelCostMultiplier(player));
        if (type == TravelCost.Type.NONE || amount <= 0) {
            return TravelCost.FREE;
        }
        return new TravelCost(type, item, amount);
    }

    // ── Teletransporte ──────────────────────────────────────────

    public boolean isTraveling(Player player) {
        return traveling.contains(player.getUniqueId());
    }

    /** Inicia a viagem: concentração com efeito de portal e, em seguida, o teletransporte seguro. */
    public void travel(Player player, Waystone destination) {
        UUID id = player.getUniqueId();
        if (traveling.contains(id)) {
            return;
        }
        if (plugin.inCombat(player)) {
            deny(player, "<red>Você não pode viajar em combate.");
            return;
        }
        if (destination.pos().bukkitWorld() == null) {
            deny(player, "<red>O mundo desse Waystone não está disponível.");
            return;
        }
        TravelCost cost = costFor(player);
        if (!cost.canPay(player)) {
            deny(player, "<red>Custo da viagem: " + cost.describe() + "<red>.");
            return;
        }

        player.closeInventory();
        traveling.add(id);
        int warmup = Math.max(0, plugin.getConfig().getInt("waystones.warmup-ticks", 30));
        Location start = player.getLocation();
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, warmup + 30, 0, false, false, false));
        Fx.sound(player, Sound.BLOCK_PORTAL_TRIGGER, 0.3f, 1.7f);
        Msg.actionBar(player, "<light_purple>Viajando para <white><name></white>…",
                Placeholder.unparsed("name", destination.name()));

        new BukkitRunnable() {
            private int elapsed;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    traveling.remove(id);
                    cancel();
                    return;
                }
                Location now = player.getLocation();
                if (now.getWorld() != start.getWorld() || now.distanceSquared(start) > 1.0) {
                    cancel();
                    abort(player, "<red>Viagem cancelada: você se moveu.");
                    return;
                }
                Location center = now.clone().add(0, 1, 0);
                Fx.burst(center, Particle.PORTAL, 14, 0.35, 0.6);
                Fx.burst(center, Particle.REVERSE_PORTAL, 4, 0.3, 0.02);
                elapsed += 2;
                if (elapsed >= warmup) {
                    cancel();
                    depart(player, destination);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void depart(Player player, Waystone destination) {
        World world = destination.pos().bukkitWorld();
        if (world == null) {
            abort(player, "<red>O mundo desse Waystone não está disponível.");
            return;
        }
        world.getChunkAtAsync(destination.pos().x() >> 4, destination.pos().z() >> 4)
                .thenAccept(chunk -> sync(() -> arrive(player, destination)));
    }

    private void arrive(Player player, Waystone destination) {
        if (!player.isOnline()) {
            traveling.remove(player.getUniqueId());
            return;
        }
        if (!exists(destination)) {
            abort(player, "<red>Esse Waystone não existe mais.");
            return;
        }
        Location target = findSafe(destination);
        if (target == null) {
            abort(player, "<red>O destino está obstruído ou é perigoso demais.");
            return;
        }
        TravelCost cost = costFor(player);
        if (!cost.canPay(player)) {
            abort(player, "<red>Custo da viagem: " + cost.describe() + "<red>.");
            return;
        }
        Location origin = player.getLocation().add(0, 1, 0);
        player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> sync(() -> {
            traveling.remove(player.getUniqueId());
            if (!success) {
                abort(player, "<red>Não foi possível completar a viagem.");
                return;
            }
            cost.charge(player);
            discover(player, destination);
            Fx.burst(origin, Particle.PORTAL, 60, 0.4, 0.8);
            Fx.worldSound(origin, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.8f);

            Location arrival = target.clone().add(0, 1, 0);
            Fx.burst(arrival, Particle.REVERSE_PORTAL, 80, 0.3, 0.12);
            Fx.ring(target.clone().add(0, 0.1, 0), Particle.END_ROD, 1.0, 16);
            Fx.worldSound(arrival, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
            Fx.sound(player, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.0f);
            // a cegueira restante se dissipa sozinha em ~1s, suavizando a chegada
            Msg.actionBar(player, "<gradient:#c4a7ff:#2cb67d>✦ <name> ✦</gradient>",
                    Placeholder.unparsed("name", destination.name()));
        }));
    }

    private void abort(Player player, String message) {
        traveling.remove(player.getUniqueId());
        if (player.isOnline()) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            deny(player, message);
        }
    }

    private void deny(Player player, String message) {
        Msg.send(player, message);
        Fx.deny(player);
    }

    private void sync(Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    /**
     * Procura um ponto seguro ao lado do Waystone: chão sólido, espaço livre para o corpo e
     * nada perigoso (lava, fogo, magma...). Como último recurso, o topo do próprio Waystone.
     */
    public Location findSafe(Waystone waystone) {
        Block base = waystone.pos().block();
        if (base == null) {
            return null;
        }
        World world = base.getWorld();
        for (int[] offset : OFFSETS) {
            for (int dy : new int[]{0, -1, 1}) {
                Block feet = base.getRelative(offset[0], dy, offset[1]);
                if (standable(world, feet)) {
                    return facing(feet, base);
                }
            }
        }
        Block top = base.getRelative(0, 1, 0);
        return standable(world, top) ? facing(top, base) : null;
    }

    private static boolean standable(World world, Block feet) {
        if (feet.getY() <= world.getMinHeight() || feet.getY() >= world.getMaxHeight() - 1) {
            return false;
        }
        Block floor = feet.getRelative(0, -1, 0);
        return clear(feet) && clear(feet.getRelative(0, 1, 0))
                && floor.getType().isSolid() && !BAD_FLOORS.contains(floor.getType());
    }

    private static boolean clear(Block block) {
        return block.isPassable() && !block.isLiquid() && !HAZARDS.contains(block.getType());
    }

    private static Location facing(Block feet, Block waystone) {
        Location location = feet.getLocation().add(0.5, 0, 0.5);
        Location look = waystone.getLocation().add(0.5, 0, 0.5);
        if (look.getX() != location.getX() || look.getZ() != location.getZ()) {
            location.setDirection(look.toVector().subtract(location.toVector()).setY(0));
        }
        return location;
    }

    // ── Ambiente ────────────────────────────────────────────────

    /** Partículas discretas sobre os Waystones em chunks carregados. */
    public void startAmbient() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getConfig().getBoolean("waystones.ambient-particles", true)) {
                return;
            }
            for (Waystone waystone : byId.values()) {
                if (!waystone.pos().isChunkLoaded()) {
                    continue;
                }
                Location above = waystone.pos().center().add(0, 1.0, 0);
                above.getWorld().spawnParticle(Particle.ENCHANT, above, 6, 0.3, 0.3, 0.3, 0.6);
                above.getWorld().spawnParticle(Particle.END_ROD, above, 1, 0.25, 0.2, 0.25, 0.005);
            }
        }, 60L, 40L);
    }
}
