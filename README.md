# Vertexx

Plugin customizado para o SMP semi-vanilla **Vertexx** — Paper **26.3**, Java 25.
Leve, sem dependências, com textos em MiniMessage e feedback de som e partículas em cada interação.

| Módulo | O que faz |
| --- | --- |
| **Reinos** | 3 reinos fixos, escolha obrigatória no primeiro login, cargos e alianças |
| **Waystones** | Lodestones renomeados viram pontos de viagem rápida |
| **Túmulos** | Itens e XP guardados no local da morte, protegidos por tempo |
| **Honra** | Reputação de -100 a 100 que julga o PvP entre reinos |
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

### Reinos

Os três reinos são fixos: **Enternais**, **Reino dos aurudos+67+ego** e **cafetões**.

No primeiro login o jogador assiste a uma **cerimônia cinematográfica**: a câmera sobe para um palco
no céu noturno, três grandes bandeirões se erguem com música, partículas e a vinheta de cada reino, e
ele escolhe segurando o **Cetro do Escolhido**: **botão esquerdo** move a seleção para a esquerda,
**botão direito** para a direita e **agachar** confirma. A escolha é permanente; a câmera avança
sobre o bandeirão, a tela escurece e o jogador volta ao ponto de origem com o título do reino
(emblema + nome) e o cargo **Membro**. O símbolo do reino aparece no chat e no TAB.

A cerimônia usa o [resource pack do servidor](resourcepack/README.md) (modelos dos bandeirões,
sons sintetizados, fontes de sobreposição). Quem recusa o pacote vê uma versão com estandartes
vanilla e sons do jogo. Administradores podem rever tudo com `/vertexx cerimonia [jogador]`
(ensaio, não altera o reino). Para voltar ao menu clássico: `onboarding.mode: menu` no `config.yml`.

Alianças são tratadas pelos **Líderes** (definidos por um administrador): um líder propõe com
`/reino aliar <reino>` e o outro aceita com o mesmo comando.

### Waystones

- **Criar:** renomeie um Lodestone na bigorna e coloque-o no chão. O nome do item vira o nome do Waystone.
  Lodestones sem nome continuam vanilla.
- **Descobrir:** clique com o botão direito em um Waystone para registrá-lo.
- **Modelo 3D:** o Waystone ganha um modelo de display entities (base de obsidiana, pilar com runas
  verdes e um ícone flutuante entre dois arcos). Ao ser colocado, ele se **monta com animação**.
  As entidades só existem com jogador por perto e só são enviadas a quem está perto, olhando na direção
  do modelo e sem uma parede no caminho (`waystones.model` no config).
- **Viajar:** o menu lista os Waystones que você já descobriu, tem 1,5 s de concentração — cancela se você
  se mover — e não funciona em combate.
- **Pagamento:** cada jogador escolhe no menu se paga com **ametista, pérola ou resina** (clique no item
  do topo). As quantidades ficam em `waystones.cost.options`.
- **Configurar (dono):** botão *Configurar* no menu — ícone flutuante (18 opções), nome visível ou oculto
  e **rota fixa**: com uma rota definida, o clique direito viaja direto, sem menu (Shift + clique abre o menu).
- **Remover:** só o dono (ou um administrador) consegue quebrar. Explosões e pistões não afetam.

### Painel e Guia

- **Guia do Vertexx:** um livro entregue no primeiro login (e com `/vertexx livro`). Clique direito abre o
  painel com reinos, Waystones, túmulos, honra e comandos.
- **Administradores** (`/vertexx` ou o próprio livro) veem também a aba *Administração*: status, modelos
  renderizados, atualização pelo GitHub, alternadores de configuração e a lista de todos os Waystones.
  Quem não tem OP não vê nada disso, nem pelo livro.

### Túmulos

