#!/bin/bash
set -e
echo "=============================================================================="
echo "  BUILDING RMI DISTRIBUTED INVENTORY SYSTEM (LINUX / MACOS)"
echo "=============================================================================="
cd "$(dirname "$0")/.."
mvn clean package
echo "[SUCCESS] Build completed successfully."
