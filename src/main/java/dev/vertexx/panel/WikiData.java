package dev.vertexx.panel;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Items;
import dev.vertexx.end.EndItem;
import dev.vertexx.fauna.FaunaKind;
import dev.vertexx.overworld.Curses;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Conteúdo da Wiki do painel: uma lista de verbetes (itens de menu) por categoria. */
final class WikiData {

    /** Categorias paginadas: id, título e ícone (Traits, Elites e Bosses têm páginas próprias, com números do config). */
    record Category(String id, String title, Material icon, String blurb) {
    }

    static final List<Category> CATEGORIES = List.of(
            new Category("waystones", "Waystones e Túmulos", Material.LODESTONE, "Viagem rápida e proteção de itens."),
            new Category("creatures", "Criaturas", Material.MOSS_BLOCK, "Todos os mobs customizados."),
            new Category("items", "Itens customizados", Material.AMETHYST_SHARD, "Materiais, mochilas, livros e utilitários."),
            new Category("endgear", "Enderita e Equipamentos", Material.NETHERITE_CHESTPLATE,
                    "Guia: materiais do End e como forjar."),
            new Category("biomes", "Biomas e climas", Material.GRASS_BLOCK, "Regiões novas e clima extremo."),
            new Category("structures", "Estruturas", Material.STONE_BRICKS, "Construções, salas de boss e bolsões."),
            new Category("systems", "Sistemas", Material.CRAFTING_TABLE, "Status, upgrades, mochilas e mais."));

    private WikiData() {
    }

    static String title(String id) {
        for (Category c : CATEGORIES) {
            if (c.id().equals(id)) {
                return c.title();
            }
        }
        return "Wiki";
    }

    private static ItemStack e(Material icon, String name, String... lore) {
        return Items.of(icon, name, List.of(lore));
    }

    static List<ItemStack> entries(PluginMain plugin, String id) {
        return switch (id) {
            case "waystones" -> waystones();
            case "creatures" -> creatures();
            case "items" -> items(plugin);
            case "endgear" -> endGear();
            case "biomes" -> biomes();
            case "structures" -> structures();
            default -> systems();
        };
    }

    // ── Waystones e Túmulos ─────────────────────────────────────

    private static List<ItemStack> waystones() {
        return List.of(
                e(Material.LODESTONE, "<white><bold>Criar um Waystone",
                        "<gray>Coloque um Lodestone e clique nele com", "<gray>um Fragmento de Ametista. Digite o nome",
                        "<gray>no chat. Sem ametista, ele é vanilla."),
                e(Material.ECHO_SHARD, "<light_purple><bold>Pedra de Warp",
                        "<gray>8 Fragmentos de Ametista + 1 Olho do Ender.", "<gray>Leva a qualquer Waystone que você conhece,",
                        "<gray>sem pérolas, mas com poucas cargas."),
                e(Material.COMPASS, "<white><bold>Descobrir",
                        "<gray>Clique direito em um Waystone para", "<gray>registrá-lo na sua lista."),
                e(Material.ENDER_PEARL, "<white><bold>Viajar",
                        "<gray>O menu lista os que você descobriu.", "<gray>1,5 s de concentração: mover-se cancela."),
                e(Material.AMETHYST_SHARD, "<white><bold>Pagamento",
                        "<gray>Cada jogador escolhe pagar com", "<gray>ametista, pérola ou resina (item do topo)."),
                e(Material.COMPARATOR, "<white><bold>Configurar (dono)",
                        "<gray>Ícone flutuante, nome visível/oculto e", "<gray>rota fixa: com rota, o clique direito viaja",
                        "<gray>direto (Shift + clique abre o menu)."),
                e(Material.OBSIDIAN, "<white><bold>Proteção",
                        "<gray>Só o dono (ou admin) quebra. Explosões", "<gray>e pistões não afetam."),
                e(Material.SKELETON_SKULL, "<white><bold>Túmulos",
                        "<gray>Ao morrer, itens e XP ficam numa cabeça", "<gray>sua. Por 30 min só você abre; depois,",
                        "<gray>qualquer um. Veja o local com /tumulo."),
                e(Material.TRIPWIRE_HOOK, "<white><bold>Chave de Túmulo",
                        "<gray>Entregue por administradores: abre um", "<gray>túmulo ainda protegido."),
                e(Material.CLOCK, "<yellow><bold>Relógio e contador de dias",
                        "<gray>Segurando um relógio, a barra de ação", "<gray>mostra o dia e a hora exata do mundo."),
                e(Material.CLOCK, "<yellow><bold>Relógio decorativo",
                        "<gray>Clique direito num bloco com um relógio:", "<gray>fixa no chão, teto ou parede. Clique nele",
                        "<gray>para configurar alarme diário, timer,", "<gray>som, pulso de redstone e horário flutuante.",
                        "<dark_gray>Agache + mão vazia recolhe."),
                e(Material.TRIPWIRE_HOOK, "<white><bold>Ganchos de exposição",
                        "<gray>Clique direito num Tripwire Hook com um", "<gray>item: ele fica pendurado. Mão vazia abre",
                        "<gray>o ajuste de tamanho, giro, inclinação e", "<gray>posição. Agache + mão vazia retira."));
    }

