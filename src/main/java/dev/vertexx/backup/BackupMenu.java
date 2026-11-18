package dev.vertexx.backup;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Items;
import dev.vertexx.core.Menu;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Ui;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Lista de backups do servidor. Clique em um para renomear, descrever, copiar, restaurar ou apagar. */
public final class BackupMenu extends Menu {

    private static final int PER_PAGE = LIST_SLOTS.length;
    private static final String TITLE = "<gradient:#c4a7ff:#2cb67d><bold>Backups do servidor</bold></gradient>";

    private final int page;

    public BackupMenu(PluginMain plugin, Player player, int page) {
        super(plugin, player, Ui.Theme.INFO, TITLE);
        this.page = page;
    }

    /** Linhas de descrição quebradas para o hover (a descrição do backup aparece ao passar o mouse). */
    static List<String> wrap(String text, String color) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (line.length() + word.length() + 1 > 38 && line.length() > 0) {
                lines.add(color + Msg.esc(line.toString()));
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(color + Msg.esc(line.toString()));
        }
        return lines;
    }

    static List<String> details(BackupInfo info, boolean pending) {
        List<String> lore = new ArrayList<>();
        if (info.description().isBlank()) {
            lore.add("<dark_gray>Sem descrição.");
        } else {
            lore.addAll(wrap(info.description(), "<gray>"));
        }
        lore.add("");
        lore.add("<gray>Criado: <white>" + info.date());
        lore.add("<gray>Tamanho: <white>" + BackupInfo.human(info.size()) + " <dark_gray>(" + info.files() + " arquivos)");
        lore.add("<gray>Tipo: <white>" + info.typeLabel() + " <dark_gray>por " + Msg.esc(info.author()));
        lore.add("<gray>Minecraft <white>" + info.minecraft() + " <gray>• Vertexx <white>" + info.plugin());
        if (pending) {
            lore.add("");
            lore.add("<red>Restauração agendada: aplica ao reiniciar.");
        }
        return lore;
    }

    static Material material(BackupInfo info) {
        return switch (info.type()) {
            case BackupInfo.AUTO -> Material.CLOCK;
            case BackupInfo.SAFETY -> Material.SHIELD;
            default -> Material.CHEST;
        };
    }

    @Override
    protected void render() {
        frame(() -> plugin.panel().open(player));
        BackupManager backups = plugin.backups();
        List<BackupInfo> all = backups.list();
        String pending = backups.pendingId();
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));

        set(HEADER_SLOT, Items.of(Material.ENDER_CHEST, "<gradient:#c4a7ff:#2cb67d><bold>Backups",
                List.of("<gray>Mundos, jogadores (inventário, XP,", "<gray>conquistas) e dados do Vertexx.",
                        "<gray>Guardados em <white>" + Msg.esc(backups.folder().getFileName().toString()) + "</white>.",
                        "<gray>Total: <white>" + all.size() + " <dark_gray>(" + BackupInfo.human(backups.totalSize()) + ")")));

        if (all.isEmpty()) {
            set(22, Items.of(Material.BARRIER, "<gray>Nenhum backup ainda", List.of("<dark_gray>Use o botão <white>Criar backup</white> abaixo.")));
        }
        for (int i = 0; i < PER_PAGE && current * PER_PAGE + i < all.size(); i++) {
            BackupInfo info = all.get(current * PER_PAGE + i);
            boolean isPending = info.id().equals(pending);
            List<String> lore = details(info, isPending);
            lore.add("");
            lore.add("<yellow>▶ Clique para gerenciar");
            set(LIST_SLOTS[i], Items.of(material(info), "<white>" + Msg.esc(info.name()), lore, isPending),
                    c -> new BackupDetailMenu(plugin, player, info.id(), current).open());
        }

        if (current > 0) {
            set(46, Items.of(Material.ARROW, "<yellow>◀ Página " + current, List.of()), c -> new BackupMenu(plugin, player, current - 1).open());
        }
        if (current < pages - 1) {
            set(52, Items.of(Material.ARROW, "<yellow>Página " + (current + 2) + " ▶", List.of()), c -> new BackupMenu(plugin, player, current + 1).open());
        }

        set(47, Items.of(Material.EMERALD, "<green><bold>Criar backup",
                List.of("<gray>Salva o servidor e cria um backup agora.", "<dark_gray>O jogo continua normal durante a cópia.", "",
                        backups.busy() ? "<red>Já há um backup em andamento." : "<yellow>▶ Clique para criar")), c -> {
            if (backups.busy()) {
                return;
            }
            plugin.namePrompt().ask(player, "Nome do backup.", name -> plugin.namePrompt().ask(player,
                    "Descrição (aparece ao passar o mouse; <white>-</white> para nenhuma).", description -> create(name, description)));
        });

        boolean auto = plugin.getConfig().getBoolean("backup.auto.enabled", false);
        set(48, Items.of(auto ? Material.LIME_DYE : Material.GRAY_DYE, auto ? "<green>Backup automático: ligado" : "<gray>Backup automático: desligado",
                List.of("<gray>A cada <white>" + plugin.getConfig().getInt("backup.auto.interval-minutes", 60) + " min</white>, guardando os <white>"
                        + plugin.getConfig().getInt("backup.auto.keep", 5) + "</white> mais recentes.",
                        "<dark_gray>Intervalo e quantidade: config.yml (backup.auto).", "", "<yellow>▶ Clique para alternar")), c -> {
            plugin.getConfig().set("backup.auto.enabled", !auto);
            plugin.saveConfig();
            refresh();
        });

        if (pending != null) {
            BackupInfo target = backups.find(pending);
            set(50, Items.of(Material.TNT, "<red><bold>Restauração agendada",
                    List.of("<gray>Backup: <white>" + (target == null ? pending : Msg.esc(target.name())),
                            "<gray>Será aplicada quando o servidor reiniciar.", "", "<yellow>▶ Clique para cancelar")), c -> {
                backups.cancelRestore();
                Msg.send(player, "<green>Restauração agendada cancelada.");
                refresh();
            });
        }
    }

    private void create(String name, String description) {
        String text = description.equals("-") ? "" : description;
        boolean started = plugin.backups().create(name, text, BackupInfo.MANUAL, player.getName(),
                info -> {
                    Msg.send(player, "<green>Backup criado: <white>" + Msg.esc(info.name()) + "</white> <dark_gray>(" + BackupInfo.human(info.size()) + ")");
                    if (player.isOnline()) {
                        new BackupMenu(plugin, player, 0).open();
                    }
                },
                error -> Msg.send(player, "<red>Falha ao criar o backup: " + Msg.esc(String.valueOf(error))));
        Msg.send(player, started ? "<gray>Salvando o servidor e criando o backup..." : "<red>Já há um backup em andamento.");
    }

    /** Opções de um backup: renomear, descrever, copiar, restaurar e apagar (com confirmação). */
    static final class BackupDetailMenu extends Menu {

        private enum Mode { VIEW, RESTORE, DELETE }

        private final String id;
        private final int backPage;
        private Mode mode = Mode.VIEW;

        BackupDetailMenu(PluginMain plugin, Player player, String id, int backPage) {
            super(plugin, player, Ui.Theme.INFO, TITLE);
            this.id = id;
            this.backPage = backPage;
        }

        @Override
        protected void render() {
            frame(() -> new BackupMenu(plugin, player, backPage).open());
            BackupManager backups = plugin.backups();
            BackupInfo info = backups.find(id);
            if (info == null) {
                set(22, Items.of(Material.BARRIER, "<red>Este backup não existe mais", List.of()));
                return;
            }
            boolean pending = id.equals(backups.pendingId());
            set(13, Items.of(material(info), "<white>" + Msg.esc(info.name()), details(info, pending), pending));

            if (mode == Mode.RESTORE) {
                set(22, Items.of(Material.TNT, "<red><bold>Restaurar este backup?",
                        List.of("<gray>Substitui mundos, jogadores e dados do", "<gray>Vertexx pelo estado de <white>" + info.date() + "</white>.",
                                "<gray>O estado atual é guardado antes num", "<gray>backup de segurança.", "",
                                "<gray>A troca acontece quando o servidor reiniciar.")));
                set(30, Items.of(Material.LIME_CONCRETE, "<green><bold>Sim, agendar restauração",
                        List.of("<yellow>▶ Clique: agenda (reinicie depois)", "<yellow>▶ Shift + clique: agenda e desliga o servidor")), c -> {
                    boolean stop = c.isShiftClick();
                    boolean started = backups.scheduleRestore(info, player.getName(), safety -> {
                        Msg.send(player, "<green>Restauração de <white>" + Msg.esc(info.name()) + "</white> agendada."
                                + (safety == null ? "" : " <gray>Estado atual guardado em <white>" + Msg.esc(safety) + "</white>."));
                        if (stop) {
                            Msg.send(player, "<yellow>Desligando o servidor para aplicar...");
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                for (Player online : Bukkit.getOnlinePlayers()) {
                                    online.kick(Msg.mm("<yellow>O servidor está sendo restaurado a partir de um backup.\n<gray>Volte em instantes."));
                                }
                                Bukkit.shutdown();
                            }, 40L);
                        } else {
                            Msg.send(player, "<yellow>Reinicie o servidor para aplicar. <gray>Cancele no menu de backups.");
                        }
                    }, error -> Msg.send(player, "<red>Falha ao agendar: " + Msg.esc(String.valueOf(error))));
                    Msg.send(player, started ? "<gray>Guardando o estado atual antes de agendar..." : "<red>Já há um backup em andamento.");
                    player.closeInventory();
                });
                set(32, Items.of(Material.RED_CONCRETE, "<red><bold>Não, voltar", List.of()), c -> {
                    mode = Mode.VIEW;
                    refresh();
                });
                return;
            }
            if (mode == Mode.DELETE) {
                set(22, Items.of(Material.TNT, "<red><bold>Apagar este backup?", List.of("<gray>Isto não pode ser desfeito.")));
                set(30, Items.of(Material.LIME_CONCRETE, "<green><bold>Sim, apagar", List.of()), c -> {
                    if (backups.delete(info)) {
                        Msg.send(player, "<green>Backup apagado.");
                        new BackupMenu(plugin, player, backPage).open();
                    } else {
                        Msg.send(player, "<red>Não foi possível apagar (é o alvo de uma restauração agendada?).");
                        mode = Mode.VIEW;
                        refresh();
                    }
                });
                set(32, Items.of(Material.RED_CONCRETE, "<red><bold>Não, voltar", List.of()), c -> {
                    mode = Mode.VIEW;
                    refresh();
                });
                return;
            }

            set(29, Items.of(Material.NAME_TAG, "<white><bold>Renomear", List.of("<gray>Muda o nome mostrado na lista.", "", "<yellow>▶ Clique para renomear")),
                    c -> plugin.namePrompt().ask(player, "Novo nome do backup.", name -> {
                        backups.rename(info, name);
                        Msg.send(player, "<green>Renomeado.");
                        new BackupDetailMenu(plugin, player, id, backPage).open();
                    }));
            set(31, Items.of(Material.WRITABLE_BOOK, "<white><bold>Descrição",
                    List.of("<gray>Texto que aparece ao passar o mouse", "<gray>sobre o backup na lista.", "", "<yellow>▶ Clique para editar")),
                    c -> plugin.namePrompt().ask(player, "Nova descrição (<white>-</white> para remover).", text -> {
                        backups.describe(info, text.equals("-") ? "" : text);
                        Msg.send(player, "<green>Descrição atualizada.");
                        new BackupDetailMenu(plugin, player, id, backPage).open();
                    }));
            set(33, Items.of(Material.PAPER, "<white><bold>Copiar",
                    List.of("<gray>Duplica este backup com outro nome.", "", backups.busy() ? "<red>Aguarde o backup em andamento." : "<yellow>▶ Clique para copiar")),
                    c -> {
                        if (backups.busy()) {
                            return;
                        }
                        plugin.namePrompt().ask(player, "Nome da cópia (<white>-</white> para o padrão).", name -> {
                            boolean started = backups.copy(info, name.equals("-") ? null : name, player.getName(), copy -> {
                                Msg.send(player, "<green>Cópia criada: <white>" + Msg.esc(copy.name()));
                                if (player.isOnline()) {
                                    new BackupMenu(plugin, player, 0).open();
                                }
                            }, error -> Msg.send(player, "<red>Falha ao copiar: " + Msg.esc(String.valueOf(error))));
                            Msg.send(player, started ? "<gray>Copiando..." : "<red>Já há um backup em andamento.");
                        });
                    });
            set(38, Items.of(Material.RESPAWN_ANCHOR, "<gold><bold>Restaurar",
                    List.of("<gray>Volta o servidor a este estado.", "<gray>Aplica ao reiniciar o servidor.", "", "<yellow>▶ Clique para restaurar")),
                    c -> {
                        mode = Mode.RESTORE;
                        refresh();
                    });
            set(42, Items.of(Material.LAVA_BUCKET, "<red><bold>Apagar", List.of("<gray>Remove o backup para sempre.", "", "<yellow>▶ Clique para apagar")),
                    c -> {
                        mode = Mode.DELETE;
                        refresh();
                    });
        }
    }
}
