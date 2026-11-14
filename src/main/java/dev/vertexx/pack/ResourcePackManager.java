package dev.vertexx.pack;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import dev.vertexx.core.YamlStore;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * Resource pack opcional. O jogador é convidado no chat a carregar o pacote e a escolha fica
 * salva em {@code resourcepack.yml}; nada é forçado, quem recusar joga 100% vanilla.
 * Fica inativo enquanto {@code resource-pack.url} estiver vazio.
 */
public final class ResourcePackManager implements Listener, TabExecutor {

    private final PluginMain plugin;
    private final YamlStore store;
    /** {@code true} = aceitou, {@code false} = recusou, ausente = ainda não respondeu. */
    private final Map<UUID, Boolean> choices = new HashMap<>();

    public ResourcePackManager(PluginMain plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "resourcepack.yml");
    }

    // ── Persistência ────────────────────────────────────────────

    /** Leitura do disco: chame fora da thread principal. */
    public YamlConfiguration read() {
        return store.load();
    }

    /** Aplica os dados lidos: chame na thread principal. */
    public void apply(YamlConfiguration data) {
        choices.clear();
        ConfigurationSection section = data.getConfigurationSection("choices");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                choices.put(UUID.fromString(key), section.getBoolean(key));
            } catch (IllegalArgumentException ignored) {
                // entrada inválida: ignora
            }
        }
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, Boolean> entry : choices.entrySet()) {
            data.set("choices." + entry.getKey(), entry.getValue());
        }
        return data;
    }

    public void saveNow() {
        store.saveNow(snapshot());
    }

    // ── Fluxo ───────────────────────────────────────────────────

    private String url() {
        return plugin.getConfig().getString("resource-pack.url", "").strip();
    }

    public boolean isConfigured() {
        return !url().isEmpty();
    }

    private UUID packId() {
        return UUID.nameUUIDFromBytes(("vertexx:" + url()).getBytes(StandardCharsets.UTF_8));
    }

    /** Chamado quando o jogador entra (já com reino) ou conclui o onboarding. */
    public void greet(Player player) {
        if (!isConfigured()) {
            return;
        }
        Boolean choice = choices.get(player.getUniqueId());
        if (choice == null) {
            prompt(player);
        } else if (choice) {
            apply(player);
        }
    }

    private void prompt(Player player) {
        player.sendMessage(Msg.mm(""));
        player.sendMessage(Msg.mm(" <gradient:#7f5af0:#2cb67d><bold>Pacote visual opcional</bold></gradient>"));
        player.sendMessage(Msg.mm(" <gray>O servidor tem um pacote de recursos leve com ícones"));
        player.sendMessage(Msg.mm(" <gray>e texturas próprias. Ele é <white>opcional</white>: sem ele, tudo"));
        player.sendMessage(Msg.mm(" <gray>continua funcionando com o visual vanilla."));
        player.sendMessage(Msg.mm(""));
        player.sendMessage(Msg.mm("   <click:run_command:'/pacote aceitar'><hover:show_text:'<green>Carregar o pacote agora'>"
                + "<green><bold>[ ✔ Carregar ]</bold></hover></click>   "
                + "<click:run_command:'/pacote recusar'><hover:show_text:'<gray>Continuar com o visual vanilla'>"
                + "<gray>[ ✖ Agora não ]</hover></click>"));
        player.sendMessage(Msg.mm(" <dark_gray>Você pode mudar de ideia com /pacote."));
        player.sendMessage(Msg.mm(""));
        Fx.sound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.5f);
    }

    private void apply(Player player) {
        String hash = plugin.getConfig().getString("resource-pack.sha1", "").strip().toLowerCase(Locale.ROOT);
        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(packId(), URI.create(url()), hash);
            player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .required(false)
                    .replace(false)
                    .prompt(Msg.mm("<gray>Pacote visual opcional do servidor."))
                    .build());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("resource-pack.url inválida no config.yml: " + e.getMessage());
        }
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!isConfigured() || !packId().equals(event.getID())) {
            return;
        }
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                Msg.actionBar(player, "<green>✔ Pacote visual carregado");
                Fx.sound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.2f);
            }
            case DECLINED -> Msg.send(player, "<gray>Pacote recusado no cliente. Sem problemas: use <white>/pacote aceitar</white> se mudar de ideia.");
            case FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL -> {
                Msg.send(player, "<red>Não foi possível carregar o pacote visual. <gray>O jogo segue normalmente sem ele.");
                Fx.deny(player);
            }
            default -> {
            }
        }
    }

    // ── /pacote ─────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "<red>Apenas jogadores podem usar este comando.");
            return true;
        }
        if (!isConfigured()) {
            Msg.send(player, "<gray>O servidor ainda não tem um pacote visual configurado.");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "aceitar" -> {
                choices.put(player.getUniqueId(), true);
                store.saveAsync(snapshot());
                Fx.click(player);
                Msg.send(player, "<green>Carregando o pacote visual…");
                apply(player);
            }
            case "recusar" -> {
                choices.put(player.getUniqueId(), false);
                store.saveAsync(snapshot());
                player.removeResourcePacks(packId());
                Fx.click(player);
                Msg.send(player, "<gray>Tudo certo, você segue com o visual vanilla.");
            }
            default -> prompt(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? List.of("aceitar", "recusar") : List.of();
    }
}
