package dev.vertexx.grave;

import dev.vertexx.PluginMain;
import dev.vertexx.core.BlockPos;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Msg;
import dev.vertexx.core.YamlStore;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Túmulos: guardam itens e XP de quem morreu em uma cabeça do jogador no local da morte.
 * Persistidos em {@code graves.yml}.
 */
public final class GraveManager {

    public enum Access {
        OWNER, EXPIRED, KEY, DENIED
    }

    private final PluginMain plugin;
    private final YamlStore store;
    private final NamespacedKey keyTag;
    private final Map<UUID, Grave> byId = new LinkedHashMap<>();
    private final Map<BlockPos, Grave> byPos = new HashMap<>();

    public GraveManager(PluginMain plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "graves.yml");
        this.keyTag = new NamespacedKey(plugin, "grave_key");
    }

    // ── Persistência ────────────────────────────────────────────

    /** Leitura do disco: chame fora da thread principal. */
    public YamlConfiguration read() {
        return store.load();
    }

    /** Aplica os dados lidos: chame na thread principal. */
    public void apply(YamlConfiguration data) {
        byId.clear();
        byPos.clear();
        ConfigurationSection section = data.getConfigurationSection("graves");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            try {
                BlockPos pos = BlockPos.read(entry);
                String display = entry.getString("display");
                Grave grave = new Grave(UUID.fromString(key), UUID.fromString(entry.getString("owner", "")),
                        entry.getString("owner-name", "?"), pos, entry.getLong("created"), entry.getInt("xp"),
                        Base64.getDecoder().decode(entry.getString("items", "")),
                        display == null ? null : UUID.fromString(display), entry.getLong("until", 0L));
                if (pos != null) {
                    byId.put(grave.id(), grave);
                    byPos.put(pos, grave);
                }
            } catch (IllegalArgumentException | NullPointerException e) {
                plugin.getLogger().warning("Túmulo inválido ignorado em graves.yml: " + key);
            }
        }
    }

    public void save() {
        store.saveAsync(snapshot());
    }

    public void saveNow() {
        store.saveNow(snapshot());
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration data = new YamlConfiguration();
        for (Grave grave : byId.values()) {
            ConfigurationSection entry = data.createSection("graves." + grave.id());
            entry.set("owner", grave.owner().toString());
            entry.set("owner-name", grave.ownerName());
            grave.pos().write(entry);
            entry.set("created", grave.created());
            entry.set("xp", grave.xp());
            entry.set("items", Base64.getEncoder().encodeToString(grave.items()));
            if (grave.display() != null) {
                entry.set("display", grave.display().toString());
            }
            if (grave.until() != 0) {
                entry.set("until", grave.until());
            }
        }
        return data;
    }

    // ── Consulta ────────────────────────────────────────────────

    public int count() {
        return byId.size();
    }

    public boolean hasGraves() {
        return !byPos.isEmpty();
    }

    public Grave at(Block block) {
        return byPos.isEmpty() ? null : byPos.get(BlockPos.of(block));
    }

    public List<Grave> ownedBy(UUID owner) {
        List<Grave> result = new ArrayList<>();
        for (Grave grave : byId.values()) {
            if (grave.owner().equals(owner)) {
                result.add(grave);
            }
        }
        return result;
    }

    /** Todos os túmulos, na ordem de criação. */
    public List<Grave> all() {
        return new ArrayList<>(byId.values());
    }

    /** Túmulo pelo id completo ou por um prefixo único dele; {@code null} se não achar (ou se for ambíguo). */
    public Grave find(String ref) {
        String text = ref.toLowerCase(java.util.Locale.ROOT);
        Grave found = null;
        for (Grave grave : byId.values()) {
            String id = grave.id().toString();
            if (id.equals(text)) {
                return grave;
            }
            if (id.startsWith(text)) {
                if (found != null) {
                    return null;
                }
                found = grave;
            }
        }
        return found;
    }

    public Grave byId(UUID id) {
        return byId.get(id);
    }

    /** Milissegundos de proteção restantes (0 = qualquer um pode abrir; {@link Long#MAX_VALUE} = permanente). */
    public long protectionLeft(Grave grave) {
        if (grave.until() == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        long end = grave.until() != 0 ? grave.until()
                : grave.created() + plugin.getConfig().getLong("graves.protection-minutes", 30) * 60_000L;
        return Math.max(0, end - System.currentTimeMillis());
    }

    public static String formatTime(long millis) {
        if (millis == Long.MAX_VALUE) {
            return "permanente";
        }
        long seconds = (millis + 999) / 1000;
        if (seconds >= 3600) {
            return String.format("%dh%02d", seconds / 3600, seconds % 3600 / 60);
        }
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    // ── Controle de administrador ───────────────────────────────

    private Grave replace(Grave grave) {
        byId.put(grave.id(), grave);
        byPos.put(grave.pos(), grave);
        save();
        return grave;
    }

    /** Soma (ou subtrai, se negativo) milissegundos ao tempo de proteção restante; nunca abaixo de zero. */
    public Grave addProtection(Grave grave, long deltaMillis) {
        long left = protectionLeft(grave);
        if (left == Long.MAX_VALUE) {
            return grave;
        }
        long until = System.currentTimeMillis() + Math.max(0, left + deltaMillis);
        return replace(grave.withUntil(Math.max(1, until)));
    }

    /** Define a proteção para daqui a {@code millis} ms (0 remove a proteção). */
    public Grave setProtection(Grave grave, long millis) {
        return replace(grave.withUntil(millis <= 0 ? 1 : System.currentTimeMillis() + millis));
    }

    public Grave setPermanent(Grave grave) {
        return replace(grave.withUntil(Long.MAX_VALUE));
    }

    /** Volta ao tempo padrão da configuração, contado a partir da criação do túmulo. */
    public Grave resetProtection(Grave grave) {
        return replace(grave.withUntil(0));
    }

    /** Itens do túmulo (posições vazias não aparecem). */
    public List<ItemStack> contents(Grave grave) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack item : ItemStack.deserializeItemsFromBytes(grave.items())) {
            if (item != null && !item.isEmpty()) {
                list.add(item);
            }
        }
        return list;
    }

    /**
     * Tira o item {@code index} (da lista de {@link #contents}) do túmulo e devolve; se o túmulo ficar vazio
     * (sem itens e sem XP) ele é removido.
     */
    public ItemStack takeItem(Grave grave, int index) {
        List<ItemStack> list = contents(grave);
        if (index < 0 || index >= list.size()) {
            return null;
        }
        ItemStack item = list.remove(index);
        replace(grave.withContents(grave.xp(), ItemStack.serializeItemsAsBytes(list)));
        dropIfEmpty(byId.get(grave.id()));
        return item;
    }

    /** Zera o XP guardado e devolve o valor. */
    public int takeXp(Grave grave) {
        int xp = grave.xp();
        replace(grave.withContents(0, grave.items()));
        dropIfEmpty(byId.get(grave.id()));
        return xp;
    }

    private void dropIfEmpty(Grave grave) {
        if (grave != null && grave.xp() <= 0 && contents(grave).isEmpty()) {
            remove(grave);
        }
    }

    /** Quebra o túmulo: o bloco some e os itens caem no chão (e o XP vira orbes) ou são destruídos. */
    public void breakGrave(Grave grave, boolean dropContents) {
        Location center = grave.pos().center();
        remove(grave);
        if (dropContents && center.getWorld() != null) {
            for (ItemStack item : contents(grave)) {
                center.getWorld().dropItemNaturally(center, item);
            }
            if (grave.xp() > 0) {
                center.getWorld().spawn(center, org.bukkit.entity.ExperienceOrb.class, orb -> orb.setExperience(grave.xp()));
            }
        }
        Fx.burst(center, Particle.SOUL, 20, 0.3, 0.04);
        Fx.worldSound(center, Sound.BLOCK_SOUL_SAND_BREAK, 1.0f, 0.8f);
    }

    /** Entrega tudo ao jogador (itens que não cabem caem aos pés dele) e remove o túmulo. */
    public void giveAll(Player target, Grave grave) {
        remove(grave);
        giveContents(target, grave, target.getLocation());
    }

    private void giveContents(Player target, Grave grave, Location overflow) {
        int dropped = 0;
        for (ItemStack item : contents(grave)) {
            for (ItemStack leftover : target.getInventory().addItem(item).values()) {
                overflow.getWorld().dropItemNaturally(overflow, leftover);
                dropped++;
            }
        }
        if (grave.xp() > 0) {
            target.giveExp(grave.xp());
        }
        if (dropped > 0) {
            Msg.send(target, "<gray>O que não coube no inventário caiu no chão.");
        }
    }

    // ── Criação ─────────────────────────────────────────────────

    /**
     * Cria o túmulo no local da morte.
     *
     * @return o túmulo, ou {@code null} se não houver onde colocá-lo (os itens caem normalmente)
     */
    public Grave create(Player player, List<ItemStack> drops, int xp) {
        Block block = findSpot(player.getLocation());
        if (block == null) {
            return null;
        }
        block.setType(Material.PLAYER_HEAD, false);
        if (block.getState() instanceof Skull skull) {
            skull.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            skull.update(true, false);
        }
        Location above = block.getLocation().add(0.5, 1.0, 0.5);
        TextDisplay display = block.getWorld().spawn(above, TextDisplay.class, entity -> {
            entity.text(Msg.mm("<gray>☠ <white><name></white>", Placeholder.unparsed("name", player.getName())));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setPersistent(true);
            entity.addScoreboardTag("vertexx_grave");
        });
        Grave grave = new Grave(UUID.randomUUID(), player.getUniqueId(), player.getName(), BlockPos.of(block),
                System.currentTimeMillis(), xp, ItemStack.serializeItemsAsBytes(drops), display.getUniqueId(), 0L);
        byId.put(grave.id(), grave);
        byPos.put(grave.pos(), grave);
        save();

        Location center = block.getLocation().add(0.5, 0.4, 0.5);
        Fx.burst(center, Particle.SOUL, 25, 0.3, 0.04);
        Fx.worldSound(center, Sound.BLOCK_SOUL_SAND_BREAK, 1.0f, 0.6f);
        return grave;
    }

    /** Primeiro bloco livre (ar, líquido ou vegetação substituível) a partir do ponto da morte, subindo. */
    private Block findSpot(Location death) {
        World world = death.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        int y = Math.max(minY, Math.min(maxY, death.getBlockY()));
        for (; y <= maxY; y++) {
            Block block = world.getBlockAt(death.getBlockX(), y, death.getBlockZ());
            if ((block.isEmpty() || block.isLiquid() || block.isReplaceable()) && !byPos.containsKey(BlockPos.of(block))) {
                return block;
            }
        }
        return null;
    }

    // ── Coleta ──────────────────────────────────────────────────

    public Access access(Player player, Grave grave) {
        if (grave.owner().equals(player.getUniqueId())) {
            return Access.OWNER;
        }
        if (protectionLeft(grave) == 0) {
            return Access.EXPIRED;
        }
        return isKey(player.getInventory().getItemInMainHand()) ? Access.KEY : Access.DENIED;
    }

    /** Tenta abrir o túmulo: devolve itens e XP a quem abriu e remove o bloco. */
    public void tryCollect(Player player, Grave grave) {
        Access access = access(player, grave);
        if (access == Access.DENIED) {
            Msg.send(player, "<red>Túmulo de <white>" + grave.ownerName() + "</white> protegido por mais <white>"
                    + formatTime(protectionLeft(grave)) + "</white>.");
            Fx.deny(player);
            return;
        }
        if (access == Access.KEY) {
            ItemStack key = player.getInventory().getItemInMainHand();
            key.setAmount(key.getAmount() - 1);
            Fx.sound(player, Sound.BLOCK_VAULT_OPEN_SHUTTER, 1.0f, 1.0f);
        }
        remove(grave);
        Location center = grave.pos().center();
        int dropped = 0;
        for (ItemStack item : contents(grave)) {
            for (ItemStack leftover : player.getInventory().addItem(item).values()) {
                center.getWorld().dropItemNaturally(center, leftover);
                dropped++;
            }
        }
        if (grave.xp() > 0) {
            player.giveExp(grave.xp());
        }

        Fx.burst(center, Particle.SOUL, 30, 0.3, 0.05);
        Fx.burst(center, Particle.SCULK_SOUL, 12, 0.25, 0.03);
        Fx.worldSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 1.3f);
        Fx.sound(player, Sound.ENTITY_ITEM_PICKUP, 0.9f, 0.9f);
        String who = access == Access.OWNER ? "<green>Você recuperou seus pertences."
                : "<yellow>Você abriu o túmulo de <white>" + grave.ownerName() + "</white>.";
        Msg.send(player, who + (dropped > 0 ? " <gray>O que não coube no inventário caiu no chão." : ""));
    }

    private void remove(Grave grave) {
        byId.remove(grave.id());
        byPos.remove(grave.pos());
        Block block = grave.pos().block();
        if (block != null && block.getType() == Material.PLAYER_HEAD) {
            block.setType(Material.AIR, false);
        }
        if (grave.display() != null) {
            Entity display = Bukkit.getEntity(grave.display());
            if (display != null) {
                display.remove();
            }
        }
        save();
    }

    // ── Chave ───────────────────────────────────────────────────

    /** Chave de Túmulo: abre qualquer túmulo ainda protegido e é consumida no uso. */
    public ItemStack createKey(int amount) {
        ItemStack key = Items.of(Material.TRIAL_KEY, "<gradient:#9be7ff:#c4a7ff>Chave de Túmulo",
                List.of(
                        "<gray>Abre um túmulo protegido",
                        "<gray>de qualquer jogador.",
                        "",
                        "<dark_gray>Consumida ao usar."), true);
        key.setAmount(Math.max(1, Math.min(64, amount)));
        key.editMeta(meta -> meta.getPersistentDataContainer().set(keyTag, PersistentDataType.BYTE, (byte) 1));
        return key;
    }

    public boolean isKey(ItemStack item) {
        return item != null && item.getType() == Material.TRIAL_KEY && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(keyTag, PersistentDataType.BYTE);
    }
}
