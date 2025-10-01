<#
rebuild-and-restart.ps1

Rebuilds the JAR using the gradle wrapper and restarts only the 'stirling' service using the override compose.
This is a fast dev loop: build locally and restart the container without rebuilding the full image.

Usage:
  .\scripts\rebuild-and-restart.ps1
<#
rebuild-and-restart.ps1

Rebuilds the JAR using the gradle wrapper and restarts only the 'stirling' service using the override compose.
This is a fast dev loop: build locally and restart the container without rebuilding the full image.

Usage:
  .\scripts\rebuild-and-restart.ps1
#>

function Write-Info($m) { Write-Host "[INFO] $m" -ForegroundColor Cyan }
function Write-Err($m) { Write-Host "[ERROR] $m" -ForegroundColor Red }

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Write-Info "Workspace root: $root"

# Build using gradlew (prefer Windows wrapper)
$gradlewBat = Join-Path $root 'gradlew.bat'
$gradlewUnix = Join-Path $root 'gradlew'
if (Test-Path $gradlewBat) { $buildCmd = "& `"$gradlewBat`" build -x test" }
elseif (Test-Path $gradlewUnix) { $buildCmd = "& `"$gradlewUnix`" build -x test" }
else { Write-Err "gradlew not found in repo root. Run gradle build manually."; exit 2 }

try {
    Write-Info "Running: $buildCmd"
    Invoke-Expression $buildCmd
} catch {
    Write-Err "Build failed: $_"
    exit 3
}

# Copy latest built jar to ./stirling/latest/app.jar for the override mount
$builtLibs = Get-ChildItem -Path (Join-Path $root 'app\core\build\libs') -Filter '*.jar' | Sort-Object LastWriteTime -Descending
if ($builtLibs.Count -eq 0) { Write-Err "No built jars found under app/core/build/libs. Run the build first."; exit 5 }
$latestJar = $builtLibs[0].FullName
$targetDir = Join-Path $root 'stirling\latest'
if (-not (Test-Path $targetDir)) { New-Item -ItemType Directory -Path $targetDir | Out-Null }
$targetJar = Join-Path $targetDir 'app.jar'
try {
    Copy-Item -Path $latestJar -Destination $targetJar -Force
    Write-Info "Copied $latestJar -> $targetJar"
} catch {
    Write-Err "Failed to copy jar: $_"
    exit 6
}

Write-Info "Restarting 'stirling' container with override compose"
try {
    Start-Process -FilePath 'docker' -ArgumentList @('compose','-f','docker-compose.postgres.yml','-f','docker-compose.override.yml','up','-d','--no-build','--no-deps','--force-recreate','stirling') -NoNewWindow -Wait -ErrorAction Stop
    Write-Info "Restart requested"
} catch {
    Write-Err "Failed to restart container: $_"
    exit 4
}

Write-Info "Waiting for application to be ready"
& "$PSScriptRoot\docker-up.ps1" -ComposeFile 'docker-compose.postgres.yml' -TimeoutSeconds 60

exit $LASTEXITCODE
