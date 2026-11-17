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
    AMBER_FRAGMENT("amber_fragment", "Fragmento de Âmbar", "<gold>", Material.PAPER, 1020,
            "<gray>Seiva fossilizada das camadas profundas.",
            "<dark_gray>4 fragmentos + 1 fóssil na Incubadora Geotérmica."),
    CALCIFIED_FOSSIL("calcified_fossil", "Fóssil Calcificado", "<white>", Material.PAPER, 1021,
            "<gray>Restos de uma criatura pré-histórica.",
            "<dark_gray>Reviva-o na Incubadora Geotérmica."),
    TERMITE_LARVA("termite_larva", "Larva de Cupim", "<yellow>", Material.PAPER, 1022,
            "<gray>Isca de pesca: segure na outra mão", "<gray>ao pescar para os peixes morderem mais rápido."),
    COCONUT("coconut", "Coco", "<white>", Material.PAPER, 1023,
            "<gray>Fruto de palmeira de oásis.", "<dark_gray>Restaura fome e um pouco de vida."),
    OSTRICH_EGG("ostrich_egg", "Ovo de Avestruz", "<white>", Material.PAPER, 1024,
            "<gray>Grande e pesado.", "<dark_gray>Use num bloco de feno para chocar."),
    WILDFIRE_CORE("wildfire_core", "Núcleo de Wildfire", "<gold>", Material.PAPER, 1030,
            "<gray>O coração em brasa do guardião", "<gray>da fortaleza do Nether.",
            "<dark_gray>Encaixa no Portal do End trancado."),
    ICEOLOGER_CORE("iceologer_core", "Núcleo de Iceologer", "<aqua>", Material.PAPER, 1031,
            "<gray>O núcleo congelado do senhor", "<gray>do castelo de gelo.",
            "<dark_gray>Encaixa no Portal do End trancado."),
    CRYSTAL_HEART("crystal_heart", "Coração de Cristal do Vazio", "<light_purple>", Material.PAPER, 1032,
            "<gray>Âncora de retorno dimensional.", "<dark_gray>Se você cair no Vazio com ele no inventário,",
            "<dark_gray>volta à última superfície segura (4 cargas).",
            "<dark_gray>Recarregue com Pérolas do Fim na bigorna."),
    CHITIN_PLATE("chitin_plate", "Placa de Quitina Reforçada", "<dark_purple>", Material.PAPER, 1033,
            "<gray>Carapaça do Void-Scarab.", "<dark_gray>Mesa de ferraria: equipamento pós-Netherite."),
    SCARAB_MANDIBLE("scarab_mandible", "Mandíbula de Escaravelho", "<white>", Material.PAPER, 1034,
            "<gray>Mandíbula abissal do Void-Scarab.", "<dark_gray>Forja a Segadora de Mandíbula."),
    GLOOM_HEART("gloom_heart", "Coração da Penumbra", "<dark_aqua>", Material.PAPER, 1060,
            "<gray>O coração azul do Gloom Stalker.",
            "<dark_aqua>◆ Passo Sombrio <gray>(botão direito, 10 s)",
            "<dark_gray>Invisível e silencioso para sensores de Sculk",
            "<dark_gray>e Wardens por 8 s, com Speed II e visão noturna.",
            "<dark_gray>32 usos; recarregue com Echo Shards na bigorna."),
    STALKER_CLAW("stalker_claw", "Garra do Espreitador", "<dark_aqua>", Material.PAPER, 1061,
            "<gray>Garra de ardósia do Gloom Stalker.",
            "<dark_gray>2 Garras + espada de diamante ou netherite,",
            "<dark_gray>na bancada, forjam as Garras de Ardósia Sônica."),
    SCULK_CARAPACE("sculk_carapace", "Carapaça Infundida em Sculk", "<dark_aqua>", Material.PAPER, 1062,
            "<gray>Casco do Gloom Stalker, vibrando de sculk.",
            "<dark_gray>Mesa de ferraria: modelo + Peitoral de Netherite",
            "<dark_gray>+ Echo Shard = Peitoral de Carapaça Sculk."),
    WARP_STONE("warp_stone", "Pedra de Warp", "<light_purple>", Material.PAPER, 1040,
            "<gray>Conecta-se a todos os Waystones", "<gray>que você já conheceu.",
            "<dark_gray>Sem custo de pérolas, mas com poucas cargas."),
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

    /** Linhas de lore base do item (sem as linhas extras). */
    public List<String> loreLines() {
        return lore;
    }

    /** Núcleos de boss que destrancam o Portal do End. */
    public boolean isBossCore() {
        return this == WILDFIRE_CORE || this == ICEOLOGER_CORE;
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
            meta.setTooltipStyle(dev.vertexx.core.Items.TOOLTIP);
            NamespacedKey key = NamespacedKey.fromString(model);
            if (key != null) {
                meta.setItemModel(key);
            }
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of((float) data));
            meta.setCustomModelDataComponent(component);
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);
        });
        if (this == COCONUT) {
            item.setData(DataComponentTypes.FOOD, io.papermc.paper.datacomponent.item.FoodProperties.food()
                    .nutrition(5).saturation(6f).canAlwaysEat(false).build());
            item.setData(DataComponentTypes.CONSUMABLE, io.papermc.paper.datacomponent.item.Consumable.consumable().build());
        }
        if (this == CRYSTAL_HEART || this == GLOOM_HEART) {
            item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
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
