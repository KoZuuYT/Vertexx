package dev.vertexx.grave;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Items;
import dev.vertexx.core.Menu;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Ui;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Administração de túmulos: lista de todos, ajustes globais, e por túmulo ver o interior, mexer no tempo de
 * proteção, entregar, teleportar e quebrar. Equivale a {@code /tumulo admin}.
 */
public final class GraveMenu extends Menu {

    private static final int PER_PAGE = LIST_SLOTS.length;
    private static final String TITLE = "<gradient:#9be7ff:#c4a7ff><bold>Túmulos (admin)</bold></gradient>";

    private final int page;
    private final java.util.UUID owner;

    public GraveMenu(PluginMain plugin, Player player, int page) {
        this(plugin, player, page, null);
    }

    /** Com {@code owner}, lista só os túmulos desse jogador. */
    public GraveMenu(PluginMain plugin, Player player, int page, java.util.UUID owner) {
        super(plugin, player, Ui.Theme.INFO, TITLE);
        this.page = page;
        this.owner = owner;
    }

    static List<String> details(PluginMain plugin, Grave grave) {
        GraveManager graves = plugin.graves();
        long left = graves.protectionLeft(grave);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Dono: <white>" + Msg.esc(grave.ownerName()));
        lore.add("<gray>Local: <white>" + grave.pos().world() + " <dark_gray>" + grave.pos().coords());
        lore.add("<gray>Itens: <white>" + graves.contents(grave).size() + " <dark_gray>• <gray>XP: <white>" + grave.xp());
        lore.add(left == 0 ? "<red>Desprotegido: qualquer um abre"
                : left == Long.MAX_VALUE ? "<green>Proteção permanente"
                : "<green>Protegido por <white>" + GraveManager.formatTime(left));
        long age = (System.currentTimeMillis() - grave.created()) / 60_000L;
        lore.add("<gray>Criado há <white>" + (age >= 60 ? age / 60 + " h " + age % 60 + " min" : age + " min"));
        return lore;
    }

    static void teleport(PluginMain plugin, Player player, Grave grave) {
        World world = grave.pos().bukkitWorld();
        if (world == null) {
            Msg.send(player, "<red>O mundo deste túmulo não está carregado.");
            return;
        }
        Location spot = grave.pos().center().add(0, 1, 0);
        player.teleportAsync(spot).thenAccept(ok -> Msg.send(player, ok ? "<green>Teleportado ao túmulo de <white>"
                + Msg.esc(grave.ownerName()) + "</white>." : "<red>Não foi possível teleportar."));
    }

