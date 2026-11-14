package dev.vertexx.waystone;

import dev.vertexx.core.BlockPos;
import java.util.UUID;

/** Um Lodestone registrado como ponto de viagem rápida. */
public record Waystone(UUID id, String name, BlockPos pos, UUID owner, String ownerName, long created) {
}
