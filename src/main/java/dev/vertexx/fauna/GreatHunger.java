package dev.vertexx.fauna;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * The Great Hunger (Mob Vote 2017): réptil pequeno camuflado no chão, de boca escancarada. Engole itens jogados
 * perto dele: comida ele come; itens encantados (e livros com vários encantamentos) ele mastiga e cospe de volta
 * separados, o item limpo e um livro encantado para cada encantamento. Itens comuns ele ignora.
 *
 * <p>Tem variantes: o corpo imita o chão onde nasceu (terra, areia, madeira, pedra, ametista ou grama).
 */
final class GreatHunger extends Critter {

    static final List<String> VARIANTS = List.of("dirt", "sand", "wood", "stone", "amethyst", "grass");

    private int chew;
    private Item meal;
    private final List<ItemStack> spit = new ArrayList<>();

    GreatHunger(FaunaManager manager, Mob mob) {
        super(manager, mob);
        mob.setSilent(true);
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.GREAT_HUNGER;
    }

    @Override
    protected String modelId() {
        String v = mob.getPersistentDataContainer().get(manager.variantKey(), PersistentDataType.STRING);
        return "great_hunger_" + (v != null && VARIANTS.contains(v) ? v : "dirt");
    }

    /** Variante pelo bloco do chão e pelo bioma. */
    static String variantFor(Block ground) {
        Material t = ground.getType();
        if (t == Material.AMETHYST_BLOCK || t == Material.BUDDING_AMETHYST || t == Material.CALCITE) {
            return "amethyst";
        }
        if (Tag.SAND.isTagged(t) || t == Material.SANDSTONE || t == Material.BONE_BLOCK) {
            return "sand";
        }
        if (Tag.LOGS.isTagged(t) || Tag.PLANKS.isTagged(t) || t == Material.ROOTED_DIRT || t == Material.MANGROVE_ROOTS) {
            return "wood";
        }
        if (t == Material.GRASS_BLOCK || t == Material.MOSS_BLOCK) {
            Biome biome = ground.getBiome();
            return biome == Biome.DARK_FOREST || biome == Biome.OLD_GROWTH_PINE_TAIGA ? "wood" : "grass";
        }
        if (t == Material.STONE || t == Material.COBBLESTONE || t == Material.MOSSY_COBBLESTONE || t == Material.ANDESITE
                || t == Material.DIORITE || t == Material.GRANITE || t == Material.DEEPSLATE || t == Material.TUFF || t == Material.GRAVEL) {
            return "stone";
        }
        return "dirt";
    }

    private boolean playerNear(double r) {
        for (Player p : mob.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(mob.getLocation()) < r * r && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void think() {
        if (chew > 0) {
            chew -= 2;
            if (chew % 6 == 0) {
                mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.6f, 0.7f);
            }
            if (chew <= 0) {
                spitOut();
            }
            return;
        }
        if (age % 6 != 0) {
            return;
        }
        for (Entity e : mob.getNearbyEntities(2.2, 1.2, 2.2)) {
            if (!(e instanceof Item item) || !item.isValid() || item.getPickupDelay() > 30
                    || item.getPersistentDataContainer().has(manager.ignoreKey(), PersistentDataType.BYTE)) {
                continue;
            }
            if (swallow(item)) {
                return;
            }
        }
    }

    private boolean swallow(Item item) {
        ItemStack stack = item.getItemStack();
        List<ItemStack> out = new ArrayList<>();
        if (stack.getType() == Material.ENCHANTED_BOOK && stack.getItemMeta() instanceof EnchantmentStorageMeta book
                && book.getStoredEnchants().size() >= 2) {
            for (Map.Entry<Enchantment, Integer> en : book.getStoredEnchants().entrySet()) {
                out.add(book(en.getKey(), en.getValue()));
            }
            for (int i = 1; i < stack.getAmount(); i++) {
                out.add(stack.asOne());          // pilha: só separa um livro por vez
            }
        } else if (!stack.getEnchantments().isEmpty() && stack.getType() != Material.ENCHANTED_BOOK) {
            ItemStack clean = stack.clone();
            for (Enchantment en : stack.getEnchantments().keySet()) {
                clean.removeEnchantment(en);
                out.add(book(en, stack.getEnchantmentLevel(en)));
            }
            out.add(0, clean);
        } else if (stack.getType().isEdible()) {
            out.clear();                         // come: não devolve nada
        } else {
            return false;
        }
        spit.clear();
        spit.addAll(out);
        meal = item;
        item.remove();
        chew = stack.getType().isEdible() ? 16 : 30;
        model.restart("chomp");
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 0.6f);
        mob.getWorld().spawnParticle(Particle.ITEM, mob.getLocation().add(0, 0.4, 0), 12, 0.2, 0.1, 0.2, 0.05, stack.asOne());
        return true;
    }

    private static ItemStack book(Enchantment en, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        book.editMeta(EnchantmentStorageMeta.class, m -> m.addStoredEnchant(en, level, true));
        return book;
    }

    private void spitOut() {
        if (spit.isEmpty()) {
            mob.getWorld().spawnParticle(Particle.HEART, mob.getLocation().add(0, 0.8, 0), 3, 0.2, 0.2, 0.2, 0);
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.6f, 1.4f);
            return;
        }
        Vector front = mob.getLocation().getDirection().setY(0).normalize().multiply(0.25).setY(0.3);
        for (ItemStack s : spit) {
            Item dropped = mob.getWorld().dropItem(mob.getLocation().add(0, 0.5, 0), s);
            dropped.setVelocity(front.clone().add(new Vector(ThreadLocalRandom.current().nextDouble(-0.08, 0.08), 0,
                    ThreadLocalRandom.current().nextDouble(-0.08, 0.08))));
            dropped.getPersistentDataContainer().set(manager.ignoreKey(), PersistentDataType.BYTE, (byte) 1);
            dropped.setPickupDelay(15);
        }
        mob.getWorld().playSound(mob.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.3f);
        mob.getWorld().spawnParticle(Particle.ENCHANT, mob.getLocation().add(0, 0.6, 0), 30, 0.4, 0.3, 0.4, 0.6);
        spit.clear();
        meal = null;
    }

    @Override
    protected void cleanup() {
        if (!spit.isEmpty() && mob.getWorld() != null) {   // descarregado mastigando: devolve tudo
            for (ItemStack s : spit) {
                mob.getWorld().dropItem(mob.getLocation(), s);
            }
            spit.clear();
        }
    }

    @Override
    protected void pose() {
        if (chew > 0) {
            if (model.finished() || !model.playing().equals("chomp")) {
                model.play("idle");
            }
        } else if (speed > 0.01) {
            model.play("walk");
        } else {
            // espreita de boca aberta; fecha quando alguém chega perto demais
            model.play(playerNear(3.5) ? "idle" : "gape");
        }
        model.speed(1f);
    }
}
