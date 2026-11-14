package dev.vertexx;

import dev.vertexx.kingdom.KingdomCommand;
import dev.vertexx.kingdom.KingdomListener;
import dev.vertexx.kingdom.KingdomManager;
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
    private volatile boolean ready;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        kingdoms = new KingdomManager(this);
        onboarding = new KingdomListener(this);
        waystones = new WaystoneManager(this);

        Bukkit.getPluginManager().registerEvents(onboarding, this);
        Bukkit.getPluginManager().registerEvents(new WaystoneListener(this), this);
        register("reino", new KingdomCommand(this));

        loadAsync();
    }

    /** Lê os arquivos fora da thread principal e aplica o resultado de volta nela. */
    private void loadAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            YamlConfiguration kingdomData = kingdoms.read();
            YamlConfiguration waystoneData = waystones.read();
            Bukkit.getScheduler().runTask(this, () -> {
                kingdoms.apply(kingdomData);
                waystones.apply(waystoneData);
                waystones.startAmbient();
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

    // ── Ganchos entre módulos ───────────────────────────────────

    /** Jogador entrou já com reino, ou acabou de escolher um. */
    public void afterOnboarding(Player player) {
    }

    /** Linhas extras exibidas em {@code /reino}. */
    public void kingdomInfoExtras(Player player) {
    }

    /** Multiplicador do custo de viagem por Waystone (0 = gratuito). */
    public double travelCostMultiplier(Player player) {
        return 1.0;
    }

    /** Jogadores em combate não podem viajar. */
    public boolean inCombat(Player player) {
        return false;
    }

    /** O líder {@code leader} rompeu uma aliança do próprio reino. */
    public void onAllianceBroken(Player leader) {
    }
}
