#!/bin/bash
echo "=============================================================================="
echo "  STARTING DISTRIBUTED INVENTORY - JAVAFX CLIENT"
echo "=============================================================================="
cd "$(dirname "$0")/.."

if [ ! -f "client/target/client-1.0.0.jar" ]; then
    echo "[INFO] JAR not found, building project first..."
    mvn package -DskipTests
fi

java -Dfile.encoding=UTF-8 -jar client/target/client-1.0.0.jar config/client.properties