    // ── Criaturas ───────────────────────────────────────────────

    private static ItemStack c(FaunaKind kind, String... lore) {
        List<String> all = new ArrayList<>(List.of(lore));
        return Items.of(kind.icon(), kind.color() + "<bold>" + kind.display(), all);
    }

    private static List<ItemStack> creatures() {
        return List.of(
                c(FaunaKind.RASCAL, "<gray>Mob Vote 2022. Minas abandonadas, Y<0.", "<gray>Esconde-esconde: ache-o de perto.",
                        "<gray>Achado 3 vezes dá uma Picareta do Rascal.", "<dark_gray>Pacífico."),
                c(FaunaKind.TUFF_GOLEM, "<gray>Mob Vote 2022. Construa: tufo, tufo", "<gray>(ou lã, para manto) e tufo cinzelado.",
                        "<gray>Clique com um item para ele exibir.", "<dark_gray>Agache e bata para desmontar."),
                c(FaunaKind.MOOBLOOM, "<gray>Mob Vote 2020. Florestas floridas e campos.", "<gray>Deixa flores, serve as abelhas",
                        "<gray>e pode ser tosquiada."),
                c(FaunaKind.GLARE, "<gray>Mob Vote 2021. Cavernas exuberantes.", "<gray>Paira sobre o escuro; acenda uma luz.",
                        "<gray>Com bagas brilhantes, te acompanha."),
                c(FaunaKind.CRAB, "<gray>Mob Vote 2023. Manguezais.", "<gray>Sobe em raízes de mangue. Solta a",
                        "<gray>Garra de Caranguejo: alcance maior ao", "<gray>colocar e quebrar blocos."),
                c(FaunaKind.PENGUIN, "<gray>Mob Vote 2023. Praias rochosas.", "<gray>Desajeitado em terra, ótimo nadador.",
                        "<gray>Barcos com pinguim por perto ficam", "<gray>mais rápidos."),
                c(FaunaKind.GREAT_HUNGER, "<gray>Mob Vote 2017. Camuflado no chão.", "<gray>Engole comida jogada; separa itens",
                        "<gray>encantados em item limpo + livros.", "<gray>Variantes: terra, areia, madeira, pedra,", "<gray>ametista e grama."),
                c(FaunaKind.BARNACLE, "<gray>Mob Vote 2017. Oceanos profundos.", "<gray>Cria um redemoinho que puxa nadadores",
                        "<gray>e barcos e morde quem chega perto.", "<red>Perigoso."),
                c(FaunaKind.VOID_RAY, "<gray>Copas do Coro (End). Segue Fruta do Coro.", "<gray>Com sela ou guia na mão, você se",
                        "<gray>pendura e plana devagar.", "<dark_gray>Pacífica."),
                c(FaunaKind.END_STINGRAY, "<gray>Copas do Coro (End). Plana em círculos", "<gray>largos sobre as ilhas e segue Fruta do",
                        "<gray>Coro. Solta fragmentos de ametista.", "<dark_gray>Pacífica."),
                c(FaunaKind.END_CRAWLER, "<gray>Besouro de obsidiana do End. Neutro até", "<gray>você minerar ametista por perto.",
                        "<gray>Resiste a projéteis; se enrola em crítico.", "<gray>Solta Quitina do Vazio."),
                c(FaunaKind.SHULKER_SENTINEL, "<gray>Guardião raro das ruínas do End.", "<gray>Rajadas cinéticas (pouco dano, muito",
                        "<gray>recuo) e saltos. Solta Núcleo de Levitação.", "<red>Hostil."),
                c(FaunaKind.AMETHYST_GOLEM, "<gray>Construa: ametista, calcita e abóbora", "<gray>entalhada. Ouve sons e gira para o",
                        "<gray>sculk sensor calibrado. Com Fragmento de", "<gray>Ametista, emite pulso de redstone numa nota."),
                c(FaunaKind.MEERKAT, "<gray>Desertos. Para, cava na areia e revela", "<gray>itens enterrados ou areia suspeita."),
                c(FaunaKind.OSTRICH, "<gray>Savanas. Montaria veloz (como cavalo).", "<gray>Deixa pegadas e bota Ovos de Avestruz",
                        "<gray>que chocam em fardos de feno."),
                c(FaunaKind.VULTURE, "<gray>Circula alto sobre itens largados no chão:", "<gray>mostra onde há loot ou uma morte."),
                c(FaunaKind.DEEPSLATE_ANKYLO, "<gray>Ressuscitado na Incubadora Geotérmica.", "<gray>Dê Glow Berries para ele te seguir; de",
                        "<gray>mão vazia, mande-o quebrar pedra 3x3x3", "<gray>(recarga de 15 s)."),
                c(FaunaKind.CAVE_GLIDER, "<gray>Ressuscitado na Incubadora Geotérmica.", "<gray>Mão vazia: monte e plane pelas cavernas.",
                        "<gray>Agachado: grito que revela minérios num", "<gray>raio de 24 blocos por 8 s (recarga de 20 s)."));
    }

