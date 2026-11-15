package dev.vertexx.boss;

import dev.vertexx.PluginMain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Fila de blocos para construir estruturas sem travar o servidor: tudo é montado na memória (a última
 * escrita em cada posição vence), os chunks são carregados e os blocos entram em lotes por tick.
 */
final class BlockQueue {

    private final World world;
    private final Map<Long, BlockData> blocks = new LinkedHashMap<>();
    private final Map<Material, BlockData> cache = new HashMap<>();

    BlockQueue(World world) {
        this.world = world;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | ((y + 2048) & 0xFFF);
    }

    private static int keyX(long key) {
        return (int) (key >> 38) << 6 >> 6;
    }

    private static int keyZ(long key) {
        return (int) ((key >> 12) & 0x3FFFFFF) << 6 >> 6;
    }

    private static int keyY(long key) {
        return (int) (key & 0xFFF) - 2048;
    }

    int size() {
        return blocks.size();
    }

    void set(int x, int y, int z, Material material) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return;
        }
        blocks.put(key(x, y, z), cache.computeIfAbsent(material, Material::createBlockData));
    }

    void set(int x, int y, int z, BlockData data) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return;
        }
        blocks.put(key(x, y, z), data);
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, material);
                }
            }
        }
    }

    /** Disco cheio no plano XZ. */
    void disk(int cx, int cz, int y, double radius, Material material) {
        int r = (int) Math.ceil(radius);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z <= radius * radius) {
                    set(cx + x, y, cz + z, material);
                }
            }
        }
    }

    /** Anel (coroa) entre dois raios. */
    void ring(int cx, int cz, int y, double inner, double outer, Material material) {
        int r = (int) Math.ceil(outer);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = x * x + z * z;
                if (d <= outer * outer && d >= inner * inner) {
                    set(cx + x, y, cz + z, material);
                }
            }
        }
    }

    void cylinder(int cx, int cz, int y1, int y2, double radius, Material material) {
        for (int y = y1; y <= y2; y++) {
            disk(cx, cz, y, radius, material);
        }
    }

    void tube(int cx, int cz, int y1, int y2, double inner, double outer, Material material) {
        for (int y = y1; y <= y2; y++) {
            ring(cx, cz, y, inner, outer, material);
        }
    }

    /** Carrega os chunks (gerando se preciso) e aplica os blocos aos poucos. */
    void run(PluginMain plugin, int perTick, Runnable done) {
        Set<Long> chunks = new HashSet<>();
        for (long key : blocks.keySet()) {
            chunks.add(((long) (keyX(key) >> 4) << 32) | ((keyZ(key) >> 4) & 0xFFFFFFFFL));
        }
        List<CompletableFuture<?>> loads = new ArrayList<>();
        List<int[]> tickets = new ArrayList<>();
        for (long chunk : chunks) {
            int cx = (int) (chunk >> 32);
            int cz = (int) chunk;
            world.addPluginChunkTicket(cx, cz, plugin);
            tickets.add(new int[] {cx, cz});
            loads.add(world.getChunkAtAsync(cx, cz, true));
        }
        CompletableFuture.allOf(loads.toArray(new CompletableFuture<?>[0])).whenComplete((ignored, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> apply(plugin, perTick, tickets, done, error)));
    }

    private void apply(PluginMain plugin, int perTick, List<int[]> tickets, Runnable done, Throwable error) {
        if (error != null) {
            plugin.getLogger().warning("Falha ao carregar chunks da estrutura: " + error.getMessage());
            tickets.forEach(t -> world.removePluginChunkTicket(t[0], t[1], plugin));
            return;
        }
        var iterator = blocks.entrySet().iterator();
        new BukkitRunnable() {
            @Override
            public void run() {
                int count = 0;
                while (iterator.hasNext() && count < perTick) {
                    var entry = iterator.next();
                    long key = entry.getKey();
                    world.getBlockAt(keyX(key), keyY(key), keyZ(key)).setBlockData(entry.getValue(), false);
                    count++;
                }
                if (!iterator.hasNext()) {
                    cancel();
                    tickets.forEach(t -> world.removePluginChunkTicket(t[0], t[1], plugin));
                    done.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
