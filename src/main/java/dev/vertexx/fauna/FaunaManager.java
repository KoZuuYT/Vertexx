package dev.vertexx.fauna;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Tab;
import dev.vertexx.model.ModelInstance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.generator.structure.StructurePiece;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * As criaturas rejeitadas da votação de mobs: Rascal (2022), Golem de Tufo (2022), Moobloom (2020) e
 * Glare (2021). Cada uma é um mob invisível de verdade com um modelo de displays por cima
 * (veja {@link Critter}). O tipo fica gravado no PersistentDataContainer, então sobrevive a reinícios.
 */
public final class FaunaManager implements Listener {

    private final PluginMain plugin;
    private final NamespacedKey kindKey;
    private final NamespacedKey partKey;
    private final NamespacedKey shornKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey homeKey;
    private final NamespacedKey heldKey;
    private final NamespacedKey capeKey;
    private final NamespacedKey ignoreKey;
    private final NamespacedKey variantKey;
    private final Map<UUID, Critter> critters = new HashMap<>();
    private BukkitTask task;
    private int tick;

    public FaunaManager(PluginMain plugin) {
        this.plugin = plugin;
        this.kindKey = new NamespacedKey(plugin, "fauna_kind");
        this.partKey = new NamespacedKey(plugin, "fauna_part");
        this.shornKey = new NamespacedKey(plugin, "fauna_shorn");
        this.ownerKey = new NamespacedKey(plugin, "fauna_owner");
        this.homeKey = new NamespacedKey(plugin, "fauna_home");
        this.heldKey = new NamespacedKey(plugin, "fauna_held");
        this.capeKey = new NamespacedKey(plugin, "fauna_cape");
        this.ignoreKey = new NamespacedKey(plugin, "fauna_ignore");
        this.variantKey = new NamespacedKey(plugin, "fauna_variant");
    }

    PluginMain plugin() {
        return plugin;
    }

    NamespacedKey partKey() {
        return partKey;
    }

    NamespacedKey shornKey() {
        return shornKey;
    }

    NamespacedKey ownerKey() {
        return ownerKey;
    }

    NamespacedKey homeKey() {
        return homeKey;
    }

    NamespacedKey heldKey() {
        return heldKey;
    }

    NamespacedKey capeKey() {
        return capeKey;
    }

    NamespacedKey ignoreKey() {
        return ignoreKey;
    }

    NamespacedKey variantKey() {
        return variantKey;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("fauna.enabled", true);
    }

