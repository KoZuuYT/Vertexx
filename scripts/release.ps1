# Publica a versão atual (gradle.properties) como release no GitHub.
# Os servidores com o Vertexx instalado baixam essa release sozinhos.
#
# Passo a passo de uma nova versão:
#   1. Altere "version=" em gradle.properties
#   2. Faça o commit das mudanças
#   3. .\scripts\release.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

& (Join-Path $PSScriptRoot 'build.ps1')

$version = (Select-String -Path (Join-Path $root 'gradle.properties') -Pattern '^version=(.+)$').Matches[0].Groups[1].Value
$tag = "v$version"
$jar = Join-Path $root "libs\Vertexx-$version.jar"

Push-Location $root
try {
    git add "libs/Vertexx-$version.jar"
    git diff --cached --quiet
    if ($LASTEXITCODE -ne 0) { git commit -m "Exporta Vertexx-$version.jar" }

    if (git status --porcelain) { throw 'Há mudanças sem commit. Faça o commit antes de publicar.' }

    if (-not (git tag --list $tag)) { git tag $tag }
    git push origin HEAD --tags
    if ($LASTEXITCODE -ne 0) { throw 'git push falhou.' }

    gh release create $tag $jar --title "Vertexx $version" --generate-notes --latest
    if ($LASTEXITCODE -ne 0) { throw 'gh release create falhou.' }
} finally {
    Pop-Location
}
Write-Host "Release $tag publicada." -ForegroundColor Green
