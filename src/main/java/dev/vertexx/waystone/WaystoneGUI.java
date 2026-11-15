package dev.vertexx.waystone;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Msg;
import dev.vertexx.kingdom.Kingdom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/** Menu de viagem rápida: lista os Waystones descobertos, com revelação gradual dos destinos. */
public final class WaystoneGUI implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int PAYMENT_SLOT = 2;
    private static final int ORIGIN_SLOT = 4;
    private static final int CONFIG_SLOT = 6;
    private static final int PREVIOUS_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private final PluginMain plugin;
    private final Player player;
    private final Waystone origin;
    private final Inventory inventory;
    private final List<Waystone> destinations;
    private final Map<Integer, Waystone> bySlot = new HashMap<>();
    private TravelCost cost;
    private int page;
    private BukkitTask reveal;

    public WaystoneGUI(PluginMain plugin, Player player, Waystone origin) {
        this.plugin = plugin;
        this.player = player;
        this.origin = origin;
        this.destinations = plugin.waystones().destinations(player, origin);
        this.cost = plugin.waystones().costFor(player);
        this.inventory = Bukkit.createInventory(this, SIZE,
                Msg.mm("<gradient:#c4a7ff:#2cb67d><bold>Waystone</bold></gradient> <dark_gray>» <dark_gray><name>",
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("name", origin.name())));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        render();
        player.openInventory(inventory);
        Fx.sound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.9f);
    }

    public void close() {
        if (reveal != null) {
            reveal.cancel();
            reveal = null;
        }
    }

    public void handleClick(int slot) {
        Waystone destination = bySlot.get(slot);
        if (destination != null) {
            Fx.click(player);
            plugin.waystones().travel(player, destination);
            return;
        }
        if (slot == PAYMENT_SLOT) {
            plugin.waystones().cyclePayment(player);
            Fx.select(player);
            cost = plugin.waystones().costFor(player);
            render();
            return;
        }
        if (slot == CONFIG_SLOT && plugin.waystones().canConfigure(player, origin)) {
            Fx.click(player);
            new WaystoneConfigGUI(plugin, player, origin).open();
            return;
        }
        int pages = pageCount();
        if (slot == PREVIOUS_SLOT && page > 0) {
            page--;
            turnPage();
        } else if (slot == NEXT_SLOT && page < pages - 1) {
            page++;
            turnPage();
        } else if (slot == CLOSE_SLOT) {
            Fx.click(player);
            player.closeInventory();
        }
    }

    private void turnPage() {
        Fx.open(player);
        render();
    }

    private int pageCount() {
        return Math.max(1, (destinations.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
    }

    private void render() {
        close();
        bySlot.clear();
        ItemStack border = Items.filler(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack dark = Items.filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            boolean edge = row == 0 || row == 5;
            boolean side = column == 0 || column == 8;
            inventory.setItem(slot, edge ? border : side ? dark : null);
        }

        inventory.setItem(ORIGIN_SLOT, Items.of(Material.LODESTONE,
                "<gradient:#c4a7ff:#2cb67d><bold>" + Msg.esc(origin.name()),
                List.of(
                        "<gray>Você está aqui.",
                        "<gray>Dono: <white>" + origin.ownerName(),
                        "",
                        "<gray>Custo por viagem: " + cost.describe(),
                        origin.route() == null ? "<dark_gray>Sem rota fixa." : "<gray>Rota fixa ativa <dark_gray>(Shift + clique direito abre este menu)",
                        "<dark_gray>Waystones descobertos: " + (destinations.size() + 1)), true));
        Material paying = plugin.waystones().chosenPayment(player);
        inventory.setItem(PAYMENT_SLOT, Items.of(paying, "<gradient:#c4a7ff:#2cb67d>Pagamento da viagem",
                List.of("<gray>Você paga com: " + cost.describe(),
                        "",
                        "<yellow>▶ Clique para trocar <dark_gray>(ametista, pérola ou resina)")));
        if (plugin.waystones().canConfigure(player, origin)) {
            inventory.setItem(CONFIG_SLOT, Items.of(Material.COMPARATOR, "<gradient:#c4a7ff:#2cb67d>Configurar Waystone",
                    List.of("<gray>Ícone, nome flutuante e rota fixa.", "", "<yellow>▶ Clique para abrir")));
        }
        inventory.setItem(CLOSE_SLOT, Items.of(Material.BARRIER, "<red>Fechar", List.of()));

        int pages = pageCount();
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, Items.of(Material.ARROW, "<yellow>◀ Página anterior",
                    List.of("<dark_gray>Página " + page + " de " + pages)));
        }
        if (page < pages - 1) {
            inventory.setItem(NEXT_SLOT, Items.of(Material.ARROW, "<yellow>Próxima página ▶",
                    List.of("<dark_gray>Página " + (page + 2) + " de " + pages)));
        }

        if (destinations.isEmpty()) {
            inventory.setItem(22, Items.of(Material.COMPASS, "<gray>Nenhum outro Waystone descoberto",
                    List.of(
                            "<dark_gray>Explore o mundo e clique em outros",
                            "<dark_gray>Waystones para registrá-los aqui.")));
            return;
        }

        // transição suave: os destinos surgem aos poucos, com um toque sonoro crescente
        int from = page * CONTENT_SLOTS.length;
        int to = Math.min(destinations.size(), from + CONTENT_SLOTS.length);
        List<Waystone> visible = new ArrayList<>(destinations.subList(from, to));
        int[] index = {0};
        reveal = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (inventory.getViewers().isEmpty() && index[0] > 0) {
                close();
                return;
            }
            for (int step = 0; step < 2 && index[0] < visible.size(); step++, index[0]++) {
                int slot = CONTENT_SLOTS[index[0]];
                Waystone waystone = visible.get(index[0]);
                inventory.setItem(slot, destinationItem(waystone));
                bySlot.put(slot, waystone);
            }
            Fx.sound(player, Sound.BLOCK_AMETHYST_CLUSTER_STEP, 0.35f,
                    0.8f + 0.9f * index[0] / Math.max(1, visible.size()));
            if (index[0] >= visible.size()) {
                close();
            }
        }, 1L, 1L);
    }

    private ItemStack destinationItem(Waystone waystone) {
        World world = waystone.pos().bukkitWorld();
        Material icon = Material.GRASS_BLOCK;
        String dimension = "Mundo desconhecido";
        if (world != null) {
            switch (world.getEnvironment()) {
                case NETHER -> {
                    icon = Material.NETHERRACK;
                    dimension = "Nether";
                }
                case THE_END -> {
                    icon = Material.END_STONE;
                    dimension = "The End";
                }
                default -> dimension = "Superfície";
            }
        }
        Kingdom ownerKingdom = plugin.kingdoms().kingdomOf(waystone.owner());
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Dono: <white>" + waystone.ownerName()
                + (ownerKingdom == null ? "" : " <dark_gray>(" + ownerKingdom.styled() + "<dark_gray>)"));
        lore.add("<gray>Local: <white>" + dimension + " <dark_gray>" + waystone.pos().coords());
        if (world != null && world.equals(player.getWorld())) {
            int distance = (int) Math.round(player.getLocation().distance(waystone.pos().center()));
            lore.add("<gray>Distância: <white>" + distance + " blocos");
        }
        lore.add("");
        lore.add("<gray>Custo: " + cost.describe());
        lore.add(cost.canPay(player) ? "<yellow>▶ Clique para viajar" : "<red>✖ Você não pode pagar o custo");
        return Items.of(icon, "<gradient:#c4a7ff:#2cb67d>" + Msg.esc(waystone.name()), lore);
    }
}
