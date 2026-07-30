@echo off
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0"
title Diagnostico Ordia 3.0

echo ORDIA 3.0.2 - DIAGNOSTICO
echo =======================
echo.

set "REPO=C:\Users\wsepulveda\Documents\GitHub\ordia-android"
if defined ORDIA_REPO set "REPO=%ORDIA_REPO%"

echo Carpeta del paquete: %~dp0
echo Repositorio esperado: %REPO%
echo.

where powershell.exe >nul 2>&1 && echo [OK] PowerShell || echo [FALTA] PowerShell
where git.exe >nul 2>&1 && echo [OK] Git || echo [FALTA] Git
where java.exe >nul 2>&1 && echo [OK] Java || echo [FALTA] Java
where python.exe >nul 2>&1 && echo [OPCIONAL] Python disponible || (where py.exe >nul 2>&1 && echo [OPCIONAL] Python Launcher disponible || echo [OPCIONAL] Python no necesario, se usa validacion nativa)
where keytool.exe >nul 2>&1 && echo [OK] keytool || echo [FALTA] keytool
where adb.exe >nul 2>&1 && echo [OPCIONAL] ADB disponible || echo [OPCIONAL] ADB no encontrado
where gh.exe >nul 2>&1 && echo [OPCIONAL] GitHub CLI disponible || echo [OPCIONAL] GitHub CLI no encontrado

if exist "%REPO%\gradlew.bat" (
  echo [OK] Proyecto Ordia localizado
) else (
  echo [FALTA] No se encontro %REPO%\gradlew.bat
  echo Puedes definir otra ruta con: set ORDIA_REPO=C:\ruta\ordia-android
)



echo.
echo Comprobando sintaxis de APLICAR_ORDIA_3.ps1...
set "ORDIA_SCRIPT=%~dp0APLICAR_ORDIA_3.ps1"
if exist "%ORDIA_SCRIPT%" (
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$errors=$null; $tokens=$null; [System.Management.Automation.Language.Parser]::ParseFile($env:ORDIA_SCRIPT,[ref]$tokens,[ref]$errors) | Out-Null; if($errors.Count -eq 0){Write-Host '[OK] Sintaxis PowerShell valida' -ForegroundColor Green; exit 0}; $errors | ForEach-Object {Write-Host ('[ERROR] linea ' + $_.Extent.StartLineNumber + ': ' + $_.Message) -ForegroundColor Red}; exit 1"
) else (
  echo [FALTA] APLICAR_ORDIA_3.ps1
)

echo.
pause
