# Run by the DolfinServer-WSL startup task, without an interactive Windows login.
param(
    [string]$Distribution = 'Ubuntu',
    [string]$LinuxRepository = '/srv/dolfin'
)
$ErrorActionPreference = 'Stop'
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$logDirectory = Join-Path $repository 'runtime/host'
New-Item -ItemType Directory -Force $logDirectory | Out-Null
Start-Transcript -Path (Join-Path $logDirectory 'startup.log') -Append
try {
    while ($true) {
        try {
            & wsl.exe -d $Distribution -u root -- sh -lc "systemctl start docker && cd '$LinuxRepository' && docker compose -f deploy/crafty/compose.yaml up -d"
            if ($LASTEXITCODE -ne 0) { throw "WSL/Docker startup failed: $LASTEXITCODE" }
            $addresses = (& wsl.exe -d $Distribution -u root -- hostname -I) -split '\s+'
            $address = $addresses | Where-Object { $_ -match '^\d+\.\d+\.\d+\.\d+$' } | Select-Object -First 1
            if (-not $address) { throw 'No WSL IPv4 address found' }
            foreach ($port in @(25565, 8443)) {
                & netsh.exe interface portproxy set v4tov4 "listenport=$port" listenaddress=0.0.0.0 "connectport=$port" "connectaddress=$address"
                if ($LASTEXITCODE -ne 0) {
                    & netsh.exe interface portproxy add v4tov4 "listenport=$port" listenaddress=0.0.0.0 "connectport=$port" "connectaddress=$address"
                    if ($LASTEXITCODE -ne 0) { throw "Port forwarding failed: $port" }
                }
            }
            Write-Output "$(Get-Date -Format o) Docker/Crafty ready at WSL address $address; waiting for WSL shutdown."
            # systemd services alone do not keep a WSL distribution alive.
            & wsl.exe -d $Distribution -u root -- sleep infinity
            throw 'WSL keepalive exited; retrying startup'
        } catch {
            Write-Output "$(Get-Date -Format o) $_"
            Start-Sleep -Seconds 15
        }
    }
} finally {
    Stop-Transcript
}
