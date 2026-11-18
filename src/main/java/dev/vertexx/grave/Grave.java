package dev.vertexx.grave;

import dev.vertexx.core.BlockPos;
import java.util.UUID;

/**
 * Túmulo de um jogador. Os itens ficam serializados em bytes e só viram
 * {@code ItemStack} de novo na hora da coleta.
 *
 * @param until fim da proteção em milissegundos (epoch) definido por um administrador;
 *              0 = padrão (criação + {@code graves.protection-minutes}); {@link Long#MAX_VALUE} = permanente
 */
public record Grave(UUID id, UUID owner, String ownerName, BlockPos pos, long created,
                    int xp, byte[] items, UUID display, long until) {

    public Grave withUntil(long value) {
        return new Grave(id, owner, ownerName, pos, created, xp, items, display, value);
    }

    public Grave withContents(int newXp, byte[] newItems) {
        return new Grave(id, owner, ownerName, pos, created, newXp, newItems, display, until);
    }
}
