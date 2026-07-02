#!/usr/bin/env bash
# End-to-end test: boot a real Paper server with the DynamicFusion plugin, then
# connect a Mineflayer bot that drives the real player input path (arm-swing, bow
# draw/release) and asserts the weapon fires. Covers what the in-process
# `/fusion test` can't. Used by CI and runnable locally (`make e2e`). Requires
# Docker + Node.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

IMAGE="${MC_IMAGE:-itzg/minecraft-server:java25}"
VERSION="${MC_VERSION:-26.1.2}"
BOT_USER="${MC_BOT_USER:-FusionBot}"
NAME="fusion-e2e-$$"
TIMEOUT="${E2E_TIMEOUT:-360}"

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "==> Building plugin jar"
./gradlew assemble --no-daemon -q
JAR="$(ls -1 build/libs/*.jar 2>/dev/null | head -1 || true)"
[ -n "$JAR" ] || { echo "FAIL: no jar produced in build/libs"; exit 1; }
echo "    jar: $JAR"

echo "==> Starting Paper $VERSION with the plugin (offline, creative, $BOT_USER opped)"
docker run -d --name "$NAME" \
  -e EULA=TRUE -e TYPE=PAPER -e VERSION="$VERSION" \
  -e ONLINE_MODE=FALSE -e MEMORY=2G \
  -e MODE=creative -e DIFFICULTY=peaceful -e OPS="$BOT_USER" \
  -e SPAWN_PROTECTION=0 \
  -p 25565:25565 \
  -v "$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")":/data/plugins/DynamicFusion.jar:ro \
  "$IMAGE" >/dev/null

echo "==> Waiting for server ready (up to ${TIMEOUT}s)"
end=$((SECONDS + TIMEOUT))
ready=0
while [ $SECONDS -lt $end ]; do
  if [ "$(docker inspect -f '{{.State.Running}}' "$NAME" 2>/dev/null)" != "true" ]; then
    echo "FAIL: container exited early"; docker logs "$NAME" 2>&1 | tail -50; exit 1
  fi
  if docker logs "$NAME" 2>&1 | grep -q 'Done ('; then ready=1; break; fi
  sleep 5
done
[ "$ready" = 1 ] || { echo "FAIL: timed out waiting for ready"; docker logs "$NAME" 2>&1 | tail -60; exit 1; }
sleep 3 # let the world settle a beat before the bot joins

echo "==> Installing bot deps"
( cd mineflayer && npm install --no-audit --no-fund --silent )

echo "==> Running Mineflayer end-to-end scenarios"
set +e
( cd mineflayer && MC_HOST=localhost MC_PORT=25565 MC_BOT_USER="$BOT_USER" \
    timeout 150 node e2e.js )
rc=$?
set -e

if [ $rc -ne 0 ]; then
  echo "E2E TEST FAILED (exit $rc)"
  echo "==> Recent server log:"
  docker logs "$NAME" 2>&1 | tail -40
  exit 1
fi
echo "E2E TEST PASSED"
