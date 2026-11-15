package dev.vertexx.waystone;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Ui;
import dev.vertexx.kingdom.Kingdom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Menu de viagem rápida: destinos descobertos com revelação gradual, favoritos, ordenação, filtro
 * por dimensão e um cabeçalho com os dados do Waystone atual, decorado com as cores do reino do dono.
 */
public final class WaystoneGUI implements InventoryHolder {

    private enum Sort {
        NAME("Nome", Material.NAME_TAG),
        DISTANCE("Distância", Material.COMPASS),
        STARS("Favoritos primeiro", Material.NETHER_STAR),
        USES("Mais visitados", Material.CLOCK);

        private final String label;
        private final Material icon;

        Sort(String label, Material icon) {
            this.label = label;
            this.icon = icon;
        }

        Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private enum Filter {
        ALL("Todas as dimensões", Material.ENDER_EYE, null),
        OVERWORLD("Superfície", Material.GRASS_BLOCK, World.Environment.NORMAL),
        NETHER("Nether", Material.NETHERRACK, World.Environment.NETHER),
        END("The End", Material.END_STONE, World.Environment.THE_END);

        private final String label;
        private final Material icon;
        private final World.Environment environment;

        Filter(String label, Material icon, World.Environment environment) {
            this.label = label;
            this.icon = icon;
            this.environment = environment;
        }

        Filter next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final int SIZE = 54;
    private static final int PAYMENT_SLOT = 2;
    private static final int SORT_SLOT = 3;
    private static final int ORIGIN_SLOT = 4;
    private static final int FILTER_SLOT = 5;
    private static final int CONFIG_SLOT = 6;
    private static final int PREVIOUS_SLOT = 45;
    private static final int HELP_SLOT = 47;
    private static final int CLOSE_SLOT = 49;
    private static final int STATS_SLOT = 51;
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
    private final boolean pack;
    private final List<Waystone> all;
    private final Map<Integer, Waystone> bySlot = new HashMap<>();
    private List<Waystone> destinations;
    private TravelCost cost;
    private Sort sort = Sort.NAME;
    private Filter filter = Filter.ALL;
    private int page;
    private BukkitTask reveal;

    public WaystoneGUI(PluginMain plugin, Player player, Waystone origin) {
        this.plugin = plugin;
        this.player = player;
        this.origin = origin;
        this.all = plugin.waystones().destinations(player, origin);
        this.cost = plugin.waystones().costFor(player);
        this.pack = plugin.resourcePack().isLoaded(player);
        this.inventory = Bukkit.createInventory(this, SIZE, Ui.title(pack,
                Ui.Theme.of(plugin.kingdoms().kingdomOf(origin.owner())),
                "<gradient:#c4a7ff:#2cb67d><bold>Waystone</bold></gradient> <dark_gray>» <gray>" + Msg.esc(origin.name())));
        applyView();
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

    // ── Cliques ────────────────────────────────────────────────

    public void handleClick(int slot, ClickType click) {
        Waystone destination = bySlot.get(slot);
        if (destination != null) {
            if (click.isRightClick()) {
                boolean now = plugin.waystones().toggleFavorite(player, destination);
                Fx.select(player);
                Msg.actionBar(player, now ? "<yellow>★ <white><name></white> adicionado aos favoritos"
                                : "<gray>☆ <white><name></white> removido dos favoritos",
                        Placeholder.unparsed("name", destination.name()));
                refreshView();
                return;
            }
            Fx.click(player);
            plugin.waystones().travel(player, destination);
            return;
        }
        switch (slot) {
            case PAYMENT_SLOT -> {
                plugin.waystones().cyclePayment(player);
                Fx.select(player);
                cost = plugin.waystones().costFor(player);
                refreshView();
            }
            case SORT_SLOT -> {
                sort = sort.next();
                Fx.select(player);
                page = 0;
                refreshView();
            }
            case FILTER_SLOT -> {
                filter = filter.next();
                Fx.select(player);
                page = 0;
                refreshView();
            }
            case CONFIG_SLOT -> {
                if (plugin.waystones().canConfigure(player, origin)) {
                    Fx.click(player);
                    new WaystoneConfigGUI(plugin, player, origin).open();
                }
            }
            case PREVIOUS_SLOT -> {
                if (page > 0) {
                    page--;
                    turnPage();
                }
            }
            case NEXT_SLOT -> {
                if (page < pageCount() - 1) {
                    page++;
                    turnPage();
                }
            }
            case CLOSE_SLOT -> {
                Fx.click(player);
                player.closeInventory();
            }
            default -> {
            }
        }
    }

    private void turnPage() {
        Fx.open(player);
        render();
    }

    private void refreshView() {
        applyView();
        render();
    }

    private int pageCount() {
        return Math.max(1, (destinations.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
    }

    /** Filtra e ordena a lista conforme as escolhas atuais. */
    private void applyView() {
        List<Waystone> list = new ArrayList<>();
        for (Waystone waystone : all) {
            World world = waystone.pos().bukkitWorld();
            if (filter.environment == null || (world != null && world.getEnvironment() == filter.environment)) {
                list.add(waystone);
            }
        }
        Comparator<Waystone> byName = Comparator.comparing(w -> w.name().toLowerCase(Locale.ROOT));
        switch (sort) {
            case NAME -> list.sort(byName);
            case DISTANCE -> list.sort(Comparator.comparingDouble(this::distanceTo).thenComparing(byName));
            case STARS -> list.sort(Comparator
                    .comparing((Waystone w) -> !plugin.waystones().isFavorite(player, w)).thenComparing(byName));
            case USES -> list.sort(Comparator.comparingInt((Waystone w) -> w.uses()).reversed().thenComparing(byName));
        }
        destinations = list;
        page = Math.min(page, pageCount() - 1);
    }

    private double distanceTo(Waystone waystone) {
        World world = waystone.pos().bukkitWorld();
        if (world == null || !world.equals(player.getWorld())) {
            return Double.MAX_VALUE;
        }
        return player.getLocation().distanceSquared(waystone.pos().center());
    }

    // ── Renderização ───────────────────────────────────────────

    private Material themePane() {
        Kingdom owner = plugin.kingdoms().kingdomOf(origin.owner());
        return owner == null ? Material.PURPLE_STAINED_GLASS_PANE : owner.pane();
    }

    private void frame() {
        ItemStack theme = Items.filler(themePane());
        ItemStack dark = Items.filler(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack soft = Items.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            ItemStack item = null;
            if (row == 0 || row == 5) {
                item = column == 0 || column == 8 ? theme : column % 2 == 1 ? soft : dark;
            } else if (column == 0 || column == 8) {
                item = row == 2 || row == 3 ? theme : dark;
            }
            inventory.setItem(slot, item);
        }
    }

    private void render() {
        close();
        bySlot.clear();
        frame();

        long days = Math.max(0, (System.currentTimeMillis() - origin.created()) / 86_400_000L);
        Kingdom ownerKingdom = plugin.kingdoms().kingdomOf(origin.owner());
        List<String> originLore = new ArrayList<>();
        originLore.add("<dark_gray>▸ Você está aqui");
        originLore.add("");
        originLore.add("<gray>Dono: <white>" + origin.ownerName()
                + (ownerKingdom == null ? "" : " <dark_gray>(" + ownerKingdom.styled() + "<dark_gray>)"));
        originLore.add("<gray>Skin: <white>" + origin.skin().label());
        originLore.add("<gray>Erguido há: <white>" + (days == 0 ? "menos de um dia" : days + (days == 1 ? " dia" : " dias")));
        originLore.add("<gray>Viagens até aqui: <white>" + origin.uses());
        originLore.add("");
        originLore.add("<gray>Custo por viagem: " + cost.describe());
        originLore.add(origin.route() == null ? "<dark_gray>Sem rota fixa."
                : "<gray>Rota fixa ativa <dark_gray>(Shift + clique direito abre este menu)");
        inventory.setItem(ORIGIN_SLOT, Items.of(origin.skin().pillar(),
                "<gradient:#c4a7ff:#2cb67d><bold>" + Msg.esc(origin.name()), originLore, true));

        Material paying = plugin.waystones().chosenPayment(player);
        inventory.setItem(PAYMENT_SLOT, Items.of(paying, "<gradient:#c4a7ff:#2cb67d>Pagamento da viagem",
                List.of("<gray>Você paga com: " + cost.describe(),
                        "",
                        "<yellow>▶ Clique para trocar <dark_gray>(ametista, pérola ou resina)")));
        inventory.setItem(SORT_SLOT, Items.of(sort.icon, "<gradient:#c4a7ff:#2cb67d>Ordenar",
                List.of("<gray>Atual: <white>" + sort.label, "",
                        "<yellow>▶ Clique para alternar", "<dark_gray>Nome, distância, favoritos, visitas")));
        inventory.setItem(FILTER_SLOT, Items.of(filter.icon, "<gradient:#c4a7ff:#2cb67d>Filtrar",
                List.of("<gray>Mostrando: <white>" + filter.label, "",
                        "<yellow>▶ Clique para alternar")));
        if (plugin.waystones().canConfigure(player, origin)) {
            inventory.setItem(CONFIG_SLOT, Items.of(Material.COMPARATOR, "<gradient:#c4a7ff:#2cb67d>Configurar Waystone",
                    List.of("<gray>Skin, ícone, nome flutuante", "<gray>e rota fixa.", "", "<yellow>▶ Clique para abrir")));
        }

        int pages = pageCount();
        inventory.setItem(CLOSE_SLOT, Items.of(Material.BARRIER, "<red>Fechar", List.of()));
        inventory.setItem(HELP_SLOT, Items.of(Material.WRITABLE_BOOK, "<gradient:#c4a7ff:#2cb67d>Como usar",
                List.of("<yellow>Clique esquerdo<gray>: viajar",
                        "<yellow>Clique direito<gray>: favoritar ★",
                        "",
                        "<dark_gray>Waystones novos entram na lista",
                        "<dark_gray>assim que você os descobre.")));
        int favorites = 0;
        for (Waystone waystone : all) {
            if (plugin.waystones().isFavorite(player, waystone)) {
                favorites++;
            }
        }
        inventory.setItem(STATS_SLOT, Items.of(Material.MAP, "<gradient:#c4a7ff:#2cb67d>Seu mapa",
                List.of("<gray>Descobertos: <white>" + plugin.waystones().discoveredCount(player)
                                + "<dark_gray>/<white>" + plugin.waystones().all().size(),
                        "<gray>Favoritos: <yellow>" + favorites,
                        "<gray>Na lista: <white>" + destinations.size(),
                        "<gray>Página: <white>" + (page + 1) + "<dark_gray>/<white>" + pages)));
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, Items.of(Material.ARROW, "<yellow>◀ Página anterior",
                    List.of("<dark_gray>Página " + page + " de " + pages)));
        }
        if (page < pages - 1) {
            inventory.setItem(NEXT_SLOT, Items.of(Material.ARROW, "<yellow>Próxima página ▶",
                    List.of("<dark_gray>Página " + (page + 2) + " de " + pages)));
        }
        Ui.skin(pack, inventory);

        if (destinations.isEmpty()) {
            boolean filtered = !all.isEmpty();
            inventory.setItem(22, Items.of(Material.COMPASS,
                    filtered ? "<gray>Nada nesta dimensão" : "<gray>Nenhum outro Waystone descoberto",
                    List.of(filtered ? "<dark_gray>Troque o filtro para ver os outros."
                                    : "<dark_gray>Explore o mundo e clique em outros",
                            filtered ? "" : "<dark_gray>Waystones para registrá-los aqui.")));
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
        String dimension = "Mundo desconhecido";
        if (world != null) {
            dimension = switch (world.getEnvironment()) {
                case NETHER -> "Nether";
                case THE_END -> "The End";
                default -> "Superfície";
            };
        }
        boolean favorite = plugin.waystones().isFavorite(player, waystone);
        Kingdom ownerKingdom = plugin.kingdoms().kingdomOf(waystone.owner());
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Dono: <white>" + waystone.ownerName()
                + (ownerKingdom == null ? "" : " <dark_gray>(" + ownerKingdom.styled() + "<dark_gray>)"));
        lore.add("<gray>Local: <white>" + dimension + " <dark_gray>" + waystone.pos().coords());
        if (world != null && world.equals(player.getWorld())) {
            int distance = (int) Math.round(player.getLocation().distance(waystone.pos().center()));
            lore.add("<gray>Distância: <white>" + distance + " blocos");
        }
        lore.add("<gray>Skin: <white>" + waystone.skin().label() + " <dark_gray>• <gray>Visitas: <white>" + waystone.uses());
        lore.add("");
        lore.add("<gray>Custo: " + cost.describe());
        lore.add(cost.canPay(player) ? "<yellow>▶ Esquerdo: viajar" : "<red>✖ Você não pode pagar o custo");
        lore.add(favorite ? "<yellow>▶ Direito: remover dos favoritos" : "<dark_gray>▶ Direito: favoritar ★");
        return Items.of(waystone.skin().pillar(),
                (favorite ? "<yellow>★ " : "") + "<gradient:#c4a7ff:#2cb67d>" + Msg.esc(waystone.name()), lore, favorite);
    }
}
