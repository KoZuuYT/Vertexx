package dev.vertexx.grave;

import dev.vertexx.core.BlockPos;
import java.util.UUID;

/**
 * Túmulo de um jogador. Os itens ficam serializados em bytes e só viram
 * {@code ItemStack} de novo na hora da coleta.
 */
public record Grave(UUID id, UUID owner, String ownerName, BlockPos pos, long created,
                    int xp, byte[] items, UUID display) {
}
