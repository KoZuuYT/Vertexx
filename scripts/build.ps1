# Compila o plugin e exporta o jar da versão atual para libs/.
# Uso:  .\scripts\build.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

# O Gradle falha quando a pasta do usuário do Windows tem acentos (ex.: "Usuário").
# Por isso o cache do Gradle fica dentro do projeto e os sockets temporários em uma pasta ASCII.
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $root '.gradle-home' }
$tmp = Join-Path $env:PUBLIC 'vertexx-gradle-tmp'
New-Item -ItemType Directory -Force $tmp | Out-Null
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$($tmp -replace '\\', '/')"

# O Gradle precisa de um JDK 17+ para rodar; o JDK 25 usado na compilação é baixado por ele.
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}

Push-Location $root
try {
    & .\gradlew.bat build
    if ($LASTEXITCODE -ne 0) { throw "Build falhou (código $LASTEXITCODE)." }
} finally {
    Pop-Location
}

$version = (Select-String -Path (Join-Path $root 'gradle.properties') -Pattern '^version=(.+)$').Matches[0].Groups[1].Value
Write-Host "OK: libs\Vertexx-$version.jar" -ForegroundColor Green