    // ── Itens customizados ──────────────────────────────────────

    private static List<ItemStack> items(PluginMain plugin) {
        List<ItemStack> list = new ArrayList<>();
        for (EndItem item : EndItem.values()) {
            list.add(item.tier() > 0 ? plugin.backpacks().create(item.tier()) : item.create(plugin));
        }
        for (Curses.Curse curse : Curses.Curse.values()) {
            list.add(plugin.curses().book(curse));
        }
        list.add(dev.vertexx.boss.ParchedGreatsword.create(plugin));
        list.add(dev.vertexx.boss.SovereignStaff.create(plugin));
        list.add(plugin.gloomGear().sonicClaws());
        list.add(plugin.climates().goggles());
        list.add(e(Material.TRIPWIRE_HOOK, "<white><bold>Chave de Túmulo", "<gray>Abre um túmulo protegido.", "<dark_gray>Só administradores entregam."));
        list.add(e(Material.IRON_PICKAXE, "<aqua><bold>Picareta do Rascal", "<gray>Recompensa por achar o Rascal 3 vezes."));
        list.add(e(Material.BONE, "<white><bold>Garra de Caranguejo", "<gray>Na mão, aumenta o alcance para", "<gray>colocar e quebrar blocos."));
        return list;
    }

    // ── Enderita e equipamentos (guia) ──────────────────────────

