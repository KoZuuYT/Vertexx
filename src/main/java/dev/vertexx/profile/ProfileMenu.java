package dev.vertexx.profile;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Items;
import dev.vertexx.core.Menu;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Ui;
import dev.vertexx.profile.InventoryProfiles.Profile;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Perfis de inventário do administrador: lista, guardar o inventário atual num perfil novo (deixando só o Guia
 * do Vertexx) e, por perfil, resgatar, editar os itens, atualizar, renomear, duplicar e apagar.
 * Equivale a {@code /vertexx perfil}.
 */
public final class ProfileMenu extends Menu {

    private static final int PER_PAGE = LIST_SLOTS.length;
    private static final String TITLE = "<gradient:#c4a7ff:#2cb67d><bold>Perfis de inventário</bold></gradient>";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final int page;

    public ProfileMenu(PluginMain plugin, Player player, int page) {
        super(plugin, player, Ui.Theme.INFO, TITLE);
        this.page = page;
    }

    private static String date(long millis) {
        return DATE.format(Instant.ofEpochMilli(millis));
    }

    private static String clip(String name) {
        String clean = name.strip();
        return clean.length() > 40 ? clean.substring(0, 40) : clean;
    }

    @Override
    protected void render() {
        frame(() -> plugin.panel().open(player));
        InventoryProfiles profiles = plugin.profiles();
        List<Profile> all = profiles.list(player.getUniqueId());
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));

        set(HEADER_SLOT, Items.of(Material.BARREL, "<gradient:#c4a7ff:#2cb67d><bold>Perfis de inventário",
                List.of("<gray>Seus perfis: <white>" + all.size(), "<gray>Guarde o inventário e fique só com o Guia.",
                        "<dark_gray>Equivale a /vertexx perfil")));
        if (!profiles.isLoaded()) {
            set(22, Items.of(Material.CLOCK, "<gray>Carregando perfis...", List.of("<dark_gray>Abra de novo em instantes.")));
            return;
        }
        if (all.isEmpty()) {
            set(22, Items.of(Material.BARRIER, "<gray>Nenhum perfil guardado",
                    List.of("<gray>Use <white>Guardar inventário atual</white>.")));
        }
        for (int i = 0; i < PER_PAGE && current * PER_PAGE + i < all.size(); i++) {
            Profile profile = all.get(current * PER_PAGE + i);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Itens: <white>" + profile.count() + " <dark_gray>• <gray>Atualizado: <white>" + date(profile.updated()));
            lore.addAll(InventoryProfiles.summary(profile, 5));
            lore.add("");
            lore.add("<yellow>▶ Clique para gerenciar");
            set(LIST_SLOTS[i], Items.of(Material.CHEST, "<white><bold>" + Msg.esc(profile.name()), lore),
                    c -> new Detail(plugin, player, profile.id(), current).open());
        }
        if (current > 0) {
            set(46, Items.of(Material.ARROW, "<yellow>◀ Página " + current, List.of()), c -> new ProfileMenu(plugin, player, current - 1).open());
        }
        if (current < pages - 1) {
            set(52, Items.of(Material.ARROW, "<yellow>Página " + (current + 2) + " ▶", List.of()), c -> new ProfileMenu(plugin, player, current + 1).open());
        }
        set(50, Items.of(Material.LIME_CONCRETE, "<green><bold>Guardar inventário atual",
                List.of("<gray>Pede um nome no chat, guarda tudo (itens,", "<gray>armadura e mão secundária) e limpa o",
                        "<gray>inventário, deixando só o Guia do Vertexx.", "", "<yellow>▶ Clique para guardar")), c -> {
            if (!profiles.hasContent(player)) {
                Msg.send(player, "<red>Seu inventário já está vazio (só o Guia): nada para guardar.");
                return;
            }
            plugin.namePrompt().ask(player, "Qual o nome do novo perfil?", text -> {
                if (!profiles.hasContent(player)) {
                    Msg.send(player, "<red>Seu inventário ficou vazio: nada foi guardado.");
                    return;
                }
                String name = clip(text);
                profiles.add(player.getUniqueId(), name.isEmpty() ? "Perfil" : name, profiles.snapshot(player));
                profiles.clear(player);
                Msg.send(player, "<green>Inventário guardado no perfil <white>" + Msg.esc(name) + "</white>. "
                        + "Só o Guia do Vertexx ficou com você.");
                new ProfileMenu(plugin, player, page).open();
            });
        });
    }

    /** Ações sobre um perfil. */
    static final class Detail extends Menu {

        private enum Confirm { NONE, RESTORE, UPDATE, DELETE }

        private final UUID id;
        private final int backPage;
        private Confirm confirm = Confirm.NONE;

        Detail(PluginMain plugin, Player player, UUID id, int backPage) {
            super(plugin, player, Ui.Theme.INFO, TITLE);
            this.id = id;
            this.backPage = backPage;
        }

        private void leave() {
            new ProfileMenu(plugin, player, backPage).open();
        }

        private void ask(Confirm what) {
            confirm = what;
            refresh();
        }

        @Override
        protected void render() {
            frame(this::leave);
            InventoryProfiles profiles = plugin.profiles();
            Profile profile = profiles.find(player.getUniqueId(), id);
            if (profile == null) {
                set(22, Items.of(Material.BARRIER, "<red>Este perfil não existe mais", List.of()));
                return;
            }
            String name = Msg.esc(profile.name());
            List<String> info = new ArrayList<>();
            info.add("<gray>Itens: <white>" + profile.count());
            info.add("<gray>Criado: <white>" + date(profile.created()));
            info.add("<gray>Atualizado: <white>" + date(profile.updated()));
            info.addAll(InventoryProfiles.summary(profile, 6));
            set(HEADER_SLOT, Items.of(Material.CHEST, "<white><bold>" + name, info));

            switch (confirm) {
                case RESTORE -> {
                    set(22, Items.of(Material.HOPPER, "<yellow><bold>Seu inventário tem itens",
                            List.of("<gray>Resgatar <white>" + name + "</white> troca o que você", "<gray>carrega agora.")));
                    set(29, Items.of(Material.LIME_CONCRETE, "<green><bold>Trocar",
                            List.of("<gray>Guarda o inventário atual num perfil", "<gray>novo (\"Automático\") e resgata este.", "",
                                    "<yellow>▶ Clique para trocar")), c -> {
                        profiles.add(player.getUniqueId(), "Automático " + date(System.currentTimeMillis()), profiles.snapshot(player));
                        profiles.restore(player, profile);
                        Msg.send(player, "<green>Perfil <white>" + name + "</white> resgatado. O inventário anterior ficou no perfil Automático.");
                        leave();
                    });
                    set(31, Items.of(Material.ORANGE_CONCRETE, "<gold><bold>Substituir",
                            List.of("<gray>Descarta o inventário atual e", "<gray>resgata este.", "", "<yellow>▶ Clique para substituir")), c -> {
                        profiles.restore(player, profile);
                        Msg.send(player, "<green>Perfil <white>" + name + "</white> resgatado (inventário anterior descartado).");
                        leave();
                    });
                    set(33, Items.of(Material.RED_CONCRETE, "<red><bold>Cancelar", List.of()), c -> ask(Confirm.NONE));
                    return;
                }
                case UPDATE -> {
                    set(22, Items.of(Material.ANVIL, "<yellow><bold>Sobrescrever " + name + "?",
                            List.of("<gray>Os itens do perfil serão trocados pelo", "<gray>seu inventário atual, que será limpo",
                                    "<gray>(fica só o Guia). Não pode ser desfeito.")));
                    set(30, Items.of(Material.LIME_CONCRETE, "<green><bold>Sim, atualizar", List.of()), c -> {
                        if (!profiles.hasContent(player)) {
                            Msg.send(player, "<red>Seu inventário está vazio: o perfil não foi alterado.");
                            ask(Confirm.NONE);
                            return;
                        }
                        profiles.replaceItems(profile, profiles.snapshot(player));
                        profiles.clear(player);
                        Msg.send(player, "<green>Perfil <white>" + name + "</white> atualizado com o seu inventário.");
                        ask(Confirm.NONE);
                    });
                    set(32, Items.of(Material.RED_CONCRETE, "<red><bold>Não, voltar", List.of()), c -> ask(Confirm.NONE));
                    return;
                }
                case DELETE -> {
                    set(22, Items.of(Material.TNT, "<red><bold>Apagar " + name + "?",
                            List.of("<gray>Os itens guardados serão destruídos.", "<gray>Não pode ser desfeito.")));
                    set(30, Items.of(Material.LIME_CONCRETE, "<green><bold>Sim, apagar", List.of()), c -> {
                        profiles.remove(player.getUniqueId(), profile);
                        Msg.send(player, "<green>Perfil apagado.");
                        leave();
                    });
                    set(32, Items.of(Material.RED_CONCRETE, "<red><bold>Não, voltar", List.of()), c -> ask(Confirm.NONE));
                    return;
                }
                default -> {
                }
            }

            set(20, Items.of(Material.HOPPER, "<white><bold>Resgatar",
                    List.of("<gray>Coloca os itens guardados no seu", "<gray>inventário (com armadura e mão secundária).",
                            "<gray>O perfil continua guardado.", "", "<yellow>▶ Clique para resgatar")), c -> {
                if (profiles.hasContent(player)) {
                    ask(Confirm.RESTORE);
                } else {
                    profiles.restore(player, profile);
                    Msg.send(player, "<green>Perfil <white>" + name + "</white> resgatado.");
                    refresh();
                }
            });
            set(22, Items.of(Material.CRAFTING_TABLE, "<white><bold>Editar itens",
                    List.of("<gray>Abre os itens do perfil para mover,", "<gray>pegar, colocar ou apagar. Salva sozinho.", "",
                            "<yellow>▶ Clique para editar")), c -> profiles.edit(player, profile, () -> new Detail(plugin, player, id, backPage).open()));
            set(24, Items.of(Material.SMITHING_TABLE, "<white><bold>Atualizar com o inventário atual",
                    List.of("<gray>Sobrescreve este perfil com o que você", "<gray>carrega agora e limpa o inventário.", "",
                            "<yellow>▶ Clique para atualizar")), c -> {
                if (profiles.hasContent(player)) {
                    ask(Confirm.UPDATE);
                } else {
                    Msg.send(player, "<red>Seu inventário está vazio (só o Guia): nada para guardar.");
                }
            });
            set(29, Items.of(Material.NAME_TAG, "<white><bold>Renomear",
                    List.of("<gray>Nome atual: <white>" + name, "", "<yellow>▶ Clique para renomear")),
                    c -> plugin.namePrompt().ask(player, "Qual o novo nome do perfil?", text -> {
                        String clean = clip(text);
                        if (!clean.isEmpty()) {
                            profiles.rename(profile, clean);
                            Msg.send(player, "<green>Perfil renomeado para <white>" + Msg.esc(clean) + "</white>.");
                        }
                        new Detail(plugin, player, id, backPage).open();
                    }));
            set(31, Items.of(Material.PAPER, "<white><bold>Duplicar",
                    List.of("<gray>Cria uma cópia com todos os itens.", "", "<yellow>▶ Clique para duplicar")), c -> {
                Profile copy = profiles.duplicate(player.getUniqueId(), profile);
                Msg.send(player, "<green>Perfil duplicado: <white>" + Msg.esc(copy.name()) + "</white>.");
                leave();
            });
            set(33, Items.of(Material.LAVA_BUCKET, "<red><bold>Apagar",
                    List.of("<gray>Remove o perfil e destrói os itens.", "", "<yellow>▶ Clique para apagar")), c -> ask(Confirm.DELETE));
        }
    }
}
