# Modelos, animações e análise de referências

## Visão geral

```
models/refs/*.png ──► tools/models/analyze.py ──► models/analysis/ (paleta, regiões, grades com coordenadas)
                              │
tools/models/author/*.py ─────┴─► models/src/*.bbmodel ──► tools/models/convert.py
 (geometria + pintura + animação)        (abre no Blockbench)         │
                                                                      ├─► resourcepack/assets/vertexx/{models,items,textures}/entity/<id>/…
                                                                      └─► src/main/resources/models/<id>.json  (ossos + animações baked)
                                                                                          │
                                                          dev.vertexx.model.ModelDef/ModelInstance (ItemDisplay por osso)
```

`python tools/pack/build.py` já roda o conversor; `python tools/models/convert.py --list` lista o que há em `models/src`.

## Formatos de entrada

| Entrada | Suporte |
| --- | --- |
| `.bbmodel` do Blockbench, **qualquer formato** (`free`, `java_block`, `modded_entity`, `optifine_entity`, `bedrock`/GeckoLib) | O `.bbmodel` guarda sempre as coordenadas do editor; o que muda entre formatos é `model_format` e o uso de *box UV*. Os dois casos são lidos (UV por face ou por `uv_offset`). Ossos aninhados, cubos com rotação (viram sub-ossos), inflate, localizadores/null objects, várias texturas |
| Animações do `.bbmodel` | Canais `rotation`, `position` e `scale`; interpolação `linear`, `catmullrom`, `bezier` (com alças) e `step`; keyframes duplos (antes/depois); expressões **Molang** (`Math.sin(query.anim_time*90)*10`, ternário, `Math.clamp/lerp/…`); loops `loop`, `once` e `hold` |
| `*.geo.json` + `*.animation.json` (GeckoLib/Bedrock cru) | `tools/models/vx/geo.py`, **experimental** (convenção de eixos do Blockbench, ainda não validada com arquivos reais) |
| `.jem` (OptiFine cru) | não implementado: exporte o modelo como `.bbmodel` no Blockbench (formato *OptiFine Entity*) |
| Malhas (`mesh`) | ignoradas (displays só desenham cubos) |

Convenção de eixos: o editor olha para `-Z` (norte); o conversor gira o modelo 180° em Y para que a frente
seja `+Z`, como nas entidades do jogo (faces norte↔sul e leste↔oeste trocadas, faces de cima/baixo com
rotação de 180° para a textura continuar igual).

**Atenção (0.16.4):** o cliente gira toda `ItemDisplay` 180° em Y antes de desenhar o item
(`DisplayRenderer.ItemDisplayRenderer` aplica `Axis.YP.rotation(PI)`; confirmado no bytecode do 26.3). Por isso
`pack._element` escreve cada elemento já girado de volta (x/z invertidos em torno do pivô, faces opostas, cima/baixo +180°);
sem isso cada osso saía espelhado no próprio pivô (partes fora do lugar, frente invertida). O plugin define também uma
caixa de culling nas displays (mods como Entity Culling escondem displays de caixa 0×0 de forma intermitente).

### O que o conversor gera

* cada **osso com cubos** vira um modelo de item (`models/entity/<id>/<osso>.json`) e uma definição em
  `items/entity/<id>/<osso>.json`; o plugin usa `item_model` numa `ItemDisplay`;
* coordenadas de elemento só vão de -16 a 32 no jogo: ossos grandes são reduzidos por um fator `k` e a
  display é ampliada de volta; cubos girados viram sub-ossos (o jogo restringe rotação de elementos);
* ossos terminados em `_tint` são tingidos pelo componente `dyed_color` (a capa do Golem de Tufo usa a cor da lã);
  ossos terminados em `_glow` ficam com brilho máximo (olhos);
* animações são amostradas a 20 quadros/s (Molang e interpolações avaliados no conversor, não no jogo).

## Anti-flicker (escala/rotação)

`ModelInstance` e o `Rig` (peças em BlockDisplay) seguem as mesmas regras: a primeira pose e as trocas de
visibilidade vão **sem interpolação** (nada cresce a partir de 0 nem gira a partir da identidade); o sinal
do quaternion é mantido contínuo (senão o cliente interpola pelo caminho longo); poses idênticas não são
reenviadas; e, no modelo novo, as displays ficam ancoradas e o deslocamento do mob vai dentro da própria
transformação (um pacote por osso, sem teleportes dessincronizados).

## Sistema de análise de referências

Ferramentas em `tools/models/vx/` (só numpy + Pillow):

* **`refscan.analyze`** (`python tools/models/analyze.py models/refs/x.png`): separa o fundo (alfa ou cor dos
  cantos), recorta, extrai paleta com frações, regiões de cor (componentes conexos com caixa e centroide),
  extremos da silhueta, perfil de largura por linha e uma **folha ampliada com grade e coordenadas** para
  leitura visual;
* **`render.py`**: traçador de raios ortográfico do modelo posado (mesmas convenções de face/UV do jogo),
  com sombreamento por face;
* **`refscan.fit_camera` / `fit_all`**: acham a câmera (yaw, pitch, escala, centro) e as **medidas das peças**
  que maximizam o IoU entre a silhueta do modelo e a da referência (busca grossa + subida de encosta).
  Nas referências de wiki isso cai em yaw ≈ 45°, pitch ≈ 30°, e a vaca do Moobloom volta com as medidas exatas
  do modelo vanilla (perna 12, corpo 10×12×18, cabeça 8×6);
