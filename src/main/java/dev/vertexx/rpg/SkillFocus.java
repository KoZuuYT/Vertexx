package dev.vertexx.rpg;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Items;
import dev.vertexx.core.Msg;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Foco de Habilidades: o item que usa as habilidades. Só age enquanto está na mão, então nunca atrapalha
 * construção, arco, comida ou poção. Botão direito usa a habilidade selecionada (ou liga/desliga um modo),
 * botão esquerdo no ar troca, Shift + direito abre o menu de seleção. Funciona igual no Java e no Bedrock;
 * o comando {@code /habilidade} faz o mesmo sem o item.
 */
public final class SkillFocus implements Listener {

    private final PluginMain plugin;
    private final SkillManager skills;
    private final NamespacedKey key;

    SkillFocus(PluginMain plugin, SkillManager skills) {
        this.plugin = plugin;
        this.skills = skills;
        this.key = new NamespacedKey(plugin, "skill_focus");
    }

    public ItemStack create() {
        ItemStack item = Items.of(Material.DISC_FRAGMENT_5, "<gradient:#4cc9f0:#7f5af0><bold>Foco de Habilidades",
                List.of("<gray>Segure para usar suas habilidades.", "",
                        "<yellow>Botão direito <gray>usa a habilidade (ou liga/desliga o modo)",
                        "<yellow>Botão esquerdo <gray>no ar troca de habilidade",
                        "<yellow>Shift + direito <gray>abre o menu de seleção", "",
                        "<dark_gray>Sem o item: /habilidade usar | trocar | menu"), true);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1));
        return item;
    }

    public boolean is(ItemStack item) {
        return item != null && item.getType() == Material.DISC_FRAGMENT_5 && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** O jogador está com o Foco na mão principal. */
    public boolean holding(Player player) {
        return is(player.getInventory().getItemInMainHand());
    }

    public boolean has(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (is(item)) {
                return true;
            }
        }
        return false;
    }

    /** Entrega o Foco a quem tem alguma habilidade usável e ainda não o tem. */
    public void ensure(Player player) {
        if (!plugin.rpg().enabled() || !skills.hasActives(player) || has(player)) {
            return;
        }
        player.getInventory().addItem(create()).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        Msg.send(player, "<aqua>Você recebeu o <white>Foco de Habilidades</white>. <gray>Segure-o e use o botão direito para usar a "
                + "habilidade; <white>/habilidade</white> faz o mesmo sem o item.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && plugin.isReady()) {
                ensure(player);
            }
        }, 60L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !is(event.getItem())) {
            return;
        }
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            // baús, portas, botões e afins continuam funcionando normalmente (agachado, o Foco age)
            if (action == Action.RIGHT_CLICK_BLOCK && !player.isSneaking() && event.getClickedBlock() != null
                    && event.getClickedBlock().getType().isInteractable()) {
                return;
            }
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            if (player.isSneaking()) {
                open(player);
            } else {
                use(player);
            }
        } else if (action == Action.LEFT_CLICK_AIR) {
            event.setCancelled(true);
            change(player);
        }
    }

    // ── Ações (também usadas pelo comando /habilidade) ────────

    public void use(Player player) {
        if (!skills.hasActives(player)) {
            noSkill(player);
            return;
        }
        skills.cast(player);
    }

    public void change(Player player) {
        if (!skills.cycle(player)) {
            noSkill(player);
        }
    }

    public void open(Player player) {
        if (!skills.hasActives(player)) {
            noSkill(player);
            return;
        }
        new SkillMenu(plugin, player, skills, this).open();
    }

    private void noSkill(Player player) {
        dev.vertexx.core.Fx.deny(player);
        Msg.actionBar(player, "<gray>Você ainda não desbloqueou uma habilidade ativa. <white>/vstats habilidades");
    }
}
