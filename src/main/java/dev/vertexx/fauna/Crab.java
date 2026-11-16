package dev.vertexx.fauna;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Caranguejo dos manguezais (Mob Vote 2023). Anda de lado, sobe em troncos e raízes de mangue e, ao trocar de
 * casca ou morrer, deixa a Garra de Caranguejo: segurada na mão, aumenta o alcance para colocar e quebrar blocos.
 */
final class Crab extends Critter {

    private int climb;
    private int waveTicks;

    Crab(FaunaManager manager, Mob mob) {
        super(manager, mob);
        mob.setSilent(true);
    }

    @Override
    protected boolean wild() {
        return false;         // mantém a IA de passear da vaca-base
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.CRAB;
    }

    /** A Garra de Caranguejo: modelo 3D da garra e alcance extra de blocos enquanto está na mão. */
    static ItemStack claw(PluginMain plugin) {
        ItemStack item = new ItemStack(Material.ORANGE_DYE);
        double reach = plugin.getConfig().getDouble("fauna.crab.claw-reach", 3.0);
        item.editMeta(meta -> {
            meta.displayName(Msg.item("<gold><bold>Garra de Caranguejo"));
            meta.lore(List.of(Msg.item("<gray>Na mão: <white>+" + (reach % 1 == 0 ? String.valueOf((int) reach) : String.valueOf(reach))
                    + " blocos <gray>de alcance para construir."), Msg.item("<dark_gray>Caiu de um caranguejo do manguezal.")));
            meta.setItemModel(NamespacedKey.fromString("vertexx:entity/crab/claw_big"));
            meta.addAttributeModifier(Attribute.BLOCK_INTERACTION_RANGE, new AttributeModifier(
                    new NamespacedKey(plugin, "crab_claw_reach"), reach, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "crab_claw"), PersistentDataType.BYTE, (byte) 1);
            meta.setMaxStackSize(1);
        });
        return item;
    }

    @Override
    protected void think() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (climb > 0) {
            climb -= 2;
            if (support() != null) {
                mob.setVelocity(new Vector(0, 0.2, 0));
            } else {
                climb = 0;
            }
        } else if (age % 40 == 0 && random.nextDouble() < 0.2 && support() != null) {
            climb = 30;     // sobe uns três blocos pelo tronco ou pela raiz
        }
        if (waveTicks > 0) {
            waveTicks -= 2;
        } else if (age % 100 == 0 && random.nextDouble() < 0.25) {
            waveTicks = 24;
        }
        // troca de casca, rara: larga a garra velha
        if (age % 200 == 0 && random.nextDouble() < 0.004) {
            mob.getWorld().dropItemNaturally(mob.getLocation(), claw(plugin));
            mob.getWorld().spawnParticle(Particle.BLOCK, mob.getLocation().add(0, 0.3, 0), 16, 0.25, 0.1, 0.25, 0,
                    Material.MUD.createBlockData());
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_TURTLE_SHAMBLE, 0.8f, 1.4f);
        }
    }

    /** Tronco, raiz de mangue ou folhas encostados no caranguejo (algo onde ele consegue se agarrar). */
    private Block support() {
        Block at = mob.getLocation().getBlock();
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            Block b = at.getRelative(face);
            Material t = b.getType();
            if (Tag.LOGS.isTagged(t) || Tag.LEAVES.isTagged(t) || t == Material.MANGROVE_ROOTS || t == Material.MUDDY_MANGROVE_ROOTS) {
                return b;
            }
        }
        return null;
    }

    @Override
    protected float yaw() {
        // caranguejo anda de lado: quando se move, o corpo fica de través para a direção do passo
        return mob.getBodyYaw() + (speed > 0.015 ? 90f : 0f);
    }

    @Override
    protected void pose() {
        if (speed > 0.015 || climb > 0) {
            model.play("walk");
            model.speed((float) Math.min(2.4, 0.8 + speed * 9));
        } else {
            model.play(waveTicks > 0 ? "wave" : "idle");
            model.speed(1f);
        }
    }
}
