@echo off
chcp 65001 > nul
echo ==============================================================================
echo   BUILDING RMI DISTRIBUTED INVENTORY SYSTEM
echo ==============================================================================
cd /d "%~dp0\.."
call mvn clean package
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed! Please check compiler or maven errors.
    pause
    exit /b %ERRORLEVEL%
)
echo [SUCCESS] Build completed successfully. JARs generated in server/target and client/target.
pause
