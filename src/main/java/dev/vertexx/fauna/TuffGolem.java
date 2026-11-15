package dev.vertexx.fauna;

import dev.vertexx.boss.Rig;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Estátua de tufo construída pelo jogador. Fica imóvel exibindo o item que segura; de tempos em tempos
 * acorda, anda até um item solto por perto, pega e volta ao ponto de origem para exibi-lo.
 * Com um bloco de lã no meio da construção ganha um manto colorido.
 */
final class TuffGolem extends Critter {

    private enum State { STATUE, FETCH, HOME }

    private final Rig.Part[] legs = new Rig.Part[2];
    private final Rig.Part[] arms = new Rig.Part[2];
    private final Rig.Part[] eyes = new Rig.Part[2];
    private Rig.Part cape;
    private Rig.Part capeHem;
    private Rig.Part head;
    private ItemDisplay display;
    private ItemStack held;
    private Location home;
    private float homeYaw;
    private State state = State.STATUE;
    private int nextWake;
    private int stateTicks;
    private Item target;
    private ItemStack pendingDrop;
    private Material capeMaterial;

    TuffGolem(FaunaManager manager, Mob mob) {
        super(manager, mob);
        mob.setInvulnerable(false);
        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(true);
        var pdc = mob.getPersistentDataContainer();
        String raw = pdc.get(manager.homeKey(), PersistentDataType.STRING);
        if (raw != null) {
            String[] p = raw.split(",");
            try {
                home = new Location(mob.getWorld(), Double.parseDouble(p[0]), Double.parseDouble(p[1]),
                        Double.parseDouble(p[2]));
                homeYaw = Float.parseFloat(p[3]);
            } catch (RuntimeException ignored) {
                home = null;
            }
        }
        if (home == null) {
            home = mob.getLocation();
            homeYaw = mob.getBodyYaw();
            saveHome();
        }
        byte[] bytes = pdc.get(manager.heldKey(), PersistentDataType.BYTE_ARRAY);
        if (bytes != null) {
            try {
                held = ItemStack.deserializeBytes(bytes);
            } catch (RuntimeException ignored) {
                held = null;
            }
        }
        String cape = pdc.get(manager.capeKey(), PersistentDataType.STRING);
        capeMaterial = cape == null ? null : Material.matchMaterial(cape);
        nextWake = 400 + ThreadLocalRandom.current().nextInt(600);
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.TUFF_GOLEM;
    }

    ItemStack held() {
        return held;
    }

    Material capeMaterial() {
        return capeMaterial;
    }

    private void saveHome() {
        mob.getPersistentDataContainer().set(manager.homeKey(), PersistentDataType.STRING,
                home.getX() + "," + home.getY() + "," + home.getZ() + "," + homeYaw);
    }

    private void saveHeld() {
        var pdc = mob.getPersistentDataContainer();
        if (held == null) {
            pdc.remove(manager.heldKey());
        } else {
            pdc.set(manager.heldKey(), PersistentDataType.BYTE_ARRAY, held.serializeAsBytes());
        }
        refreshDisplay();
    }

    @Override
    protected void build() {
        Material tuff = Material.TUFF;
        // pernas curtas, tronco e cabeça quadrada de tufo
        legs[0] = rig.add(tuff, 0.13f, 0.17f, 0, 0.2f, 0.34f, 0.24f);
        legs[1] = rig.add(tuff, -0.13f, 0.17f, 0, 0.2f, 0.34f, 0.24f);
        rig.add(Material.POLISHED_TUFF, 0, 0.55f, 0, 0.5f, 0.42f, 0.34f);
        rig.add(Material.COBBLED_DEEPSLATE, 0, 0.35f, 0, 0.46f, 0.06f, 0.3f); // cinto escuro
        head = rig.add(tuff, 0, 1.0f, 0.02f, 0.5f, 0.5f, 0.46f);
        rig.add(Material.POLISHED_TUFF, 0, 1.0f, -0.18f, 0.42f, 0.38f, 0.12f);
        rig.add(Material.COBBLED_DEEPSLATE, 0, 1.2f, 0.21f, 0.5f, 0.07f, 0.04f); // sobrancelha pesada
        for (int i = 0; i < 2; i++) {
            eyes[i] = rig.add(Material.LIGHT_BLUE_CONCRETE, i == 0 ? 0.12f : -0.12f, 1.04f, 0.26f, 0.1f, 0.1f, 0.03f);
        }
        rig.add(Material.CHISELED_TUFF, 0, 0.9f, 0.255f, 0.14f, 0.1f, 0.03f);
        arms[0] = rig.add(tuff, 0.36f, 0.6f, 0, 0.16f, 0.46f, 0.16f);
        arms[1] = rig.add(tuff, -0.36f, 0.6f, 0, 0.16f, 0.46f, 0.16f);
        // manto de lã (opcional): capa nas costas e nos ombros, com a barra mais viva
        if (capeMaterial != null) {
            cape = rig.add(capeMaterial, 0, 0.58f, -0.2f, 0.56f, 0.5f, 0.06f);
            capeHem = rig.add(capeMaterial, 0, 0.3f, -0.21f, 0.58f, 0.08f, 0.08f);
            rig.add(capeMaterial, 0.29f, 0.78f, 0, 0.12f, 0.12f, 0.4f);
            rig.add(capeMaterial, -0.29f, 0.78f, 0, 0.12f, 0.12f, 0.4f);
            rig.add(capeMaterial, 0, 0.8f, 0.19f, 0.56f, 0.1f, 0.06f);
        }
        refreshDisplay();
    }

