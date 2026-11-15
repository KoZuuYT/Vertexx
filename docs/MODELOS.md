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
