param(
    [string]$TaskName = "AI Rental Weekly Retrain",
    [string]$PythonExe = "python",
    [string]$DayOfWeek = "Sunday",
    [int]$AtHour = 2,
    [int]$MaxPages = 20,
    [int]$MogiMaxDetails = 450,
    [int]$Workers = 6
)

$ErrorActionPreference = "Stop"

$AutomationDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AiRentalDir = Split-Path -Parent $AutomationDir
$ScriptPath = Join-Path $AiRentalDir "automation\retrain_weekly.py"

if (-not (Test-Path $ScriptPath)) {
    throw "Cannot find retrain script: $ScriptPath"
}

$Arguments = @(
    "automation\retrain_weekly.py",
    "--max-pages", $MaxPages,
    "--mogi-max-details", $MogiMaxDetails,
    "--workers", $Workers
) -join " "

$Action = New-ScheduledTaskAction `
    -Execute $PythonExe `
    -Argument $Arguments `
    -WorkingDirectory $AiRentalDir

$Trigger = New-ScheduledTaskTrigger `
    -Weekly `
    -DaysOfWeek $DayOfWeek `
    -At ([datetime]::Today.AddHours($AtHour))

$Settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $Action `
    -Trigger $Trigger `
    -Settings $Settings `
    -Description "Crawl rental listings and retrain the AI rental price model every 7 days." `
    -Force | Out-Null

Write-Host "Registered task '$TaskName'."
Write-Host "Working directory: $AiRentalDir"
Write-Host "Command: $PythonExe $Arguments"