    @Override
    protected void render() {
        frame(owner == null ? () -> plugin.panel().open(player) : () -> new GravePlayersMenu(plugin, player, 0).open());
        GraveManager graves = plugin.graves();
        List<Grave> all = owner == null ? graves.all() : graves.ownedBy(owner);
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));

        set(HEADER_SLOT, Items.of(Material.SKELETON_SKULL, "<gradient:#9be7ff:#c4a7ff><bold>Túmulos",
                List.of((owner == null ? "<gray>Ativos no mapa: <white>" : "<gray>Túmulos do jogador: <white>") + all.size(), "<gray>Clique em um para ver o interior,",
                        "<gray>mexer na proteção ou quebrar.", "", "<dark_gray>Atalho: Shift + clique direito no bloco.")));

        if (all.isEmpty()) {
            set(22, Items.of(Material.BARRIER, "<gray>Nenhum túmulo ativo", List.of()));
        }
        for (int i = 0; i < PER_PAGE && current * PER_PAGE + i < all.size(); i++) {
            Grave grave = all.get(current * PER_PAGE + i);
            List<String> lore = details(plugin, grave);
            lore.add("");
            lore.add("<yellow>▶ Clique para gerenciar");
            set(LIST_SLOTS[i], Items.of(Material.PLAYER_HEAD, "<white>☠ " + Msg.esc(grave.ownerName()), lore),
                    c -> new GraveDetailMenu(plugin, player, grave.id(), current, owner).open());
        }
        if (current > 0) {
            set(46, Items.of(Material.ARROW, "<yellow>◀ Página " + current, List.of()), c -> new GraveMenu(plugin, player, current - 1, owner).open());
        }
        if (current < pages - 1) {
            set(52, Items.of(Material.ARROW, "<yellow>Página " + (current + 2) + " ▶", List.of()), c -> new GraveMenu(plugin, player, current + 1, owner).open());
        }

        set(51, Items.of(Material.PLAYER_HEAD, "<white><bold>Por jogador",
                List.of("<gray>Todos os túmulos do mapa, agrupados", "<gray>pelo dono.", "", "<yellow>▶ Clique para abrir")),
                c -> new GravePlayersMenu(plugin, player, 0).open());

        boolean enabled = plugin.getConfig().getBoolean("graves.enabled", true);
        set(47, Items.of(enabled ? Material.LIME_DYE : Material.GRAY_DYE, enabled ? "<green>Túmulos: ligados" : "<gray>Túmulos: desligados",
                List.of("<gray>Desligado, quem morre solta os itens", "<gray>normalmente. Túmulos existentes continuam.", "",
                        "<yellow>▶ Clique para alternar")), c -> {
            plugin.getConfig().set("graves.enabled", !enabled);
            plugin.saveConfig();
            refresh();
        });

        long minutes = plugin.getConfig().getLong("graves.protection-minutes", 30);
        set(48, Items.of(Material.CLOCK, "<white><bold>Proteção padrão: " + minutes + " min",
                List.of("<gray>Tempo em que só o dono (ou uma Chave)", "<gray>abre um túmulo novo.",
                        "<dark_gray>Não muda túmulos já ajustados à mão.", "",
                        "<yellow>▶ Esquerdo: +5   ▶ Direito: -5", "<yellow>▶ Shift: +/- 1")), c -> {
            long step = c.isShiftClick() ? 1 : 5;
            long value = minutes + (c.isLeftClick() ? step : -step);
            plugin.getConfig().set("graves.protection-minutes", Math.max(0, Math.min(10_080, value)));
            plugin.saveConfig();
            refresh();
        });

        int percent = plugin.getConfig().getInt("graves.xp-percent", 100);
        set(50, Items.of(Material.EXPERIENCE_BOTTLE, "<white><bold>XP guardado: " + percent + "%",
                List.of("<gray>Parte do XP total que vai para o túmulo.", "", "<yellow>▶ Esquerdo: +10   ▶ Direito: -10",
                        "<yellow>▶ Shift: +/- 1")), c -> {
            int step = c.isShiftClick() ? 1 : 10;
            int value = percent + (c.isLeftClick() ? step : -step);
            plugin.getConfig().set("graves.xp-percent", Math.max(0, Math.min(100, value)));
            plugin.saveConfig();
            refresh();
        });
    }

    /** Opções de um túmulo: interior, tempo de proteção, entrega, teleporte e quebra. */
    static final class GraveDetailMenu extends Menu {

        private final java.util.UUID id;
        private final int backPage;
        private final java.util.UUID filter;
        private boolean confirmDelete;

        GraveDetailMenu(PluginMain plugin, Player player, java.util.UUID id, int backPage) {
            this(plugin, player, id, backPage, null);
        }

        GraveDetailMenu(PluginMain plugin, Player player, java.util.UUID id, int backPage, java.util.UUID filter) {
            super(plugin, player, Ui.Theme.INFO, TITLE);
            this.id = id;
            this.backPage = backPage;
            this.filter = filter;
        }

        private void adjust(Grave grave, long minutes) {
            plugin.graves().addProtection(grave, minutes * 60_000L);
            refresh();
        }

        @Override
        protected void render() {
            frame(() -> new GraveMenu(plugin, player, backPage, filter).open());
            GraveManager graves = plugin.graves();
            Grave grave = graves.byId(id);
            if (grave == null) {
                set(22, Items.of(Material.BARRIER, "<red>Este túmulo não existe mais", List.of()));
                return;
            }
            set(13, Items.of(Material.PLAYER_HEAD, "<white>☠ " + Msg.esc(grave.ownerName()), details(plugin, grave)));

            if (confirmDelete) {
                set(22, Items.of(Material.TNT, "<red><bold>Apagar este túmulo?",
                        List.of("<gray>Os itens e o XP guardados serão", "<gray>destruídos. Não pode ser desfeito.")));
                set(30, Items.of(Material.LIME_CONCRETE, "<green><bold>Sim, apagar", List.of()), c -> {
                    graves.breakGrave(grave, false);
                    Msg.send(player, "<green>Túmulo apagado (itens destruídos).");
                    new GraveMenu(plugin, player, backPage, filter).open();
                });
                set(32, Items.of(Material.RED_CONCRETE, "<red><bold>Não, voltar", List.of()), c -> {
                    confirmDelete = false;
                    refresh();
                });
                return;
            }

            set(20, Items.of(Material.CHEST, "<white><bold>Ver interior",
                    List.of("<gray>Itens e XP guardados; pegue um por um.", "", "<yellow>▶ Clique para abrir")),
                    c -> new GraveContentsMenu(plugin, player, id, backPage, filter).open());
            set(21, Items.of(Material.ENDER_PEARL, "<white><bold>Teleportar",
                    List.of("<gray>Vai até o túmulo.", "", "<yellow>▶ Clique para ir")), c -> {
                player.closeInventory();
                teleport(plugin, player, grave);
            });
            set(23, Items.of(Material.HOPPER, "<white><bold>Pegar tudo",
                    List.of("<gray>Itens e XP vão para você e o", "<gray>túmulo some.", "", "<yellow>▶ Clique para pegar")), c -> {
                graves.giveAll(player, grave);
                Msg.send(player, "<green>Você pegou tudo do túmulo de <white>" + Msg.esc(grave.ownerName()) + "</white>.");
                new GraveMenu(plugin, player, backPage, filter).open();
            });
            Player owner = org.bukkit.Bukkit.getPlayer(grave.owner());
            set(24, Items.of(owner == null ? Material.GRAY_DYE : Material.PLAYER_HEAD, "<white><bold>Entregar ao dono",
                    List.of(owner == null ? "<red>O dono está offline." : "<gray>Envia tudo a <white>" + Msg.esc(owner.getName()) + "</white>.",
                            "", owner == null ? "<dark_gray>Indisponível" : "<yellow>▶ Clique para entregar")), c -> {
                if (owner == null || !owner.isOnline()) {
                    return;
                }
                graves.giveAll(owner, grave);
                Msg.send(owner, "<green>Um administrador devolveu o conteúdo do seu túmulo.");
                Msg.send(player, "<green>Conteúdo entregue a <white>" + Msg.esc(owner.getName()) + "</white>.");
                new GraveMenu(plugin, player, backPage, filter).open();
            });

            long left = graves.protectionLeft(grave);
            set(29, Items.of(Material.RED_STAINED_GLASS_PANE, "<red><bold>- tempo",
                    List.of("<yellow>▶ Esquerdo: -5 min", "<yellow>▶ Direito: -1 min", "<yellow>▶ Shift: -30 min")),
                    c -> adjust(grave, c.isShiftClick() ? -30 : c.isLeftClick() ? -5 : -1));
            set(30, Items.of(Material.BARRIER, "<red><bold>Sem proteção",
                    List.of("<gray>Qualquer um abre agora.", "", "<yellow>▶ Clique para remover")), c -> {
                graves.setProtection(grave, 0);
                refresh();
            });
            set(31, Items.of(Material.CLOCK, "<white><bold>Proteção: " + (left == 0 ? "nenhuma" : GraveManager.formatTime(left)),
                    List.of("<gray>Tempo em que só o dono (ou uma", "<gray>Chave) abre este túmulo.", "",
                            "<yellow>▶ Clique: voltar ao padrão", "<dark_gray>(" + plugin.getConfig().getLong("graves.protection-minutes", 30) + " min desde a criação)")),
                    c -> {
                        graves.resetProtection(grave);
                        refresh();
                    });
            set(32, Items.of(Material.OBSIDIAN, "<light_purple><bold>Permanente",
                    List.of("<gray>Só o dono (ou uma Chave) abre,", "<gray>sem prazo.", "", "<yellow>▶ Clique para proteger")), c -> {
                graves.setPermanent(grave);
                refresh();
            });
            set(33, Items.of(Material.LIME_STAINED_GLASS_PANE, "<green><bold>+ tempo",
                    List.of("<yellow>▶ Esquerdo: +5 min", "<yellow>▶ Direito: +1 min", "<yellow>▶ Shift: +30 min")),
                    c -> adjust(grave, c.isShiftClick() ? 30 : c.isLeftClick() ? 5 : 1));

            set(38, Items.of(Material.IRON_PICKAXE, "<gold><bold>Quebrar",
                    List.of("<gray>Remove o túmulo e solta os itens", "<gray>e o XP no chão.", "", "<yellow>▶ Clique para quebrar")), c -> {
                graves.breakGrave(grave, true);
                Msg.send(player, "<green>Túmulo quebrado: itens soltos no chão.");
                new GraveMenu(plugin, player, backPage, filter).open();
            });
            set(42, Items.of(Material.LAVA_BUCKET, "<red><bold>Apagar",
                    List.of("<gray>Remove o túmulo e destrói o conteúdo.", "", "<yellow>▶ Clique para apagar")), c -> {
                confirmDelete = true;
                refresh();
            });
        }
    }

    /** Interior do túmulo: clique em um item para pegá-lo; também pega o XP ou tudo. */
    static final class GraveContentsMenu extends Menu {

        private static final int ITEM_SLOTS = 45;

        private final java.util.UUID id;
        private final int backPage;

        private final java.util.UUID filter;

        GraveContentsMenu(PluginMain plugin, Player player, java.util.UUID id, int backPage) {
            this(plugin, player, id, backPage, null);
        }

        GraveContentsMenu(PluginMain plugin, Player player, java.util.UUID id, int backPage, java.util.UUID filter) {
            super(plugin, player, Ui.Theme.INFO, "<gradient:#9be7ff:#c4a7ff><bold>Interior do túmulo</bold></gradient>");
            this.id = id;
            this.backPage = backPage;
            this.filter = filter;
        }

        private void leave() {
            Grave grave = plugin.graves().byId(id);
            if (grave == null) {
                new GraveMenu(plugin, player, backPage, filter).open();
            } else {
                new GraveDetailMenu(plugin, player, id, backPage, filter).open();
            }
        }

        private void give(ItemStack item) {
            for (ItemStack leftover : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }

        @Override
        protected void render() {
            GraveManager graves = plugin.graves();
            Grave grave = graves.byId(id);
            for (int slot = 0; slot < SIZE; slot++) {
                inventory.setItem(slot, slot >= ITEM_SLOTS ? Items.filler(Material.BLACK_STAINED_GLASS_PANE) : null);
            }
            set(BACK_SLOT, Items.of(Material.ARROW, "<yellow>◀ Voltar", List.of()), c -> leave());
            set(CLOSE_SLOT, Items.of(Material.BARRIER, "<red>Fechar", List.of()), c -> player.closeInventory());
            if (grave == null) {
                set(22, Items.of(Material.BARRIER, "<gray>Túmulo vazio ou removido", List.of()));
                return;
            }
            List<ItemStack> items = graves.contents(grave);
            for (int i = 0; i < items.size() && i < ITEM_SLOTS; i++) {
                int index = i;
                ItemStack shown = items.get(i).clone();
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                if (shown.lore() != null) {
                    lore.addAll(shown.lore());
                }
                lore.add(Msg.mm(""));
                lore.add(Msg.mm("<yellow>▶ Clique para pegar"));
                shown.lore(lore);
                set(i, shown, c -> {
                    ItemStack taken = graves.takeItem(grave, index);
                    if (taken != null) {
                        give(taken);
                    }
                    if (graves.byId(id) == null) {
                        Msg.send(player, "<gray>O túmulo ficou vazio e foi removido.");
                        new GraveMenu(plugin, player, backPage, filter).open();
                    } else {
                        refresh();
                    }
                });
            }
            if (items.size() > ITEM_SLOTS) {
                set(48, Items.of(Material.PAPER, "<gray>+" + (items.size() - ITEM_SLOTS) + " itens não exibidos",
                        List.of("<dark_gray>Pegue alguns para ver o restante.")));
            }
            if (grave.xp() > 0) {
                set(47, Items.of(Material.EXPERIENCE_BOTTLE, "<green><bold>Pegar o XP",
                        List.of("<gray>XP guardado: <white>" + grave.xp(), "", "<yellow>▶ Clique para pegar")), c -> {
                    player.giveExp(graves.takeXp(grave));
                    if (graves.byId(id) == null) {
                        new GraveMenu(plugin, player, backPage, filter).open();
                    } else {
                        refresh();
                    }
                });
            }
            set(51, Items.of(Material.HOPPER, "<white><bold>Pegar tudo", List.of("<gray>Itens e XP vão para você.", "", "<yellow>▶ Clique para pegar")), c -> {
                graves.giveAll(player, grave);
                Msg.send(player, "<green>Você pegou tudo do túmulo de <white>" + Msg.esc(grave.ownerName()) + "</white>.");
                new GraveMenu(plugin, player, backPage, filter).open();
            });
        }
    }

    /** Todos os túmulos do mapa classificados por jogador. */
    static final class GravePlayersMenu extends Menu {

        private final int page;

        GravePlayersMenu(PluginMain plugin, Player player, int page) {
            super(plugin, player, Ui.Theme.INFO, "<gradient:#9be7ff:#c4a7ff><bold>Túmulos por jogador</bold></gradient>");
            this.page = page;
        }

        @Override
        protected void render() {
            frame(() -> new GraveMenu(plugin, player, 0).open());
            java.util.Map<java.util.UUID, List<Grave>> groups = new java.util.LinkedHashMap<>();
            for (Grave grave : plugin.graves().all()) {
                groups.computeIfAbsent(grave.owner(), k -> new ArrayList<>()).add(grave);
            }
            List<java.util.Map.Entry<java.util.UUID, List<Grave>>> entries = new ArrayList<>(groups.entrySet());
            entries.sort((a, b) -> a.getValue().get(0).ownerName().compareToIgnoreCase(b.getValue().get(0).ownerName()));
            int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
            int current = Math.max(0, Math.min(page, pages - 1));

            set(HEADER_SLOT, Items.of(Material.SKELETON_SKULL, "<gradient:#9be7ff:#c4a7ff><bold>Por jogador",
                    List.of("<gray>Jogadores com túmulos: <white>" + entries.size(), "<gray>Túmulos no mapa: <white>" + plugin.graves().count())));
            if (entries.isEmpty()) {
                set(22, Items.of(Material.BARRIER, "<gray>Nenhum túmulo ativo", List.of()));
            }
            for (int i = 0; i < PER_PAGE && current * PER_PAGE + i < entries.size(); i++) {
                java.util.Map.Entry<java.util.UUID, List<Grave>> entry = entries.get(current * PER_PAGE + i);
                List<Grave> list = entry.getValue();
                int items = 0;
                int xp = 0;
                for (Grave grave : list) {
                    items += plugin.graves().contents(grave).size();
                    xp += grave.xp();
                }
                ItemStack head = Items.of(Material.PLAYER_HEAD, "<white>" + Msg.esc(list.get(0).ownerName()),
                        List.of("<gray>Túmulos: <white>" + list.size(), "<gray>Itens: <white>" + items + " <dark_gray>• <gray>XP: <white>" + xp,
                                "", "<yellow>▶ Clique para ver os túmulos"));
                head.editMeta(org.bukkit.inventory.meta.SkullMeta.class,
                        meta -> meta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(entry.getKey())));
                set(LIST_SLOTS[i], head, c -> new GraveMenu(plugin, player, 0, entry.getKey()).open());
            }
            if (current > 0) {
                set(46, Items.of(Material.ARROW, "<yellow>◀ Página " + current, List.of()), c -> new GravePlayersMenu(plugin, player, current - 1).open());
            }
            if (current < pages - 1) {
                set(52, Items.of(Material.ARROW, "<yellow>Página " + (current + 2) + " ▶", List.of()), c -> new GravePlayersMenu(plugin, player, current + 1).open());
            }
            set(50, Items.of(Material.CHEST, "<white><bold>Ver todos", List.of("<gray>Lista única, sem agrupar.", "", "<yellow>▶ Clique para abrir")),
                    c -> new GraveMenu(plugin, player, 0).open());
        }
    }
}
