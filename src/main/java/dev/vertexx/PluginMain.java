package dev.vertexx;

import dev.vertexx.core.Msg;
import dev.vertexx.core.MenuListener;
import dev.vertexx.kingdom.Kingdom;
import dev.vertexx.kingdom.Membership;
import dev.vertexx.mercenary.MercenaryCommand;
import dev.vertexx.mercenary.MercenaryListener;
import dev.vertexx.mercenary.MercenaryManager;
import dev.vertexx.grave.GraveCommand;
import dev.vertexx.grave.GraveListener;
import dev.vertexx.grave.GraveManager;
import dev.vertexx.honor.CombatListener;
import dev.vertexx.honor.HonorCommand;
import dev.vertexx.honor.HonorManager;
import dev.vertexx.intro.IntroManager;
import dev.vertexx.kingdom.KingdomCommand;
import dev.vertexx.kingdom.KingdomListener;
import dev.vertexx.kingdom.KingdomManager;
import dev.vertexx.pack.ResourcePackManager;
import dev.vertexx.panel.PanelCommand;
import dev.vertexx.panel.PanelManager;
import dev.vertexx.update.AutoUpdater;
import dev.vertexx.waystone.ModelManager;
import dev.vertexx.waystone.WaystoneListener;
import dev.vertexx.waystone.WaystoneManager;
import java.io.File;
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
    private IntroManager intro;
    private ModelManager models;
    private PanelManager panel;
    private MercenaryManager mercenaries;
    private Membership membership;
    private AutoUpdater updater;
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
        intro = new IntroManager(this);
        models = new ModelManager(this);
        panel = new PanelManager(this);
        mercenaries = new MercenaryManager(this);
        membership = new Membership(this);

        var plugins = Bukkit.getPluginManager();
        plugins.registerEvents(new MenuListener(), this);
        plugins.registerEvents(new MercenaryListener(this), this);
        plugins.registerEvents(onboarding, this);
        plugins.registerEvents(new WaystoneListener(this), this);
        plugins.registerEvents(new GraveListener(this), this);
        plugins.registerEvents(combat, this);
        plugins.registerEvents(resourcePack, this);
        plugins.registerEvents(intro, this);
        plugins.registerEvents(models, this);
        plugins.registerEvents(panel, this);

        register("reino", new KingdomCommand(this));
        register("tumulo", new GraveCommand(this));
        register("honra", new HonorCommand(this));
        register("pacote", resourcePack);
        var mercenaryCommand = new MercenaryCommand(this);
        register("recompensa", mercenaryCommand);
        register("mercenario", mercenaryCommand);

        updater = new AutoUpdater(this);
        plugins.registerEvents(updater, this);
        register("vertexx", new PanelCommand(this));
        updater.start();
        resourcePack.startResolver();

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
            YamlConfiguration panelData = panel.read();
            YamlConfiguration mercenaryData = mercenaries.read();
            Bukkit.getScheduler().runTask(this, () -> {
                mercenaries.apply(mercenaryData);
                mercenaries.startTasks();
                startKingdomHonorRegen();
                kingdoms.apply(kingdomData);
                waystones.apply(waystoneData);
                graves.apply(graveData);
                honor.apply(honorData);
                resourcePack.apply(packData);
                panel.apply(panelData);
                models.load(waystones.all());
                models.start();
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
        intro.shutdown();
        if (ready) {
            kingdoms.saveNow();
            waystones.saveNow();
            graves.saveNow();
            honor.saveNow();
            resourcePack.saveNow();
            panel.saveNow();
            mercenaries.saveNow();
        }
        models.shutdown();
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

    public IntroManager intro() {
        return intro;
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

    public AutoUpdater updater() {
        return updater;
    }

    public MercenaryManager mercenaries() {
        return mercenaries;
    }

    public Membership membership() {
        return membership;
    }

    public GraveManager graves() {
        return graves;
    }

    public HonorManager honor() {
        return honor;
    }

    /** Arquivo .jar do plugin em execução (usado pelo atualizador). */
    public File jarFile() {
        return getFile();
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

    /**
     * Multiplicador do custo de viagem por Waystone (0 = gratuito): a honra define a base e a reputação
     * de mercenário (só para quem está sem reino) dá desconto por cima.
     */
    public double travelCostMultiplier(Player player) {
        double base = honor.tier(player.getUniqueId()).travelMultiplier();
        return base * mercenaries.activeTier(player.getUniqueId()).travelMultiplier();
    }

    /** Alguém abriu o túmulo de outra pessoa (protegido por chave ou já expirado). */
    public void onGraveLooted(Player looter, java.util.UUID owner) {
        if (!mercenaries.isOutlaw(looter.getUniqueId())) {
            return;
        }
        mercenaries.addRep(looter, getConfig().getInt("mercenary.reputation.theft", 4), "saque");
        Kingdom hit = kingdoms.kingdomOf(owner);
        if (hit != null) {
            membership.shake(hit, getConfig().getInt("kingdom-honor.theft", -2), "túmulo saqueado por mercenário");
        }
    }

    /** A honra dos reinos se recompõe aos poucos, até o valor inicial. */
    private void startKingdomHonorRegen() {
        long period = 20L * 60 * 10;
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            int step = getConfig().getInt("kingdom-honor.regen-per-10-min", 1);
            if (step <= 0) {
                return;
            }
            for (Kingdom kingdom : Kingdom.values()) {
                int now = kingdoms.honor(kingdom);
                if (now < KingdomManager.HONOR_START) {
                    kingdoms.addHonor(kingdom, Math.min(step, KingdomManager.HONOR_START - now));
                }
            }
        }, period, period);
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