    private static List<ItemStack> endGear() {
        return List.of(
                e(Material.END_STONE, "<light_purple><bold>1. Chegando aos materiais",
                        "<gray>Tudo vem das <white>ilhas externas do End</white> (além de", "<gray>1000 blocos do centro). O progresso é:",
                        "<gray>Enderita → temperar armadura → criaturas do", "<gray>End → Void-Scarab → equipamentos finais."),
                e(Material.AMETHYST_CLUSTER, "<light_purple><bold>2. Minério de Enderita",
                        "<gray>Onde: <white>Abismos de Enderita</white> (ardósia escura e", "<gray>ametista, ilhas externas do End).",
                        "<gray>Como: <white>picareta de diamante</white> ou melhor.", "<gray>Dropa: <white>Fragmento de Enderita</white>.",
                        "<gray>9 fragmentos = 1 Bloco de Enderita (mesa de", "<gray>criação) e o bloco volta a 9 fragmentos."),
                e(Material.AMETHYST_SHARD, "<light_purple><bold>3. Temperar armadura (Fragmento)",
                        "<gray>Bigorna: <white>peça de armadura + 1 Fragmento</white>", "<gray>(custa 3 níveis). Também vale na mesa de",
                        "<gray>ferraria (fragmento no slot do material).",
                        "<gray>Efeito por peça: <white>−25% de dano do Vazio</white>.", "<gray>As botas temperadas anulam a levitação das",
                        "<gray>bordas dos Abismos. Cada peça só uma vez."),
                e(Material.PHANTOM_MEMBRANE, "<aqua><bold>4. Membrana Etérea",
                        "<gray>Dropa da <white>Arraia do Vazio</white> (Copas do Coro).", "<gray>Bigorna: Elytra + Membrana repara a Elytra.",
                        "<gray>Também entra na poção de Queda Lenta e é", "<gray>o ingrediente dos Élitros de Asa do Vazio."),
                e(Material.OBSIDIAN, "<dark_purple><bold>5. Quitina do Vazio",
                        "<gray>Dropa do <white>Rastejador do Fim</white>.", "<gray>• Escudo + Quitina na bigorna: Inquebrável III",
                        "<gray>  e repara 25% da durabilidade.", "<gray>• Mochila do Vazio (sem forma): 1 Papel,",
                        "<gray>  2 Cascos de Shulker, 2 Quitinas e 1 Pérola", "<gray>  do Fim."),
                e(Material.SHULKER_SHELL, "<yellow><bold>6. Núcleo de Levitação",
                        "<gray>Dropa da <white>Sentinela de Shulker</white> (70%).", "<gray>Cervejaria: Poção Estranha + Núcleo =",
                        "<gray>Poção de Levitação (45 s)."),
                e(Material.CRYING_OBSIDIAN, "<dark_purple><bold>7. Void-Scarab: o chefe",
                        "<gray>Ache o <white>Pináculo de Quitina</white> (raro, ilhas grandes", "<gray>dos Abismos, bem longe do centro do End).",
                        "<gray>Entre na câmara e ele acorda. Os Endermans", "<gray>não entram na torre.",
                        "<gray>Dropa: <white>Coração de Cristal</white>, <white>4–6 Placas de", "<white>Quitina Reforçada</white> e <white>Mandíbula</white>."),
                e(Material.NETHERITE_SWORD, "<white><bold>8. Segadora de Mandíbula",
                        "<gray>Mesa de ferraria (netherite):", "<gray>• Modelo: <white>2 Placas de Quitina Reforçada</white>",
                        "<gray>• Base: <white>Espada de Netherite</white>", "<gray>• Material: <white>Mandíbula de Escaravelho</white>",
                        "<gray>+2,5 de dano; clique direito: avanço com", "<gray>golpe (10,5 de dano, ignora ~40% da armadura;", "<gray>recarga de 6 s). Não fere jogadores."),
                e(Material.ELYTRA, "<aqua><bold>9. Élitros de Asa do Vazio",
                        "<gray>Mesa de ferraria:", "<gray>• Modelo: <white>2 Placas de Quitina Reforçada</white>",
                        "<gray>• Base: <white>Elytra</white>", "<gray>• Material: <white>Membrana Etérea</white>",
                        "<gray>+6 de armadura e o dobro da durabilidade."),
                e(Material.NETHERITE_CHESTPLATE, "<dark_purple><bold>10. Peitoral de Quitina",
                        "<gray>Mesa de ferraria:", "<gray>• Modelo: <white>2 Placas de Quitina Reforçada</white>",
                        "<gray>• Base: <white>Peitoral de Netherite</white>", "<gray>• Material: <white>Fragmento de Enderita</white>",
                        "<gray>−35% de dano de projétil e explosão; devolve", "<gray>3 de dano a mobs que te atacam de perto."),
                e(Material.HEART_OF_THE_SEA, "<light_purple><bold>11. Coração de Cristal do Vazio",
                        "<gray>Com ele no inventário, cair no Vazio (Y < −10)", "<gray>no End te devolve ao ponto seguro, com Queda",
                        "<gray>Lenta. Tem 4 cargas.", "<gray>Clique direito: fixa uma âncora na ilha.",
                        "<gray>Recarga: Coração + Pérolas do Fim na bigorna."));
    }

