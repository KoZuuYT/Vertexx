package dev.vertexx.waystone;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Custo de uma viagem, já com os modificadores do jogador aplicados. */
public record TravelCost(Type type, Material item, int amount) {

    public enum Type {
        ITEM, XP_LEVELS, NONE
    }

    public static final TravelCost FREE = new TravelCost(Type.NONE, Material.AIR, 0);

    public boolean isFree() {
        return type == Type.NONE || amount <= 0;
    }

    public boolean canPay(Player player) {
        if (isFree() || player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        return switch (type) {
            case ITEM -> player.getInventory().containsAtLeast(new ItemStack(item), amount);
            case XP_LEVELS -> player.getLevel() >= amount;
            case NONE -> true;
        };
    }

    public void charge(Player player) {
        if (isFree() || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        switch (type) {
            case ITEM -> player.getInventory().removeItem(new ItemStack(item, amount));
            case XP_LEVELS -> player.setLevel(Math.max(0, player.getLevel() - amount));
            case NONE -> {
            }
        }
    }

    /** Descrição em MiniMessage (o nome do item é traduzido pelo cliente). */
    public String describe() {
        if (isFree()) {
            return "<green>Gratuito";
        }
        return switch (type) {
            case ITEM -> "<white>" + amount + "x <lang:" + item.translationKey() + ">";
            case XP_LEVELS -> "<white>" + amount + (amount == 1 ? " nível" : " níveis") + " de XP";
            case NONE -> "<green>Gratuito";
        };
    }
}
