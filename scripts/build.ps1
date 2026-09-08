$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java no esta instalado. Instala JDK 21 y vuelve a intentarlo."
}

$JavaVersionLine = (& java --version | Select-Object -First 1)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($JavaVersionLine)) {
    throw "No se pudo ejecutar Java para comprobar su version."
}
if ($JavaVersionLine -notmatch '"?(?<major>\d+)') {
    throw "No se pudo comprobar la version de Java: $JavaVersionLine"
}
if ([int]$Matches.major -lt 21) {
    throw "Se requiere JDK 21 o superior. Version encontrada: $JavaVersionLine"
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven no esta instalado o no esta en PATH."
}

mvn --batch-mode --no-transfer-progress clean verify
if ($LASTEXITCODE -ne 0) {
    throw "La compilacion de Maven fallo con el codigo $LASTEXITCODE."
}

Write-Host "Bot compilado: target\isla-spider-ticket-bot.jar"
