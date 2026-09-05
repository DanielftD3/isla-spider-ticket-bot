$ErrorActionPreference = "Stop"

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java no esta instalado. Instala JDK 21 y vuelve a intentarlo."
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven no esta instalado o no esta en PATH."
}

mvn clean package
Write-Host "Bot compilado: target\isla-spider-ticket-bot.jar"

