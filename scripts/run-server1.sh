#!/bin/bash
echo "=============================================================================="
echo "  STARTING DISTRIBUTED INVENTORY - SERVER 1"
echo "=============================================================================="
cd "$(dirname "$0")/.."

if [ ! -f "server/target/server-1.0.0.jar" ]; then
    echo "[INFO] JAR not found, building project first..."
    mvn package -DskipTests
fi

java -Dfile.encoding=UTF-8 -jar server/target/server-1.0.0.jar config/server1.properties
