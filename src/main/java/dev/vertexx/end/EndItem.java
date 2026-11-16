package dev.vertexx.end;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Msg;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Itens customizados da End Update. Todos ficam marcados no PersistentDataContainer ({@code vertexx:end_item}),
 * usam um {@code item_model} do resource pack e um CustomModelData; os dois ids vêm do {@code config.yml}
 * ({@code end.items.<id>}), para ligar facilmente os modelos do pacote.
 */
public enum EndItem {
    ENDERITE_FRAGMENT("enderite_fragment", "Fragmento de Enderita", "<light_purple>", Material.AMETHYST_SHARD, 1001,
            "<gray>Extraído do minério de Enderita.",
            "<dark_gray>Na bigorna ou na mesa de ferraria,",
            "<dark_gray>tempera uma armadura contra o Vazio."),
    ETHEREAL_MEMBRANE("ethereal_membrane", "Membrana Etérea", "<aqua>", Material.PHANTOM_MEMBRANE, 1002,
            "<gray>Pele leve da Arraia do Vazio.",
            "<dark_gray>Repara Elytras na bigorna e entra",
            "<dark_gray>nas poções de gravidade."),
    VOID_CHITIN("void_chitin", "Quitina do Vazio", "<dark_purple>", Material.PAPER, 1003,
            "<gray>Carapaça de obsidiana do Rastejador do Fim.",
            "<dark_gray>Fabrica a Mochila do Vazio e reforça escudos."),
    LEVITATION_CORE("levitation_core", "Núcleo de Levitação", "<yellow>", Material.PAPER, 1004,
            "<gray>Cristal cinético da Sentinela de Shulker.",
            "<dark_gray>Poções de levitação e utilitários de movimento."),
    BACKPACK_LEATHER("backpack_leather", "Mochila de Couro", "<gold>", Material.PAPER, 1010,
            "<gray>9 espaços."),
    BACKPACK_IRON("backpack_iron", "Mochila Reforçada", "<white>", Material.PAPER, 1011,
            "<gray>18 espaços."),
    BACKPACK_VOID("backpack_void", "Mochila do Vazio", "<light_purple>", Material.PAPER, 1012,
            "<gray>27 espaços.", "<dark_purple>Coleta magnética de itens do chão.");

    private static final NamespacedKey KEY = new NamespacedKey("vertexx", "end_item");

    private final String id;
    private final String display;
    private final String color;
    private final Material base;
    private final int cmd;
    private final List<String> lore;

    EndItem(String id, String display, String color, Material base, int cmd, String... lore) {
        this.id = id;
        this.display = display;
        this.color = color;
        this.base = base;
        this.cmd = cmd;
        this.lore = List.of(lore);
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public String color() {
        return color;
    }

    /** 1 a 3 para as mochilas, 0 para os demais. */
    public int tier() {
        return switch (this) {
            case BACKPACK_LEATHER -> 1;
            case BACKPACK_IRON -> 2;
            case BACKPACK_VOID -> 3;
            default -> 0;
        };
    }

    public static EndItem backpack(int tier) {
        return switch (tier) {
            case 1 -> BACKPACK_LEATHER;
            case 2 -> BACKPACK_IRON;
            default -> BACKPACK_VOID;
        };
    }

    /** Cria o item. {@code extra} são linhas de lore adicionais (por exemplo, o resumo da mochila). */
    public ItemStack create(PluginMain plugin, List<Component> extra) {
        ItemStack item = new ItemStack(base);
        String model = plugin.getConfig().getString("end.items." + id + ".model", "vertexx:" + id);
        int data = plugin.getConfig().getInt("end.items." + id + ".custom-model-data", cmd);
        item.editMeta(meta -> {
            meta.itemName(Msg.mm(color + display));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Msg.item(line));
            }
            if (extra != null) {
                lines.addAll(extra);
            }
            meta.lore(lines);
            NamespacedKey key = NamespacedKey.fromString(model);
            if (key != null) {
                meta.setItemModel(key);
            }
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of((float) data));
            meta.setCustomModelDataComponent(component);
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);
        });
        if (tier() > 0) {
            item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
            // vestida no peito, o pack desenha a mochila nas costas; o botão direito abre a janela (não equipa)
            item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.CHEST)
                    .assetId(Key.key("vertexx", id)).equipOnInteract(false).dispensable(false).swappable(false).build());
        }
        return item;
    }

    public ItemStack create(PluginMain plugin) {
        return create(plugin, null);
    }

    /** O item customizado da End Update que esta pilha representa, ou nulo. */
    public static EndItem of(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        for (EndItem value : values()) {
            if (value.id.equals(raw)) {
                return value;
            }
        }
        return null;
    }

    public static boolean isBackpack(ItemStack item) {
        EndItem type = of(item);
        return type != null && type.tier() > 0;
    }

    public boolean matches(ItemStack item) {
        return of(item) == this;
    }
}
