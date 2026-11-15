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
- **Administradores** (`/vertexx` ou o próprio livro) veem também a aba *Administração*: status, modelos
  renderizados, atualização pelo GitHub, alternadores de configuração e a lista de todos os Waystones.
  Quem não tem OP não vê nada disso, nem pelo livro.

### Túmulos

Ao morrer, seus itens e todo o seu XP ficam em uma cabeça sua no local da morte. Clique ou quebre para
recuperar. Por 30 minutos só você abre; depois disso, qualquer um. Uma **Chave de Túmulo**
(entregue por administradores) abre um túmulo ainda protegido.

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
| `/vertexx` | admin | Abre o painel completo |
| `/vertexx livro` | todos | Recebe o Guia do Vertexx |
| `/vertexx versao\|atualizar` | admin | Versão e atualização |

Administrador = permissão `vertexx.admin` (padrão: OP).

## Arquivos no servidor

Tudo fica em `plugins/Vertexx/`, salvo de forma assíncrona:

| Arquivo | Conteúdo |
| --- | --- |
| `config.yml` | Todas as opções, comentadas |
| `waystones.yml` | Waystones e quem descobriu cada um |
| `graves.yml` | Túmulos pendentes |
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
      ├─ waystone/        WaystoneManager, ModelManager (display entities), menus, eventos
      ├─ panel/           painel, Guia (livro) e /vertexx
      ├─ grave/           túmulos e Chave de Túmulo
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

## Servidor de teste (Windows)

`tools\VertexxServer.exe` é um aplicativo leve (~20 KB) para ligar e desligar o Paper de teste da pasta
`run/`: botão Iniciar/Parar, status, log ao vivo e caixa de comandos (via RCON). Ao iniciar, ele copia o
jar mais novo de `libs/` para `run/plugins/`. Para recompilar: `.	oolsuild-launcher.ps1`.
