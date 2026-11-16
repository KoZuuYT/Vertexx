package dev.vertexx;

import dev.vertexx.boss.BossManager;
import dev.vertexx.fauna.FaunaManager;
import dev.vertexx.core.MenuListener;
import dev.vertexx.grave.GraveCommand;
import dev.vertexx.grave.GraveListener;
import dev.vertexx.grave.GraveManager;
import dev.vertexx.mob.MobManager;
import dev.vertexx.pack.ResourcePackManager;
import dev.vertexx.panel.PanelCommand;
import dev.vertexx.panel.PanelManager;
import dev.vertexx.rpg.ActivityListener;
import dev.vertexx.rpg.RpgManager;
import dev.vertexx.rpg.SkillManager;
import dev.vertexx.rpg.StatsCommand;
import dev.vertexx.update.AutoUpdater;
import dev.vertexx.upgrade.UpgradeManager;
import dev.vertexx.waystone.ModelManager;
import dev.vertexx.waystone.WaystoneListener;
import dev.vertexx.waystone.WaystoneManager;
import java.io.File;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Ponto de entrada do Vertexx: registra eventos e comandos e carrega os dados de forma assíncrona.
 * Enquanto os dados não terminam de carregar, interações dos módulos aguardam ({@link #isReady()}).
 */
public final class PluginMain extends JavaPlugin {

    private WaystoneManager waystones;
    private GraveManager graves;
    private ResourcePackManager resourcePack;
    private ModelManager models;
    private PanelManager panel;
    private MobManager mobs;
    private BossManager bosses;
    private FaunaManager fauna;
    private dev.vertexx.model.Models entityModels;
    private RpgManager rpg;
    private SkillManager skills;
    private UpgradeManager upgrades;
    private dev.vertexx.end.Backpacks backpacks;
    private dev.vertexx.end.EndGear endGear;
    private dev.vertexx.end.EndWorld endWorld;
    private dev.vertexx.overworld.Curses curses;
    private dev.vertexx.overworld.Climates climates;
    private AutoUpdater updater;
    private volatile boolean ready;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        waystones = new WaystoneManager(this);
        graves = new GraveManager(this);
        resourcePack = new ResourcePackManager(this);
        models = new ModelManager(this);
        panel = new PanelManager(this);
        entityModels = new dev.vertexx.model.Models(this);
        mobs = new MobManager(this);
        bosses = new BossManager(this);
        fauna = new FaunaManager(this);
        rpg = new RpgManager(this);
        skills = new SkillManager(this);
        upgrades = new UpgradeManager(this);
        backpacks = new dev.vertexx.end.Backpacks(this);
        endGear = new dev.vertexx.end.EndGear(this);
        endWorld = new dev.vertexx.end.EndWorld(this);
        curses = new dev.vertexx.overworld.Curses(this);
        climates = new dev.vertexx.overworld.Climates(this);

        var plugins = Bukkit.getPluginManager();
        plugins.registerEvents(new MenuListener(), this);
        plugins.registerEvents(new WaystoneListener(this), this);
        plugins.registerEvents(new GraveListener(this), this);
        plugins.registerEvents(resourcePack, this);
        plugins.registerEvents(models, this);
        plugins.registerEvents(panel, this);
        plugins.registerEvents(mobs, this);
        plugins.registerEvents(bosses, this);
        plugins.registerEvents(fauna, this);
        plugins.registerEvents(rpg, this);
        plugins.registerEvents(skills, this);
        plugins.registerEvents(upgrades, this);
        plugins.registerEvents(backpacks, this);
        plugins.registerEvents(endGear, this);
        plugins.registerEvents(endWorld, this);
        plugins.registerEvents(curses, this);
        plugins.registerEvents(climates, this);
        plugins.registerEvents(new ActivityListener(this), this);

        register("tumulo", new GraveCommand(this));
        register("pacote", resourcePack);
        register("vstats", new StatsCommand(this));
        register("mochila", backpacks);
        Objects.requireNonNull(getCommand("overworldreload")).setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("vertexx.admin")) {
                sender.sendMessage("Sem permissão.");
                return true;
            }
            reloadConfig();
            sender.sendMessage("Vertexx: configuração recarregada.");
            return true;
        });

        updater = new AutoUpdater(this);
        plugins.registerEvents(updater, this);
        register("vertexx", new PanelCommand(this));
        updater.start();
        resourcePack.startResolver();
        mobs.start();
        bosses.start();
        fauna.start();
        rpg.start();
        backpacks.start();
        endGear.start();
        endWorld.start();
        curses.start();
        climates.start();

        loadAsync();
    }

    /** Lê os arquivos fora da thread principal e aplica o resultado de volta nela. */
    private void loadAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            YamlConfiguration waystoneData = waystones.read();
            YamlConfiguration graveData = graves.read();
            YamlConfiguration packData = resourcePack.read();
            YamlConfiguration panelData = panel.read();
            YamlConfiguration rpgData = rpg.read();
            YamlConfiguration bossData = bosses.read();
            Bukkit.getScheduler().runTask(this, () -> {
                waystones.apply(waystoneData);
                graves.apply(graveData);
                resourcePack.apply(packData);
                panel.apply(panelData);
                rpg.apply(rpgData);
                bosses.apply(bossData);
                models.load(waystones.all());
                models.start();
                waystones.startAmbient();
                rpg.refreshOnline();
                ready = true;
                getLogger().info("Dados carregados. Vertexx " + getPluginMeta().getVersion() + " pronto.");
                // cobre /reload e jogadores que já estavam online
                Bukkit.getOnlinePlayers().forEach(resourcePack::greet);
            });
        });
    }

    @Override
    public void onDisable() {
        if (ready) {
            waystones.saveNow();
            graves.saveNow();
            resourcePack.saveNow();
            panel.saveNow();
            rpg.saveNow();
            bosses.saveNow();
        }
        models.shutdown();
        mobs.shutdown();
        bosses.shutdown();
        fauna.shutdown();
        backpacks.shutdown();
        curses.shutdown();
        climates.shutdown();
        rpg.shutdown();
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

    public WaystoneManager waystones() {
        return waystones;
    }

    public ResourcePackManager resourcePack() {
        return resourcePack;
    }

    public ModelManager models() {
        return models;
    }

    public PanelManager panel() {
        return panel;
    }

    public RpgManager rpg() {
        return rpg;
    }

    public UpgradeManager upgrades() {
        return upgrades;
    }

    public SkillManager skills() {
        return skills;
    }

    public MobManager mobs() {
        return mobs;
    }

    public dev.vertexx.end.Backpacks backpacks() {
        return backpacks;
    }

    public dev.vertexx.end.EndWorld endWorld() {
        return endWorld;
    }

    public dev.vertexx.overworld.Curses curses() {
        return curses;
    }

    public dev.vertexx.overworld.Climates climates() {
        return climates;
    }

    public dev.vertexx.end.EndGear endGear() {
        return endGear;
    }

    public FaunaManager fauna() {
        return fauna;
    }

    /** Modelos de entidade (bbmodel compilado) usados por bosses e criaturas. */
    public dev.vertexx.model.Models entityModels() {
        return entityModels;
    }

    public BossManager bosses() {
        return bosses;
    }

    public AutoUpdater updater() {
        return updater;
    }

    public GraveManager graves() {
        return graves;
    }

    /** Arquivo .jar do plugin em execução (usado pelo atualizador). */
    public File jarFile() {
        return getFile();
    }
}
