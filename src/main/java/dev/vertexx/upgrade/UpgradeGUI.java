package dev.vertexx.upgrade;

import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Menu;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Ui;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Menu de upgrade de uma fornalha ou baú: níveis, custos e botão de melhorar. */
final class UpgradeGUI extends Menu {

    private static final int[] TIER_SLOTS = {28, 29, 30, 31, 32, 33};

    private final UpgradeManager manager;
    private final Block block;
    private final UpgradeManager.Kind kind;

    UpgradeGUI(UpgradeManager manager, Player player, Block block, UpgradeManager.Kind kind) {
        super(manager.plugin(), player, Ui.Theme.FORGE,
                "<gradient:#7f5af0:#2cb67d><bold>Vertexx</bold></gradient> <dark_gray>» <gray>Upgrade");
        this.manager = manager;
        this.block = block;
        this.kind = kind;
    }

    @Override
    protected void render() {
        frame(null);
        if (UpgradeManager.kindOf(block.getType()) != kind) {
            player.closeInventory();
            return;
        }
        List<UpgradeManager.Tier> tiers = manager.tiers(kind);
        int current = manager.tierOf(block);

        List<String> info = new ArrayList<>();
        info.add("<gray>Nível atual: <white>" + current + "<dark_gray>/<white>" + tiers.size());
        if (current > 0 && current <= tiers.size()) {
            info.add(manager.effectText(kind, tiers.get(current - 1)));
        } else {
            info.add(kind == UpgradeManager.Kind.FURNACE ? "<gray>Velocidade <white>normal" : "<gray>Capacidade <white>vanilla (27)");
        }
        set(13, Items.of(block.getType(), "<gold><bold>" + kind.display() + " " + (current > 0 ? "melhorada" : "comum"),
                info, current > 0), null);

        if (current >= tiers.size()) {
            set(22, Items.of(Material.NETHER_STAR, "<gold><bold>Nível máximo!",
                    List.of("<gray>Esta " + kind.display().toLowerCase() + " não pode ser melhorada mais.")), null);
        } else {
            UpgradeManager.Tier next = tiers.get(current);
            boolean afford = manager.canAfford(player, next);
            List<String> lore = new ArrayList<>();
            lore.add(manager.effectText(kind, next));
            lore.add("");
            lore.add("<gray>Custo:");
            manager.costLines(player, next).forEach(line -> lore.add(" " + line));
            lore.add("");
            lore.add(afford ? "<yellow>▶ Clique para melhorar" : "<red>Faltam materiais.");
            set(22, Items.of(afford ? Material.ANVIL : Material.BARRIER,
                    (afford ? "<green><bold>" : "<red><bold>") + "Melhorar para o nível " + (current + 1), lore, afford),
                    click -> doUpgrade());
        }

        for (int i = 0; i < tiers.size() && i < TIER_SLOTS.length; i++) {
            UpgradeManager.Tier tier = tiers.get(i);
            boolean done = i < current;
            boolean next = i == current;
            List<String> lore = new ArrayList<>();
            lore.add(manager.effectText(kind, tier));
            lore.add("");
            lore.add("<gray>Custo: <white>" + tier.amount() + "x " + UpgradeManager.itemName(tier.item()));
            if (tier.essence() > 0) {
                lore.add("<gray>        <white>" + tier.essence() + "x <gradient:#c4a7ff:#2cb67d>Essência Elite");
            }
            set(TIER_SLOTS[i], Items.of(tier.item(), (done ? "<green>✔ " : next ? "<yellow>" : "<gray>") + "Nível " + (i + 1),
                    lore, done), null);
        }
    }

    private void doUpgrade() {
        String error = manager.upgrade(player, block);
        if (error != null) {
            Fx.deny(player);
            Msg.actionBar(player, "<red>" + error);
        } else {
            Fx.success(player);
        }
        refresh();
    }
}
