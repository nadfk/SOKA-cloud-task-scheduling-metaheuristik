@echo off
setlocal
cd /d "%~dp0"

echo ==========================================================
echo    SOKA - Cloud Task Scheduling Simulation Runner
echo ==========================================================

powershell -ExecutionPolicy Bypass -File "%~dp0run-demo.ps1" %*

pause
