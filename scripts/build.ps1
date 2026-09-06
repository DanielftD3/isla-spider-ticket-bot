$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java no esta instalado. Instala JDK 21 y vuelve a intentarlo."
}

$JavaVersionLine = (& java -version 2>&1 | Select-Object -First 1)
if ($JavaVersionLine -notmatch '"(?<major>\d+)') {
    throw "No se pudo comprobar la version de Java: $JavaVersionLine"
}
if ([int]$Matches.major -lt 21) {
    throw "Se requiere JDK 21 o superior. Version encontrada: $JavaVersionLine"
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven no esta instalado o no esta en PATH."
}

mvn --batch-mode --no-transfer-progress clean verify
Write-Host "Bot compilado: target\isla-spider-ticket-bot.jar"
