package dev.vertexx.intro;

import dev.vertexx.PluginMain;
import dev.vertexx.core.Items;
import dev.vertexx.core.Menu;
import dev.vertexx.core.Msg;
import dev.vertexx.core.Ui;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Recado de boas-vindas em páginas: boas-vindas, pacote de recursos, status e skills, mundo, bosses e End, regras
 * do SMP e a confirmação final. Na última página o jogador confirma que leu tudo e quer participar.
 * Com {@code preview} ligado (já confirmado) o menu é só de leitura.
 */
final class IntroMenu extends Menu {

    private static final String TITLE = "<gradient:#7f5af0:#2cb67d><bold>Bem-vindo ao SMP</bold></gradient>";
    private static final int PAGES = 7;

    private final IntroManager manager;
    private final int page;
    private final boolean preview;

    IntroMenu(PluginMain plugin, IntroManager manager, Player player, int page, boolean preview) {
        super(plugin, player, Ui.Theme.VERTEXX, TITLE);
        this.manager = manager;
        this.page = Math.max(0, Math.min(page, PAGES - 1));
        this.preview = preview;
    }

    private void card(int slot, Material material, String name, String... lore) {
        List<String> lines = new ArrayList<>();
        for (String line : lore) {
            lines.addAll(Arrays.asList(line.split("\\|")));
        }
        set(slot, Items.of(material, name, lines));
    }

    private void header(Material material, String name, String subtitle) {
        set(HEADER_SLOT, Items.of(material, name, List.of(subtitle, "", "<dark_gray>Página " + (page + 1) + " de " + PAGES), false));
    }

    @Override
    protected void render() {
        frame(page > 0 ? () -> manager.show(player, page - 1) : null);
        if (page < PAGES - 1) {
            set(NEXT_SLOT, Items.of(Material.ARROW, "<yellow>Continuar ▶", List.of("<gray>Próxima página do recado.")),
                    c -> manager.show(player, page + 1));
        }
        switch (page) {
            case 0 -> welcome();
            case 1 -> pack();
            case 2 -> progression();
            case 3 -> world();
            case 4 -> bosses();
            case 5 -> rules();
            default -> confirm();
        }
    }

    private void welcome() {
        header(Material.NETHER_STAR, "<gradient:#7f5af0:#2cb67d><bold>Bem-vindo, " + Msg.esc(player.getName()) + "!</bold></gradient>",
                "<gray>Um SMP survival com muito a descobrir.");
        card(20, Material.GRASS_BLOCK, "<green><bold>O que é este SMP",
                "<gray>Um survival cooperativo, sem PvP, com|<gray>progressão própria, criaturas novas, bosses|<gray>e uma dimensão do End inteira por explorar.",
                "", "<gray>Tudo vem do plugin <white>Vertexx</white>.");
        card(22, Material.BOOK, "<aqua><bold>Seu Guia do Vertexx",
                "<gray>Você recebe um livro: botão direito abre|<gray>o painel com a <white>wiki</white> (criaturas, itens,|<gray>estruturas, sistemas) e seus status.",
                "", "<gray>Perdeu? <white>/vertexx livro");
        card(24, Material.WRITABLE_BOOK, "<gold><bold>Neste recado",
                "<gray>1. O pacote de recursos|<gray>2. Status e habilidades|<gray>3. Mundo e sistemas|<gray>4. Bosses e o End|<gray>5. As regras do SMP",
                "", "<gray>No fim, você confirma e entra no servidor.");
    }

    private void pack() {
        header(Material.PAINTING, "<light_purple><bold>Pacote de recursos", "<gray>Necessário para ver tudo direito.");
        boolean loaded = plugin.resourcePack().isLoaded(player);
        boolean bedrock = plugin.bedrock().is(player);
        card(20, Material.ITEM_FRAME, "<white><bold>Por que baixar",
                "<gray>O pacote traz os ícones, menus, sons,|<gray>itens e modelos das criaturas do Vertexx.|<gray>Sem ele esses elementos aparecem como|<gray>peças vanilla ou ficam sem visual.");
        if (bedrock) {
            card(22, Material.GLOW_ITEM_FRAME, "<yellow><bold>Você joga no Bedrock",
                    "<gray>O pacote Java não é usado no Bedrock:|<gray>menus e ícones usam o visual padrão e as|<gray>criaturas aparecem como versões vanilla.");
        } else if (loaded) {
            card(22, Material.LIME_DYE, "<green><bold>✔ Pacote carregado", "<gray>Tudo certo: você vê o visual completo.");
        } else {
            set(22, Items.of(Material.LIME_CONCRETE, "<green><bold>✔ Baixar o pacote agora",
                    List.of("<gray>Equivale a <white>/pacote aceitar</white>.", "<gray>O menu se atualiza quando terminar.", "",
                            "<yellow>▶ Clique para baixar")), c -> player.performCommand("pacote aceitar"));
        }
        card(24, Material.COMPARATOR, "<gray><bold>Dica",
                "<gray>Pode recusar com <white>/pacote recusar</white> e mudar|<gray>de ideia a qualquer hora com|<white>/pacote aceitar</white><gray>. O jogo funciona nos dois casos.");
    }

