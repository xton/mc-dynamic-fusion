#!/usr/bin/env bash
# One-time cache warm for running the Docker test harness in the Claude Code
# cloud sandbox. Run it once per environment (or point the environment "setup
# script" at it in claude.ai/settings) — the results (pulled image, Gradle and
# npm caches) are written to disk and cached across sessions, so later `make
# smoke` / `make e2e` runs skip the slow downloads.
#
# Requires the environment's network to allow the hosts in docs/cloud-sandbox.md
# (simplest: set the environment to full internet access).
set -uo pipefail
cd "$(dirname "$0")/.."

echo "==> Starting the Docker daemon"
./scripts/docker-up.sh

echo "==> Pre-pulling the server image (cached across sessions)"
docker pull "${MC_IMAGE:-itzg/minecraft-server:java25}" \
  || echo "  pull failed — check the network allowlist (docs/cloud-sandbox.md)"

echo "==> Warming Gradle build cache"
./gradlew --no-daemon assemble || echo "  gradle warm skipped"

echo "==> Warming Mineflayer (e2e) deps"
[ -d mineflayer ] && ( cd mineflayer && npm install --no-audit --no-fund ) \
  || echo "  npm warm skipped"

echo "cloud-setup done — 'make smoke' and 'make e2e' should now run in-session."
