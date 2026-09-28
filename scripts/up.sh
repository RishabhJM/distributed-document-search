#!/usr/bin/env bash
set -e

MODE="full"
SEED=false

while [[ "$#" -gt 0 ]]; do
    case $1 in
        -Mode|--mode) MODE="$2"; shift ;;
        -Seed|--seed) SEED=true ;;
        *) echo "Unknown parameter: $1"; exit 1 ;;
    esac
    shift
done

echo "Starting DeepRunner Document Search in mode: $MODE..."

if [ "$MODE" = "full" ]; then
    docker compose --profile full up -d --build
else
    docker compose up -d postgres opensearch redis
fi

echo "Waiting for services to become healthy..."
MAX_RETRIES=30
RETRY=0

if [ "$MODE" = "full" ]; then
    until curl -s -f http://localhost:8080/health > /dev/null 2>&1 || [ $RETRY -eq $MAX_RETRIES ]; do
        echo "Waiting for API readiness... ($((RETRY+1))/$MAX_RETRIES)"
        sleep 3
        RETRY=$((RETRY+1))
    done

    if [ $RETRY -eq $MAX_RETRIES ]; then
        echo "❌ Timed out waiting for API to become ready."
        exit 1
    fi
    echo "✓ DeepRunner Document Search is ready!"
    echo "  Web UI:  http://localhost:3000"
    echo "  API:     http://localhost:8080"
fi

if [ "$SEED" = true ]; then
    echo "Seeding initial dataset..."
    ./scripts/seed.sh
fi
