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
    private dev.vertexx.waystone.NamePrompt namePrompt;
    private dev.vertexx.waystone.WarpStone warpStone;
    private dev.vertexx.core.RecipeBook recipeBook;
    private GraveManager graves;
    private ResourcePackManager resourcePack;
    private ModelManager models;
    private PanelManager panel;
    private dev.vertexx.core.Bedrock bedrock;
    private MobManager mobs;
    private BossManager bosses;
    private FaunaManager fauna;
    private dev.vertexx.blueprint.Blueprints blueprints;
    private dev.vertexx.model.Models entityModels;
    private RpgManager rpg;
    private SkillManager skills;
    private UpgradeManager upgrades;
    private dev.vertexx.end.Backpacks backpacks;
    private dev.vertexx.end.EndGear endGear;
    private dev.vertexx.end.ScarabGear scarabGear;
    private dev.vertexx.end.GloomGear gloomGear;
    private dev.vertexx.end.EndWorld endWorld;
    private dev.vertexx.end.PortalLock portalLock;
    private dev.vertexx.block.CustomBlocks customBlocks;
    private dev.vertexx.overworld.Curses curses;
    private dev.vertexx.overworld.Climates climates;
    private dev.vertexx.overworld.Arid arid;
    private dev.vertexx.overworld.Archaeology archaeology;
    private dev.vertexx.decor.ClockManager clocks;
    private dev.vertexx.decor.HookManager hooks;
    private dev.vertexx.backup.BackupManager backups;
    private dev.vertexx.profile.InventoryProfiles profiles;
    private dev.vertexx.intro.IntroManager intro;
    private AutoUpdater updater;
    private volatile boolean ready;

    @Override
    public void onLoad() {
        // antes de qualquer mundo existir: aplica uma restauração de backup que o desligamento não conseguiu fazer
        dev.vertexx.backup.BackupManager.applyPendingAtLoad(this);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        waystones = new WaystoneManager(this);
        namePrompt = new dev.vertexx.waystone.NamePrompt(this);
        warpStone = new dev.vertexx.waystone.WarpStone(this);
        recipeBook = new dev.vertexx.core.RecipeBook(this);
        graves = new GraveManager(this);
        resourcePack = new ResourcePackManager(this);
        models = new ModelManager(this);
        panel = new PanelManager(this);
        bedrock = new dev.vertexx.core.Bedrock(this);
        entityModels = new dev.vertexx.model.Models(this);
        mobs = new MobManager(this);
        bosses = new BossManager(this);
        fauna = new FaunaManager(this);
        blueprints = new dev.vertexx.blueprint.Blueprints(this);
        rpg = new RpgManager(this);
        skills = new SkillManager(this);
        upgrades = new UpgradeManager(this);
        backpacks = new dev.vertexx.end.Backpacks(this);
        endGear = new dev.vertexx.end.EndGear(this);
        scarabGear = new dev.vertexx.end.ScarabGear(this);
        gloomGear = new dev.vertexx.end.GloomGear(this);
        endWorld = new dev.vertexx.end.EndWorld(this);
        portalLock = new dev.vertexx.end.PortalLock(this);
        customBlocks = new dev.vertexx.block.CustomBlocks(this);
        curses = new dev.vertexx.overworld.Curses(this);
        climates = new dev.vertexx.overworld.Climates(this);
        arid = new dev.vertexx.overworld.Arid(this);
        archaeology = new dev.vertexx.overworld.Archaeology(this);
        clocks = new dev.vertexx.decor.ClockManager(this);
        hooks = new dev.vertexx.decor.HookManager(this);
        backups = new dev.vertexx.backup.BackupManager(this);
        profiles = new dev.vertexx.profile.InventoryProfiles(this);
        intro = new dev.vertexx.intro.IntroManager(this);

        var plugins = Bukkit.getPluginManager();
        plugins.registerEvents(new MenuListener(), this);
        plugins.registerEvents(new WaystoneListener(this), this);
        plugins.registerEvents(namePrompt, this);
        plugins.registerEvents(bedrock, this);
        bedrock.start();
        plugins.registerEvents(warpStone, this);
        plugins.registerEvents(recipeBook, this);
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
        plugins.registerEvents(scarabGear, this);
        plugins.registerEvents(gloomGear, this);
        plugins.registerEvents(new dev.vertexx.boss.ParchedGreatsword(this), this);
        plugins.registerEvents(new dev.vertexx.boss.SovereignStaff(this), this);
        plugins.registerEvents(endWorld, this);
        plugins.registerEvents(portalLock, this);
        plugins.registerEvents(customBlocks, this);
        plugins.registerEvents(curses, this);
        plugins.registerEvents(climates, this);
        plugins.registerEvents(arid, this);
        plugins.registerEvents(archaeology, this);
        plugins.registerEvents(clocks, this);
        plugins.registerEvents(hooks, this);
        plugins.registerEvents(backups, this);
        plugins.registerEvents(profiles, this);
        plugins.registerEvents(intro, this);
        plugins.registerEvents(new ActivityListener(this), this);

        register("tumulo", new GraveCommand(this));
        Objects.requireNonNull(getCommand("moderar")).setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("vertexx.admin")) {
                sender.sendMessage("Sem permissão.");
            } else if (sender instanceof org.bukkit.entity.Player p) {
                new dev.vertexx.moderation.ModerationMenu(this, p, 0).open();
            } else {
                sender.sendMessage("Apenas jogadores.");
            }
            return true;
        });
        register("pacote", resourcePack);
        register("vstats", new StatsCommand(this));
        register("mochila", backpacks);
        register("vxp", new dev.vertexx.rpg.XpCommand(this));
        register("vbackup", new dev.vertexx.backup.BackupCommand(this));
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
        blueprints.start();
        rpg.start();
        backpacks.start();
        endGear.start();
        scarabGear.start();
        gloomGear.start();
        endWorld.start();
        customBlocks.start();
        curses.start();
        climates.start();
        arid.start();
        archaeology.start();
        clocks.start();
        hooks.start();
        backups.start();
        profiles.start();
        warpStone.start();
        recipeBook.start();

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
            YamlConfiguration introData = intro.read();
            Bukkit.getScheduler().runTask(this, () -> {
                waystones.apply(waystoneData);
                graves.apply(graveData);
                resourcePack.apply(packData);
                panel.apply(panelData);
                rpg.apply(rpgData);
                bosses.apply(bossData);
                intro.apply(introData);
                models.load(waystones.all());
                models.start();
                waystones.ensureAllUpper();
                waystones.startAmbient();
                rpg.refreshOnline();
                ready = true;
                getLogger().info("Dados carregados. Vertexx " + getPluginMeta().getVersion() + " pronto.");
                // cobre /reload e jogadores que já estavam online
                Bukkit.getOnlinePlayers().forEach(resourcePack::greet);
            });
        });
    }

    /** Grava agora os dados do plugin (usado no desligamento e antes de cada backup). */
    public void saveData() {
        profiles.saveNow();
        if (ready) {
            waystones.saveNow();
            graves.saveNow();
            resourcePack.saveNow();
            panel.saveNow();
            rpg.saveNow();
            bosses.saveNow();
            intro.saveNow();
        }
    }

    @Override
    public void onDisable() {
        saveData();
        backups.shutdown();
        bedrock.shutdown();
        models.shutdown();
        mobs.shutdown();
        bosses.shutdown();
        fauna.shutdown();
        blueprints.shutdown();
        backpacks.shutdown();
        curses.shutdown();
        climates.shutdown();
        arid.shutdown();
        archaeology.shutdown();
        clocks.shutdown();
        hooks.shutdown();
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

    public dev.vertexx.waystone.NamePrompt namePrompt() {
        return namePrompt;
    }

    public dev.vertexx.waystone.WarpStone warpStone() {
        return warpStone;
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

    public dev.vertexx.core.Bedrock bedrock() {
        return bedrock;
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

    public dev.vertexx.block.CustomBlocks customBlocks() {
        return customBlocks;
    }

    public dev.vertexx.end.EndWorld endWorld() {
        return endWorld;
    }

    public dev.vertexx.overworld.Curses curses() {
        return curses;
    }

    public dev.vertexx.overworld.Archaeology archaeology() {
        return archaeology;
    }

    public dev.vertexx.overworld.Arid arid() {
        return arid;
    }

    public dev.vertexx.overworld.Climates climates() {
        return climates;
    }

    public dev.vertexx.end.EndGear endGear() {
        return endGear;
    }

    /** Modelos automáticos: .bbmodel em plugins/Vertexx/blueprints viram mobs e resource pack sem comandos. */
    public dev.vertexx.blueprint.Blueprints blueprints() {
        return blueprints;
    }

    public FaunaManager fauna() {
        return fauna;
    }

    /** Modelos de entidade (bbmodel compilado) usados por bosses e criaturas. */
    public dev.vertexx.model.Models entityModels() {
        return entityModels;
    }

    public dev.vertexx.end.GloomGear gloomGear() {
        return gloomGear;
    }

    public BossManager bosses() {
        return bosses;
    }

    public dev.vertexx.backup.BackupManager backups() {
        return backups;
    }

    public dev.vertexx.profile.InventoryProfiles profiles() {
        return profiles;
    }

    public dev.vertexx.intro.IntroManager intro() {
        return intro;
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