Ao morrer, seus itens e todo o seu XP ficam em uma cabeça sua no local da morte. Clique ou quebre para
recuperar. Por 30 minutos só você abre; depois disso, qualquer um. Uma **Chave de Túmulo**
(entregue por administradores) abre um túmulo ainda protegido.

### Honra

| Faixa | Honra | Efeito |
| --- | --- | --- |
| Desonrado | -30 ou menos | Paga o dobro nos Waystones; pode ser atacado sem punição |
| Neutro | -29 a 29 | — |
| Honrado | 30 a 69 | — |
| Glorioso | 70 ou mais | Viaja de graça pelos Waystones |

| Evento | Honra |
| --- | --- |
| Jogar ativamente (a cada 10 min) | +1 |
| Matar quem te atacou primeiro | +3 |
| Matar um Desonrado | +5 |
| Dar o primeiro golpe em alguém | -1 |
| Matar sendo o agressor | -10 |
| Romper uma aliança (líder) | -20 |
| Matar um aliado (só com fogo amigo ligado) | -25 |

Membros do mesmo reino e de reinos aliados não se ferem (fogo amigo desativado).

### Sair ou trocar de reino

`/reino sair` (ou o botão **Deixar o reino** no painel do reino) abre um menu em três passos:
caminho, destino e confirmação com todas as consequências à vista.

| Caminho | Destino | Efeito |
| --- | --- | --- |
| **Aliança** | reino aliado do seu | Sem perdas |
| **Traição** | reino que não é aliado | -30 de honra; o reino que você deixou perde 12 de honra; +10 de reputação; marca de **traidor** por 3 dias (matá-lo não é punido) |
| **Fora da lei** | nenhum | -10 de honra; o reino perde 4; você vira **mercenário** |

Depois de qualquer troca há uma carência de 24 h. Se o líder sair, o comando passa a um oficial (ou membro).
Quem está sem reino usa `/reino entrar` para voltar a um dos três reinos.

### Mercenários, reputação e recompensas

Quem está **sem reino** é um fora da lei. Ele ganha **reputação** (0 a 100) com mortes (+3), cobrando
recompensas (+8), traindo (+10) e saqueando túmulos alheios (+4). Morrer tira 25% da reputação; ter a
cabeça cobrada por um caçador a **zera**. Os benefícios só valem enquanto está sem reino:

| Nível | Reputação | Benefícios |
| --- | --- | --- |
| Desconhecido | 0 a 19 | — |
| Notório | 20+ | Waystones -25%, recompensas +10% |
| Temido | 50+ | Waystones -50%, recompensas +25% |
| Lendário | 80+ | Waystones de graça, recompensas +50% |

Qualquer jogador pode pôr preço na cabeça de um mercenário (`/recompensa`, pago em esmeraldas por padrão).
Quem o matar leva tudo, e a oferta aumenta a **fama** do alvo. Foras da lei e traidores podem ser atacados
sem perder honra.

Cada ato de um mercenário também derruba a **honra do reino** atingido (0 a 200, começa em 100 e se
recompõe aos poucos): mortes de membros (-3), túmulos saqueados (-2), traições (-12) e deserções (-4).
A honra do reino aparece no painel e entra no cálculo do prestígio.

## Menus

Todos os menus têm duas versões com a mesma função. Com o **resource pack** carregado, ganham fundo
ilustrado com o tema do reino (roxo, âmbar, carmim ou o preto e vermelho dos foras da lei), ícones e
emblemas próprios. Sem o pacote, aparecem com os painéis de vidro vanilla.

## Comandos

