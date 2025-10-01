<#
shutdown-app.ps1
Stops application services, closes ports, kills Java processes, and optionally removes H2 DB files.

Usage:
  .\shutdown-app.ps1                # uses default port 8080 and will prompt before killing Java processes
  .\shutdown-app.ps1 -Ports 8080,9090 -Force -RemoveDbFiles

What it does:
  - Attempts to stop processes listening on specified ports (default 8080).
  - Gracefully stops Java processes matching common Stirling-PDF identifiers, then force-kills if requested.
  - Optionally removes H2 DB files found under ./configs (asks for confirmation unless -Force is used).

Note: Run in an elevated PowerShell session if you need to kill system-level processes.
#>

param(
    [int[]] $Ports = @(8080),
    [switch] $Force,
    [switch] $RemoveDbFiles,
    [switch] $DryRun,
    [switch] $DockerDown,
    [string] $DockerComposeFile = 'docker-compose.postgres.yml'
)

function Write-Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-Err($msg) { Write-Host "[ERROR] $msg" -ForegroundColor Red }

Write-Info "Shutdown script started at $(Get-Date -Format o)"

# 1) Stop processes listening on given ports
foreach ($p in $Ports) {
    Write-Info "Checking for processes listening on port $p..."
    try {
        $listeners = Get-NetTCPConnection -LocalPort $p -ErrorAction Stop | Select-Object -Unique OwningProcess
    } catch {
        Write-Warn "Unable to query NetTCPConnection for port $p. Falling back to netstat parsing."
        $netstat = netstat -ano | Select-String ":$p\s"
        $listeners = @()
        foreach ($line in $netstat) {
            $parts = $line -split '\s+' | Where-Object { $_ -ne '' }
            $foundPid = $parts[-1]
            $listeners += [PSCustomObject]@{ OwningProcess = [int]$foundPid }
        }
    }

    if ($listeners -and $listeners.Count -gt 0) {
        foreach ($l in $listeners) {
            $foundPid = $l.OwningProcess
            Write-Info "Found process PID $foundPid listening on port $p"
            if ($DryRun) { Write-Info "DryRun: Would stop PID $foundPid"; continue }
                try {
                    # Try graceful stop via Stop-Process -Id (first)
                    Stop-Process -Id $foundPid -ErrorAction Stop -Confirm:$false
                    Write-Info "Stopped process $foundPid"
                } catch {
                    Write-Warn "Could not stop process $foundPid gracefully. Attempting Force kill."
                    try { Stop-Process -Id $foundPid -Force -ErrorAction Stop -Confirm:$false; Write-Info "Force killed $foundPid" } catch { Write-Err ("Failed to kill PID {0}: {1}" -f $foundPid, $_) }
                }
        }
    } else {
        Write-Info "No process found listening on port $p"
    }
}

# 2) Try to stop Java processes used by the app (look for Stirling-PDF, stirling, or java -jar app names)
$javaCandidates = Get-CimInstance Win32_Process | Where-Object { $_.Name -match '(?i)java' -or $_.CommandLine -match '(?i)stirling' } | Select-Object ProcessId,Name,CommandLine
if ($javaCandidates.Count -eq 0) { Write-Info "No Java processes found matching 'java' or 'stirling'" }
else {
    Write-Info "Found Java-related processes:"
    $javaCandidates | ForEach-Object { Write-Host "  PID: $($_.ProcessId)  Cmd: $($_.CommandLine)" }

    if ($DryRun) { Write-Info "DryRun: would attempt to stop the above Java processes" }
    else {
        foreach ($proc in $javaCandidates) {
            $procId = $proc.ProcessId
            # Prefer to attempt graceful shutdown if the command contains 'stirling' or 'app.jar' via sending a CTRL+C to the process group is tricky in PowerShell.
                try {
                    if ($Force) {
                        Stop-Process -Id $procId -Force -ErrorAction Stop
                        Write-Info "Force killed Java PID $procId"
                    } else {
                        Stop-Process -Id $procId -ErrorAction Stop -Confirm:$false
                        Write-Info "Stopped Java PID $procId"
                    }
                } catch {
                    Write-Warn "Could not stop Java PID $procId gracefully: $_. Attempting force kill."
                    try { Stop-Process -Id $procId -Force -ErrorAction Stop; Write-Info "Force killed Java PID $procId" } catch { Write-Err ("Failed to kill Java PID {0}: {1}" -f $procId, $_) }
                }
        }
    }
}

# 3) Optionally remove H2 DB files under ./configs
# (repo root is detected earlier as $repoRoot)
# In case script is launched elsewhere, prefer repo root detection: look for 'README.md' up to 4 levels
function Find-RepoRoot {
    $cur = (Get-Location).Path
    for ($i=0; $i -lt 5; $i++) {
        if (Test-Path (Join-Path $cur 'README.md')) { return $cur }
        $cur = Split-Path $cur -Parent
    }
    return (Get-Location).Path
}
$repoRoot = Find-RepoRoot
$configs = Join-Path $repoRoot 'configs'
$h2Files = @()
if (Test-Path $configs) {
    $h2Files = Get-ChildItem -Path $configs -Recurse -Include '*.mv.db','*.h2.db','stirling-pdf*' -File -ErrorAction SilentlyContinue
}

if ($h2Files.Count -gt 0) {
    Write-Info ("Found H2 DB files under {0}:" -f $configs)
    $h2Files | ForEach-Object { Write-Host "  $($_.FullName)" }

    if ($RemoveDbFiles) {
        $doRemove = $true
    } else {
        $Y = $null
        while ($Y -notin @('y','n')) {
            $Y = Read-Host "Remove these DB files? (y/n)"
        }
        $doRemove = ($Y -eq 'y')
    }

    if ($doRemove) {
        foreach ($f in $h2Files) {
            if ($DryRun) { Write-Info "DryRun: Would remove $($f.FullName)"; continue }
            try { Remove-Item -Path $f.FullName -Force -ErrorAction Stop; Write-Info "Removed $($f.FullName)" } catch { Write-Err "Failed to remove $($f.FullName): $_" }
        }
    } else { Write-Info "Skipping DB file removal" }
} else {
    Write-Info "No H2 DB files found under $configs"
}

# 4) Optionally bring down Docker compose (run from the repository root)
if ($DockerDown) {
    Write-Info "Docker down requested for compose file: $DockerComposeFile"
    if ($DryRun) {
        Write-Info "DryRun: would run 'docker compose -f $DockerComposeFile down --volumes --remove-orphans' in repository root: $repoRoot"
    } else {
            try {
            $dockerArgs = @('compose','-f',$DockerComposeFile,'down','--volumes','--remove-orphans')
            # Run docker compose down from the repo root so relative compose paths resolve correctly
            $proc = Start-Process -FilePath 'docker' -ArgumentList $dockerArgs -WorkingDirectory $repoRoot -NoNewWindow -Wait -PassThru -ErrorAction Stop
            Write-Info "Docker compose down completed (exit code $($proc.ExitCode))"
        } catch {
            Write-Err "Failed to run docker compose down: $_"
        }
    }
} else {
    Write-Info "DockerDown not requested; skipping docker compose down"
}

Write-Info "Shutdown script finished at $(Get-Date -Format o)"

# Exit code 0
exit 0
