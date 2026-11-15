# Vertexx

Plugin customizado para o SMP pacífico e semi-vanilla **Vertexx** — Paper **26.3**, Java 25.
Leve, sem dependências, com textos em MiniMessage e feedback de som e partículas em cada interação.

| Módulo | O que faz |
| --- | --- |
| **Waystones** | Lodestones renomeados viram pontos de viagem rápida |
| **Túmulos** | Itens e XP guardados no local da morte, protegidos por tempo |
| **Resource pack** | Convite opcional, nunca forçado |
| **Auto-update** | O servidor baixa sozinho a release mais recente deste repositório |

## Instalação no servidor

1. Baixe o `Vertexx-x.y.z.jar` da [release mais recente](https://github.com/KoZuuYT/Vertexx/releases/latest)
   e coloque em `plugins/` — ou rode o script de sincronização:
   ```powershell
   .\scripts\sync-latest.ps1 -PluginsDir "C:\servidor\plugins"
   ```
   ```bash
   ./scripts/sync-latest.sh /caminho/do/servidor/plugins
   ```
2. Inicie o servidor (Paper 26.3, Java 25).

### Sincronização automática

Depois de instalado, o plugin se atualiza sozinho:

- A cada 30 minutos (e 15 segundos após ligar) ele consulta a release mais recente no GitHub.
- Se for mais nova, baixa o jar para `plugins/update/`, confere o SHA-256 publicado pelo GitHub e avisa os administradores.
- O Paper troca o jar **no próximo reinício** — não há troca a quente, para não corromper dados.
- `/vertexx atualizar` força a checagem; `/vertexx` mostra a versão em execução.
- Com `updater.restart-when-empty: true`, o servidor se desliga quando estiver vazio e houver atualização
  baixada. Só ative se o seu painel ou script reinicia o servidor automaticamente.

## Como se joga

### Waystones

- **Criar:** renomeie um Lodestone na bigorna e coloque-o no chão. O nome do item vira o nome do Waystone.
  Lodestones sem nome continuam vanilla.
- **Descobrir:** clique com o botão direito em um Waystone para registrá-lo.
- **Modelo 3D:** o Waystone ganha um modelo de display entities (base de obsidiana, pilar com runas
  verdes e um ícone flutuante entre dois arcos). Ao ser colocado, ele se **monta com animação**.
  As entidades só existem com jogador por perto e só são enviadas a quem está perto, olhando na direção
  do modelo e sem uma parede no caminho (`waystones.model` no config).
- **Viajar:** o menu lista os Waystones que você já descobriu, tem 1,5 s de concentração — cancela se você
  se mover.
- **Pagamento:** cada jogador escolhe no menu se paga com **ametista, pérola ou resina** (clique no item
  do topo). As quantidades ficam em `waystones.cost.options`.
- **Configurar (dono):** botão *Configurar* no menu — ícone flutuante (18 opções), nome visível ou oculto
  e **rota fixa**: com uma rota definida, o clique direito viaja direto, sem menu (Shift + clique abre o menu).
- **Remover:** só o dono (ou um administrador) consegue quebrar. Explosões e pistões não afetam.

### Painel e Guia

- **Guia do Vertexx:** um livro entregue no primeiro login (e com `/vertexx livro`). Clique direito abre o
  painel com Waystones, túmulos e comandos.
- **Wiki** (na tela inicial do painel, para todos): todos os traits dos mobs, os mobs Elite (chances, drops,
  níveis) e os bosses.
- **Administradores** (`/vertexx` ou o próprio livro) veem também a aba *Administração*: status, modelos
  renderizados, atualização pelo GitHub, alternadores de configuração, a lista de todos os Waystones e os
  comandos de teste em forma de menu: **Criar mobs** (tipo, nível, elite, traits), **Conceder XP** e
  **Bosses** (criar teste, construir sala, despertar, reiniciar).
  Quem não tem OP não vê nada disso, nem pelo livro.

### Túmulos

Ao morrer, seus itens e todo o seu XP ficam em uma cabeça sua no local da morte. Clique ou quebre para
recuperar. Por 30 minutos só você abre; depois disso, qualquer um. Uma **Chave de Túmulo**
(entregue por administradores) abre um túmulo ainda protegido.

### Mobs nivelados, traits e elites

Mobs hostis nascem com nível conforme a distância do spawn do mundo (do centro, no Nether e no End),
a dimensão, a profundidade e as estruturas em que estão (estruturas raras como Cidade Ancestral,
Mansão, Fortaleza do Fim e Câmaras de Provação rendem mais). Mais nível = mais vida, dano e XP.
Mobs podem ter **traits** (Veloz, Bruto, Vampírico, Explosivo...) e uns poucos nascem **Elite**:
maiores, com traits melhores, barra de chefe, drops especiais e **Essência Elite** (usada nos upgrades).
Tudo é ajustável em `mobs:` no `config.yml`. Teste com `/vertexx mob <tipo> [nivel] [elite]`.

### Status, nível geral e habilidades

Dez status sobem fazendo a atividade correspondente: **Mineração, Escavação, Lenhador, Agricultura, Luta,
Arquearia, Resistência, Acrobacia, Pesca e Exploração**. Cada um dá um bônus passivo pequeno e todo XP
também soma no **nível geral**, que rende **pontos de habilidade** (2 a cada 5 níveis). Abra o menu com
`/vstats` ou pelo Guia do Vertexx: três páginas (Status, Árvore de Habilidades e Informações).

A árvore tem 16 habilidades: Mãos Rápidas, **Pico Largo** (mineração 2x2 e 3x3 temporária), Veeiro (vein
miner), Garimpeiro, Machadeiro (derruba a árvore), Colheita Farta, Replantio, **Golpe Atordoante**, Frenesi,
Ímã, **Investida** (dash), Pés Leves, Pele Grossa, Vitalidade, Reposição e Alcance do Construtor.
Controles: **F** usa a habilidade ativa selecionada, **Shift + F** troca, e agachado ativa Veeiro e
Machadeiro. Nenhuma habilidade afeta jogadores. Subir de nível toca uma cinemática (banner do resource
pack, clarão e sons; sem o pacote, título, partículas e sons vanilla).

### Bosses

Dois bosses com sala própria, feitos de *display entities* (visual 3D que não depende de resource pack),
com animações, barra de chefe, cinemática ao despertar e sala selada durante a luta:

| Boss | Onde vive | Ataques |
| --- | --- | --- |
| **Wildfire** (o Blaze chefe da Mojang) | **Câmara do Wildfire**: uma ala especial de tijolos do Nether, anexa às fortalezas, ligada por um corredor | **Onda de Choque** (anel de chamas: pule), **Chuva de Bolas de Fogo** (leque e anel), **Investida Aérea** (agarra, queima e arremessa; se errar, fica exposto por 3,5 s com +50% de dano). Quatro **escudos giratórios** bloqueiam projéteis |
| **Iceologer** (o saqueador do gelo da Mojang) | **Castelo de Gelo**: muralha, quatro torres e um torreão cujo salão é a sala do boss, em terras geladas | **Espinhos de Gelo** (invoca à la Evoker e lança um a um), **Iceberg** (arranca o chão, carrega, marca o alvo e arremessa) |

- As salas são **construídas sozinhas** quando alguém explora a região (fortalezas do Nether e biomas
  gelados), com chance e distâncias mínimas configuráveis; nada é alterado antes disso.
- Entrar no salão desperta o boss; a porta é **selada** até a luta acabar (ou todos caírem, e então ele reinicia).
- A vida cresce 60% por jogador extra (até 4). Abaixo de 50% o boss entra em **fúria**.
- Drops: Essência Elite, recursos raros e um troféu, mais XP de Luta. Renasce depois de `respawn-minutes`.
- Admin: `/vertexx boss list|spawn|build|wake|attack|reset|kill|tp` (`bosses:` no `config.yml`).

### Mobs rejeitados da votação (pacíficos)
Quatro criaturas que perderam a Mob Vote da Mojang, cada uma um mob invisível com modelo de displays (`fauna:` no `config.yml`):
- **Rascal** (2022): nas minas abandonadas abaixo de Y=0. Brinca de esconde-esconde: olhe para ele de perto para achá-lo; ele foge e se esconde. Achado 3 vezes, dá uma *Picareta do Rascal* e vai embora.
- **Golem de Tufo** (2022): construído por você com tufo, tufo (ou um bloco de lã, para ganhar manto) e tufo cinzelado no topo. Clique com um item para ele exibir; de tempos em tempos acorda, pega um item do chão e volta ao posto. Agache e bata para desmontar.
- **Moobloom** (2020): vacas amarelas de florestas floridas e campos. Deixam rastro de flores, dão néctar às abelhas e podem ser tosquiadas.
- **Glare** (2021): nas cavernas exuberantes. Paira ranzinza sobre pontos escuros e se acalma com luz; com bagas brilhantes passa a te acompanhar.
- Admin: `/vertexx rejeitado spawn|limpar|lista`.

### Upgrades de fornalhas e baús

**Agache e clique com a mão vazia** em uma fornalha (comum, alto-forno ou defumador) ou baú (baú, baú
armadilhado ou barril) para abrir o menu de upgrade. São 6 níveis, pagos com recursos por progressão
(cobre, ouro, ametista, diamante, obsidiana, netherite) e, nos mais altos, **Essência Elite**.
- **Fornalhas** cozinham de 1,3x até 5x mais rápido.
- **Baús** passam a ter páginas de 45 espaços (de 45 até 360 espaços); os 27 primeiros são os do próprio
  baú, então funis continuam funcionando quando ninguém o está usando. Não formam baú duplo.
O nível acompanha o item quando o bloco é quebrado e colocado de novo. Custos e capacidades ficam em
`upgrades:` no `config.yml`.

## Menus

Todos os menus têm duas versões com a mesma função. Com o **resource pack** carregado, ganham fundo
ilustrado com o tema do Vertexx, ícones e
emblemas próprios. Sem o pacote, aparecem com os painéis de vidro vanilla.

## Comandos

| Comando | Quem | Descrição |
| --- | --- | --- |
| `/tumulo` | todos | Coordenadas dos seus túmulos |
| `/tumulo chave [jogador] [qtd]` | admin | Entrega Chaves de Túmulo |
| `/pacote [aceitar\|recusar]` | todos | Resource pack do servidor |
| `/vstats [habilidades\|info]` | todos | Status, árvore de habilidades e informações |
| `/vertexx mob <tipo> [nível] [elite] [traits...]` | admin | Cria um mob nivelado (parâmetros em qualquer ordem) |
| `/vertexx xp <jogador> <status> <qtd>` | admin | Concede XP de status |
| `/vertexx boss <list\|spawn\|build\|wake\|attack\|reset\|kill\|tp>` | admin | Controle dos bosses |
| `/vertexx rejeitado <spawn\|limpar\|lista>` | admin | Mobs rejeitados da votação |
| `/vertexx` | admin | Abre o painel completo |
| `/vertexx livro` | todos | Recebe o Guia do Vertexx |
| `/vertexx versao\|atualizar` | admin | Versão e atualização |

Administrador = permissão `vertexx.admin` (padrão: OP). Todos os comandos têm **autocomplete** (tipos de mob,
traits, status, jogadores online, bosses, mundos...).

## Arquivos no servidor

Tudo fica em `plugins/Vertexx/`, salvo de forma assíncrona:

| Arquivo | Conteúdo |
| --- | --- |
| `config.yml` | Todas as opções, comentadas |
| `waystones.yml` | Waystones e quem descobriu cada um |
| `graves.yml` | Túmulos pendentes |
| `resourcepack.yml` | Quem aceitou ou recusou o pacote |
| `panel.yml` | Quem já recebeu o Guia |
| `rpg.yml` | Status, nível geral, pontos e habilidades dos jogadores |
| `bosses.yml` | Salas de boss construídas, renascimento, selos e locais já sorteados |

## Estrutura do projeto

```
Vertexx/
├─ libs/                  jars exportados, um por etapa/versão
├─ scripts/               build, release e sincronização
├─ tools/                 aplicativo VertexxServer.exe (liga/desliga o servidor de teste)
├─ docs/                  guia de desenvolvimento
├─ resourcepack/          resource pack gerado (texturas, fontes, modelos, sons)
├─ tools/pack/            gerador do resource pack (Python) e zip determinístico
├─ tools/models/          conversor bbmodel para o pack, análise de referências e modelos dos mobs (Python)
├─ models/                refs/ (imagens de referência) e src/ (.bbmodel dos mobs e bosses)
└─ src/main/
   ├─ resources/          plugin.yml e config.yml
   └─ java/dev/vertexx/
      ├─ PluginMain.java  registro de eventos/comandos e carregamento assíncrono
      ├─ core/            MiniMessage, efeitos, itens de menu, YAML assíncrono
      ├─ waystone/        WaystoneManager, ModelManager (display entities), menus, eventos
      ├─ panel/           painel, Guia (livro) e /vertexx
      ├─ grave/           túmulos e Chave de Túmulo
      ├─ model/           ModelDef/ModelInstance: bbmodel compilado e animado em ItemDisplays
      ├─ pack/            resource pack: descoberta automática na release e envio
      └─ update/          auto-update pelo GitHub
```

Para compilar e publicar versões, veja [docs/DESENVOLVIMENTO.md](docs/DESENVOLVIMENTO.md).

## Histórico de etapas

| Versão | Etapa |
| --- | --- |
| 0.1.0 | Reinos e onboarding |
| 0.2.0 | Waystones |
| 0.3.0 | Túmulos, honra, fogo amigo e resource pack opcional |
| 0.4.0 | Auto-update pelo GitHub |
| 0.5.0 | Painel e Guia, modelo 3D animado das Waystones, personalização, rotas fixas, escolha de pagamento |
| 0.6.0 | Modelo fiel à referência (olhos no lugar das runas), 8 skins, ondas de impacto e brilho na montagem, painel do reino (status, membros, diplomacia, mural), menu de Waystones com favoritos, ordenação e filtro |
| 0.6.1 | Waystone menor que o jogador (escala 0,85) e display entities com brilho máximo (acabou o visual preto) |
| 0.7.0 | Escolha de reino em cutscene (3 bandeirões, câmera, mouse + agachar), resource pack gerado e publicado na release, configurado automaticamente pelo plugin |
| 0.8.0 | Sair e trocar de reino (aliança, traição, fora da lei), mercenários com reputação e recompensas, honra dos reinos, menus refeitos com fundos e ícones do resource pack (com versão vanilla equivalente) |
| 0.8.1 | Waystone gira para quem a colocou (yaw salvo); o pacote novo é reenviado a quem entrou antes da descoberta da release |
| 0.9.0 | SMP pacífico: removidos reinos, mercenários, recompensas, honra e a cerimônia de escolha. Ficam Waystones, túmulos, painel, resource pack e auto-update; a viagem não depende mais de reino nem de combate |

| 0.10.0 | Mobs nivelados por distância, dimensão e estruturas; traits; mobs Elite com barra de chefe e Essência Elite |
| 0.11.0 | Status de atividade, nível geral, árvore de habilidades (16), `/vstats` com 3 páginas, cinemáticas com banners do resource pack |
| 0.12.0 | Upgrades de fornalhas (velocidade) e baús (capacidade em páginas) por níveis com recursos e Essência Elite; menu de upgrade com tema próprio |
| 0.13.0 | Bosses Wildfire (Câmara do Wildfire) e Iceologer (Castelo de Gelo) com salas construídas sozinhas; `/vertexx mob` com nível, elite e traits; autocomplete em todos os comandos; Wiki de traits, Elites e bosses no painel; comandos de admin no painel |
| 0.14.0 | Rótulos de mobs nivelados não atravessam mais paredes; bosses colidem com blocos; mobs rejeitados Rascal, Golem de Tufo, Moobloom e Glare; aba Rejeitados na Wiki |
| 0.15.0 | Modelos em bbmodel com animações (conversor para o resource pack), sistema de análise de referências pixel a pixel, mobs rejeitados e bosses remodelados a partir das imagens, correção do flicker de escala/rotação dos displays |
| 0.15.1 | Atualizador e resource pack automático não dependem mais da API do GitHub quando ela responde 403/429 (limite de 60 req/h por IP): a tag vem do redirecionamento de /releases/latest |
| 0.16.0 | Análise com OpenCV/scikit-image/SciPy (texel, direções de aresta, câmera pelas arestas, métricas), captura de tela/janela/GIF/vídeo/jar do cliente, ajuste e cópia de poses (textura projetada com o modelo posado; Golem de Tufo agora projetado), cabeças de jogador com skin do resource pack (`/vertexx cabeca`) |
| 0.16.1 | Resource pack: UUID fixo e envio com substituição, para cada versão nova trocar a anterior no cliente em vez de empilhar |

## Modelos e animações (bbmodel)

Os mobs rejeitados e os bosses são **modelos do Blockbench** em `models/src/*.bbmodel`, com textura embutida e
animações por osso. `python tools/pack/build.py` os compila para o resource pack (um modelo de item por osso)
e para `src/main/resources/models/*.json` (ossos e animações amostradas a 20 Hz); o plugin os desenha com uma
ItemDisplay por osso. Formatos aceitos (modded entity, OptiFine, GeckoLib) e a ferramenta de análise:
[docs/MODELOS.md](docs/MODELOS.md).

## Servidor de teste (Windows)

`tools\VertexxServer.exe` é um aplicativo leve (~20 KB) para ligar e desligar o Paper de teste da pasta
`run/`: botão Iniciar/Parar, status, log ao vivo e caixa de comandos (via RCON). Ao iniciar, ele copia o
jar mais novo de `libs/` para `run/plugins/`. Para recompilar: `.	oolsuild-launcher.ps1`.

### Aplicativo Vertexx Server (`tools\VertexxServer.exe`)

Liga e desliga o servidor de teste, mostra o log e envia comandos. O botão **Mundos e servidor** (com o
servidor parado) permite:
- **Mundos:** criar um mundo novo escolhendo nome, tipo (Normal, Plano, Vazio, Biomas grandes, Ampliado),
  seed, estruturas, modo de jogo e dificuldade; trocar de mundo; excluir mundos antigos.
- **Servidor:** nome (MOTD), porta, máximo de jogadores, distância de visão, memória, modo online,
  whitelist, PvP, modo de jogo e dificuldade.

O aplicativo também silencia o ruído inofensivo do log (contadores de desempenho do Windows) por meio de
`run/vertexx-log4j2.xml`. Recompile com `toolsuild-launcher.ps1`.
