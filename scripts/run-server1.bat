@echo off
chcp 65001 > nul
title Distributed Inventory - SERVER 1
echo ==============================================================================
echo   STARTING DISTRIBUTED INVENTORY - SERVER 1
echo ==============================================================================
cd /d "%~dp0\.."

if not exist "server\target\server-1.0.0.jar" (
    echo [INFO] JAR not found, building project first...
    call mvn package -DskipTests
)

java -Dfile.encoding=UTF-8 -jar server\target\server-1.0.0.jar config\server1.properties
pause
