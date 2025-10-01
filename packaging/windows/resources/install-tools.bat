@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-optional-tools.ps1"
endlocal