    // ── Biomas e climas ─────────────────────────────────────────

    private static List<ItemStack> biomes() {
        return List.of(
                e(Material.CHORUS_FLOWER, "<light_purple><bold>Floresta de Dossel do Coro",
                        "<gray>Ilhas externas do End (além de 1000 blocos).", "<gray>Nilium (musgo do Vazio), árvores altas com",
                        "<gray>lanternas em correntes e fungos que dão", "<gray>Queda Lenta. Lar da Arraia do Vazio."),
                e(Material.AMETHYST_CLUSTER, "<dark_purple><bold>Abismos de Enderita",
                        "<gray>Ilhas externas do End. Ardósia escura,", "<gray>cristais de ametista, fendas e geodos de",
                        "<gray>obsidiana. Minério de Enderita (bloco próprio,", "<gray>picareta de diamante). Levitação nas bordas.", "<red>Perigoso."),
                e(Material.SAND, "<gold><bold>Deserto: oásis",
                        "<gray>Oásis com palmeiras (Coco) e água. Casa", "<gray>do Suricato."),
                e(Material.ACACIA_LOG, "<yellow><bold>Savana: baobás e cupinzeiros",
                        "<gray>Baobás enormes e cupinzeiros que rendem", "<gray>Larvas de Cupim (isca). Avestruzes e abutres."),
                e(Material.RED_SAND, "<red><bold>Badlands: cactos e tumbleweeds",
                        "<gray>Cactos-barril e tumbleweeds soprados", "<gray>pelo vento."),
                e(Material.DEEPSLATE, "<dark_gray><bold>Cavernas profundas (Y<0)",
                        "<gray>Bolsões de cascalho suspeito: escove para", "<gray>achar Âmbar e Fósseis Calcificados."),
                e(Material.SANDSTONE, "<gold><bold>Clima: Tempestade de areia",
                        "<gray>Deserto e badlands. Escuridão e vento", "<gray>empurrando. Use Óculos de Explorador ou",
                        "<gray>abrigue-se (agachar reduz o vento)."),
                e(Material.SNOW_BLOCK, "<aqua><bold>Clima: Nevasca",
                        "<gray>Planícies nevadas, espinhos de gelo, bosque.", "<gray>Congela e acumula neve. Proteja-se com",
                        "<gray>couro ou fique perto de uma tocha."),
                e(Material.PACKED_ICE, "<white><bold>Clima: Ventania de altitude",
                        "<gray>Picos (Y>150). Rajadas empurram você.", "<gray>Botas pesadas reduzem o efeito."),
                e(Material.NETHER_BRICKS, "<gold><bold>Nether: Câmara do Wildfire",
                        "<gray>Ala anexa às fortalezas. Veja Bosses."),
                e(Material.BLUE_ICE, "<aqua><bold>Terras geladas: Castelo de Gelo",
                        "<gray>Fortaleza do Iceologer em biomas gelados."),
                e(Material.CRYING_OBSIDIAN, "<dark_purple><bold>Abismos: Pináculo de Quitina",
                        "<gray>Torre-colmeia rara nas ilhas grandes dos", "<gray>Abismos de Enderita, longe do centro do End.",
                        "<gray>Veja Bosses."),
                e(Material.CHISELED_SANDSTONE, "<gold><bold>Deserto: Cidadela Soterrada",
                        "<gray>Complexo subterrâneo raro sob as dunas;", "<gray>guarda o Parched Titan. Veja Bosses."),
                e(Material.MOSS_BLOCK, "<green><bold>Lush Caves: Gruta dos Esporos",
                        "<gray>Complexo raro enterrado ao lado das lush", "<gray>caves (Y -10 a -45); guarda o Root", "<gray>Sovereign. Veja Bosses."),
                e(Material.SCULK_CATALYST, "<dark_aqua><bold>Deep Dark: Covil Ressonante",
                        "<gray>Complexo raro no Deep Dark (Y -35 a -52),", "<gray>longe de Cidades Antigas; guarda o Gloom", "<gray>Stalker. Veja Bosses."));
    }

