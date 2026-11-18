package dev.vertexx.blueprint;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import dev.vertexx.model.ModelDef;
import dev.vertexx.model.ModelInstance;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Criaturas dos blueprints: um mob-base invisível (IA, gravidade, colisão, vida) com o modelo por cima; sub-hitboxes
 * ({@link Interaction}) presas aos localizadores {@code hitbox*}; assento ({@code seat}) para montar; e surgimento
 * natural. Tudo é guiado pelo {@code <id>.yml} ao lado do modelo, sem comandos.
 */
public final class BlueprintMobs implements Listener {

    /** Configuração de um mob (arquivo {@code <id>.yml}). */
    static final class Spec {
        String name;
        EntityType entity = EntityType.ZOMBIE;
        double health = 20;
        double scale = 1;
        double speed = 1;
        double damage = -1;
        boolean sounds;
        boolean rideable;
        boolean flying;
        double rideSpeed = 0.25;
        double hitboxWidth = 1;
        double hitboxHeight = 1;
        final Map<String, String> anims = new HashMap<>();
        final List<ItemStack> drops = new ArrayList<>();
        boolean spawn;
        List<String> biomes = List.of();
        List<String> worlds = List.of();
        double chance = 0.25;
        int max = 2;
        int minY = -64;
        int maxY = 320;

        static Spec read(String id, File file, PluginMain plugin) {
            Spec s = new Spec();
            s.name = prettify(id);
            if (!file.isFile()) {
                return s;
            }
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            s.name = y.getString("name", s.name);
            String type = y.getString("entity", "ZOMBIE").toUpperCase(Locale.ROOT);
            try {
                EntityType t = EntityType.valueOf(type);
                if (t.getEntityClass() != null && Mob.class.isAssignableFrom(t.getEntityClass())) {
                    s.entity = t;
                } else {
                    plugin.getLogger().warning("Blueprint " + id + ": entity " + type + " não é um mob; usando ZOMBIE.");
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Blueprint " + id + ": entity " + type + " desconhecido; usando ZOMBIE.");
            }
            s.health = Math.max(1, y.getDouble("health", 20));
            s.scale = Math.max(0.05, y.getDouble("scale", 1));
            s.speed = Math.max(0, y.getDouble("speed", 1));
            s.damage = y.getDouble("damage", -1);
            s.sounds = y.getBoolean("sounds", false);
            s.rideable = y.getBoolean("rideable", false);
            s.flying = y.getBoolean("flying", false);
            s.rideSpeed = y.getDouble("ride-speed", 0.25);
            s.hitboxWidth = Math.max(0.1, y.getDouble("hitbox.width", 1));
            s.hitboxHeight = Math.max(0.1, y.getDouble("hitbox.height", 1));
            if (y.isConfigurationSection("animations")) {
                for (String key : y.getConfigurationSection("animations").getKeys(false)) {
                    s.anims.put(key, y.getString("animations." + key, ""));
                }
            }
            for (String line : y.getStringList("drops")) {
                String[] p = line.split(":");
                Material m = Material.matchMaterial(p[0].strip());
                if (m == null) {
                    continue;
                }
                int amount = 1;
                try {
                    amount = p.length > 1 ? Integer.parseInt(p[1].strip()) : 1;
                } catch (NumberFormatException ignored) {
                    // quantidade inválida: 1
                }
                s.drops.add(new ItemStack(m, Math.max(1, Math.min(64, amount))));
            }
            s.spawn = y.getBoolean("natural-spawn.enabled", false);
            s.biomes = y.getStringList("natural-spawn.biomes").stream().map(b -> b.toLowerCase(Locale.ROOT)).toList();
            s.worlds = y.getStringList("natural-spawn.worlds");
            s.chance = y.getDouble("natural-spawn.chance", 0.25);
            s.max = Math.max(1, y.getInt("natural-spawn.max-near-player", 2));
            s.minY = y.getInt("natural-spawn.min-y", -64);
            s.maxY = y.getInt("natural-spawn.max-y", 320);
            return s;
        }
    }