* **`refscan.bake`**: **projeta a referência sobre as faces visíveis** (descontando o sombreamento por face) e
  grava texel a texel na textura; os texels que a imagem não mostra são preenchidos pelo cubo espelhado, pela
  face oposta ou pela cor média (`author/common.fill_unseen`);
* **`refscan.compare`**: painel referência | modelo | diferença (vermelho = falta, azul = sobra) com IoU e
  erro de cor médio, em `models/preview/<mob>_compare.png`; `models/preview/<mob>_sheet.png` mostra o modelo
  em quatro ângulos e quatro instantes da animação.

### Refazer um modelo

```powershell
python tools\models\author\moobloom.py     # reconstrói models/src/moobloom.bbmodel e as pranchas de conferência
python tools\pack\build.py                 # recompila o pack e os dados de runtime
```

Os `author/*.py` descrevem geometria, pintura e animações em código, mas o resultado é um `.bbmodel`
comum: pode ser aberto, editado no Blockbench e convertido de novo sem passar pelos scripts.

## Limites conhecidos

* Sem o resource pack carregado o cliente mostra o modelo "faltando" (roxo/preto) nas displays.
* A conferência visual foi feita com o renderizador de referência, não no cliente do jogo.
* Referências em perspectiva (Glare, Golem de Tufo) casam pior com a câmera ortográfica; o Golem de Tufo
  (pose esticada) é pintado por código com a paleta medida, não projetado.

## Análise com OpenCV, captura e poses (0.16.0)

Dependências extras do lado Python: `opencv-python-headless`, `scipy`, `scikit-image`, `mss`.

### `vx/cvscan.py` (OpenCV / SciPy / scikit-image)

* `texel_size`: tamanho do "pixel de arte" da referência por periodicidade das bordas (com confiança);
* `edge_directions` + `estimate_view`: Canny + Hough nas arestas → câmera (yaw/pitch) **só pela geometria**, sem
  usar a silhueta. Nas referências de wiki (Moobloom, Iceologer, Rascal) dá 46°/30°, igual ao ajuste por silhueta;
* `metrics`: IoU, distância de chanfro, F1 de arestas, SSIM e ΔE em CIELAB; `palette_kmeans`, `polygons`, `inner_edges`.
  `python tools/models/analyze.py` imprime tudo para cada referência.

### `vx/capture.py` / `tools/models/capture.py`

Captura de tela ou janela (`--window Minecraft`), GIF/vídeo/folha de sprites em quadros, download de URL,
recorte do fundo com GrabCut e **leitura de assets do jar do cliente** (`vanilla ...` lista/extrai texturas
vanilla, verdade-terreno do layout de UV das entidades).

### Poses (`vx/pose.py`, `tools/models/pose_fit.py`)

Uma pose é `{osso: {rot, pos}}` no espaço do editor. `fit_pose` renderiza o modelo e minimiza
`(1-IoU) + chanfro + arestas internas (+ cor)` por descida de coordenadas, osso a osso (pai → filho), com
`limits` por eixo (a silhueta não distingue frente de trás), `sweep=True` para escapar de mínimos locais e
`free_pos` para deslocar ossos. `author/common.run(pose=…, exclude=…)` ajusta a pose e a câmera juntas e **projeta a
textura com o modelo posado** (o arquivo salvo volta ao repouso); `exclude` deixa ossos de fora da projeção
(a capa tingida do Golem de Tufo). Poses ficam em `models/poses/*.json`.

```powershell
python tools\models\pose_fit.py fit moobloom models\refs\x.png --bones head,leg_fl --yaw 20,80
python tools\models\pose_fit.py copy models\poses\a.json glare --mirror --save-as pose_a   # copia para outro modelo
python tools\models\pose_fit.py seq moobloom models\refs\frames --fps 10 --name walk_ref   # sequência → animação
```

Teste sintético (pose conhecida renderizada e recuperada): cabeça 21°/14° para 20°/15°, três das quatro pernas
exatas; a perna de trás escondida pelo corpo fica ambígua. Em referência em repouso o ajuste "inventa" ângulos
para compensar proporções erradas: use `bones`/`limits` e só quando a imagem realmente está posada. O Golem de
Tufo (pose esticada) passou de IoU 0,86 sem pose para 0,88 com pose e agora tem a textura projetada da imagem.

## Cabeças de jogador customizadas

O método clássico (valor base64 com URL de `textures.minecraft.net`) só funciona com skins **hospedadas pela
Mojang**, e subir uma skin para lá exige autenticar numa conta de jogador; o cliente recusa qualquer outro domínio.
Por isso eu não tinha como criá-las. Desde o 1.21.9 o componente `profile` aceita um *skin patch*
(`ResolvableProfile.skinPatch().body(Key)`) que aponta para uma textura **do resource pack**:
`assets/vertexx/textures/entity/player/wide/<nome>.png` (skin 64x64 comum). `tools/models/heads.py` monta a skin
da cabeça de cada modelo (osso `head`, 6 faces 8x8) em `models/heads/`; o build do pack as copia e escreve
`heads.txt`; `/vertexx cabeca <nome>` entrega o item (`dev.vertexx.model.CustomHeads`). Continua exigindo o
resource pack e **não foi visto no cliente do jogo**.
