#!/usr/bin/env bash
# Start the Docker daemon inside the Claude Code cloud sandbox so the container
# test harness (`make smoke` / `make e2e` / `make uat`) can run in-session
# instead of only in GitHub CI.
#
# The sandbox ships the docker binary but does not start the daemon at boot, and
# a running daemon does not survive across sessions — so the SessionStart hook
# calls this every session. Idempotent and best-effort: it never fails the
# caller (exits 0) so it can't block session startup.
set -uo pipefail

if ! command -v dockerd >/dev/null 2>&1; then
  echo "docker: dockerd not installed; skipping"
  exit 0
fi
if docker info >/dev/null 2>&1; then
  echo "docker: daemon already running"
  exit 0
fi
if [ "$(id -u)" != "0" ]; then
  echo "docker: not root; cannot start the daemon here" >&2
  exit 0
fi

echo "docker: starting daemon..."
# Detach so the daemon outlives this script. It inherits the session env
# (HTTPS_PROXY + the agent CA in the system trust store), so image pulls go
# through the policy egress proxy — no daemon.json needed.
nohup dockerd >/tmp/dockerd.log 2>&1 &

for _ in $(seq 1 20); do
  if docker info >/dev/null 2>&1; then
    echo "docker: daemon up ($(docker version -f '{{.Server.Version}}' 2>/dev/null))"
    exit 0
  fi
  sleep 1
done

echo "docker: daemon did not become ready in time; see /tmp/dockerd.log" >&2
exit 0
