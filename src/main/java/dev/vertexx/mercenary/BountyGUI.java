package dev.vertexx.mercenary;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Fx;
import dev.vertexx.core.Items;
import dev.vertexx.core.Menu;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Ui;
import dev.vertexx.kingdom.Kingdom;
import dev.vertexx.kingdom.KingdomSwitchGUI;
import dev.vertexx.mercenary.MercenaryManager.Tier;
import dev.vertexx.mercenary.MercenaryManager.Wanted;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Quadro de Procurados: lista os foras da lei com a reputação e o preço de cada cabeça e permite
 * colocar uma recompensa (paga em itens) sobre um mercenário.
 */
public final class BountyGUI extends Menu {

    private record Entry(UUID id, String name, boolean outlaw, int rep, int total, List<MercenaryManager.Pledge> pledges) {
    }

    private UUID target;
    private String targetName;
    private int amount;
    private int listPage;

    public BountyGUI(PluginMain plugin, Player player) {
        super(plugin, player, Ui.Theme.OUTLAW, "<red><bold>Quadro de Procurados</bold>");
    }

    private MercenaryManager mercs() {
        return plugin.mercenaries();
    }

    @Override
    protected void render() {
        frame(target == null ? () -> plugin.panel().open(player) : this::toList);
        if (target == null) {
            list();
        } else {
            place();
        }
    }

    private void toList() {
        target = null;
        refresh();
    }

    // ── Lista ───────────────────────────────────────────────────

