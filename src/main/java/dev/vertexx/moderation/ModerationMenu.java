package dev.vertexx.moderation;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Items;
import dev.vertexx.core.Menu;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Ui;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Ferramenta de moderação: lista todos os jogadores que já entraram no servidor e, por jogador, mostra mortes,
 * data de entrada, tempo de jogo, abre o inventário (OpenInv, {@code /oi}) e bane ou desbane. Equivale a
 * {@code /moderar}.
 */
public final class ModerationMenu extends Menu {

    private static final int PER_PAGE = LIST_SLOTS.length;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final int page;
    private final int filter; // 0 todos, 1 online, 2 banidos

    public ModerationMenu(PluginMain plugin, Player player, int page) {
        this(plugin, player, page, 0);
    }

    ModerationMenu(PluginMain plugin, Player player, int page, int filter) {
        super(plugin, player, Ui.Theme.INFO, "<gradient:#ff9b9b:#ffd29b><bold>Moderação</bold></gradient>");
        this.page = page;
        this.filter = filter;
    }

    static String date(long millis) {
        return millis <= 0 ? "desconhecida" : DATE.format(Instant.ofEpochMilli(millis));
    }

    static String playTime(OfflinePlayer target) {
        long minutes = target.getStatistic(Statistic.PLAY_ONE_MINUTE) / 1200L;
        return minutes >= 60 ? minutes / 60 + " h " + minutes % 60 + " min" : minutes + " min";
    }

    static String name(OfflinePlayer target) {
        return target.getName() == null ? target.getUniqueId().toString().substring(0, 8) : target.getName();
    }

