$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

$JavaCommand = Get-Command java -ErrorAction SilentlyContinue
if (-not $JavaCommand) {
    throw "Java no esta instalado. Instala JDK 21 y vuelve a intentarlo."
}

$PreviousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    $JavaVersionOutput = @(& $JavaCommand.Source --version 2>&1)
    $JavaExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $PreviousErrorActionPreference
}

$JavaVersionLine = $JavaVersionOutput | Select-Object -First 1
if ($JavaExitCode -ne 0 -or [string]::IsNullOrWhiteSpace([string]$JavaVersionLine)) {
    throw "No se pudo ejecutar Java para comprobar su version. Codigo: $JavaExitCode"
}
if ([string]$JavaVersionLine -notmatch '"?(?<major>\d+)') {
    throw "No se pudo comprobar la version de Java: $JavaVersionLine"
}
if ([int]$Matches.major -lt 21) {
    throw "Se requiere JDK 21 o superior. Version encontrada: $JavaVersionLine"
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven no esta instalado o no esta en PATH."
}

$PreviousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    & mvn --batch-mode --no-transfer-progress clean verify
    $MavenExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $PreviousErrorActionPreference
}

if ($MavenExitCode -ne 0) {
    throw "La compilacion de Maven fallo con el codigo $MavenExitCode."
}

Write-Host "Bot compilado: target\isla-spider-ticket-bot.jar"
