package dev.vertexx.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

/** Posição de bloco independente de o mundo estar carregado. */
public record BlockPos(String world, int x, int y, int z) {

    public static BlockPos of(Block block) {
        return new BlockPos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockPos read(ConfigurationSection section) {
        String world = section.getString("world");
        return world == null ? null
                : new BlockPos(world, section.getInt("x"), section.getInt("y"), section.getInt("z"));
    }

    public void write(ConfigurationSection section) {
        section.set("world", world);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
    }

    /** Mundo do Bukkit, ou {@code null} se não estiver carregado. */
    public World bukkitWorld() {
        return Bukkit.getWorld(world);
    }

    public boolean isChunkLoaded() {
        World loaded = bukkitWorld();
        return loaded != null && loaded.isChunkLoaded(x >> 4, z >> 4);
    }

    /** Centro do bloco, ou {@code null} se o mundo não estiver carregado. */
    public Location center() {
        World loaded = bukkitWorld();
        return loaded == null ? null : new Location(loaded, x + 0.5, y + 0.5, z + 0.5);
    }

    public Block block() {
        World loaded = bukkitWorld();
        return loaded == null ? null : loaded.getBlockAt(x, y, z);
    }

    public String coords() {
        return x + ", " + y + ", " + z;
    }
}
