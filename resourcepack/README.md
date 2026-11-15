# Resource pack do Vertexx

O conteúdo desta pasta é **gerado** por [`tools/pack/build.py`](../tools/pack/build.py): não edite os
PNG/OGG/JSON à mão, altere o gerador e rode de novo.

```powershell
python tools\pack\build.py        # recria resourcepack/ e libs/Vertexx-Pack.zip (SHA-1 estável)
```

Requer Python 3 com `Pillow`, `numpy` e `soundfile`.

## O que tem no pacote

| Recurso | Uso |
| --- | --- |
| `items/` + `models/item/banner_*` | Modelos 3D dos três bandeirões (display entities via `item_model`) |
| `items/selector` | Cetro do Escolhido (item de escolha) |
| `font/cinematic` | Sobreposições de tela cheia (preto, flash, tinta do reino), emblemas e ícones de mouse/Shift |
| `font/menu` + `textures/gui/` | Fundos ilustrados dos menus de 6 linhas, um por tema (vertexx, três reinos e fora da lei), posicionados pelo título do inventário |
| `items/ui_*`, `items/emblem_*`, `items/blank` | Ícones de interface, emblemas dos reinos e item invisível dos painéis decorativos |
| `sounds.json` + `sounds/intro/` | Música ambiente em loop, riser, impactos, vinhetas por reino, coro de confirmação (tudo sintetizado) |

## Como chega ao jogador

1. O script de release (`scripts/release.ps1`) anexa `Vertexx-Pack.zip` à release do GitHub, junto com o `.jar`.
2. O plugin (`PackResolver`) consulta a release mais recente, baixa o zip, valida e calcula o SHA-1 sozinho
   (cache em `plugins/Vertexx/pack-cache.properties`), sem editar nada no `config.yml`.
3. A cerimônia de entrada envia o pacote ao jogador e espera carregar. Quem recusar ou falhar vê a
   versão vanilla da cerimônia.

Um link manual em `resource-pack.url` / `resource-pack.sha1` do `config.yml` substitui o automático.
