package dev.vertexx.model;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Cabeças com skin própria <b>sem conta da Mojang</b>.
 *
 * <p>O jeito clássico (valor base64 de {@code textures.minecraft.net}) exige que a skin esteja hospedada nos
 * servidores da Mojang, e só dá para subir uma skin para lá autenticado numa conta de jogador; o cliente
 * recusa qualquer outro domínio. Desde o 1.21.9 o componente {@code profile} aceita um <i>skin patch</i> com
 * o {@code body} apontando para uma textura <b>do resource pack</b>: a cabeça usa
 * {@code assets/vertexx/textures/entity/player/wide/<nome>.png} (skin 64x64 comum). O pack gerado por
 * {@code tools/models/heads.py} coloca ali as skins de {@code models/heads/}.
 */
public final class CustomHeads {

    private CustomHeads() {
    }

    /** Nomes das skins incluídas no jar (arquivo {@code heads.txt}, gerado pelo build do pack). */
    public static List<String> names() {
        List<String> out = new ArrayList<>();
        try (InputStream in = CustomHeads.class.getClassLoader().getResourceAsStream("heads.txt")) {
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        out.add(line.strip());
                    }
                }
            }
        } catch (IOException ignored) {
            // sem lista: nenhuma cabeça
        }
        return out;
    }

    /** Cabeça de jogador que mostra a skin {@code vertexx:entity/player/wide/<nome>} do resource pack. */
    public static ItemStack of(String name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile()
                .skinPatch(patch -> patch.body(Key.key("vertexx", "entity/player/wide/" + name)))
                .build());
        return head;
    }
}
