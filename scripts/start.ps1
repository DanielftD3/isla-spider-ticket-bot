$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

if (-not (Test-Path ".env")) {
    throw "Falta el archivo .env. Copia .env.example como .env y configuralo."
}

if (-not (Test-Path "target\isla-spider-ticket-bot.jar")) {
    throw "Falta el JAR. Ejecuta scripts\build.ps1 primero."
}

java -Xms128m -Xmx512m -jar "target\isla-spider-ticket-bot.jar"

