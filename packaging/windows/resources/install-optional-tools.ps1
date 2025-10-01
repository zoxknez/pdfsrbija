# Installs optional external tools on Windows: Ghostscript, qpdf, Tesseract OCR, LibreOffice.
# Run as Administrator after installing o0o0o0o, or let the installer expose this script.
# Silent where possible; modifies PATH via system environment.

param(
    [switch]$Quiet
)

function Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Err($msg)  { Write-Host "[ERR ] $msg" -ForegroundColor Red }

$ErrorActionPreference = 'Stop'

# Helper: Add a directory to PATH if not already present (system-wide)
function Add-ToPath([string]$dir) {
    if (-not (Test-Path $dir)) { return }
    $sysPath = [Environment]::GetEnvironmentVariable('Path','Machine')
    if ($sysPath -notmatch [Regex]::Escape($dir)) {
        Info "Dodajem u PATH: $dir"
        [Environment]::SetEnvironmentVariable('Path', ($sysPath.TrimEnd(';') + ";$dir"), 'Machine')
    } else {
        Info "PATH već sadrži: $dir"
    }
}

# Download helper
function Download-File($url, $dest) {
    Info "Preuzimam: $url"
    $wc = New-Object System.Net.WebClient
    $wc.DownloadFile($url, $dest)
}

# Install Ghostscript
function Install-Ghostscript {
    try {
        $ver = '10.04.0' # stable-ish
        $url = "https://github.com/ArtifexSoftware/ghostpdl-downloads/releases/download/gs$($ver -replace '\\.','')/gs$($ver)-x64.exe"
        $tmp = Join-Path $env:TEMP "ghostscript-$ver.exe"
        Download-File $url $tmp
        Info 'Instaliram Ghostscript (tiho)'
        Start-Process -FilePath $tmp -ArgumentList "/SILENT" -Wait -Verb RunAs
        $gsDir = (Get-ChildItem 'C:\Program Files\gs' -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
        Add-ToPath (Join-Path $gsDir 'bin')
    } catch { Warn "Ghostscript instalacija nije uspela: $_" }
}

# Install qpdf
function Install-Qpdf {
    try {
        $ver = '11.9.1'
        $zipUrl = "https://github.com/qpdf/qpdf/releases/download/release-$ver/qpdf-$ver-bin-msvc64.zip"
        $tmpZip = Join-Path $env:TEMP "qpdf-$ver.zip"
        Download-File $zipUrl $tmpZip
        $dest = 'C:\Program Files\qpdf'
        if (-not (Test-Path $dest)) { New-Item -ItemType Directory -Path $dest | Out-Null }
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory($tmpZip, $dest)
        $inner = Join-Path $dest "qpdf-$ver" | Join-Path -ChildPath 'bin'
        Add-ToPath $inner
    } catch { Warn "qpdf instalacija nije uspela: $_" }
}

# Install Tesseract OCR
function Install-Tesseract {
    try {
        $ver = '5.4.0.20240606'
        $url = "https://github.com/UB-Mannheim/tesseract/releases/download/v5.4.0/tesseract-$ver-x64-setup.exe"
        $tmp = Join-Path $env:TEMP "tesseract-$ver.exe"
        Download-File $url $tmp
        Info 'Instaliram Tesseract (tiho)'
        Start-Process -FilePath $tmp -ArgumentList "/SILENT" -Wait -Verb RunAs
        Add-ToPath 'C:\Program Files\Tesseract-OCR'
    } catch { Warn "Tesseract instalacija nije uspela: $_" }
}

# Install LibreOffice (for soffice/unoconvert)
function Install-LibreOffice {
    try {
        $ver = '24.2.4'
        $url = "https://download.documentfoundation.org/libreoffice/stable/$ver/win/x86_64/LibreOffice_${ver}_Win_x86-64.msi"
        $tmp = Join-Path $env:TEMP "libreoffice-$ver.msi"
        Download-File $url $tmp
        Info 'Instaliram LibreOffice (tiho)'
        Start-Process msiexec.exe -ArgumentList "/i `"$tmp`" /qn" -Wait -Verb RunAs
        Add-ToPath 'C:\Program Files\LibreOffice\program'
    } catch { Warn "LibreOffice instalacija nije uspela: $_" }
}

# Main
Info 'Počinjem instalaciju opcionih alata (Ghostscript, qpdf, Tesseract, LibreOffice)'
Install-Ghostscript
Install-Qpdf
Install-Tesseract
Install-LibreOffice

Info 'Gotovo. Možda je potrebno da odjavite/prijavite se ili restartujete radi osvežavanja PATH-a.'
if (-not $Quiet) { Pause }
