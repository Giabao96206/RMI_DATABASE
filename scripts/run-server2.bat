@echo off
chcp 65001 > nul
title Distributed Inventory - SERVER 2
echo ==============================================================================
echo   STARTING DISTRIBUTED INVENTORY - SERVER 2
echo ==============================================================================
cd /d "%~dp0\.."

if not exist "server\target\server-1.0.0.jar" (
    echo [INFO] JAR not found, building project first...
    call mvn package -DskipTests
)

java -Dfile.encoding=UTF-8 -jar server\target\server-1.0.0.jar config\server2.properties
pause
