#!/bin/sh
set -eu

if [ ! -f "./gradlew" ] || [ ! -f "./settings.gradle.kts" ]; then
    echo "Run this script from the ChatMap project root." >&2
    exit 1
fi

CHATMAP_A2A_WORKER=ollama
CHATMAP_A2A_OLLAMA_TARGET=${CHATMAP_A2A_OLLAMA_TARGET:-ollama-qwen2.5-7b}
export CHATMAP_A2A_WORKER
export CHATMAP_A2A_OLLAMA_TARGET

echo "Starting ChatMap A2A server"
echo "worker=$CHATMAP_A2A_WORKER"
echo "target=$CHATMAP_A2A_OLLAMA_TARGET"

exec ./gradlew quarkusDev --console=plain
