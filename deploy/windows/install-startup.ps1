# Run in elevated PowerShell after Docker Engine is provisioned in Ubuntu WSL.
param(
    [string]$Distribution = 'Ubuntu',
    [string]$LinuxRepository = '/srv/dolfin'
)
$ErrorActionPreference = 'Stop'
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
if (-not ([Security.Principal.WindowsPrincipal]$identity).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Run this script from an elevated PowerShell session.'
}
$script = Join-Path $PSScriptRoot 'start-crafty.ps1'
$arguments = "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$script`" -Distribution `"$Distribution`" -LinuxRepository `"$LinuxRepository`""
$action = New-ScheduledTaskAction -Execute "$env:SystemRoot/System32/WindowsPowerShell/v1.0/powershell.exe" -Argument $arguments
$trigger = New-ScheduledTaskTrigger -AtStartup
$trigger.Delay = 'PT20S'
# S4U runs under the distribution owner's profile, with no stored password or login.
$principal = New-ScheduledTaskPrincipal -UserId $identity.Name -LogonType S4U -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName 'DolfinServer-WSL' -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Description 'Starts Ubuntu Docker Engine and Crafty before login; keeps WSL running and forwards Minecraft/Crafty ports.' -Force | Out-Null
Set-Service iphlpsvc -StartupType Automatic
Start-Service iphlpsvc
if (-not (Get-NetFirewallRule -Name 'DolfinServer-Minecraft' -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -Name 'DolfinServer-Minecraft' -DisplayName 'DolfinServer Minecraft TCP' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 25565 | Out-Null
}
if (-not (Get-NetFirewallRule -Name 'DolfinServer-Crafty' -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -Name 'DolfinServer-Crafty' -DisplayName 'DolfinServer Crafty HTTPS (LAN and Tailscale)' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8443 -RemoteAddress LocalSubnet,100.64.0.0/10 | Out-Null
}
Start-ScheduledTask -TaskName 'DolfinServer-WSL'
Get-ScheduledTask -TaskName 'DolfinServer-WSL' | Select-Object TaskName,State
