package dev.vertexx.waystone;

import dev.vertexx.core.BlockPos;
import java.util.UUID;
import org.bukkit.Material;

/** Um Lodestone registrado como ponto de viagem rápida, com a decoração escolhida pelo dono. */
public final class Waystone {

    private final UUID id;
    private final String name;
    private final BlockPos pos;
    private final UUID owner;
    private final String ownerName;
    private final long created;
    private volatile Material icon = WaystoneIcons.DEFAULT;
    private volatile boolean showName = true;
    private volatile WaystoneSkin skin = WaystoneSkin.DEFAULT;
    private volatile int uses;
    /** Rota fixa: ao clicar, viaja direto para este Waystone em vez de abrir o menu. */
    private volatile UUID route;

    public Waystone(UUID id, String name, BlockPos pos, UUID owner, String ownerName, long created) {
        this.id = id;
        this.name = name;
        this.pos = pos;
        this.owner = owner;
        this.ownerName = ownerName;
        this.created = created;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public BlockPos pos() {
        return pos;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public long created() {
        return created;
    }

    public Material icon() {
        return icon;
    }

    public void icon(Material icon) {
        this.icon = icon;
    }

    public boolean showName() {
        return showName;
    }

    public void showName(boolean showName) {
        this.showName = showName;
    }

    public WaystoneSkin skin() {
        return skin;
    }

    public void skin(WaystoneSkin skin) {
        this.skin = skin;
    }

    /** Quantas viagens já terminaram neste Waystone. */
    public int uses() {
        return uses;
    }

    public void uses(int uses) {
        this.uses = uses;
    }

    public UUID route() {
        return route;
    }

    public void route(UUID route) {
        this.route = route;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Waystone waystone && waystone.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
