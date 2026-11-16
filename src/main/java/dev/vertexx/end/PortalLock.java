package dev.vertexx.end;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Msg;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Portal do End trancado. Os 12 encaixes do portal exigem 11 Núcleos de boss, todos de tipos diferentes, e por
 * último um Olho do Ender comum no 12º encaixe; só então o portal abre. O olho vanilla não entra enquanto os
 * núcleos não estiverem todos no lugar. Hoje existem 2 tipos de núcleo (Wildfire e Iceologer), então o portal
 * permanece trancado até os demais bosses existirem (basta registrar o item em {@link #CORES}).
 * <p>
 * Estado: o encaixe fica "com olho" no mundo; qual núcleo está em qual encaixe vai no PDC do chunk
 * ({@code vertexx:portal_cores}, linhas {@code x;y;z;id}).
 */
public final class PortalLock implements Listener {

    /** Núcleos de boss que já existem (o portal exige {@code end.portal.core-slots} deles; hoje faltam 9). */
    public static final List<EndItem> CORES = List.of(EndItem.WILDFIRE_CORE, EndItem.ICEOLOGER_CORE);

    private final PluginMain plugin;
    private final NamespacedKey key;

    public PortalLock(PluginMain plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey("vertexx", "portal_cores");
    }

    private boolean locked() {
        return plugin.getConfig().getBoolean("end.portal.locked", true);
    }

    private int slots() {
        return Math.max(1, plugin.getConfig().getInt("end.portal.core-slots", 11));
    }

    // ── Estado salvo no chunk ───────────────────────────────────

    private static String pos(Block b) {
        return b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private Map<String, String> read(Block b) {
        Map<String, String> map = new HashMap<>();
        String raw = b.getChunk().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw != null && !raw.isEmpty()) {
            for (String line : raw.split("\\|")) {
                int cut = line.lastIndexOf(';');
                if (cut > 0) {
                    map.put(line.substring(0, cut), line.substring(cut + 1));
                }
            }
        }
        return map;
    }

    private void write(Block b, Map<String, String> map) {
        PersistentDataContainer pdc = b.getChunk().getPersistentDataContainer();
        if (map.isEmpty()) {
            pdc.remove(key);
            return;
        }
        List<String> lines = new ArrayList<>();
        map.forEach((k, v) -> lines.add(k + ";" + v));
        pdc.set(key, PersistentDataType.STRING, String.join("|", lines));
    }

    /** Núcleo registrado neste encaixe, "eye" para o olho, ou nulo. */
    private String stored(Block frame) {
        return read(frame).get(pos(frame));
    }

    private void store(Block frame, String value) {
        Map<String, String> map = read(frame);
        map.put(pos(frame), value);
        write(frame, map);
    }

    // ── Grupo de encaixes (o anel do portal) ────────────────────

    private List<Block> ring(Block start) {
        List<Block> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.getBlockKey());
        while (!queue.isEmpty() && found.size() < 24) {
            Block b = queue.poll();
            found.add(b);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block n = b.getRelative(dx, 0, dz);
                    if (n.getType() == Material.END_PORTAL_FRAME && seen.add(n.getBlockKey())) {
                        queue.add(n);
                    }
                }
            }
        }
        return found;
    }

    private static boolean filled(Block frame) {
        return frame.getBlockData() instanceof EndPortalFrame data && data.hasEye();
    }

    private static void fill(Block frame) {
        if (frame.getBlockData() instanceof EndPortalFrame data) {
            data.setEye(true);
            frame.setBlockData(data, false);
        }
    }

    // ── Interação ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block frame = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || frame == null
                || frame.getType() != Material.END_PORTAL_FRAME || !locked()) {
            return;
        }
        // o encaixe nunca recebe o olho vanilla: tudo passa por aqui
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        List<Block> ring = ring(frame);
        Map<String, Integer> used = usedCores(ring);
        int cores = used.size();
        EndItem core = EndItem.of(held);
        boolean isEye = held.getType() == Material.ENDER_EYE;
        if (filled(frame)) {
            Msg.actionBar(player, "<gray>Este encaixe já está preenchido. <dark_gray>(Núcleos: " + cores + "/" + slots() + ")");
            return;
        }
        if (core != null && CORES.contains(core)) {
            if (used.containsKey(core.id())) {
                Fx.deny(player);
                Msg.actionBar(player, "<red>Este núcleo já está encaixado neste portal.");
                return;
            }
            if (cores >= slots()) {
                Fx.deny(player);
                Msg.actionBar(player, "<red>Todos os núcleos já estão no lugar. Falta o Olho do Ender.");
                return;
            }
            fill(frame);
            store(frame, core.id());
            consume(player, held);
            effect(frame, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.8f);
            Msg.actionBar(player, "<light_purple>" + core.display() + " encaixado. <gray>(" + (cores + 1) + "/" + slots() + ")");
            tryOpen(frame, player);
            return;
        }
        if (isEye) {
            if (cores < slots()) {
                Fx.deny(player);
                Msg.actionBar(player, "<red>O portal está selado. <gray>Faltam <white>" + (slots() - cores)
                        + " <gray>núcleos de boss. <dark_gray>(" + cores + "/" + slots() + ")");
                effect(frame, Sound.BLOCK_BEACON_DEACTIVATE, 1.4f);
                return;
            }
            fill(frame);
            store(frame, "eye");
            consume(player, held);
            effect(frame, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0f);
            tryOpen(frame, player);
            return;
        }
        Msg.actionBar(player, "<gray>Portal selado. Núcleos de boss encaixados: <white>" + cores + "<dark_gray>/" + slots());
    }

    private Map<String, Integer> usedCores(List<Block> ring) {
        Map<String, Integer> used = new HashMap<>();
        for (Block b : ring) {
            String v = stored(b);
            if (v != null && !v.equals("eye") && filled(b)) {
                used.put(v, 1);
            }
        }
        return used;
    }

    private void consume(Player player, ItemStack held) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }
    }

    private void effect(Block frame, Sound sound, float pitch) {
        Location at = frame.getLocation().add(0.5, 1.0, 0.5);
        at.getWorld().playSound(at, sound, 1f, pitch);
        at.getWorld().spawnParticle(Particle.REVERSE_PORTAL, at, 20, 0.25, 0.2, 0.25, 0.05);
    }

    /** Abre o portal quando o anel inteiro está preenchido: 11 núcleos mais o olho. */
    private void tryOpen(Block frame, Player player) {
        List<Block> ring = ring(frame);
        if (ring.size() < 12) {
            return;
        }
        boolean eye = false;
        for (Block b : ring) {
            if (!filled(b)) {
                return;
            }
            eye |= "eye".equals(stored(b));
        }
        if (!eye || usedCores(ring).size() < slots()) {
            return;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Block b : ring) {
            minX = Math.min(minX, b.getX());
            maxX = Math.max(maxX, b.getX());
            minZ = Math.min(minZ, b.getZ());
            maxZ = Math.max(maxZ, b.getZ());
        }
        int cx = (minX + maxX) / 2;
        int cz = (minZ + maxZ) / 2;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                frame.getWorld().getBlockAt(cx + dx, frame.getY(), cz + dz).setType(Material.END_PORTAL, false);
            }
        }
        Location center = new Location(frame.getWorld(), cx + 0.5, frame.getY() + 1, cz + 0.5);
        center.getWorld().playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 1f, 1f);
        center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center, 80, 1.2, 0.4, 1.2, 0.1);
        Msg.send(player, "<dark_purple><bold>O Portal do End despertou.");
    }
}
