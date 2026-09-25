@echo off
setlocal
cd /d "%~dp0"
where javaw.exe >nul 2>nul
if errorlevel 1 (
    echo Java 8 or newer is required. Install Java and try again.
    pause
    exit /b 1
)
start "ZLT X28 Unlock" javaw.exe -jar "%~dp0dist\ZLT-X28-Unlock.jar"
