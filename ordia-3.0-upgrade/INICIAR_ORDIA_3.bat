@echo off
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0"
title Ordia 3.0.2 - Instalador validado

 echo.
 echo =============================================================
 echo   ORDIA 3.0.2 - INSTALADOR DE DOBLE CLIC
 echo =============================================================
 echo.

set "ORDIA_SCRIPT=%~dp0APLICAR_ORDIA_3.ps1"
if not exist "%ORDIA_SCRIPT%" (
  echo ERROR: No se encontro APLICAR_ORDIA_3.ps1.
  echo Extrae el ZIP completo antes de ejecutar este archivo.
  echo.
  pause
  exit /b 2
)

where powershell.exe >nul 2>&1
if errorlevel 1 (
  where pwsh.exe >nul 2>&1
  if errorlevel 1 (
    echo ERROR: PowerShell no esta disponible.
    echo Instala PowerShell o abre este paquete en Windows 10/11.
    echo.
    pause
    exit /b 3
  )
  set "PS_EXE=pwsh.exe"
) else (
  set "PS_EXE=powershell.exe"
)

 echo 1/3 Comprobando la sintaxis completa del instalador...
"%PS_EXE%" -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$errors=$null; $tokens=$null; [System.Management.Automation.Language.Parser]::ParseFile($env:ORDIA_SCRIPT,[ref]$tokens,[ref]$errors) | Out-Null; if($errors.Count -gt 0){ $errors | ForEach-Object { Write-Host ('ERROR DE SINTAXIS - linea ' + $_.Extent.StartLineNumber + ': ' + $_.Message) -ForegroundColor Red }; exit 91 }"
if errorlevel 1 (
  echo.
  echo EL INSTALADOR NO SE EJECUTO porque PowerShell encontro un error de sintaxis.
  echo Conserva esta ventana o toma una captura del mensaje anterior.
  echo.
  pause
  exit /b 91
)

 echo 2/3 Desbloqueando temporalmente el archivo...
"%PS_EXE%" -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "Unblock-File -LiteralPath $env:ORDIA_SCRIPT -ErrorAction SilentlyContinue"
if errorlevel 1 (
  echo ERROR: No se pudo desbloquear temporalmente el instalador.
  echo.
  pause
  exit /b 4
)

 echo 3/3 Validando, compilando y preparando Ordia 3.0...
 echo No cierres esta ventana.
 echo.
"%PS_EXE%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ORDIA_SCRIPT%"
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if "%EXIT_CODE%"=="0" (
  echo ORDIA 3.0 TERMINO CORRECTAMENTE.
) else (
  echo ORDIA NO PUDO TERMINAR. Codigo: %EXIT_CODE%
  echo Revisa el mensaje anterior y ORDIA-3.0-RESULTADO.txt en el Escritorio, si fue creado.
)
echo.
pause
exit /b %EXIT_CODE%
