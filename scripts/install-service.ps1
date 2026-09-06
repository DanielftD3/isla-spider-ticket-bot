# Ejecutar PowerShell como Administrador.
# Crea una tarea programada que inicia el bot al arrancar Windows.
$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$StartScript = Join-Path $PSScriptRoot "start.ps1"

$JavaFromHome = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "" }
if ($JavaFromHome -and (Test-Path $JavaFromHome)) {
    $JavaExecutable = $JavaFromHome
} else {
    $JavaCommand = Get-Command java -ErrorAction SilentlyContinue
    if (-not $JavaCommand) { throw "Java no esta instalado o no esta disponible en PATH." }
    $JavaExecutable = $JavaCommand.Source
}

$Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$StartScript`" -JavaPath `"$JavaExecutable`""
$Action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $Arguments -WorkingDirectory $ProjectRoot
$Trigger = New-ScheduledTaskTrigger -AtStartup
$Settings = New-ScheduledTaskSettingsSet -RestartCount 99 -RestartInterval (New-TimeSpan -Minutes 1) `
    -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Seconds 0)
$Principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
Register-ScheduledTask -TaskName "IslaSpiderTicketBot" -Action $Action -Trigger $Trigger -Settings $Settings -Principal $Principal -Force
Write-Host "Servicio instalado como tarea programada: IslaSpiderTicketBot"
Write-Host "Java fijado en: $JavaExecutable"
Write-Host "Logs: $ProjectRoot\logs\bot.log"
