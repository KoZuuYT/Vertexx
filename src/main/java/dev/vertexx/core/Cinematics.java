package dev.vertexx.core;

import dev.vertexx.PluginMain;
import java.time.Duration;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Cinemáticas curtas (subida de nível, habilidade nova, upgrades, elites). Com o resource pack, o título é
 * um banner desenhado (fonte {@code vertexx:cinematic}), precedido por um clarão de tela cheia e com sons
 * próprios; sem o pacote, o mesmo momento aparece com texto, partículas e sons vanilla.
 */
public final class Cinematics {

    /** Glifos de banner da fonte vertexx:cinematic. */
    public enum Banner {
        LEVEL('', "#ffd166"), SKILL('', "#4cc9f0"), ELITE('', "#f72585"),
        FORGE('', "#ff9f1c"), STAT('', "#2cb67d");

        private final char glyph;
        private final String flash;

        Banner(char glyph, String flash) {
            this.glyph = glyph;
            this.flash = flash;
        }
    }

    private static final Key FONT = Key.key("vertexx", "cinematic");
    private static final char FLASH = '';

    private final PluginMain plugin;

    public Cinematics(PluginMain plugin) {
        this.plugin = plugin;
    }

    private boolean pack(Player player) {
        return plugin.resourcePack().isLoaded(player);
    }

    private void packSound(Player player, String event, float volume, float pitch) {
        player.playSound(net.kyori.adventure.sound.Sound.sound(Key.key("vertexx", event),
                net.kyori.adventure.sound.Sound.Source.MASTER, volume, pitch));
    }

    /**
     * Toca a cinemática: clarão, banner, subtítulo, som e partículas.
     *
     * @param fallback título em texto para quem não tem o pacote
     */
    public void play(Player player, Banner banner, String fallback, String subtitle, boolean big) {
        boolean pack = pack(player);
        Component sub = Msg.mm(subtitle);
        if (pack) {
            Component flash = Component.text(String.valueOf(FLASH)).font(FONT)
                    .color(TextColor.fromHexString(banner.flash));
            player.showTitle(Title.title(flash, Component.empty(),
                    Title.Times.times(Duration.ofMillis(60), Duration.ZERO, Duration.ofMillis(550))));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Component glyph = Component.text(String.valueOf(banner.glyph)).font(FONT).color(NamedTextColor.WHITE);
                player.showTitle(Title.title(glyph, sub, Title.Times.times(Duration.ofMillis(200),
                        Duration.ofMillis(big ? 2400 : 1500), Duration.ofMillis(700))));
            }, 5L);
            packSound(player, big ? "intro.boom" : "intro.confirm", big ? 0.8f : 0.9f, 1f);
            if (big) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> packSound(player, "intro.title", 0.7f, 1f), 8L);
            }
        } else {
            player.showTitle(Title.title(Msg.mm(fallback), sub, Title.Times.times(Duration.ofMillis(200),
                    Duration.ofMillis(big ? 2400 : 1500), Duration.ofMillis(700))));
            Fx.sound(player, big ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
        helix(player, big);
    }

    /** Espiral de partículas subindo em volta do jogador, visível a todos por perto. */
    private void helix(Player player, boolean big) {
        int duration = big ? 40 : 24;
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!player.isOnline() || t >= duration) {
                    cancel();
                    return;
                }
                Location base = player.getLocation();
                double y = (t / (double) duration) * 2.2;
                for (int arm = 0; arm < 2; arm++) {
                    double angle = t * 0.45 + arm * Math.PI;
                    base.getWorld().spawnParticle(Particle.END_ROD,
                            base.getX() + Math.cos(angle) * 0.9, base.getY() + y, base.getZ() + Math.sin(angle) * 0.9,
                            1, 0, 0, 0, 0);
                }
                if (t == duration - 1) {
                    Fx.burst(base.clone().add(0, 1.2, 0), Particle.TOTEM_OF_UNDYING, big ? 60 : 25, 0.5, 0.25);
                    Fx.worldSound(base, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1.2f);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Momentos prontos ───────────────────────────────────────

    public void generalLevelUp(Player player, int level, int points) {
        play(player, Banner.LEVEL, "<gold><bold>✦ NÍVEL " + level + " ✦",
                "<gold>Nível " + level + " <dark_gray>• <white>+" + points + " ponto" + (points > 1 ? "s" : "")
                        + " de habilidade", true);
    }

    public void statLevelUp(Player player, String statName, int level, boolean title) {
        if (title) {
            play(player, Banner.STAT, "<green><bold>" + statName, "<green>" + statName + " <white>nível " + level, false);
        } else {
            Msg.actionBar(player, "<green>▲ " + statName + " <white>nível " + level);
            if (pack(player)) {
                packSound(player, "intro.tick", 0.9f, 1.2f);
            } else {
                Fx.sound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
            }
        }
    }

    public void skillUnlocked(Player player, String skill, int rank) {
        play(player, Banner.SKILL, "<aqua><bold>Habilidade", "<aqua>" + skill + " <white>nível " + rank, false);
    }

    public void upgraded(Player player, String what, int tier) {
        play(player, Banner.FORGE, "<gold><bold>Upgrade", "<gold>" + what + " <white>nível " + tier, false);
    }

    public void eliteSlain(Player player, String mobName) {
        play(player, Banner.ELITE, "<light_purple><bold>Elite derrotado", "<light_purple>" + mobName, false);
    }

    public void bossAwakens(Player player, String fallbackName, String subtitle) {
        play(player, Banner.ELITE, fallbackName, subtitle, true);
    }

    public void bossSlain(Player player, String bossName) {
        play(player, Banner.LEVEL, "<gold><bold>✦ VITÓRIA ✦", "<white>Você derrotou " + bossName, true);
    }
}
