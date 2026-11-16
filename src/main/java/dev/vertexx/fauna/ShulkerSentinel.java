package dev.vertexx.fauna;

import org.bukkit.entity.Mob;

/**
 * Sentinela de Shulker: guardião hostil raro das ruínas e torres do End. Base Breeze com a IA original: as rajadas
 * cinéticas (baixo dano, muito recuo) e os saltos são do próprio mob; o plugin só desenha o modelo.
 */
final class ShulkerSentinel extends Critter {

    ShulkerSentinel(FaunaManager manager, Mob mob) {
        super(manager, mob);
    }

    @Override
    protected boolean wild() {
        return false;
    }

    @Override
    FaunaKind kind() {
        return FaunaKind.SHULKER_SENTINEL;
    }

    @Override
    protected void think() {
    }

    /** Disparou uma rajada. */
    void onShoot() {
        model.restart("shoot");
    }

    @Override
    protected void pose() {
        if ("shoot".equals(model.playing()) && !model.finished()) {
            return;
        }
        model.play("idle");
        model.speed(1f);
    }
}