    // ── Ciclo ──────────────────────────────────────────────────

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 20L, 2L);
        // criaturas que já estavam carregadas (por exemplo após /reload)
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                adopt(entity);
            }
        }
    }

    private final java.util.Map<Block, org.bukkit.block.data.BlockData> pulses = new java.util.HashMap<>();

    java.util.Map<Block, org.bukkit.block.data.BlockData> pulses() {
        return pulses;
    }

    /** Sons do mundo: os golens de ametista ouvem. */
    @EventHandler(ignoreCancelled = true)
    public void onNote(org.bukkit.event.block.NotePlayEvent event) {
        int note = event.getNote().getId();
        hearAll(event.getBlock().getLocation().add(0.5, 0.5, 0.5), note);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameEvent(org.bukkit.event.world.GenericGameEvent event) {
        String name = event.getEvent().getKey().getKey();
        if (name.equals("step") || name.equals("block_activate") || name.equals("piston_extend")
                || name.equals("piston_contract") || name.equals("instrument_play")) {
            hearAll(event.getLocation(), -1);
        }
    }

    private void hearAll(Location at, int note) {
        for (Critter critter : critters.values()) {
            if (critter instanceof AmethystGolem golem) {
                golem.hear(at, note);
            }
        }
    }

    /** Construção: bloco de ametista, calcita e abóbora entalhada por cima. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGolemBuild(BlockPlaceEvent event) {
        if (!enabled() || event.getBlockPlaced().getType() != Material.CARVED_PUMPKIN) {
            return;
        }
        Block head = event.getBlockPlaced();
        Block middle = head.getRelative(0, -1, 0);
        Block base = head.getRelative(0, -2, 0);
        if (middle.getType() != Material.CALCITE || base.getType() != Material.AMETHYST_BLOCK) {
            return;
        }
        head.setType(Material.AIR);
        middle.setType(Material.AIR);
        base.setType(Material.AIR);
        Location at = base.getLocation().add(0.5, 0.1, 0.5);
        spawn(at, FaunaKind.AMETHYST_GOLEM);
        at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
        Msg.actionBar(event.getPlayer(), "<light_purple>Golem de Ametista invocado! Clique com um Fragmento de Ametista para sintonizar.");
    }

    public void shutdown() {
        for (var entry : pulses.entrySet()) {
            entry.getKey().setBlockData(entry.getValue(), false);
        }
        pulses.clear();
        if (task != null) {
            task.cancel();
        }
        for (Critter critter : critters.values()) {
            critter.remove();
        }
        critters.clear();
        for (Dying d : dying) {
            d.model.remove();
        }
        dying.clear();
    }

    /** Morte: o modelo pisca em vermelho, tomba de lado e vira fumaça, como um mob comum. */
    private static final class Dying {
        final ModelInstance model;
        final Location at;
        final float yaw;
        final float height;
        final float width;
        int age;

        Dying(ModelInstance model, Location at, float yaw, float height, float width) {
            this.model = model;
            this.at = at;
            this.yaw = yaw;
            this.height = height;
            this.width = width;
        }
    }

    private final List<Dying> dying = new ArrayList<>();

    private void tickDying() {
        for (java.util.Iterator<Dying> it = dying.iterator(); it.hasNext(); ) {
            Dying d = it.next();
            d.age += 2;
            try {
                float t = Math.min(1f, d.age / 12f);
                d.model.tilt(90f * (1f - (1f - t) * (1f - t)));
                d.model.hurtFlash(4);
                d.model.update(d.at, d.yaw, 2);
            } catch (RuntimeException e) {
                d.age = 99;                      // nunca deixa um modelo órfão no mundo
            }
            if (d.age >= 22) {
                d.model.remove();
                World world = d.at.getWorld();
                world.spawnParticle(Particle.POOF, d.at.clone().add(0, d.height / 2, 0), 20, d.width / 2, d.height / 3, d.width / 2, 0.02);
                it.remove();
            }
        }
    }

    private void tickAll() {
        tick += 2;
        tickDying();
        // cópia: remover o mob (Rascal sumindo, golem desmontado…) dispara EntityRemoveEvent, que mexe no mapa
        for (Critter critter : new java.util.ArrayList<>(critters.values())) {
            boolean keep;
            try {
                keep = critter.tick();
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Falha em " + critter.kind() + ": " + e);
                keep = false;
            }
            if (!keep) {
                critter.remove();
                critters.values().removeIf(other -> other == critter);
            }
        }
        long interval = Math.max(2, plugin.getConfig().getInt("fauna.spawn-interval-seconds", 12)) * 20L;
        if (enabled() && tick % interval == 0) {
            spawnScan();
        }
    }

    FaunaKind kindOf(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
        return raw == null ? null : FaunaKind.parse(raw);
    }

    private Critter critterOf(Entity entity) {
        return critters.get(entity.getUniqueId());
    }

    /** Cria o modelo para uma entidade marcada, se ainda não houver um. */
    private void adopt(Entity entity) {
        if (!(entity instanceof Mob mob) || !entity.isValid() || critters.containsKey(entity.getUniqueId())) {
            return;
        }
        FaunaKind kind = kindOf(entity);
        if (kind == null) {
            return;
        }
        Critter critter = switch (kind) {
            case RASCAL -> new Rascal(this, mob);
            case TUFF_GOLEM -> new TuffGolem(this, mob);
            case MOOBLOOM -> new Moobloom(this, mob);
            case GLARE -> new Glare(this, mob);
            case CRAB -> new Crab(this, mob);
            case PENGUIN -> new Penguin(this, mob);
            case GREAT_HUNGER -> new GreatHunger(this, mob);
            case BARNACLE -> new Barnacle(this, mob);
            case VOID_RAY -> new VoidRay(this, mob);
            case END_CRAWLER -> new EndCrawler(this, mob);
            case SHULKER_SENTINEL -> new ShulkerSentinel(this, mob);
            case AMETHYST_GOLEM -> new AmethystGolem(this, mob);
            case MEERKAT -> new Meerkat(this, mob);
            case OSTRICH -> new Ostrich(this, mob);
            case VULTURE -> new Vulture(this, mob);
            case DEEPSLATE_ANKYLO -> new Ankylo(this, mob);
            case CAVE_GLIDER -> new CaveGlider(this, mob);
        };
        // nome próprio (invisível, o mob-base também é): mensagens de morte e legendas citam a criatura, não a vaca
        if (mob.customName() == null) {
            mob.customName(Msg.mm(kind.color() + kind.display()));
            mob.setCustomNameVisible(false);
        }
        critters.put(entity.getUniqueId(), critter);
        critter.start();
    }

    // ── Ovos ───────────────────────────────────────────────────

    /** Ovo da criatura: item sem comportamento vanilla (nunca vira uma vaca), com modelo próprio do pack. */
    public ItemStack egg(FaunaKind kind) {
        ItemStack egg = new ItemStack(Material.PAPER);
        egg.editMeta(meta -> {
            meta.setItemModel(new NamespacedKey("vertexx", "spawn_egg_" + kind.key()));
            meta.itemName(Msg.mm(kind.color() + "Ovo de " + kind.display()));
            meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind.key());
        });
        return egg;
    }

    private FaunaKind eggKind(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
        return raw == null ? null : FaunaKind.parse(raw);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseEgg(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        ItemStack item = event.getItem();
        FaunaKind kind = eggKind(item);
        if (kind == null) {
            return;
        }
        event.setCancelled(true);
        Block clicked = event.getClickedBlock();
        Block at = clicked.isPassable() && !clicked.isLiquid() ? clicked : clicked.getRelative(event.getBlockFace());
        spawn(at.getLocation().add(0.5, 0, 0.5), kind);
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
        player.swingHand(event.getHand() == null ? EquipmentSlot.HAND : event.getHand());
    }

    /** Botão do meio numa criatura: dá o ovo dela (não o da vaca/papagaio-base). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPick(io.papermc.paper.event.player.PlayerPickEntityEvent event) {
        Critter critter = critterOf(event.getEntity());
        if (critter == null) {
            return;
        }
        event.setCancelled(true);
        giveToHand(event.getPlayer(), egg(critter.kind()));
    }

    /** Mesmas regras do "pegar bloco" vanilla: seleciona se já está na barra, puxa do inventário ou (criativo) cria. */
    static void giveToHand(Player player, ItemStack item) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (item.isSimilar(inv.getItem(i))) {
                inv.setHeldItemSlot(i);
                return;
            }
        }
        int found = -1;
        for (int i = 9; i < 36; i++) {
            if (item.isSimilar(inv.getItem(i))) {
                found = i;
                break;
            }
        }
        if (found < 0 && player.getGameMode() != GameMode.CREATIVE) {
            return;
        }
        int slot = inv.getHeldItemSlot();
        if (inv.getItem(slot) != null && !inv.getItem(slot).isEmpty()) {
            for (int i = 0; i < 9; i++) {
                if (inv.getItem(i) == null || inv.getItem(i).isEmpty()) {
                    slot = i;
                    break;
                }
            }
        }
        ItemStack old = inv.getItem(slot);
        if (found >= 0) {
            inv.setItem(slot, inv.getItem(found));
            inv.setItem(found, old);
        } else {
            inv.setItem(slot, item);
            if (old != null && !old.isEmpty() && inv.firstEmpty() >= 0) {
                inv.addItem(old);         // criativo: o item substituído vai para o inventário, como no vanilla
            }
        }
        inv.setHeldItemSlot(slot);
    }

    // ── Criação ────────────────────────────────────────────────

    /** Cria uma criatura (administração e regras de surgimento). */
    public Mob spawn(Location at, FaunaKind kind) {
        return spawn(at, kind, null);
    }

    /** {@code variant}: variante do Great Hunger (nulo = pelo bloco do chão). */
    public Mob spawn(Location at, FaunaKind kind, String variant) {
        String v = kind != FaunaKind.GREAT_HUNGER ? null
                : variant != null ? variant : GreatHunger.variantFor(at.getBlock().getRelative(0, -1, 0));
        Mob mob = at.getWorld().spawn(at, kind.base(), entity -> {
            entity.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind.key());
            if (v != null) {
                entity.getPersistentDataContainer().set(variantKey, PersistentDataType.STRING, v);
            }
            if (entity instanceof org.bukkit.entity.Ageable ageable) {
                ageable.setAdult();
            }
            if (entity instanceof org.bukkit.entity.Breedable breedable) {
                breedable.setAgeLock(true);
                breedable.setBreed(false);
            }
            entity.setPersistent(true);
            entity.setRemoveWhenFarAway(false);
            AttributeInstance scale = entity.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(switch (kind) {
                    case RASCAL -> 0.55;
                    case TUFF_GOLEM -> 0.85;
                    case MOOBLOOM -> 1.0;
                    case GLARE -> 1.1;
                    case CRAB -> 0.5;
                    case PENGUIN -> 0.75;
                    case GREAT_HUNGER -> 0.45;
                    case BARNACLE -> 2.2;
                    case VOID_RAY -> 1.0;
                    case END_CRAWLER -> 2.2;
                    case SHULKER_SENTINEL -> 1.0;
                    case AMETHYST_GOLEM -> 1.0;
                    case MEERKAT -> 1.0;
                    case OSTRICH -> 1.0;
                    case VULTURE -> 1.0;
                    case DEEPSLATE_ANKYLO -> 1.5;
                    case CAVE_GLIDER -> 1.0;
                });
            }
            entity.setInvisible(true);
        });
        adopt(mob);
        return mob;
    }

    /** Alguns cows de florestas floridas e campos nascem como Moobloom. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!enabled() || !(event.getEntity() instanceof Cow cow) || kindOf(cow) != null || !cow.isAdult()) {
            return;
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL && reason != CreatureSpawnEvent.SpawnReason.CHUNK_GEN) {
            return;
        }
        Biome biome = cow.getWorld().getBiome(cow.getLocation());
        double chance = plugin.getConfig().getDouble("fauna.moobloom.chance", 0.35);
        double factor = biome == Biome.FLOWER_FOREST ? 1.0 : biome == Biome.MEADOW ? 0.4 : biome == Biome.SUNFLOWER_PLAINS ? 0.25 : 0;
        if (factor == 0 || ThreadLocalRandom.current().nextDouble() > chance * factor) {
            return;
        }
        cow.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, FaunaKind.MOOBLOOM.key());
        Bukkit.getScheduler().runTask(plugin, () -> adopt(cow));
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            adopt(entity);
        }
    }

    @EventHandler
    public void onRemoved(EntityRemoveEvent event) {
        Critter critter = critters.remove(event.getEntity().getUniqueId());
        if (critter != null) {
            critter.remove();
        }
    }

    // ── Interação e dano ───────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Critter critter = critterOf(event.getRightClicked());
        if (critter == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        FaunaKind kind = critter.kind();
        if (kind == FaunaKind.MOOBLOOM) {
            if (hand.getType() == Material.SHEARS) {
                event.setCancelled(true);
                shear(player, (Mob) event.getRightClicked(), hand);
            }
            return;
        }
        event.setCancelled(true);
        critter.interact(player, hand);
    }

    private void shear(Player player, Mob mob, ItemStack shears) {
        Long until = mob.getPersistentDataContainer().get(shornKey, PersistentDataType.LONG);
        long now = mob.getWorld().getFullTime();
        if (until != null && until > now) {
            player.sendActionBar(Msg.mm("<gray>As flores ainda estão crescendo de novo."));
            return;
        }
        mob.getPersistentDataContainer().set(shornKey, PersistentDataType.LONG, now + 6000L);
        Location at = mob.getLocation().add(0, 1.4, 0);
        mob.getWorld().dropItemNaturally(at, new ItemStack(Material.DANDELION, 4 + ThreadLocalRandom.current().nextInt(3)));
        mob.getWorld().playSound(at, Sound.ENTITY_MOOSHROOM_SHEAR, 1f, 1.2f);
        mob.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 10, 0.4, 0.2, 0.4, 0);
        if (player.getGameMode() != GameMode.CREATIVE) {
            shears.damage(1, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        Critter critter = critterOf(event.getEntity());
        if (critter != null && !(critter instanceof TuffGolem)) {
            double k = critter.resist(event);
            if (k != 1.0) {
                event.setDamage(event.getDamage() * k);
            }
        }
        if (!(critter instanceof TuffGolem golem)) {
            return;
        }
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player player
                && (player.isSneaking() || player.getGameMode() == GameMode.CREATIVE)) {
            golem.dismantle();
        } else if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player player) {
            player.sendActionBar(Msg.mm("<gray>Agache e bata para desmontar o Golem de Tufo."));
        }
    }

    /** Dano que passou (não cancelado): flash vermelho e reação (fuga, redemoinho…). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        Critter critter = critterOf(event.getEntity());
        if (critter == null || event.getFinalDamage() <= 0) {
            return;
        }
        Entity attacker = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            attacker = byEntity.getDamager();
            if (attacker instanceof org.bukkit.entity.Projectile projectile
                    && projectile.getShooter() instanceof Entity shooter) {
                attacker = shooter;
            }
        }
        critter.hurt(attacker);
    }

    /** Quem minera cristais de ametista desperta os Rastejadores do Fim por perto. */
    @EventHandler(ignoreCancelled = true)
    public void onCrystalBreak(org.bukkit.event.block.BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (type != Material.AMETHYST_CLUSTER && type != Material.LARGE_AMETHYST_BUD && type != Material.MEDIUM_AMETHYST_BUD
                && type != Material.SMALL_AMETHYST_BUD && type != Material.AMETHYST_BLOCK && type != Material.BUDDING_AMETHYST) {
            return;
        }
        for (Critter critter : critters.values()) {
            if (critter instanceof EndCrawler crawler && critter.mob.getWorld().equals(event.getBlock().getWorld())
                    && critter.mob.getLocation().distanceSquared(event.getBlock().getLocation()) < 16 * 16) {
                crawler.provoke(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onShoot(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Entity shooter && critterOf(shooter) instanceof ShulkerSentinel sentinel) {
            sentinel.onShoot();
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Critter critter = critterOf(event.getEntity());
        if (critter == null) {
            return;
        }
        if (critter.mob.getWorld().getPlayers().stream().anyMatch(p -> p.getLocation().distanceSquared(critter.mob.getLocation()) < 56 * 56)) {
            Mob dead = critter.mob;
            dying.add(new Dying(critter.detach(), dead.getLocation(), critter.modelYaw(), (float) dead.getHeight(), (float) dead.getWidth()));
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (critter.kind() == FaunaKind.GLARE) {
            event.getDrops().clear();
            event.getDrops().add(new ItemStack(Material.GLOW_BERRIES, 1 + random.nextInt(3)));
        } else if (critter.kind() == FaunaKind.MOOBLOOM) {
            event.getDrops().add(new ItemStack(Material.DANDELION, 1 + random.nextInt(2)));
        } else if (critter.kind() == FaunaKind.CRAB) {
            event.getDrops().clear();
            event.getDrops().add(Crab.claw(plugin));
        } else if (critter.kind() == FaunaKind.PENGUIN) {
            event.getDrops().clear();
            event.getDrops().add(new ItemStack(Material.FEATHER, 1 + random.nextInt(2)));
        } else if (critter.kind() == FaunaKind.GREAT_HUNGER) {
            event.getDrops().clear();
        } else if (critter.kind() == FaunaKind.VOID_RAY) {
            event.getDrops().clear();
            ItemStack membrane = dev.vertexx.end.EndItem.ETHEREAL_MEMBRANE.create(plugin);
            membrane.setAmount(1 + random.nextInt(2));
            event.getDrops().add(membrane);
        } else if (critter.kind() == FaunaKind.END_CRAWLER) {
            event.getDrops().clear();
            ItemStack chitin = dev.vertexx.end.EndItem.VOID_CHITIN.create(plugin);
            chitin.setAmount(1 + random.nextInt(2));
            event.getDrops().add(chitin);
        } else if (critter.kind() == FaunaKind.SHULKER_SENTINEL) {
            event.getDrops().clear();
            if (random.nextDouble() < 0.7) {
                event.getDrops().add(dev.vertexx.end.EndItem.LEVITATION_CORE.create(plugin));
            }
        } else if (critter.kind() == FaunaKind.DEEPSLATE_ANKYLO) {
            event.getDrops().clear();
            event.getDrops().add(new ItemStack(Material.BONE, 2 + random.nextInt(3)));
            event.getDrops().add(new ItemStack(Material.COBBLED_DEEPSLATE, 2));
        } else if (critter.kind() == FaunaKind.CAVE_GLIDER) {
            event.getDrops().clear();
            event.getDrops().add(new ItemStack(Material.BONE, 1 + random.nextInt(2)));
            event.getDrops().add(new ItemStack(Material.LEATHER, random.nextInt(2)));
        } else if (critter.kind() == FaunaKind.VULTURE) {
            event.getDrops().clear();
            event.getDrops().add(new ItemStack(Material.FEATHER, 1 + random.nextInt(3)));
            event.getDrops().add(new ItemStack(Material.ROTTEN_FLESH, random.nextInt(3)));
        } else if (critter.kind() == FaunaKind.MEERKAT) {
            event.getDrops().clear();
        } else if (critter.kind() == FaunaKind.OSTRICH) {
            event.getDrops().clear();
            event.getDrops().add(new ItemStack(Material.FEATHER, 2 + random.nextInt(4)));
        } else if (critter.kind() == FaunaKind.AMETHYST_GOLEM) {
            event.getDrops().clear();
            event.getDrops().add(new ItemStack(Material.AMETHYST_BLOCK));
            event.getDrops().add(new ItemStack(Material.CALCITE));
            event.getDrops().add(new ItemStack(Material.CARVED_PUMPKIN));
        } else if (critter.kind() == FaunaKind.BARNACLE) {
            event.getDrops().clear();
            event.getDrops().add(new ItemStack(Material.PRISMARINE_SHARD, 3 + random.nextInt(4)));
            event.getDrops().add(new ItemStack(Material.INK_SAC, 2 + random.nextInt(3)));
        }
    }

    // ── Golem de Tufo: construção ──────────────────────────────

    /**
     * Tufo, tufo (ou lã) e tufo cinzelado por cima: o último bloco colocado vira a cabeça.
     * Um bloco de lã no meio dá ao golem um manto da mesma cor.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!enabled() || !plugin.getConfig().getBoolean("fauna.tuff-golem.enabled", true)
                || event.getBlockPlaced().getType() != Material.CHISELED_TUFF) {
            return;
        }
        Block head = event.getBlockPlaced();
        Block middle = head.getRelative(0, -1, 0);
        Block base = head.getRelative(0, -2, 0);
        if (base.getType() != Material.TUFF) {
            return;
        }
        Material cape = null;
        if (middle.getType() != Material.TUFF) {
            if (!middle.getType().name().endsWith("_WOOL")) {
                return;
            }
            cape = middle.getType();
        }
        Material capeUsed = cape;
        Location at = base.getLocation().add(0.5, 0, 0.5);
        float yaw = event.getPlayer().getLocation().getYaw() + 180f;
        head.setType(Material.AIR, false);
        middle.setType(Material.AIR, false);
        base.setType(Material.AIR, false);
        at.setYaw(yaw);
        World world = at.getWorld();
        world.spawnParticle(Particle.BLOCK, at.clone().add(0, 1, 0), 40, 0.3, 0.7, 0.3, 0, Material.TUFF.createBlockData());
        world.playSound(at, Sound.BLOCK_STONE_PLACE, 1f, 0.7f);
        world.playSound(at, Sound.ENTITY_IRON_GOLEM_REPAIR, 0.8f, 0.7f);
        Mob mob = world.spawn(at, FaunaKind.TUFF_GOLEM.base(), entity -> {
            entity.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, FaunaKind.TUFF_GOLEM.key());
            if (capeUsed != null) {
                entity.getPersistentDataContainer().set(capeKey, PersistentDataType.STRING, capeUsed.name());
            }
            if (entity instanceof Cow cow) {
                cow.setAdult();
                cow.setAgeLock(true);
                cow.setBreed(false);
            }
            AttributeInstance scale = entity.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(0.85);
            }
            entity.setRotation(yaw, 0);
        });
        adopt(mob);
        event.getPlayer().sendActionBar(Msg.mm("<gray>Golem de Tufo criado. Clique com um item para ele exibir; agache e bata para desmontar."));
    }

    // ── Abutres: pontos com itens largados ─────────────────────

    private final java.util.List<Object[]> hotspots = new java.util.ArrayList<>();   // {Location, tickDeAgora}

    @EventHandler(ignoreCancelled = true)
    public void onItemDrop(org.bukkit.event.entity.ItemSpawnEvent event) {
        Location at = event.getLocation();
        if (at.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        hotspots.removeIf(h -> ((Number) h[1]).longValue() < tick - 2400 || ((Location) h[0]).getWorld() != at.getWorld()
                || ((Location) h[0]).distanceSquared(at) < 36);
        if (hotspots.size() < 40) {
            hotspots.add(new Object[]{at.clone(), tick});
        }
    }

    /** O ponto com itens largados mais perto de {@code from} (até {@code range} blocos), ou nulo. */
    Location hotspot(Location from, double range) {
        Location best = null;
        double bestD = range * range;
        for (Object[] h : hotspots) {
            Location at = (Location) h[0];
            if (at.getWorld() == from.getWorld() && ((Number) h[1]).longValue() >= tick - 2400) {
                double d = at.distanceSquared(from);
                if (d < bestD) {
                    bestD = d;
                    best = at;
                }
            }
        }
        return best;
    }

    /** Fauna das regiões áridas: suricato (deserto), avestruz (savana) e abutre (deserto e badlands). */
    private void spawnArid(Player player) {
        if (!plugin.getConfig().getBoolean("overworld.arid.mobs", true)) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location l = player.getLocation();
        String biome = l.getBlock().getBiome().getKey().getKey();
        boolean desert = biome.equals("desert");
        boolean savanna = biome.startsWith("savanna");
        boolean badlands = biome.contains("badlands");
        if (desert && count(FaunaKind.MEERKAT, l, 64) < 3 && random.nextDouble() < 0.3) {
            spawnNear(player, FaunaKind.MEERKAT, 12, 26, false);
        }
        if (savanna && count(FaunaKind.OSTRICH, l, 64) < 3 && random.nextDouble() < 0.25) {
            spawnNear(player, FaunaKind.OSTRICH, 14, 28, false);
        }
        if ((desert || badlands) && count(FaunaKind.VULTURE, l, 96) < 1 && random.nextDouble() < 0.08) {
            spawnNear(player, FaunaKind.VULTURE, 20, 36, false);
        }
    }

    // ── Surgimento natural ─────────────────────────────────────

    private void spawnScan() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location l = player.getLocation();
            World world = l.getWorld();
            if (world.getEnvironment() == World.Environment.THE_END) {
                spawnEnd(player);
                continue;
            }
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            spawnArid(player);
            if (plugin.getConfig().getBoolean("fauna.rascal.enabled", true) && l.getY() < 0 && count(FaunaKind.RASCAL, l, 64) == 0
                    && random.nextDouble() < plugin.getConfig().getDouble("fauna.rascal.chance", 0.12) && inMineshaft(l)) {
                spawnNear(player, FaunaKind.RASCAL, 9, 18, true);
            }
            if (plugin.getConfig().getBoolean("fauna.glare.enabled", true) && l.getY() < 64
                    && world.getBiome(l) == Biome.LUSH_CAVES && count(FaunaKind.GLARE, l, 48) < 2
                    && random.nextDouble() < plugin.getConfig().getDouble("fauna.glare.chance", 0.15)) {
                spawnNear(player, FaunaKind.GLARE, 8, 18, false);
            }
            Biome biome = world.getBiome(l);
            if (plugin.getConfig().getBoolean("fauna.crab.enabled", true) && biome == Biome.MANGROVE_SWAMP
                    && count(FaunaKind.CRAB, l, 48) < 3 && random.nextDouble() < plugin.getConfig().getDouble("fauna.crab.chance", 0.18)) {
                spawnNear(player, FaunaKind.CRAB, 8, 20, false);
            }
            if (plugin.getConfig().getBoolean("fauna.penguin.enabled", true) && biome == Biome.STONY_SHORE
                    && count(FaunaKind.PENGUIN, l, 64) == 0 && random.nextDouble() < plugin.getConfig().getDouble("fauna.penguin.chance", 0.15)) {
                int n = 2 + random.nextInt(2);
                for (int i = 0; i < n; i++) {
                    spawnNear(player, FaunaKind.PENGUIN, 10, 20, false);
                }
            }
            if (plugin.getConfig().getBoolean("fauna.great-hunger.enabled", true) && l.getY() > 50
                    && world.getHighestBlockYAt(l) <= l.getBlockY() + 1 && count(FaunaKind.GREAT_HUNGER, l, 96) == 0
                    && random.nextDouble() < plugin.getConfig().getDouble("fauna.great-hunger.chance", 0.04)) {
                spawnNear(player, FaunaKind.GREAT_HUNGER, 10, 22, false);
            }
            if (plugin.getConfig().getBoolean("fauna.barnacle.enabled", true) && biome.getKey().getKey().startsWith("deep_")
                    && (player.isInWater() || player.getVehicle() instanceof org.bukkit.entity.Boat)
                    && count(FaunaKind.BARNACLE, l, 96) == 0 && random.nextDouble() < plugin.getConfig().getDouble("fauna.barnacle.chance", 0.05)) {
                spawnSeabed(player);
            }
        }
    }

    /** Arraias nas copas do Coro, Rastejadores nos Abismos e Sentinelas raras nos abismos e perto das cidades do End. */
    private void spawnEnd(Player player) {
        if (!plugin.getConfig().getBoolean("end.mobs.enabled", true)) {
            return;
        }
        Location l = player.getLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        dev.vertexx.end.EndWorld.Zone zone = dev.vertexx.end.EndWorld.zoneAt(l.getWorld(), l.getBlockX(), l.getBlockZ());
        if (zone == dev.vertexx.end.EndWorld.Zone.CANOPY && count(FaunaKind.VOID_RAY, l, 64) < 3 && random.nextDouble() < 0.35) {
            spawnNear(player, FaunaKind.VOID_RAY, 10, 22, false);
        }
        if (zone == dev.vertexx.end.EndWorld.Zone.WASTES) {
            if (count(FaunaKind.END_CRAWLER, l, 64) < 3 && random.nextDouble() < 0.3) {
                spawnNear(player, FaunaKind.END_CRAWLER, 10, 22, false);
            }
            if (count(FaunaKind.SHULKER_SENTINEL, l, 64) == 0 && random.nextDouble() < 0.04) {
                spawnNear(player, FaunaKind.SHULKER_SENTINEL, 12, 24, false);
            }
        }
        if (count(FaunaKind.SHULKER_SENTINEL, l, 80) == 0 && random.nextDouble() < 0.15 && nearEndCity(l)) {
            spawnNear(player, FaunaKind.SHULKER_SENTINEL, 8, 20, false);
        }
    }

    private static boolean nearEndCity(Location l) {
        Vector at = l.toVector();
        for (GeneratedStructure generated : l.getChunk().getStructures(Structure.END_CITY)) {
            for (StructurePiece piece : generated.getPieces()) {
                if (piece.getBoundingBox().clone().expand(24).contains(at)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Barnacle: no fundo do oceano, a 10-20 blocos do jogador, com água por cima. */
    private void spawnSeabed(Player player) {
        World world = player.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 12; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 10 + random.nextDouble() * 10;
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = player.getLocation().getBlockY();
            while (y > world.getMinHeight() + 1 && world.getBlockAt(x, y - 1, z).getType() == Material.WATER) {
                y--;
            }
            Block spot = world.getBlockAt(x, y, z);
            if (spot.getType() == Material.WATER && spot.getRelative(0, 3, 0).getType() == Material.WATER
                    && player.getLocation().getY() - y >= 8) {
                spawn(spot.getLocation().add(0.5, 0.2, 0.5), FaunaKind.BARNACLE);
                return;
            }
        }
    }

    private int count(FaunaKind kind, Location around, double radius) {
        int n = 0;
        for (Critter critter : critters.values()) {
            if (critter.kind() == kind && critter.mob.getWorld().equals(around.getWorld())
                    && critter.mob.getLocation().distanceSquared(around) < radius * radius) {
                n++;
            }
        }
        return n;
    }

    private static boolean inMineshaft(Location l) {
        Vector at = l.toVector();
        for (Structure structure : List.of(Structure.MINESHAFT, Structure.MINESHAFT_MESA)) {
            for (GeneratedStructure generated : l.getChunk().getStructures(structure)) {
                for (StructurePiece piece : generated.getPieces()) {
                    if (piece.getBoundingBox().clone().expand(8).contains(at)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void spawnNear(Player player, FaunaKind kind, int min, int max, boolean hidden) {
        World world = player.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 16; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = min + random.nextDouble() * (max - min);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            for (int dy = 3; dy >= -4; dy--) {
                Block b = world.getBlockAt(x, player.getLocation().getBlockY() + dy, z);
                boolean ground = kind != FaunaKind.GLARE && kind != FaunaKind.VOID_RAY;
                if (!b.isPassable() || b.isLiquid() || !b.getRelative(0, 1, 0).isPassable() || !b.getRelative(0, 2, 0).isPassable()
                        || (ground && b.getRelative(0, -1, 0).isPassable())) {
                    continue;
                }
                if (kind == FaunaKind.GLARE && world.getBiome(b.getLocation()) != Biome.LUSH_CAVES) {
                    continue;
                }
                Location spot = b.getLocation().add(0.5, 0, 0.5);
                if (hidden) {
                    Location eye = player.getEyeLocation();
                    Vector dir = spot.clone().add(0, 0.5, 0).toVector().subtract(eye.toVector());
                    double length = dir.length();
                    if (length < 1 || world.rayTraceBlocks(eye, dir.normalize(), length, FluidCollisionMode.NEVER, true) == null) {
                        break;
                    }
                }
                spawn(spot, kind);
                return;
            }
        }
    }

    // ── Administração ──────────────────────────────────────────

    public void command(CommandSender sender, String[] args) {
        String sub = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "spawn", "criar" -> {
                FaunaKind kind = args.length < 3 ? null : FaunaKind.parse(args[2]);
                if (kind == null) {
                    Msg.send(sender, "<gray>Uso: <white>/vertexx rejeitado spawn <rascal|tuffgolem|moobloom|glare|crab|penguin|greathunger|barnacle|voidray|endcrawler|shulkersentinel|amethystgolem|meerkat|ostrich|vulture|deepslateankylo|caveglider> [variante]");
                    return;
                }
                String variant = args.length >= 4 && GreatHunger.VARIANTS.contains(args[3].toLowerCase(Locale.ROOT))
                        ? args[3].toLowerCase(Locale.ROOT) : null;
                Location at;
                if (sender instanceof Player player) {
                    at = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(3));
                } else {
                    at = Bukkit.getWorlds().get(0).getSpawnLocation().add(0.5, 1, 0.5);
                }
                spawn(at, kind, variant);
                Msg.send(sender, "<green>" + kind.display() + " criado.");
            }
            case "limpar" -> {
                int n = 0;
                for (Critter critter : new ArrayList<>(critters.values())) {
                    critter.mob.remove();
                    n++;
                }
                Msg.send(sender, "<green>" + n + " criatura(s) removida(s).");
            }
            case "lista" -> {
                for (Critter critter : critters.values()) {
                    Location l = critter.mob.getLocation();
                    Msg.send(sender, "<gray>" + critter.kind().display() + " <dark_gray>@ <white>" + l.getWorld().getName()
                            + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
                }
                Msg.send(sender, "<gray>Total: <white>" + critters.size());
            }
            default -> Msg.send(sender, "<gray>Uso: <white>/vertexx rejeitado <spawn|limpar|lista>");
        }
    }

    public List<String> tab(String[] args) {
        if (args.length == 2) {
            return Tab.of(args[1], "spawn", "limpar", "lista");
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("spawn")) {
            return Tab.of(args[2], "rascal", "tuffgolem", "moobloom", "glare", "crab", "penguin", "greathunger", "barnacle", "voidray", "endcrawler", "shulkersentinel", "amethystgolem", "meerkat", "ostrich", "vulture", "deepslateankylo", "caveglider");
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("spawn") && FaunaKind.parse(args[2]) == FaunaKind.GREAT_HUNGER) {
            return Tab.of(args[3], GreatHunger.VARIANTS.toArray(new String[0]));
        }
        return List.of();
    }
}