    private void refreshDisplay() {
        if (display == null || !display.isValid()) {
            if (held == null || !mob.isValid()) {
                return;
            }
            display = mob.getWorld().spawn(mob.getLocation(), ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.setViewRange(0.8f);
                d.setTeleportDuration(2);
                d.setInterpolationDuration(2);
                d.setBrightness(new org.bukkit.entity.Display.Brightness(13, 15));
            });
        }
        if (held == null) {
            display.remove();
            display = null;
            return;
        }
        display.setItemStack(held);
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.42f), new Quaternionf()));
    }

    // ── Interação ──────────────────────────────────────────────

    @Override
    boolean interact(Player player, ItemStack hand) {
        World world = mob.getWorld();
        boolean empty = hand == null || hand.getType().isAir();
        if (empty) {
            if (held == null) {
                return true;
            }
            dropIgnored(player.getLocation().add(0, 0.5, 0), held);
            held = null;
            saveHeld();
            world.playSound(mob.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.1f);
            return true;
        }
        ItemStack previous = held;
        held = hand.clone();
        held.setAmount(1);
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
        if (previous != null) {
            dropIgnored(player.getLocation().add(0, 0.5, 0), previous);
        }
        saveHeld();
        world.playSound(mob.getLocation(), Sound.BLOCK_STONE_PLACE, 0.8f, 1.3f);
        world.spawnParticle(Particle.HAPPY_VILLAGER, mob.getLocation().add(0, 1.1, 0), 5, 0.25, 0.25, 0.25, 0);
        return true;
    }

    /** Itens devolvidos pelo golem não despertam o interesse dele de novo. */
    private void dropIgnored(Location at, ItemStack stack) {
        Item item = at.getWorld().dropItem(at, stack);
        item.setVelocity(new org.bukkit.util.Vector());
        item.getPersistentDataContainer().set(manager.ignoreKey(), PersistentDataType.BYTE, (byte) 1);
    }

    /** Desmonta a estátua, devolvendo os blocos e o item que ela segura. */
    void dismantle() {
        World world = mob.getWorld();
        Location at = mob.getLocation().add(0, 0.6, 0);
        world.dropItemNaturally(at, new ItemStack(Material.TUFF, 2));
        world.dropItemNaturally(at, new ItemStack(Material.CHISELED_TUFF));
        if (capeMaterial != null) {
            world.dropItemNaturally(at, new ItemStack(capeMaterial));
        }
        if (held != null) {
            world.dropItemNaturally(at, held);
        }
        if (pendingDrop != null) {
            world.dropItemNaturally(at, pendingDrop);
        }
        world.spawnParticle(Particle.BLOCK, at, 30, 0.3, 0.5, 0.3, 0, Material.TUFF.createBlockData());
        world.playSound(at, Sound.BLOCK_STONE_BREAK, 1f, 0.9f);
        held = null;
        pendingDrop = null;
        mob.remove();
    }

    // ── Comportamento ──────────────────────────────────────────

    @Override
    protected void think() {
        World world = mob.getWorld();
        stateTicks += 2;
        switch (state) {
            case STATUE -> {
                mob.getPathfinder().stopPathfinding();
                if (age % 40 == 0 && mob.getLocation().distanceSquared(home) > 0.64) {
                    state = State.HOME; // empurrado para longe do posto: volta andando
                    stateTicks = 0;
                }
                if (age >= nextWake && age % 20 == 0) {
                    wake(world);
                }
            }
            case FETCH -> {
                if (target == null || !target.isValid() || stateTicks > 400) {
                    state = State.HOME;
                    stateTicks = 0;
                    return;
                }
                mob.getPathfinder().moveTo(target.getLocation(), 1.0);
                if (mob.getLocation().distanceSquared(target.getLocation()) < 1.3 * 1.3) {
                    take(target);
                    target = null;
                    state = State.HOME;
                    stateTicks = 0;
                }
            }
            case HOME -> {
                mob.getPathfinder().moveTo(home, 1.0);
                if (mob.getLocation().distanceSquared(home) < 0.5 || stateTicks > 500) {
                    mob.getPathfinder().stopPathfinding();
                    Location back = home.clone();
                    back.setYaw(homeYaw);
                    mob.teleport(back);
                    mob.setRotation(homeYaw, 0);
                    if (pendingDrop != null) {
                        dropIgnored(home.clone().add(0, 0.6, 1.0), pendingDrop);
                        pendingDrop = null;
                    }
                    state = State.STATUE;
                    nextWake = age + 600 + ThreadLocalRandom.current().nextInt(700);
                    world.playSound(mob.getLocation(), Sound.BLOCK_STONE_PLACE, 0.8f, 0.8f);
                    world.spawnParticle(Particle.BLOCK, mob.getLocation().add(0, 0.1, 0), 8, 0.25, 0.05, 0.25, 0,
                            Material.TUFF.createBlockData());
                }
            }
        }
    }

    private void wake(World world) {
        Item found = null;
        double best = 9 * 9;
        for (Entity entity : world.getNearbyEntities(home, 9, 4, 9)) {
            if (entity instanceof Item item && item.getPickupDelay() < 20 && item.isOnGround()
                    && !item.getPersistentDataContainer().has(manager.ignoreKey(), PersistentDataType.BYTE)) {
                double d = item.getLocation().distanceSquared(home);
                if (d < best) {
                    best = d;
                    found = item;
                }
            }
        }
        nextWake = age + 400 + ThreadLocalRandom.current().nextInt(500);
        if (found == null) {
            return;
        }
        target = found;
        state = State.FETCH;
        stateTicks = 0;
        world.playSound(mob.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.6f);
        world.playSound(mob.getLocation(), Sound.BLOCK_STONE_BREAK, 0.7f, 0.7f);
        world.spawnParticle(Particle.BLOCK, mob.getLocation().add(0, 0.8, 0), 16, 0.25, 0.4, 0.25, 0, Material.TUFF.createBlockData());
    }

    private void take(Item item) {
        ItemStack stack = item.getItemStack();
        ItemStack one = stack.clone();
        one.setAmount(1);
        stack.setAmount(stack.getAmount() - 1);
        if (stack.getAmount() <= 0) {
            item.remove();
        } else {
            item.setItemStack(stack);
        }
        if (held != null) {
            pendingDrop = held; // devolve o item antigo ao chegar em casa
        }
        held = one;
        saveHeld();
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.8f);
        mob.getWorld().spawnParticle(Particle.END_ROD, mob.getLocation().add(0, 1.0, 0), 4, 0.2, 0.2, 0.2, 0.02);
    }

    /** Os itens que o golem devolve não são pegos de novo por ele mesmo. */
    @Override
    protected void cleanup() {
        if (display != null) {
            display.remove();
            display = null;
        }
    }

    // ── Desenho ────────────────────────────────────────────────

    @Override
    protected void pose() {
        boolean awake = state != State.STATUE;
        Material eye = awake ? Material.LIGHT_BLUE_CONCRETE : Material.GRAY_CONCRETE;
        for (Rig.Part e : eyes) {
            e.material(eye);
        }
        float swing = (float) Math.sin(phase) * 0.6f * gait();
        swing(legs[0], 0.13f, 0.34f, 0, 0.34f, swing);
        swing(legs[1], -0.13f, 0.34f, 0, 0.34f, -swing);
        // braço direito levantado segurando o item; o esquerdo balança
        boolean holding = held != null;
        float raise = holding ? -1.15f : swing * 0.8f;
        arms[0].pos.set(0.36f, 0.64f + (holding ? 0.06f : 0), holding ? 0.14f : 0);
        arms[0].rot.identity().rotateX(raise);
        arms[1].pos.set(-0.36f, 0.64f, 0);
        arms[1].rot.identity().rotateX(-swing * 0.8f);
        head.rot.identity().rotateX(awake ? (float) Math.sin(age * 0.3) * 0.04f : 0);
        if (cape != null) {
            float flap = awake ? (float) Math.sin(age * 0.25) * 0.08f * (0.4f + gait()) : 0;
            cape.rot.identity().rotateX(flap);
            capeHem.rot.identity().rotateX(flap * 1.4f);
        }
        if (display != null && display.isValid()) {
            double yawRad = Math.toRadians(-yaw());
            Vector3f hand = new Quaternionf().rotateY((float) yawRad).transform(new Vector3f(0.36f, 0.98f, 0.5f));
            Location at = mob.getLocation().add(hand.x, hand.y, hand.z);
            at.setYaw(yaw());
            display.teleport(at);
            display.setInterpolationDelay(0);
            display.setTransformation(new Transformation(new Vector3f(0, (float) Math.sin(age * 0.1) * 0.03f, 0),
                    new Quaternionf().rotateY((float) Math.toRadians(age * 3.0)), new Vector3f(0.42f), new Quaternionf()));
        }
    }
}
