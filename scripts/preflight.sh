#!/usr/bin/env bash
set -e

echo "=== DeepRunner Document Search Service — Preflight Checks ==="

# 1. Check Docker CLI
if ! command -v docker &> /dev/null; then
    echo "❌ Error: Docker CLI not found. Please install Docker Desktop."
    exit 1
fi
echo "✓ Docker CLI is installed."

# 2. Check Docker Daemon
if ! docker info &> /dev/null; then
    echo "⚠️ Warning: Docker daemon is not currently running. Please start Docker Desktop to run containers."
else
    echo "✓ Docker daemon is active."
fi

# 3. Check Ports
PORTS=(5432 9200 6379 8080 3000)
for PORT in "${PORTS[@]}"; do
    if lsof -i :"$PORT" &> /dev/null; then
        echo "⚠️ Warning: Port $PORT is already in use by another process."
    else
        echo "✓ Port $PORT is available."
    fi
done

echo "=== Preflight checks complete ==="