    private void progression() {
        header(Material.EXPERIENCE_BOTTLE, "<green><bold>Status e habilidades", "<gray>Você evolui fazendo o que faz.");
        card(20, Material.DIAMOND_PICKAXE, "<aqua><bold>10 status",
                "<gray>Mineração, Escavação, Lenhador, Agricultura,|<gray>Luta, Arquearia, Resistência, Acrobacia,|<gray>Pesca e Exploração. Cada ação rende XP|<gray>no status correspondente.",
                "", "<gray>Veja com <white>/vstats</white> ou no Guia.");
        card(22, Material.ENCHANTED_BOOK, "<gold><bold>Árvore de habilidades",
                "<gray>Subir de nível dá pontos para gastar em|<gray>habilidades: <white>Pico Largo, Veeiro, Investida,|<white>Golpe Atordoante, Vitalidade, Replantio</white>...|<gray>Cada uma melhora seu estilo de jogo.");
        card(24, Material.ZOMBIE_HEAD, "<red><bold>Mobs nivelados",
                "<gray>Mobs hostis ficam mais fortes quanto mais|<gray>longe do spawn, no Nether, no End e em|<gray>estruturas. Existem <light_purple>elites</light_purple> com traits|<gray>e recompensas melhores.");
        card(38, Material.BLAST_FURNACE, "<gold><bold>Upgrades",
                "<gray>Melhore fornalhas e baús para trabalhar|<gray>mais rápido e guardar mais.");
        card(40, Material.SHULKER_BOX, "<light_purple><bold>Mochilas",
                "<gray>Mochilas em vários níveis para carregar|<gray>mais itens. Use <white>/mochila</white>.");
    }

    private void world() {
        header(Material.LODESTONE, "<gold><bold>Mundo e sistemas", "<gray>Viaje, explore e não perca nada.");
        card(19, Material.LODESTONE, "<light_purple><bold>Waystones",
                "<gray>Clique num Lodestone com um Fragmento de|<gray>Ametista para criar um ponto de viagem rápida.");
        card(21, Material.SKELETON_SKULL, "<gray><bold>Túmulos",
                "<gray>Ao morrer, seus itens ficam num túmulo|<gray>protegido por um tempo. Nada se perde.");
        card(23, Material.MOSS_BLOCK, "<green><bold>Criaturas novas",
                "<gray>Pinguins, avestruzes, caranguejos, golens|<gray>e outras criaturas aparecem em biomas|<gray>e estruturas do mundo.");
        card(25, Material.SAND, "<yellow><bold>Biomas e climas",
                "<gray>Regiões áridas, tempestades de areia,|<gray>nevascas e ventanias extremas pedem|<gray>preparo. Veja a wiki.");
        card(37, Material.BRUSH, "<gold><bold>Arqueologia",
                "<gray>Fósseis e relíquias escondidos no|<gray>cascalho suspeito esperam por você.");
        card(39, Material.CLOCK, "<white><bold>Relógios e ganchos",
                "<gray>Decorações funcionais para sua base,|<gray>com menus próprios.");
    }

    private void bosses() {
        header(Material.DRAGON_HEAD, "<dark_purple><bold>Bosses e o End", "<gray>Desafios para quem quer mais.");
        card(20, Material.BLAZE_ROD, "<gold><bold>6 bosses",
                "<gray>Wildfire, Iceologer, Void-Scarab,|<gray>Parched Titan, Root Sovereign e Gloom Stalker.|<gray>Cada um vive em uma sala própria,|<gray>com recompensa garantida.");
        card(22, Material.END_PORTAL_FRAME, "<dark_purple><bold>Portal do End trancado",
                "<gray>O portal só abre com os <white>Núcleos</white> dos|<gray>bosses, além do Olho de Ender. Reúna-os|<gray>com os outros jogadores.");
        card(24, Material.NETHERITE_CHESTPLATE, "<dark_gray><bold>Equipamentos de Enderita",
                "<gray>Os materiais e armas dos bosses viram|<gray>conjuntos exclusivos: veja receitas e|<gray>efeitos na wiki do Guia.");
        card(40, Material.TOTEM_OF_UNDYING, "<red><bold>Jogue em equipe",
                "<gray>Os bosses são difíceis. Chame a|<gray>galera e vá preparado.");
    }

    private void rules() {
        header(Material.OAK_SIGN, "<red><bold>Regras do SMP", "<gray>Leia com atenção: valem para todos.");
        List<String> rules = IntroManager.rules();
        for (int i = 0; i < rules.size(); i++) {
            String[] parts = rules.get(i).split("\\|");
            List<String> lore = new ArrayList<>(Arrays.asList(parts).subList(1, parts.length));
            set(new int[]{19, 21, 23, 25}[i], Items.of(new Material[]{Material.CAMPFIRE, Material.CHEST, Material.IRON_SWORD,
                    Material.TNT}[i], parts[0], lore));
        }
        card(40, Material.PAPER, "<white><bold>Quebrou uma regra?",
                "<gray>A administração pode advertir, restaurar|<gray>o que foi estragado e remover quem insistir.");
    }

    private void confirm() {
        header(Material.WRITTEN_BOOK, "<green><bold>Confirmação", "<gray>Último passo antes de jogar.");
        card(22, Material.BOOK, "<white><bold>Resumo das regras",
                "<gold>• <gray>Conviver com os jogadores próximos|<red>• <gray>Não roubar|<dark_red>• <gray>Não matar|<yellow>• <gray>Sem griefing");
        if (preview) {
            card(31, Material.LIME_DYE, "<green><bold>Você já confirmou",
                    "<gray>Este é só o seu recado para releitura.");
            return;
        }
        set(20, Items.of(Material.LIME_CONCRETE, "<green><bold>✔ Li tudo e quero participar do SMP",
                List.of("<gray>Confirmo que li o recado e as regras", "<gray>e que quero jogar neste servidor.", "",
                        "<yellow>▶ Clique para confirmar")), c -> manager.confirm(player));
        set(24, Items.of(Material.RED_CONCRETE, "<red><bold>✖ Não quero participar",
                List.of("<gray>Você sai do servidor. O recado", "<gray>volta quando entrar de novo.", "",
                        "<yellow>▶ Clique para sair")), c -> manager.decline(player));
    }
}