    private List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        List<UUID> seen = new ArrayList<>();
        for (Wanted wanted : mercs().wanted()) {
            seen.add(wanted.target());
            list.add(new Entry(wanted.target(), wanted.name(), mercs().isOutlaw(wanted.target()),
                    mercs().rep(wanted.target()), wanted.total(), wanted.pledges()));
        }
        for (UUID id : mercs().outlawIds()) {
            if (!seen.contains(id)) {
                list.add(new Entry(id, mercs().nameOf(id), true, mercs().rep(id), 0, List.of()));
            }
        }
        list.sort(Comparator.comparingInt(Entry::total).reversed().thenComparing(Comparator.comparingInt(Entry::rep).reversed()));
        return list;
    }

    private void list() {
        set(HEADER_SLOT, icon(Material.SKELETON_SKULL, "ui_skull", "<red><bold>Quadro de Procurados",
                List.of("<gray>Foras da lei do servidor e o preço de cada cabeça.",
                        "<gray>Quem matar um procurado leva a recompensa.", "",
                        "<dark_gray>Pague em: " + mercs().currencyName(),
                        "<dark_gray>Colocar recompensa só vale para quem está sem reino."), true));

        List<Entry> all = entries();
        int perPage = LIST_SLOTS.length;
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        listPage = Math.min(listPage, pages - 1);
        for (int i = 0; i < perPage && listPage * perPage + i < all.size(); i++) {
            Entry entry = all.get(listPage * perPage + i);
            ItemStack head = Items.of(Material.PLAYER_HEAD, (entry.outlaw() ? "<red>" : "<gray>") + Msg.esc(entry.name()),
                    entryLore(entry), entry.total() > 0);
            head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.id())));
            set(LIST_SLOTS[i], head, c -> choose(entry));
        }
        if (all.isEmpty()) {
            set(22, icon(Material.COMPASS, "ui_skull", "<gray>Ninguém é procurado",
                    List.of("<dark_gray>Quando alguém abandonar um reino,", "<dark_gray>o nome aparece aqui."), false));
        }
        if (pages > 1) {
            set(NEXT_SLOT, Items.of(Material.ARROW, "<yellow>Próxima página ▶",
                    List.of("<dark_gray>Página " + (listPage + 1) + " de " + pages)), c -> {
                listPage = (listPage + 1) % pages;
                refresh();
            });
        }

        UUID me = player.getUniqueId();
        Tier tier = mercs().tier(me);
        List<String> status = new ArrayList<>();
        if (mercs().isOutlaw(me)) {
            status.add("<gray>Situação: <red>fora da lei");
            status.add("<gray>Reputação: <white>" + mercs().rep(me) + " <dark_gray>(" + tier.styled() + "<dark_gray>)");
            int bounty = mercs().total(me);
            status.add("<gray>Sua cabeça vale: " + (bounty > 0 ? "<yellow>" + bounty + " " + mercs().currencyName()
                    : "<dark_gray>nada ainda"));
            status.add("");
            status.add("<yellow>▶ Clique para voltar a um reino");
            set(40, icon(Material.IRON_SWORD, "ui_daggers", "<red><bold>Você é um mercenário", status, true), c ->
                    new KingdomSwitchGUI(plugin, player).open());
        } else {
            Kingdom own = plugin.kingdoms().kingdomOf(me);
            status.add("<gray>Reino: " + (own == null ? "<red>nenhum" : own.styled()));
            status.add("<gray>Reputação guardada: <white>" + mercs().rep(me));
            status.add("");
            status.add("<dark_gray>Mercenários não têm reino: para");
            status.add("<dark_gray>virar um, deixe o seu pelo painel do reino.");
            set(40, icon(Material.PLAYER_HEAD, "ui_star", "<white>" + Msg.esc(player.getName()), status, false));
        }

        set(47, icon(Material.NETHER_STAR, "ui_star", "<gold><bold>Níveis de reputação", tierLore(), false));
        set(51, icon(Material.WRITABLE_BOOK, "ui_coin", "<gold>Como ganhar fama", fameLore(), false));
    }

    private List<String> tierLore() {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Só vale enquanto você está sem reino.");
        lore.add("");
        for (Tier tier : Tier.values()) {
            String perks;
            if (tier.travelMultiplier() == 0) {
                perks = "Waystones grátis";
            } else if (tier.travelMultiplier() < 1) {
                perks = "Waystones -" + Math.round((1 - tier.travelMultiplier()) * 100) + "%";
            } else {
                perks = "sem benefícios";
            }
            if (tier.bountyBonusPercent() > 0) {
                perks += ", recompensas +" + tier.bountyBonusPercent() + "%";
            }
            lore.add(tier.styled() + " <dark_gray>(" + tier.min() + "+) <gray>" + perks);
        }
        lore.add("");
        lore.add("<dark_gray>Morrer tira reputação; ter a cabeça cobrada zera.");
        return lore;
    }

    private List<String> fameLore() {
        var cfg = plugin.getConfig();
        return List.of(
                "<gray>Matar jogadores: <gold>+" + cfg.getInt("mercenary.reputation.kill", 3),
                "<gray>Cobrar uma recompensa: <gold>+" + cfg.getInt("mercenary.reputation.bounty-kill", 8),
                "<gray>Trair um reino: <gold>+" + cfg.getInt("mercenary.reputation.betrayal", 10),
                "<gray>Saquear túmulos alheios: <gold>+" + cfg.getInt("mercenary.reputation.theft", 4),
                "<gray>Ter a cabeça a prêmio: <gold>+ conforme o valor",
                "",
                "<dark_gray>Cada ato também tira honra do reino atingido.");
    }

    private List<String> entryLore(Entry entry) {
        List<String> lore = new ArrayList<>();
        Tier tier = Tier.of(entry.rep());
        lore.add("<gray>Situação: " + (entry.outlaw() ? "<red>fora da lei" : "<dark_gray>escondido em um reino"));
        lore.add("<gray>Reputação: <white>" + entry.rep() + " <dark_gray>(" + tier.styled() + "<dark_gray>)");
        lore.add("<gray>Recompensa: " + (entry.total() > 0 ? "<yellow>" + entry.total() + " " + mercs().currencyName()
                : "<dark_gray>nenhuma"));
        if (!entry.pledges().isEmpty()) {
            lore.add("<gray>Ofertas: <white>" + entry.pledges().size());
        }
        MercenaryManager.Outlaw outlaw = mercs().outlaw(entry.id());
        if (outlaw != null) {
            long days = Math.max(0, (System.currentTimeMillis() - outlaw.since()) / 86_400_000L);
            Kingdom previous = Kingdom.byId(outlaw.previousKingdom());
            lore.add("<gray>Fora da lei há: <white>" + (days == 0 ? "menos de um dia" : days + " dia(s)"));
            if (previous != null) {
                lore.add("<gray>Ex-membro de: " + previous.styled());
            }
        }
        lore.add("");
        if (entry.id().equals(player.getUniqueId())) {
            lore.add("<dark_gray>Esta é a sua cabeça.");
        } else if (entry.outlaw()) {
            lore.add("<yellow>▶ Clique para colocar recompensa");
        } else {
            lore.add("<dark_gray>Só dá para pôr recompensa em quem está sem reino.");
        }
        return lore;
    }

    private void choose(Entry entry) {
        if (entry.id().equals(player.getUniqueId()) || !entry.outlaw()) {
            Fx.deny(player);
            return;
        }
        target = entry.id();
        targetName = entry.name();
        amount = Math.max(1, plugin.getConfig().getInt("bounty.min", 8));
        refresh();
    }

    // ── Colocar recompensa ──────────────────────────────────────

    private int min() {
        return Math.max(1, plugin.getConfig().getInt("bounty.min", 8));
    }

    private int max() {
        return Math.max(min(), plugin.getConfig().getInt("bounty.max", 2304));
    }

    private static int step(ClickType click) {
        return click.isShiftClick() ? 64 : click.isRightClick() ? 8 : 1;
    }

    private void adjust(int delta) {
        int next = Math.max(min(), Math.min(max(), amount + delta));
        if (next != amount) {
            amount = next;
            Fx.sound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 0.8f + 0.8f * amount / max());
        } else {
            Fx.deny(player);
        }
        refresh();
    }

    private void place() {
        int carried = mercs().carried(player);
        int gain = Math.min(plugin.getConfig().getInt("bounty.rep-cap-per-placement", 15),
                amount / Math.max(1, plugin.getConfig().getInt("bounty.rep-per-units", 8)));
        String currency = mercs().currencyName();
        ItemStack head = Items.of(Material.PLAYER_HEAD, "<red><bold>" + Msg.esc(targetName),
                List.of("<gray>Recompensa atual: <yellow>" + mercs().total(target) + " " + currency,
                        "<gray>Sua oferta: <gold><bold>" + amount + " " + currency,
                        "<gray>Você carrega: " + (carried >= amount ? "<green>" : "<red>") + carried + " " + currency,
                        "",
                        "<gray>Fama dele por esta oferta: <gold>+" + gain,
                        "<dark_gray>Quem o matar leva tudo."), true);
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(target)));
        set(HEADER_SLOT, icon(Material.SKELETON_SKULL, "ui_skull", "<red><bold>Colocar recompensa",
                List.of("<gray>Escolha o valor e confirme.", "",
                        "<dark_gray>O pagamento sai do seu inventário na hora."), true));
        set(22, head);
        List<String> hint = List.of("<yellow>Esquerdo: <white>1", "<yellow>Direito: <white>8", "<yellow>Shift: <white>64");
        set(20, icon(Material.RED_DYE, "ui_minus", "<red><bold>Diminuir", hint, false), c -> adjust(-step(c)));
        set(24, icon(Material.LIME_DYE, "ui_plus", "<green><bold>Aumentar", hint, false), c -> adjust(step(c)));
        ItemStack coins = new ItemStack(mercs().currency(), Math.max(1, Math.min(64, amount)));
        coins.editMeta(meta -> {
            meta.displayName(Msg.item("<gold><bold>" + amount + " " + currency));
            meta.lore(List.of(Msg.item("<dark_gray>Mínimo " + min() + ", máximo " + max())));
        });
        set(31, coins);
        boolean afford = carried >= amount;
        set(40, icon(afford ? Material.LIME_DYE : Material.GRAY_DYE, afford ? "ui_check" : "ui_minus",
                afford ? "<green><bold>Confirmar recompensa" : "<red>Faltam itens",
                afford ? List.of("<gray>Pagar <white>" + amount + " " + currency + "<gray> pela cabeça de",
                        "<white>" + Msg.esc(targetName) + "<gray>.", "", "<red>Não há reembolso.")
                        : List.of("<gray>Você precisa de <white>" + amount + " " + currency + "<gray>."), afford),
                c -> confirm());
    }

    private void confirm() {
        UUID id = target;
        if (id == null || !mercs().isOutlaw(id)) {
            Msg.send(player, "<red>Este alvo não está mais fora da lei.");
            Fx.deny(player);
            toList();
            return;
        }
        if (!mercs().take(player, amount)) {
            Fx.deny(player);
            refresh();
            return;
        }
        mercs().place(player, id, targetName, amount);
        Bukkit.broadcast(Msg.mm(Msg.PREFIX + "<gold>Procurado! <white><who></white> <gray>pôs <yellow>" + amount + " "
                        + mercs().currencyName() + "<gray> na cabeça de <red><target></red><gray>. Total: <yellow>"
                        + mercs().total(id) + "<gray>.",
                Placeholder.unparsed("who", player.getName()), Placeholder.unparsed("target", targetName)));
        for (Player online : Bukkit.getOnlinePlayers()) {
            Fx.sound(online, Sound.BLOCK_BELL_USE, 0.6f, 0.6f);
        }
        player.closeInventory();
    }
}
