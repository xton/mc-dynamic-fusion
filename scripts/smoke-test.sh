#!/usr/bin/env bash
# Functional smoke test: boot a real Paper server with the DynamicFusion
# plugin installed and assert it loads cleanly. Used by CI and runnable
# locally (`make smoke`). Requires Docker.
#
# Offline-mode + ephemeral container — no auth, no persistence. This verifies
# the plugin enables, registers, and throws no errors. Actual gameplay
# (swinging the Nova Sword) is covered by human UAT via `make uat`.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

IMAGE="${MC_IMAGE:-itzg/minecraft-server:java25}"
VERSION="${MC_VERSION:-26.1.2}"
NAME="fusion-smoke-$$"
TIMEOUT="${SMOKE_TIMEOUT:-360}"

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "==> Building plugin jar"
./gradlew assemble --no-daemon -q
JAR="$(ls -1 build/libs/*.jar 2>/dev/null | head -1 || true)"
[ -n "$JAR" ] || { echo "FAIL: no jar produced in build/libs"; exit 1; }
echo "    jar: $JAR"

# In the Claude Code sandbox, add flags so the container trusts the egress CA.
source "$ROOT/scripts/sandbox-ca.sh"

echo "==> Starting Paper $VERSION ($IMAGE) with the plugin"
docker run -d --name "$NAME" \
  -e EULA=TRUE -e TYPE=PAPER -e VERSION="$VERSION" \
  -e ONLINE_MODE=FALSE -e MEMORY=2G \
  -e ENABLE_RCON=TRUE -e RCON_PASSWORD=smoke \
  ${SANDBOX_DOCKER_ARGS[@]:+"${SANDBOX_DOCKER_ARGS[@]}"} \
  -v "$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")":/data/plugins/DynamicFusion.jar:ro \
  "$IMAGE" >/dev/null

echo "==> Waiting for server ready (up to ${TIMEOUT}s)"
ready=0
end=$((SECONDS + TIMEOUT))
while [ $SECONDS -lt $end ]; do
  if [ "$(docker inspect -f '{{.State.Running}}' "$NAME" 2>/dev/null)" != "true" ]; then
    echo "FAIL: container exited early"; docker logs "$NAME" 2>&1 | tail -50; exit 1
  fi
  if docker logs "$NAME" 2>&1 | grep -q 'Done ('; then ready=1; break; fi
  sleep 5
done
[ "$ready" = 1 ] || { echo "FAIL: timed out waiting for ready"; docker logs "$NAME" 2>&1 | tail -60; exit 1; }

LOG="$(docker logs "$NAME" 2>&1)"
fail=0

echo "==> Assert: plugin enabled"
grep -q 'DynamicFusion enabled' <<<"$LOG" \
  || { echo "  MISSING: enable log line"; fail=1; }

echo "==> Assert: plugin listed via RCON"
plugins="$(docker exec "$NAME" rcon-cli plugins 2>&1 || true)"
grep -qi 'DynamicFusion' <<<"$plugins" \
  || { echo "  MISSING: DynamicFusion in /plugins -> $plugins"; fail=1; }

echo "==> Assert: no plugin load/enable errors"
if grep -E "Could not load 'plugins.*DynamicFusion|Error occurred while enabling DynamicFusion" <<<"$LOG"; then
  echo "  FOUND: plugin error in log"; fail=1
fi

echo "==> Functional self-test: run '/fusion test' (real projectile/burst vs. live world)"
docker exec "$NAME" rcon-cli fusion test >/dev/null 2>&1 || true
# The mining-ray scenario asserts asynchronously (~25 ticks after firing); give
# it a moment before reading the result sentinel from the log.
sleep 6
LOG="$(docker logs "$NAME" 2>&1)"
selftest="$(grep -F '[fusion-selftest] RESULT:' <<<"$LOG" | tail -1 || true)"
echo "  ${selftest:-<no self-test result line found>}"
if ! grep -qF '[fusion-selftest] RESULT: PASS' <<<"$LOG"; then
  echo "  FAIL: self-test did not report PASS"
  grep -F '[fusion-selftest]' <<<"$LOG" || true
  fail=1
fi

if [ "$fail" -ne 0 ]; then
  echo "SMOKE TEST FAILED"; docker logs "$NAME" 2>&1 | tail -60; exit 1
fi
echo "SMOKE TEST PASSED"
