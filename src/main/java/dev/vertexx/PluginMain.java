package dev.vertexx;

import dev.vertexx.core.Msg;
import dev.vertexx.grave.GraveCommand;
import dev.vertexx.grave.GraveListener;
import dev.vertexx.grave.GraveManager;
import dev.vertexx.honor.CombatListener;
import dev.vertexx.honor.HonorCommand;
import dev.vertexx.honor.HonorManager;
import dev.vertexx.kingdom.KingdomCommand;
import dev.vertexx.kingdom.KingdomListener;
import dev.vertexx.kingdom.KingdomManager;
import dev.vertexx.pack.ResourcePackManager;
import dev.vertexx.waystone.WaystoneListener;
import dev.vertexx.waystone.WaystoneManager;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Ponto de entrada do Vertexx: registra eventos e comandos e carrega os dados de forma assíncrona.
 * Enquanto os dados não terminam de carregar, novos logins são recusados ({@link #isReady()}).
 */
public final class PluginMain extends JavaPlugin {

    private KingdomManager kingdoms;
    private KingdomListener onboarding;
    private WaystoneManager waystones;
    private GraveManager graves;
    private HonorManager honor;
    private CombatListener combat;
    private ResourcePackManager resourcePack;
    private volatile boolean ready;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        kingdoms = new KingdomManager(this);
        onboarding = new KingdomListener(this);
        waystones = new WaystoneManager(this);
        graves = new GraveManager(this);
        honor = new HonorManager(this);
        combat = new CombatListener(this);
        resourcePack = new ResourcePackManager(this);

        var plugins = Bukkit.getPluginManager();
        plugins.registerEvents(onboarding, this);
        plugins.registerEvents(new WaystoneListener(this), this);
        plugins.registerEvents(new GraveListener(this), this);
        plugins.registerEvents(combat, this);
        plugins.registerEvents(resourcePack, this);

        register("reino", new KingdomCommand(this));
        register("tumulo", new GraveCommand(this));
        register("honra", new HonorCommand(this));
        register("pacote", resourcePack);

        loadAsync();
    }

    /** Lê os arquivos fora da thread principal e aplica o resultado de volta nela. */
    private void loadAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            YamlConfiguration kingdomData = kingdoms.read();
            YamlConfiguration waystoneData = waystones.read();
            YamlConfiguration graveData = graves.read();
            YamlConfiguration honorData = honor.read();
            YamlConfiguration packData = resourcePack.read();
            Bukkit.getScheduler().runTask(this, () -> {
                kingdoms.apply(kingdomData);
                waystones.apply(waystoneData);
                graves.apply(graveData);
                honor.apply(honorData);
                resourcePack.apply(packData);
                waystones.startAmbient();
                honor.startTasks();
                ready = true;
                getLogger().info("Dados carregados. Vertexx " + getPluginMeta().getVersion() + " pronto.");
                // cobre /reload e jogadores que já estavam online
                Bukkit.getOnlinePlayers().forEach(onboarding::handleJoin);
            });
        });
    }

    @Override
    public void onDisable() {
        if (ready) {
            kingdoms.saveNow();
            waystones.saveNow();
            graves.saveNow();
            honor.saveNow();
            resourcePack.saveNow();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "comando ausente no plugin.yml: " + name);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    // ── Acesso aos módulos ──────────────────────────────────────

    public boolean isReady() {
        return ready;
    }

    public KingdomManager kingdoms() {
        return kingdoms;
    }

    public KingdomListener onboarding() {
        return onboarding;
    }

    public WaystoneManager waystones() {
        return waystones;
    }

    public GraveManager graves() {
        return graves;
    }

    public HonorManager honor() {
        return honor;
    }

    // ── Ganchos entre módulos ───────────────────────────────────

    /** Jogador entrou já com reino, ou acabou de escolher um. */
    public void afterOnboarding(Player player) {
        resourcePack.greet(player);
    }

    /** Linhas extras exibidas em {@code /reino}. */
    public void kingdomInfoExtras(Player player) {
        int value = honor.get(player.getUniqueId());
        player.sendMessage(Msg.mm(" <gray>Honra: " + HonorCommand.bar(value) + " <white>" + value
                + " <dark_gray>(" + HonorManager.Tier.of(value).styled() + "<dark_gray>)"));
    }

    /** Multiplicador do custo de viagem por Waystone (0 = gratuito), definido pela honra. */
    public double travelCostMultiplier(Player player) {
        return honor.tier(player.getUniqueId()).travelMultiplier();
    }

    /** Jogadores em combate não podem viajar. */
    public boolean inCombat(Player player) {
        return combat.inCombat(player);
    }

    /** O líder {@code leader} rompeu uma aliança do próprio reino. */
    public void onAllianceBroken(Player leader) {
        honor.add(leader, getConfig().getInt("honor.alliance-break", -20), "aliança rompida");
    }
}
