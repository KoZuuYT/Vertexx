# Compila o aplicativo "Vertexx Server" (liga e desliga o servidor de teste) em tools\VertexxServer.exe.
# Usa o csc.exe do .NET Framework, que já vem com o Windows: nada para instalar.
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path $csc)) { throw "csc.exe não encontrado em $csc" }

& $csc /nologo /target:winexe /optimize+ /codepage:65001 `
    "/out:$here\VertexxServer.exe" `
    /r:System.Windows.Forms.dll /r:System.Drawing.dll /r:System.Management.dll `
    "$here\ServerLauncher\ServerLauncher.cs" "$here\ServerLauncher\SettingsForm.cs"
if ($LASTEXITCODE -ne 0) { throw "Compilação falhou (código $LASTEXITCODE)." }
Write-Host "OK: tools\VertexxServer.exe" -ForegroundColor Green