    static String prettify(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
            }
        }
        return sb.toString().strip();
    }

    static String template(String id, BbCompiler.Result r) {
        return "# Configuração do mob '" + id + "' (gerada automaticamente; salve o arquivo para aplicar, sem comandos).\n"
                + "name: \"" + prettify(id) + "\"\n"
                + "# Mob-base invisível: define a IA, o tamanho de colisão e o comportamento (ZOMBIE, COW, WOLF, BAT, PHANTOM...).\n"
                + "entity: ZOMBIE\n"
                + "health: 20\n"
                + "scale: 1.0          # escala do modelo e do mob-base\n"
                + "speed: 1.0          # multiplicador de velocidade do mob-base\n"
                + "# damage: 4         # dano do ataque (opcional)\n"
                + "sounds: false       # sons do mob-base (desligado por padrão)\n"
                + "rideable: false     # clique direito monta (assento = locator 'seat')\n"
                + "flying: false       # montado: espaço sobe, correr desce\n"
                + "ride-speed: 0.25\n"
                + "hitbox:             # tamanho das sub-hitboxes (locators 'hitbox*')\n"
                + "  width: 1.0\n"
                + "  height: 1.0\n"
                + "# animations:        # nomes das animações por função (padrão: idle, walk, run, fly, attack, hurt, death)\n"
                + "#   idle: " + (r.animations().isEmpty() ? "idle" : r.animations().get(0)) + "\n"
                + "# drops: [BONE:2, LEATHER:1]\n"
                + "natural-spawn:\n"
                + "  enabled: false\n"
                + "  biomes: []        # vazio = qualquer. Ex.: [plains, desert]\n"
                + "  worlds: []        # vazio = qualquer mundo\n"
                + "  chance: 0.25      # por jogador a cada ciclo\n"
                + "  max-near-player: 2\n"
                + "  min-y: -64\n"
                + "  max-y: 320\n"
                + "# Animações encontradas no modelo: " + String.join(", ", r.animations()) + "\n"
                + "# Locators encontrados no modelo: " + String.join(", ", r.locators()) + "\n";
    }

    // ── Estado ──────────────────────────────────────────────────

    private final PluginMain plugin;
    private final Blueprints blueprints;
    private final NamespacedKey idKey;
    private final NamespacedKey partKey;
    private final NamespacedKey modelKey;
    private final Map<UUID, Inst> insts = new HashMap<>();
    private final Map<UUID, Inst> parts = new HashMap<>();       // Interaction → dono
    private final Map<UUID, Inst> riders = new HashMap<>();
    private final List<Dying> dying = new ArrayList<>();
    private BukkitTask task;
    private int tick;

    BlueprintMobs(PluginMain plugin, Blueprints blueprints) {
        this.plugin = plugin;
        this.blueprints = blueprints;
        this.idKey = new NamespacedKey(plugin, "bp_id");
        this.partKey = new NamespacedKey(plugin, "bp_part");
        this.modelKey = new NamespacedKey(plugin, "bp_model");
    }

    private final class Inst {
        final Mob mob;
        final String id;
        ModelInstance model;
        Blueprints.Entry entry;
        final List<Interaction> boxes = new ArrayList<>();
        final List<String> boxLocators = new ArrayList<>();
        ArmorStand seat;
        Player rider;
        org.bukkit.Input input;
        double lastX;
        double lastZ;
        double speed;
        int age;
        int attackUntil;

        Inst(Mob mob, Blueprints.Entry entry) {
            this.mob = mob;
            this.id = entry.id();
            lastX = mob.getX();
            lastZ = mob.getZ();
            plugin.bedrock().track(mob);
            bind(entry);
        }

        void bind(Blueprints.Entry e) {
            if (model != null) {
                model.remove();
            }
            for (Interaction i : boxes) {
                parts.remove(i.getUniqueId());
                i.remove();
            }
            boxes.clear();
            boxLocators.clear();
            entry = e;
            model = ModelInstance.spawn(e.def(), modelKey, mob.getLocation(), (float) e.spec().scale);
            for (ModelDef.Locator l : e.def().locators) {
                if (l.name().toLowerCase(Locale.ROOT).startsWith("hitbox")) {
                    Interaction box = mob.getWorld().spawn(mob.getLocation(), Interaction.class, i -> {
                        i.setInteractionWidth((float) (e.spec().hitboxWidth * e.spec().scale));
                        i.setInteractionHeight((float) (e.spec().hitboxHeight * e.spec().scale));
                        i.setResponsive(true);
                        i.setPersistent(false);
                        i.getPersistentDataContainer().set(partKey, PersistentDataType.BYTE, (byte) 1);
                    });
                    boxes.add(box);
                    boxLocators.add(l.name());
                    parts.put(box.getUniqueId(), this);
                }
            }
            model.play(anim(this, "idle"));
            model.update(mob.getLocation(), mob.getBodyYaw(), 0);
        }

        void remove() {
            if (model != null) {
                model.remove();
            }
            for (Interaction i : boxes) {
                parts.remove(i.getUniqueId());
                i.remove();
            }
            boxes.clear();
            dismount();
        }

        void dismount() {
            if (seat != null) {
                seat.remove();
                seat = null;
            }
            if (rider != null) {
                riders.remove(rider.getUniqueId());
                rider = null;
                mob.setAware(true);
            }
        }
    }

    private record Dying(ModelInstance model, Location at, float yaw, String clip, int total, int[] age) {
    }

    // ── Ciclo ──────────────────────────────────────────────────

    void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 20L, 2L);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (Inst i : new ArrayList<>(insts.values())) {
            i.remove();
        }
        insts.clear();
        for (Dying d : dying) {
            d.model().remove();
        }
        dying.clear();
    }

    /** Thread principal: modelos recompilados (ou novos): atualiza os mobs existentes e adota os do mundo. */
    void refresh(List<String> updated) {
        for (Inst inst : insts.values()) {
            if (updated.contains(inst.id)) {
                Blueprints.Entry e = blueprints.entry(inst.id);
                if (e != null) {
                    applySpec(inst.mob, e.spec());
                    inst.bind(e);
                }
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                adopt(entity);
            }
        }
    }

    /** Modelo apagado: remove as criaturas dele. */
    void dropModel(String id) {
        for (Inst inst : new ArrayList<>(insts.values())) {
            if (inst.id.equals(id)) {
                inst.remove();
                insts.remove(inst.mob.getUniqueId());
                inst.mob.remove();
            }
        }
    }

    private void adopt(Entity entity) {
        if (!(entity instanceof Mob mob) || !mob.isValid() || insts.containsKey(mob.getUniqueId())) {
            return;
        }
        String id = mob.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        Blueprints.Entry e = blueprints.entry(id);
        if (e == null) {
            return;
        }
        mob.setInvisible(true);
        insts.put(mob.getUniqueId(), new Inst(mob, e));
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            adopt(entity);
        }
    }

    @EventHandler
    public void onRemoved(EntityRemoveEvent event) {
        Inst inst = insts.remove(event.getEntity().getUniqueId());
        if (inst != null) {
            inst.remove();
        }
    }

    // ── Criação ────────────────────────────────────────────────

    private static void applySpec(Mob mob, Spec spec) {
        set(mob, Attribute.MAX_HEALTH, spec.health);
        mob.setHealth(Math.min(mob.getHealth(), spec.health));
        AttributeInstance move = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (move != null) {
            double base = move.getDefaultValue();
            move.setBaseValue(base * spec.speed);
        }
        set(mob, Attribute.SCALE, spec.scale);
        if (spec.damage >= 0) {
            set(mob, Attribute.ATTACK_DAMAGE, spec.damage);
        }
        mob.setSilent(!spec.sounds);
    }

    private static void set(Mob mob, Attribute attribute, double value) {
        AttributeInstance a = mob.getAttribute(attribute);
        if (a != null) {
            a.setBaseValue(value);
        }
    }

    /** Cria a criatura do blueprint no local. */
    public Mob spawn(Location at, String id) {
        Blueprints.Entry e = blueprints.entry(id);
        if (e == null) {
            return null;
        }
        Spec spec = e.spec();
        @SuppressWarnings("unchecked")
        Class<? extends Mob> type = (Class<? extends Mob>) spec.entity.getEntityClass();
        Mob mob = at.getWorld().spawn(at, type, entity -> {
            entity.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
            entity.setPersistent(true);
            entity.setRemoveWhenFarAway(false);
            entity.setInvisible(true);
            entity.customName(Msg.mm("<white>" + spec.name));
            entity.setCustomNameVisible(false);
            if (entity instanceof org.bukkit.entity.Ageable ageable) {
                ageable.setAdult();
            }
            if (entity instanceof org.bukkit.entity.Zombie zombie) {
                zombie.setShouldBurnInDay(false);
            }
            applySpec(entity, spec);
            entity.setHealth(spec.health);
        });
        adopt(mob);
        return mob;
    }

    // ── Ovos (disponíveis no painel de administração) ───────────

    public ItemStack egg(String id) {
        Blueprints.Entry e = blueprints.entry(id);
        String name = e == null ? prettify(id) : e.spec().name;
        Material base = e == null ? null : Material.matchMaterial(e.spec().entity.name() + "_SPAWN_EGG");
        ItemStack egg = new ItemStack(Material.PAPER);
        egg.editMeta(meta -> {
            meta.setItemModel(NamespacedKey.minecraft((base == null ? Material.GHAST_SPAWN_EGG : base).name().toLowerCase(Locale.ROOT)));
            meta.itemName(Msg.mm("<aqua>Ovo de " + Msg.esc(name)));
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        });
        return egg;
    }

    public List<String> ids() {
        return blueprints.ids();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseEgg(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        Block clicked = event.getClickedBlock();
        Block at = clicked.isPassable() && !clicked.isLiquid() ? clicked : clicked.getRelative(event.getBlockFace());
        if (spawn(at.getLocation().add(0.5, 0, 0.5), id) == null) {
            Msg.actionBar(event.getPlayer(), "<red>Esse modelo não está mais disponível.");
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
        player.swingHand(event.getHand() == null ? EquipmentSlot.HAND : event.getHand());
    }

    // ── Laço principal ─────────────────────────────────────────

    private static String anim(Inst inst, String role) {
        ModelDef def = inst.entry.def();
        String custom = inst.entry.spec().anims.get(role);
        if (custom != null && def.clips.containsKey(custom)) {
            return custom;
        }
        String[] candidates = switch (role) {
            case "idle" -> new String[]{"idle", "stand", "default", "rest"};
            case "walk" -> new String[]{"walk", "move", "run", "fly"};
            case "run" -> new String[]{"run", "sprint", "walk", "move"};
            case "fly" -> new String[]{"fly", "flying", "glide", "walk"};
            case "attack" -> new String[]{"attack", "bite", "hit", "slash"};
            case "hurt" -> new String[]{"hurt", "hit", "damage"};
            case "death" -> new String[]{"death", "die", "dead"};
            default -> new String[]{role};
        };
        for (String c : candidates) {
            if (def.clips.containsKey(c)) {
                return c;
            }
        }
        return "";
    }

    private void tickAll() {
        tick += 2;
        tickDying();
        for (Inst inst : new ArrayList<>(insts.values())) {
            if (!inst.mob.isValid()) {
                inst.remove();
                insts.remove(inst.mob.getUniqueId());
                continue;
            }
            if (inst.model == null) {
                continue;
            }
            try {
                tickOne(inst);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Falha no blueprint " + inst.id + ": " + e);
                inst.remove();
                insts.remove(inst.mob.getUniqueId());
            }
        }
        long interval = Math.max(5, plugin.getConfig().getInt("blueprints.natural-spawn-interval-seconds", 20)) * 20L;
        if (tick % interval == 0) {
            naturalSpawn();
        }
    }

    private void tickOne(Inst inst) {
        Mob mob = inst.mob;
        inst.age += 2;
        inst.speed = Math.hypot(mob.getX() - inst.lastX, mob.getZ() - inst.lastZ) / 2.0;
        inst.lastX = mob.getX();
        inst.lastZ = mob.getZ();
        Spec spec = inst.entry.spec();
        if (inst.rider != null) {
            drive(inst);
        }
        boolean near = !mob.getWorld().getNearbyPlayers(mob.getLocation(), 56).isEmpty();
        if (!near) {
            return;
        }
        // animação por estado
        String want;
        boolean airborne = !mob.isOnGround() && (spec.flying || !mob.hasGravity() || mob.getVelocity().getY() > 0.1);
        if (inst.age < inst.attackUntil && !anim(inst, "attack").isEmpty()) {
            want = anim(inst, "attack");
        } else if (airborne && !anim(inst, "fly").isEmpty() && inst.entry.def().clips.containsKey(anim(inst, "fly"))
                && (spec.flying || !mob.hasGravity())) {
            want = anim(inst, "fly");
        } else if (inst.speed > 0.2 && !anim(inst, "run").isEmpty()) {
            want = anim(inst, "run");
        } else if (inst.speed > 0.02 && !anim(inst, "walk").isEmpty()) {
            want = anim(inst, "walk");
        } else {
            want = anim(inst, "idle");
        }
        if (mob.getTarget() != null && mob.getLocation().distanceSquared(mob.getTarget().getLocation()) < 6.25
                && !anim(inst, "attack").isEmpty() && inst.age >= inst.attackUntil && inst.age % 20 == 0) {
            inst.attackUntil = inst.age + (int) (inst.entry.def().clips.get(anim(inst, "attack")).length * 20);
            inst.model.restart(anim(inst, "attack"));
        }
        if (!want.isEmpty() && !want.equals(inst.model.playing())) {
            inst.model.play(want);
        } else if (!want.isEmpty() && inst.model.finished()) {
            inst.model.restart(want);
        }
        float walkSpeed = (float) Math.max(0.6, Math.min(2.0, inst.speed * 5));
        inst.model.speed(want.equals(anim(inst, "walk")) || want.equals(anim(inst, "run")) ? walkSpeed : 1f);
        inst.model.advance(2);
        float yaw = inst.rider != null ? inst.rider.getLocation().getYaw() : mob.getBodyYaw();
        inst.model.update(mob.getLocation(), yaw, 2);
        // sub-hitboxes e assento seguem os localizadores
        for (int i = 0; i < inst.boxes.size(); i++) {
            Location loc = inst.model.locator(inst.boxLocators.get(i));
            if (loc != null && inst.boxes.get(i).isValid()) {
                inst.boxes.get(i).teleport(loc.add(0, -inst.boxes.get(i).getInteractionHeight() / 2.0, 0));
            }
        }
        if (inst.seat != null && inst.seat.isValid()) {
            Location s = inst.model.locator("seat");
            inst.seat.teleport((s == null ? mob.getLocation().add(0, mob.getHeight(), 0) : s).add(0, -0.7, 0));
        }
    }

    /** Montaria: o jogador comanda o mob-base pelo teclado (PlayerInputEvent). */
    private void drive(Inst inst) {
        Player rider = inst.rider;
        if (!rider.isOnline() || rider.getVehicle() != inst.seat) {
            inst.dismount();
            return;
        }
        org.bukkit.Input in = inst.input;
        Mob mob = inst.mob;
        Spec spec = inst.entry.spec();
        mob.setAware(false);
        double yaw = Math.toRadians(rider.getLocation().getYaw());
        double fwd = in == null ? 0 : (in.isForward() ? 1 : 0) - (in.isBackward() ? 1 : 0);
        double side = in == null ? 0 : (in.isLeft() ? 1 : 0) - (in.isRight() ? 1 : 0);
        Vector dir = new Vector(-Math.sin(yaw) * fwd + Math.cos(yaw) * side, 0, Math.cos(yaw) * fwd + Math.sin(yaw) * side);
        if (dir.lengthSquared() > 0) {
            dir.normalize().multiply(spec.rideSpeed);
        }
        Vector v = mob.getVelocity();
        double y = v.getY();
        if (spec.flying) {
            mob.setGravity(false);
            y = in != null && in.isJump() ? spec.rideSpeed : in != null && in.isSprint() ? -spec.rideSpeed : 0;
        } else {
            mob.setGravity(true);
            if (in != null && in.isJump() && mob.isOnGround()) {
                y = 0.55;
            }
        }
        mob.setVelocity(new Vector(dir.getX(), y, dir.getZ()));
        mob.setRotation(rider.getLocation().getYaw(), 0);
        mob.setBodyYaw(rider.getLocation().getYaw());
    }

    @EventHandler
    public void onInput(org.bukkit.event.player.PlayerInputEvent event) {
        Inst inst = riders.get(event.getPlayer().getUniqueId());
        if (inst != null) {
            inst.input = event.getInput();
        }
    }

    @EventHandler
    public void onDismount(org.bukkit.event.entity.EntityDismountEvent event) {
        if (event.getEntity() instanceof Player p) {
            Inst inst = riders.get(p.getUniqueId());
            if (inst != null && event.getDismounted() == inst.seat) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    inst.dismount();
                    if (!inst.entry.spec().flying) {
                        inst.mob.setGravity(true);
                    }
                });
            }
        }
    }

    // ── Interação e dano ───────────────────────────────────────

    private Inst ownerOf(Entity entity) {
        Inst inst = insts.get(entity.getUniqueId());
        return inst != null ? inst : parts.get(entity.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Inst inst = ownerOf(event.getRightClicked());
        if (inst == null || !inst.entry.spec().rideable || inst.rider != null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking() || player.getVehicle() != null) {
            return;
        }
        event.setCancelled(true);
        Location at = inst.model.locator("seat");
        inst.seat = inst.mob.getWorld().spawn(at == null ? inst.mob.getLocation() : at, ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setMarker(true);
            a.setGravity(false);
            a.setPersistent(false);
            a.setSilent(true);
        });
        inst.seat.addPassenger(player);
        inst.rider = player;
        riders.put(player.getUniqueId(), inst);
        Msg.actionBar(player, "<gray>WASD move, <white>espaço</white> pula" + (inst.entry.spec().flying ? " ou sobe, <white>correr</white> desce" : "")
                + ", <white>agachar</white> desmonta.");
    }

    /** Ataque a uma sub-hitbox: repassa o dano para o mob dono (interações não recebem dano vanilla). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAttackPart(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event) {
        Entity target = event.getAttacked();
        if (!(target instanceof Interaction)) {
            return;
        }
        Inst inst = parts.get(target.getUniqueId());
        if (inst == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        AttributeInstance dmg = player.getAttribute(Attribute.ATTACK_DAMAGE);
        double amount = (dmg == null ? 1 : dmg.getValue()) * (0.2 + 0.8 * player.getAttackCooldown());
        if (inst.rider == player) {
            return;
        }
        inst.mob.damage(amount, player);
        Vector push = inst.mob.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.1);
        if (push.lengthSquared() > 0) {
            inst.mob.setVelocity(inst.mob.getVelocity().add(push.normalize().multiply(0.3)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        Inst inst = insts.get(event.getEntity().getUniqueId());
        if (inst != null) {
            inst.model.hurtFlash(10);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Inst inst = insts.get(event.getEntity().getUniqueId());
        if (inst == null) {
            return;
        }
        if (!inst.entry.spec().drops.isEmpty()) {
            event.getDrops().clear();
            for (ItemStack drop : inst.entry.spec().drops) {
                ItemStack copy = drop.clone();
                copy.setAmount(Math.max(1, ThreadLocalRandom.current().nextInt(drop.getAmount() + 1)));
                event.getDrops().add(copy);
            }
        }
        // o modelo vira a animação de morte (ou tomba), depois some
        String death = anim(inst, "death");
        ModelInstance model = inst.model;
        inst.model = null;
        if (model == null) {
            return;
        }
        int total = death.isEmpty() ? 12 : (int) Math.ceil(inst.entry.def().clips.get(death).length * 20) + 6;
        if (!death.isEmpty()) {
            model.restart(death);
        }
        dying.add(new Dying(model, inst.mob.getLocation(), inst.mob.getBodyYaw(), death, total, new int[]{0}));
    }

    private void tickDying() {
        for (java.util.Iterator<Dying> it = dying.iterator(); it.hasNext(); ) {
            Dying d = it.next();
            d.age()[0] += 2;
            try {
                if (d.clip().isEmpty()) {
                    float t = Math.min(1f, d.age()[0] / 12f);
                    d.model().tilt(90f * (1f - (1f - t) * (1f - t)));
                } else {
                    d.model().advance(2);
                }
                d.model().hurtFlash(4);
                d.model().update(d.at(), d.yaw(), 2);
            } catch (RuntimeException e) {
                d.age()[0] = 9999;
            }
            if (d.age()[0] >= d.total()) {
                d.model().remove();
                d.at().getWorld().spawnParticle(Particle.POOF, d.at().clone().add(0, 0.8, 0), 20, 0.5, 0.4, 0.5, 0.02);
                it.remove();
            }
        }
    }

    // ── Surgimento natural ─────────────────────────────────────

    private void naturalSpawn() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Map.Entry<String, Blueprints.Entry> en : blueprints.all().entrySet()) {
            Spec spec = en.getValue().spec();
            if (!spec.spawn) {
                continue;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                World world = player.getWorld();
                if ((!spec.worlds.isEmpty() && !spec.worlds.contains(world.getName())) || random.nextDouble() > spec.chance) {
                    continue;
                }
                long near = insts.values().stream().filter(i -> i.id.equals(en.getKey())
                        && i.mob.getWorld() == world && i.mob.getLocation().distanceSquared(player.getLocation()) < 64 * 64).count();
                if (near >= spec.max) {
                    continue;
                }
                double angle = random.nextDouble() * Math.PI * 2;
                double dist = 24 + random.nextDouble() * 24;
                int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
                int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                int y = world.getHighestBlockYAt(x, z) + 1;
                if (spec.flying) {
                    y += 6 + random.nextInt(8);
                }
                if (y < spec.minY || y > spec.maxY) {
                    continue;
                }
                Location at = new Location(world, x + 0.5, y, z + 0.5);
                if (!spec.biomes.isEmpty() && !spec.biomes.contains(world.getBiome(at).getKey().getKey())) {
                    continue;
                }
                if (at.getBlock().isLiquid() || at.clone().add(0, -1, 0).getBlock().isLiquid()) {
                    continue;
                }
                spawn(at, en.getKey());
            }
        }
    }
}
