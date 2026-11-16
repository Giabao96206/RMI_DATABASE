@echo off
chcp 65001 > nul
title Distributed Inventory - CLIENT
echo ==============================================================================
echo   STARTING DISTRIBUTED INVENTORY - JAVAFX CLIENT
echo ==============================================================================
cd /d "%~dp0\.."

if not exist "client\target\client-1.0.0.jar" (
    echo [INFO] JAR not found, building project first...
    call mvn package -DskipTests
)

java -Dfile.encoding=UTF-8 -jar client\target\client-1.0.0.jar config\client.properties
pause