| Comando | Quem | Descrição |
| --- | --- | --- |
| `/reino` | todos | Painel do seu reino: status, membros, mural e diplomacia |
| `/reino info` | todos | Resumo do reino no chat |
| `/reino anuncio <texto\|limpar>` | Líder | Fixa o mural do reino (aparece ao entrar) |
| `/reino promover\|rebaixar <jogador>` | Líder | Alterna Membro e Oficial |
| `/reino membros` | todos | Lista os membros do seu reino |
| `/reino sair` | membros | Menu para trocar de reino ou virar fora da lei |
| `/reino entrar` | foras da lei | Menu para entrar em um reino |
| `/recompensa [lista\|colocar <jogador> <quantia>]` | todos | Quadro de procurados e recompensas |
| `/mercenario [jogador]` | todos | Reputação de um mercenário |
| `/reino aliar <reino>` | Líder | Propõe ou aceita uma aliança |
| `/reino romper <reino>` | Líder | Rompe uma aliança |
| `/reino admin definir <jogador> <reino>` | admin | Move um jogador de reino |
| `/reino admin cargo <jogador> <membro\|oficial\|lider>` | admin | Define o cargo |
| `/tumulo` | todos | Coordenadas dos seus túmulos |
| `/tumulo chave [jogador] [qtd]` | admin | Entrega Chaves de Túmulo |
| `/honra [jogador]` | todos | Mostra a honra |
| `/honra definir <jogador> <valor>` | admin | Ajusta a honra |
| `/pacote [aceitar\|recusar]` | todos | Resource pack do servidor |
| `/vertexx` | admin | Abre o painel completo |
| `/vertexx livro` | todos | Recebe o Guia do Vertexx |
| `/vertexx versao\|atualizar` | admin | Versão e atualização |
| `/vertexx cerimonia [jogador]` | admin | Ensaio da cerimônia de escolha de reino |

Os ids dos reinos nos comandos são `enternais`, `aurudos` e `cafetoes`.
Administrador = permissão `vertexx.admin` (padrão: OP).

## Arquivos no servidor

Tudo fica em `plugins/Vertexx/`, salvo de forma assíncrona:

| Arquivo | Conteúdo |
| --- | --- |
| `config.yml` | Todas as opções, comentadas |
| `kingdoms.yml` | Membros, cargos e alianças |
| `waystones.yml` | Waystones e quem descobriu cada um |
| `graves.yml` | Túmulos pendentes |
| `honor.yml` | Honra de cada jogador |
| `mercenaries.yml` | Foras da lei, reputação, recompensas, marcas de traidor e carência de troca |
| `resourcepack.yml` | Quem aceitou ou recusou o pacote |
| `panel.yml` | Quem já recebeu o Guia |

## Estrutura do projeto

```
Vertexx/
├─ libs/                  jars exportados, um por etapa/versão
├─ scripts/               build, release e sincronização
├─ tools/                 aplicativo VertexxServer.exe (liga/desliga o servidor de teste)
├─ docs/                  guia de desenvolvimento
├─ resourcepack/          resource pack gerado (texturas, fontes, modelos, sons)
├─ tools/pack/            gerador do resource pack (Python) e zip determinístico
└─ src/main/
   ├─ resources/          plugin.yml e config.yml
   └─ java/dev/vertexx/
      ├─ PluginMain.java  registro de eventos/comandos e carregamento assíncrono
      ├─ core/            MiniMessage, efeitos, itens de menu, YAML assíncrono
      ├─ kingdom/         KingdomGUI, reinos, cargos, alianças, /reino
      ├─ waystone/        WaystoneManager, ModelManager (display entities), menus, eventos
      ├─ panel/           painel, Guia (livro) e /vertexx
      ├─ grave/           túmulos e Chave de Túmulo
      ├─ honor/           honra e regras de combate
      ├─ mercenary/       foras da lei, reputação, recompensas e quadro de procurados
      ├─ intro/           cerimônia cinematográfica de escolha de reino
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

## Servidor de teste (Windows)

`tools\VertexxServer.exe` é um aplicativo leve (~20 KB) para ligar e desligar o Paper de teste da pasta
`run/`: botão Iniciar/Parar, status, log ao vivo e caixa de comandos (via RCON). Ao iniciar, ele copia o
jar mais novo de `libs/` para `run/plugins/`. Para recompilar: `.	oolsuild-launcher.ps1`.
