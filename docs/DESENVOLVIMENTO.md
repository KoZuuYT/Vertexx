# Desenvolvimento

## Compilar

```powershell
.\scripts\build.ps1
```

O jar sai em `build/libs/` e é copiado para `libs/Vertexx-<versão>.jar`.

Requisitos: um JDK 17+ instalado para rodar o Gradle. O JDK 25 exigido pelo Paper 26.3 é baixado
automaticamente pelo Gradle na primeira compilação.

> **Por que usar o script e não `gradlew build` direto?**
> Nesta máquina a pasta do usuário do Windows tem acento, e o Gradle falha com caminhos assim
> (`Unable to establish loopback connection` e `GradleWorkerMain` não encontrado). O script aponta o
> cache do Gradle para `.gradle-home/` dentro do projeto e os sockets temporários para uma pasta sem
> acentos. Em máquinas sem esse problema, `gradlew build` funciona normalmente.

## Publicar uma versão

1. Altere `version=` em `gradle.properties`.
2. Faça o commit das mudanças.
3. Rode:
   ```powershell
   .\scripts\release.ps1
   ```

O script compila, exporta o jar para `libs/`, cria a tag `v<versão>`, envia tudo e cria a release
no GitHub com o jar e o `Vertexx-Pack.zip` (resource pack) anexados. Os servidores baixam essa release sozinhos em até 30 minutos
(ou na hora, com `/vertexx atualizar`) e aplicam no reinício seguinte.

A versão da release precisa ser **maior** que a instalada para o servidor atualizar
(comparação numérica: `0.10.0` > `0.9.2`).

## Convenções do código

- **Textos:** sempre MiniMessage via `core/Msg`. Texto vindo de jogador (nome de Waystone, por exemplo)
  entra por `Placeholder.unparsed` ou `Msg.esc`, nunca concatenado cru.
- **Feedback:** toda interação tem som e/ou partícula — use `core/Fx`.
- **Dados:** cada módulo tem um `*Manager` com `read()` (disco, fora da thread principal),
  `apply()` (thread principal) e `save()` (snapshot na thread principal, escrita assíncrona e atômica
  via `core/YamlStore`). No desligamento, `saveNow()`.
- **Ligação entre módulos:** os módulos não se conhecem diretamente; os ganchos ficam em `PluginMain`
  (`afterOnboarding`, `travelCostMultiplier`, `inCombat`, `onAllianceBroken`).
- **Reinos:** os nomes de exibição em `kingdom/Kingdom.java` são definidos pela comunidade e não devem
  ser alterados.
