package dev.vertexx.core;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Feedback sensorial (sons e partículas) padronizado. */
public final class Fx {

    private Fx() {
    }

    public static void sound(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public static void worldSound(Location at, Sound sound, float volume, float pitch) {
        at.getWorld().playSound(at, sound, volume, pitch);
    }

    public static void click(Player player) {
        sound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
    }

    public static void select(Player player) {
        sound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 1.4f);
    }

    public static void deny(Player player) {
        sound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.6f);
    }

    public static void open(Player player) {
        sound(player, Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.1f);
    }

    public static void success(Player player) {
        sound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    }

    /** Anel horizontal de partículas visível a todos. */
    public static void ring(Location center, Particle particle, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            center.getWorld().spawnParticle(particle,
                    center.getX() + Math.cos(angle) * radius, center.getY(), center.getZ() + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);
        }
    }

    public static void burst(Location center, Particle particle, int count, double spread, double speed) {
        center.getWorld().spawnParticle(particle, center, count, spread, spread, spread, speed);
    }
}
