# Baixa a release mais recente do Vertexx para a pasta plugins do servidor (Windows).
# Use na primeira instalação ou no script de inicialização do servidor, antes de abrir o Paper.
# Depois de instalado, o próprio plugin se mantém atualizado.
#
# Uso:  .\sync-latest.ps1 -PluginsDir "C:\servidor\plugins"
param(
    [string]$PluginsDir = '.\plugins',
    [string]$Repository = 'KoZuuYT/Vertexx'
)
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$release = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repository/releases/latest" -Headers @{ 'User-Agent' = 'Vertexx-Sync' }
$asset = $release.assets | Where-Object { $_.name -like '*.jar' } | Select-Object -First 1
if (-not $asset) { throw "A release $($release.tag_name) não tem um arquivo .jar." }

New-Item -ItemType Directory -Force $PluginsDir | Out-Null
$target = Join-Path $PluginsDir 'Vertexx.jar'
$marker = Join-Path $PluginsDir '.vertexx-version'

if ((Test-Path $target) -and (Test-Path $marker) -and ((Get-Content $marker -Raw).Trim() -eq $release.tag_name)) {
    Write-Host "Vertexx já está na versão $($release.tag_name)."
    exit 0
}

$partial = "$target.part"
Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $partial -Headers @{ 'User-Agent' = 'Vertexx-Sync' } -UseBasicParsing
if ($asset.digest -like 'sha256:*') {
    $expected = $asset.digest.Substring(7)
    $actual = (Get-FileHash $partial -Algorithm SHA256).Hash
    if ($actual -ne $expected) {
        Remove-Item $partial -Force
        throw 'O SHA-256 do arquivo baixado não confere com o da release.'
    }
}
Move-Item $partial $target -Force
Set-Content -Path $marker -Value $release.tag_name -Encoding ascii
Write-Host "Vertexx $($release.tag_name) instalado em $target" -ForegroundColor Green
