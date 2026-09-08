param(
    [string]$JavaPath = ""
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

if (-not (Test-Path ".env")) {
    throw "Falta el archivo .env. Copia .env.example como .env y configuralo."
}

if (-not (Test-Path "target\isla-spider-ticket-bot.jar")) {
    throw "Falta el JAR. Ejecuta scripts\build.ps1 primero."
}

if ([string]::IsNullOrWhiteSpace($JavaPath)) {
    $JavaFromHome = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "" }
    if ($JavaFromHome -and (Test-Path $JavaFromHome)) {
        $JavaPath = $JavaFromHome
    } else {
        $JavaCommand = Get-Command java -ErrorAction SilentlyContinue
        if (-not $JavaCommand) { throw "Java no esta disponible para la cuenta que ejecuta el bot." }
        $JavaPath = $JavaCommand.Source
    }
}
if (-not (Test-Path $JavaPath)) { throw "No existe Java en la ruta configurada: $JavaPath" }

$LogDirectory = Join-Path $ProjectRoot "logs"
New-Item -ItemType Directory -Path $LogDirectory -Force | Out-Null
$LogFile = Join-Path $LogDirectory "bot.log"
$StartedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
"[$StartedAt] Iniciando Isla Spider Ticket Bot con $JavaPath" | Tee-Object -FilePath $LogFile -Append

$PreviousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    & $JavaPath -Xms128m -Xmx512m -jar "target\isla-spider-ticket-bot.jar" 2>&1 |
        Tee-Object -FilePath $LogFile -Append
    $ExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $PreviousErrorActionPreference
}

if ($ExitCode -ne 0) { throw "El bot termino con codigo $ExitCode. Revisa $LogFile" }
