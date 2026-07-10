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
PORT="${MC_PORT:-25565}"   # host port the bot connects through (override if busy)
NAME="fusion-e2e-$$"
TIMEOUT="${E2E_TIMEOUT:-360}"
PLUGINS_DIR=""

cleanup() {
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  # itzg writes root/UID-1000-owned config files into the mount; ignore the
  # resulting permission noise (CI's /tmp is ephemeral anyway).
  [ -n "$PLUGINS_DIR" ] && rm -rf "$PLUGINS_DIR" 2>/dev/null || true
}
trap cleanup EXIT

echo "==> Building plugin jar"
./gradlew assemble --no-daemon -q
JAR="$(ls -1 build/libs/*.jar 2>/dev/null | head -1 || true)"
[ -n "$JAR" ] || { echo "FAIL: no jar produced in build/libs"; exit 1; }
echo "    jar: $JAR"

# Stage the plugin in a writable dir mounted as /data/plugins. (Mounting the jar
# as a bare file makes Docker create /data/plugins owned by root, so the itzg
# user can't write the ViaVersion download into it.)
PLUGINS_DIR="$(mktemp -d)"
cp "$JAR" "$PLUGINS_DIR/DynamicFusion.jar"
chmod -R 777 "$PLUGINS_DIR"

# In the Claude Code sandbox, add flags so the container trusts the egress CA.
source "$ROOT/scripts/sandbox-ca.sh"

# Reap orphaned e2e containers from a prior interrupted run (the cleanup trap
# only removes this run's container, so a crashed run can leave the port held).
stale="$(docker ps -aq --filter "name=fusion-e2e-" 2>/dev/null || true)"
[ -n "$stale" ] && docker rm -f $stale >/dev/null 2>&1 || true

# The bot reaches the server through a published host port. If it's already
# taken (commonly a `make uat` server left running), fail with an actionable
# hint instead of docker's opaque "port is already allocated".
holder="$(docker ps --filter "publish=$PORT" --format '{{.Names}}' 2>/dev/null | head -1 || true)"
if [ -n "$holder" ]; then
  echo "FAIL: host port $PORT is already published by container '$holder'."
  echo "      Stop it first (e.g. 'make down' if it's the UAT server), or pick"
  echo "      another port:  MC_PORT=25566 make e2e"
  exit 1
fi

echo "==> Starting Paper $VERSION with the plugin + Via (offline, creative, $BOT_USER opped)"
# node-minecraft-protocol can't speak the server's protocol directly, so the bot
# joins as 1.21.x. An OLDER client joining a NEWER server needs *ViaBackwards*
# (ViaVersion alone only bridges newer clients to older servers); both are
# auto-resolved from Modrinth for this server version.
docker run -d --name "$NAME" \
  -e EULA=TRUE -e TYPE=PAPER -e VERSION="$VERSION" \
  -e ONLINE_MODE=FALSE -e MEMORY=2G \
  -e MODE=creative -e DIFFICULTY=peaceful -e OPS="$BOT_USER" \
  -e SPAWN_PROTECTION=0 -e ALLOW_FLIGHT=TRUE \
  -e MODRINTH_PROJECTS="${MC_MODRINTH_PROJECTS:-viaversion,viabackwards}" \
  ${SANDBOX_DOCKER_ARGS[@]:+"${SANDBOX_DOCKER_ARGS[@]}"} \
  -p "$PORT":25565 \
  -v "$PLUGINS_DIR":/data/plugins \
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

# Op the bot via RCON. The OPS env op's the wrong (online) UUID in offline mode,
# so op by name over RCON instead — that resolves to the bot's offline UUID.
# Retry in the background until it lands (the bot waits on spawn for this).
echo "==> Opping $BOT_USER via RCON (background retry until it connects)"
(
  for _ in $(seq 1 40); do
    out="$(docker exec "$NAME" rcon-cli op "$BOT_USER" 2>&1 || true)"
    if echo "$out" | grep -qiE "Made .*operator|already .*operator"; then
      echo "    opped: $out"; break
    fi
    sleep 1
  done
) &
OP_PID=$!

echo "==> Running Mineflayer end-to-end scenarios"
set +e
( cd mineflayer && MC_HOST=localhost MC_PORT="$PORT" MC_BOT_USER="$BOT_USER" \
    timeout 150 node e2e.js )
rc=$?
set -e
kill "$OP_PID" >/dev/null 2>&1 || true

if [ $rc -ne 0 ]; then
  echo "E2E TEST FAILED (exit $rc)"
  echo "==> Recent server log:"
  docker logs "$NAME" 2>&1 | tail -40
  exit 1
fi
echo "E2E TEST PASSED"
