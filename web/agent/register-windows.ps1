param([string]$NodePath = (Get-Command node.exe).Source)
$ErrorActionPreference = 'Stop'
$webDirectory = Split-Path -Parent $PSScriptRoot
$cliPath = Join-Path $PSScriptRoot 'cli.ts'
$action = New-ScheduledTaskAction -Execute $NodePath -Argument ('--experimental-strip-types "' + $cliPath + '" run-due') -WorkingDirectory $webDirectory
$triggers = @(
    (New-ScheduledTaskTrigger -Daily -At '08:00'),
    (New-ScheduledTaskTrigger -Weekly -DaysOfWeek Sunday -At '20:00'),
    (New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME)
)
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 15)
Register-ScheduledTask -TaskName 'TrackMaster Personal Assistant' -Action $action -Trigger $triggers -Settings $settings -Description 'Revisiones TrackMaster; recupera períodos al iniciar sesión.' -Force | Out-Null
Write-Host 'Programación instalada. Los horarios del sistema deben corresponder a Bogotá.'