    // ── Estruturas ──────────────────────────────────────────────

    private static List<ItemStack> structures() {
        return List.of(
                e(Material.NETHER_BRICKS, "<gold><bold>Câmara do Wildfire",
                        "<gray>Ala de tijolos do Nether ligada às", "<gray>fortalezas por um corredor. Sala do boss",
                        "<gray>selada durante a luta."),
                e(Material.PACKED_ICE, "<aqua><bold>Castelo de Gelo",
                        "<gray>Muralha, quatro torres e torreão com o", "<gray>salão do Iceologer, em terras geladas."),
                e(Material.CRYING_OBSIDIAN, "<dark_purple><bold>Pináculo de Quitina",
                        "<gray>Torre cônica de 52 blocos de ardósia e", "<gray>obsidiana chorosa, com costelas de End Stone,",
                        "<gray>janelas âmbar/verdes e quatro pontes sobre", "<gray>o abismo. Dentro: câmara de 48 blocos de",
                        "<gray>diâmetro e poças de ácido (Poison I sem botas", "<gray>encantadas). O centro é livre.",
                        "<gray>Entre na câmara para acordar o chefe."),
                e(Material.CHISELED_SANDSTONE, "<gold><bold>Cidadela Soterrada",
                        "<gray>Rara, em desertos: entrada monumental de", "<gray>arenito e escadaria até o subsolo.",
                        "<gray>1) Átrio dos Ventos Cortantes: 24 blocos com", "<gray>areia movediça, fossos de espinhos, dardos e", "<gray>rajadas de areia.",
                        "<gray>2) Salão dos Guardiões: 6 Husks blindados e", "<gray>2 Parched nas varandas; o portão só abre", "<gray>quando todos caem.",
                        "<gray>3) Santuário da Fornalha Seca: arena de 34", "<gray>blocos, 6 pilares quebráveis e clarabóias.", "<gray>Veja Bosses."),
                e(Material.MOSS_BLOCK, "<green><bold>Gruta dos Esporos Ancestrais",
                        "<gray>Rara, ao lado de lush caves, no subsolo:",
                        "<gray>1) Labirinto de Vinhas: cipós (-45% de", "<gray>velocidade e veneno leve) e tripwire com", "<gray>dispensers de dardos de lentidão.",
                        "<gray>2) Salão dos Protetores: 4 Sentinelas de", "<gray>Vinhas e teias de musgo; o portão só abre", "<gray>quando todas caem.",
                        "<gray>3) Câmara dos Esporos: 36 blocos, lagos", "<gray>rasos, pilares de raízes e teto de Spore", "<gray>Blossoms.", "<gray>Veja Bosses."),
                e(Material.SCULK_CATALYST, "<dark_aqua><bold>Covil Ressonante",
                        "<gray>Raro, no Deep Dark, sem nenhuma luz:",
                        "<gray>1) Galeria dos Sensores: 28 blocos, pontes", "<gray>sobre fossos. Correr sem agachar dispara", "<gray>dardos de Darkness e recua o piso.",
                        "<gray>2) Câmara de Incubação: destrua os 3", "<gray>Catalisadores e vença 5 Rastejadores e 2", "<gray>Sentinelas para abrir o portão.",
                        "<gray>3) Abismo do Coração de Alma: 38 blocos,", "<gray>pilares de Reinforced Deepslate, plataformas.", "<gray>Veja Bosses."),
                e(Material.WATER_BUCKET, "<aqua><bold>Oásis",
                        "<gray>Lago com palmeiras no deserto."),
                e(Material.JUNGLE_LOG, "<yellow><bold>Baobá",
                        "<gray>Árvore gigante da savana."),
                e(Material.DIRT, "<gold><bold>Cupinzeiro",
                        "<gray>Monte da savana; dá Larvas de Cupim."),
                e(Material.SUSPICIOUS_GRAVEL, "<white><bold>Bolsão de cascalho suspeito",
                        "<gray>Nas cavernas de ardósia: escove com um", "<gray>pincel."),
                e(Material.CAULDRON, "<gold><bold>Incubadora Geotérmica",
                        "<gray>Caldeirão com lava cercado por 4 blocos", "<gray>de magma e um pedestal de cobre ou ametista",
                        "<gray>por cima. 4 Âmbares + 1 Fóssil: em 3 min", "<gray>ressuscita um Anquilossauro ou Pterodáctilo."),
                e(Material.TUFF, "<gray><bold>Golem de Tufo (construção)",
                        "<gray>Tufo, tufo e tufo cinzelado."),
                e(Material.AMETHYST_BLOCK, "<light_purple><bold>Golem de Ametista (construção)",
                        "<gray>Bloco de ametista, calcita e abóbora entalhada."),
                e(Material.LODESTONE, "<white><bold>Waystone",
                        "<gray>Lodestone ativado com ametista, com modelo 3D animado."));
    }

