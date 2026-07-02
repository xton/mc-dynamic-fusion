# Running the Docker test harness in the Claude Code cloud sandbox

The functional harness (`make smoke`, `make e2e`, `make uat`) boots a real Paper
server in Docker. It normally runs in GitHub CI, so each iteration is a
push-and-wait. Claude Code on the web now ships Docker in its sandbox, so the
same harness can run **in-session** — no CI round-trip.

Two things make it work: the repo starts the Docker daemon for you each session,
and you widen the environment's network once in settings.

## What the repo already does

- **`.claude/hooks/session-start.sh`** (SessionStart hook) starts the Docker
  daemon every web session via **`scripts/docker-up.sh`** (the daemon does not
  survive across sessions, so it must start each time). It also installs JDK 25
  and registers the Gradle toolchain. Both are best-effort and never block
  startup.
- **`scripts/cloud-setup.sh`** (`make cloud-setup`) is a one-time cache warm:
  it pre-pulls the server image and warms the Gradle + Mineflayer caches. The
  results are written to disk and **cached across sessions**, so later runs skip
  the slow downloads. Run it once, or point the environment "setup script" at it.

## What you configure once (claude.ai/settings → your environment)

### 1. Network access

The default *Trusted* allowlist does **not** cover everything the harness pulls.
Simplest: set the environment to **Full internet access**. If you prefer a
tight allowlist instead, add these (verified against the running harness):

| Host | For |
|---|---|
| `registry-1.docker.io`, `auth.docker.io`, `production.cloudfront.docker.com` | Docker Hub image pull (registry + auth + blob CDN) |
| `piston-data.mojang.com`, `piston-meta.mojang.com`, `libraries.minecraft.net` | Minecraft server jar + libraries (Paper downloads these at boot) |
| `api.papermc.io`, `fill.papermc.io`, `repo.papermc.io` | Paper build/download |
| `api.modrinth.com`, `cdn.modrinth.com` | ViaVersion / ViaBackwards (`make e2e` only) |
| `registry.npmjs.org` | Mineflayer deps (`make e2e`; usually already reachable) |

> The Docker Hub blob CDN (`production.cloudfront.docker.com`) and the Mojang /
> Modrinth hosts are the ones the default allowlist misses — an image pull gets
> a 403 on the CDN, and the server can't fetch its libraries. Full internet is
> the low-maintenance choice (Docker's CDN hostnames can change).

### 2. (Optional) Setup script

Point the environment's setup script at `scripts/cloud-setup.sh` so the image is
pre-pulled and cached the first time. Otherwise the image is pulled on demand the
first time you run `make smoke`.

## Using it

Once the network is open and the daemon is up (automatic each session):

```bash
make smoke   # boots Paper + plugin, runs the in-process /fusion self-test
make e2e     # + Mineflayer bot: swing / bow / anvil-GUI scenarios
make uat     # interactive server at localhost:25565
```

`docker-up.sh` runs automatically via the hook; run `make docker-up` by hand if
you ever need to (re)start the daemon.

## Notes & limitations

- **Daemon, not image, is the per-session cost.** The daemon is restarted each
  session (cheap). The pulled image and the downloaded Paper/library binaries
  live in Docker's storage, which is out-of-repo disk and **is** cached across
  sessions — so only the first run is slow.
- **The repo is re-cloned each session.** Anything under the working tree
  (including `docker/data/`) is wiped. The compose file (`make uat`) binds the
  server's `/data` to `docker/data/`, so a fresh session re-downloads Paper for
  `uat`. To avoid that, move `uat` data to a named Docker volume (a follow-up
  optimization; the smoke/e2e scripts already use ephemeral per-run containers).
- **Verified:** the daemon starts in the sandbox and the Gradle build runs.
  End-to-end `make smoke` / `make e2e` need the network widening above — until
  then the image pull 403s on the Docker Hub CDN.