    private static ItemStack head(OfflinePlayer target, String title, List<String> lore) {
        ItemStack item = Items.of(Material.PLAYER_HEAD, title, lore);
        item.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(target));
        return item;
    }

    @Override
    protected void render() {
        frame(() -> plugin.panel().open(player));
        List<OfflinePlayer> all = new ArrayList<>();
        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            if (filter == 1 && !p.isOnline()) {
                continue;
            }
            if (filter == 2 && !p.isBanned()) {
                continue;
            }
            all.add(p);
        }
        all.sort(Comparator.<OfflinePlayer>comparingInt(p -> p.isOnline() ? 0 : 1)
                .thenComparing(Comparator.<OfflinePlayer>comparingLong(OfflinePlayer::getLastSeen).reversed()));
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));

        set(HEADER_SLOT, Items.of(Material.PLAYER_HEAD, "<gradient:#ff9b9b:#ffd29b><bold>Moderação",
                List.of("<gray>Jogadores que já entraram: <white>" + Bukkit.getOfflinePlayers().length,
                        "<gray>Online: <white>" + Bukkit.getOnlinePlayers().size(), "<dark_gray>Equivale a /moderar")));
        if (all.isEmpty()) {
            set(22, Items.of(Material.BARRIER, "<gray>Nenhum jogador", List.of()));
        }
        for (int i = 0; i < PER_PAGE && current * PER_PAGE + i < all.size(); i++) {
            OfflinePlayer target = all.get(current * PER_PAGE + i);
            List<String> lore = new ArrayList<>();
            lore.add(target.isOnline() ? "<green>Online" : "<gray>Visto por último: <white>" + date(target.getLastSeen()));
            if (target.isBanned()) {
                lore.add("<red>Banido");
            }
            lore.add("<gray>Mortes: <white>" + target.getStatistic(Statistic.DEATHS) + " <dark_gray>• <gray>Jogo: <white>" + playTime(target));
            lore.add("");
            lore.add("<yellow>▶ Clique para moderar");
            set(LIST_SLOTS[i], head(target, "<white>" + Msg.esc(name(target)), lore),
                    c -> new PlayerMenu(plugin, player, target.getUniqueId(), current, filter).open());
        }
        if (current > 0) {
            set(46, Items.of(Material.ARROW, "<yellow>◀ Página " + current, List.of()), c -> new ModerationMenu(plugin, player, current - 1, filter).open());
        }
        if (current < pages - 1) {
            set(52, Items.of(Material.ARROW, "<yellow>Página " + (current + 2) + " ▶", List.of()), c -> new ModerationMenu(plugin, player, current + 1, filter).open());
        }
        String[] names = {"Todos", "Online", "Banidos"};
        Material[] icons = {Material.COMPASS, Material.LIME_DYE, Material.BARRIER};
        set(50, Items.of(icons[filter], "<white><bold>Filtro: " + names[filter],
                List.of("<gray>Alterna entre todos, online e banidos.", "", "<yellow>▶ Clique para alternar")),
                c -> new ModerationMenu(plugin, player, 0, (filter + 1) % 3).open());
    }

    /** Ações sobre um jogador. */
    static final class PlayerMenu extends Menu {

        private final UUID id;
        private final int backPage;
        private final int filter;

        PlayerMenu(PluginMain plugin, Player player, UUID id, int backPage, int filter) {
            super(plugin, player, Ui.Theme.INFO, "<gradient:#ff9b9b:#ffd29b><bold>Moderar jogador</bold></gradient>");
            this.id = id;
            this.backPage = backPage;
            this.filter = filter;
        }

        @Override
        protected void render() {
            frame(() -> new ModerationMenu(plugin, player, backPage, filter).open());
            OfflinePlayer target = Bukkit.getOfflinePlayer(id);
            String name = name(target);
            boolean banned = target.isBanned();

            List<String> info = new ArrayList<>();
            info.add(target.isOnline() ? "<green>Online agora" : "<gray>Visto por último: <white>" + date(target.getLastSeen()));
            info.add(banned ? "<red>Banido" : "<gray>Não está banido");
            set(HEADER_SLOT, head(target, "<white><bold>" + Msg.esc(name), info));

            int deaths = target.getStatistic(Statistic.DEATHS);
            Location last = target.getLastDeathLocation();
            List<String> deathLore = new ArrayList<>(List.of("<gray>Total de mortes: <white>" + deaths,
                    "<gray>Túmulos pendentes: <white>" + plugin.graves().ownedBy(id).size()));
            if (last != null && last.getWorld() != null) {
                deathLore.add("<gray>Última: <white>" + last.getWorld().getName() + " <dark_gray>" + last.getBlockX() + " "
                        + last.getBlockY() + " " + last.getBlockZ());
                deathLore.add("");
                deathLore.add("<yellow>▶ Clique para ir ao local da última morte");
            } else {
                deathLore.add("<dark_gray>Sem local de morte registrado.");
            }
            set(20, Items.of(Material.SKELETON_SKULL, "<white><bold>Mortes", deathLore), c -> {
                if (last == null || last.getWorld() == null) {
                    return;
                }
                player.closeInventory();
                player.teleportAsync(last).thenAccept(ok -> Msg.send(player, ok
                        ? "<green>Teleportado ao local da última morte de <white>" + Msg.esc(name) + "</white>."
                        : "<red>Não foi possível teleportar."));
            });

            Location pos = target.isOnline() && target.getPlayer() != null ? target.getPlayer().getLocation() : target.getLocation();
            List<String> posLore = new ArrayList<>();
            if (pos != null && pos.getWorld() != null) {
                posLore.add("<gray>" + (target.isOnline() ? "Posição atual: " : "Onde saiu: ") + "<white>" + pos.getWorld().getName()
                        + " <dark_gray>" + pos.getBlockX() + " " + pos.getBlockY() + " " + pos.getBlockZ());
                posLore.add("");
                posLore.add("<yellow>▶ Clique para teleportar");
            } else {
                posLore.add("<dark_gray>Sem posição registrada.");
            }
            set(29, Items.of(Material.ENDER_PEARL, "<white><bold>Última posição", posLore), c -> {
                if (pos == null || pos.getWorld() == null) {
                    return;
                }
                player.closeInventory();
                player.teleportAsync(pos).thenAccept(ok -> Msg.send(player, ok
                        ? "<green>Teleportado à última posição de <white>" + Msg.esc(name) + "</white>."
                        : "<red>Não foi possível teleportar."));
            });

            set(22, Items.of(Material.CHEST, "<white><bold>Inventário",
                    List.of("<gray>Abre o inventário e o baú de ender", "<gray>com o OpenInv.", "<dark_gray>Equivale a /oi " + name, "",
                            "<yellow>▶ Clique para abrir")), c -> {
                if (Bukkit.getPluginManager().getPlugin("OpenInv") == null) {
                    Msg.send(player, "<red>O OpenInv não está instalado neste servidor.");
                    return;
                }
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("oi " + name));
            });

            set(24, Items.of(Material.WRITABLE_BOOK, "<white><bold>Datas",
                    List.of("<gray>Primeira entrada: <white>" + date(target.getFirstPlayed()),
                            target.isOnline() ? "<gray>Agora: <green>online" : "<gray>Última vez: <white>" + date(target.getLastSeen()))), null);

            set(31, Items.of(Material.CLOCK, "<white><bold>Tempo de jogo",
                    List.of("<gray>Total jogado: <white>" + playTime(target))), null);

            if (banned) {
                set(40, Items.of(Material.LIME_CONCRETE, "<green><bold>Desbanir",
                        List.of("<gray>Remove o banimento de <white>" + Msg.esc(name) + "<gray>.", "", "<yellow>▶ Clique para desbanir")), c -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pardon " + name);
                    Msg.send(player, "<green>" + Msg.esc(name) + " foi desbanido.");
                    new PlayerMenu(plugin, player, id, backPage, filter).open();
                });
            } else {
                set(40, Items.of(Material.RED_CONCRETE, "<red><bold>Banir",
                        List.of("<gray>Pede o motivo no chat e bane <white>" + Msg.esc(name) + "<gray>.", "", "<yellow>▶ Clique para banir")),
                        c -> plugin.namePrompt().ask(player, "Qual o motivo do banimento de " + name + "?", reason -> {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + name + " " + reason);
                            Msg.send(player, "<red>" + Msg.esc(name) + " foi banido: <white>" + Msg.esc(reason));
                            new PlayerMenu(plugin, player, id, backPage, filter).open();
                        }));
            }
        }
    }
}