    // ── Sistemas ────────────────────────────────────────────────

    private static List<ItemStack> systems() {
        return List.of(
                e(Material.NETHER_STAR, "<white><bold>Status e habilidades",
                        "<gray>10 status sobem com a atividade:", "<gray>Mineração, Escavação, Lenhador, Agricultura,",
                        "<gray>Luta, Arquearia, Resistência, Acrobacia,", "<gray>Pesca e Exploração. Árvore de 16 habilidades.",
                        "<dark_gray>/vstats  •  F usa, Shift+F troca."),
                e(Material.ZOMBIE_HEAD, "<white><bold>Mobs nivelados",
                        "<gray>O nível cresce com a distância do spawn,", "<gray>dimensão, profundidade e estruturas.",
                        "<dark_gray>Veja Traits e Elites na Wiki."),
                e(Material.FURNACE, "<white><bold>Upgrades de fornalhas e baús",
                        "<gray>Agache e clique com a mão vazia. 6 níveis:", "<gray>fornalhas de 1,3x a 5x; baús de 45 a 360",
                        "<gray>espaços (em páginas)."),
                e(Material.CHEST, "<white><bold>Mochilas",
                        "<gray>Couro (9), Reforçada (18) e do Vazio (27,", "<gray>coleta magnética). /mochila abre."),
                e(Material.ENCHANTED_BOOK, "<light_purple><bold>Maldições com benefício",
                        "<gray>Livros raros em baús de estruturas e", "<gray>bibliotecários: bônus forte + desvantagem."),
                e(Material.END_PORTAL_FRAME, "<dark_purple><bold>Portal do End trancado",
                        "<gray>Os 12 encaixes não aceitam o Olho do Ender", "<gray>sozinho. Encaixe 11 Núcleos de boss (um de",
                        "<gray>cada, do Underworld) e só então o Olho.", "<red>Hoje existem 2 de 11: o portal segue selado."),
                e(Material.AMETHYST_BLOCK, "<light_purple><bold>Blocos customizados",
                        "<gray>Minério e Bloco de Enderita, com textura", "<gray>própria. Picareta de diamante para dropar;",
                        "<gray>9 fragmentos fazem 1 bloco."),
                e(Material.ANVIL, "<white><bold>Enderita e Quitina",
                        "<gray>Fragmento tempera armaduras contra o Vazio;", "<gray>Membrana repara Elytras; Quitina reforça",
                        "<gray>escudos."),
                e(Material.NETHERITE_CHESTPLATE, "<dark_purple><bold>Equipamentos do Void-Scarab",
                        "<gray>Mesa de ferraria, com 2 Placas de Quitina no", "<gray>slot do modelo (guia em Enderita e Equip.):",
                        "<gray>• Netherite + Mandíbula = <white>Segadora</white> (avanço)",
                        "<gray>• Elytra + Membrana = <white>Élitros de Asa do Vazio</white>",
                        "<gray>• Peitoral + Fragmento = <white>Peitoral de Quitina</white>",
                        "<gray>Coração de Cristal recarrega com Pérolas", "<gray>do Fim na bigorna."),
                e(Material.AXOLOTL_BUCKET, "<white><bold>Resource pack",
                        "<gray>Opcional. Ícones, menus ilustrados e", "<gray>modelos 3D. /pacote aceita ou recusa."),
                e(Material.BOOK, "<white><bold>Comandos úteis",
                        "<gray>/tumulo  /pacote  /vstats  /mochila", "<gray>/vertexx livro (Guia)"));
    }
}
