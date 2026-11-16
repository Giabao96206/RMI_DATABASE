@echo off
chcp 65001 > nul
echo ==============================================================================
echo   SEEDING 20 INITIAL WAREHOUSE PRODUCTS
echo ==============================================================================
cd /d "%~dp0\.."

java -cp server\target\server-1.0.0.jar com.inventory.server.database.DataSeeder config\server1.properties
echo [SUCCESS] 20 sample products seeded into Server 1 database.
pause
