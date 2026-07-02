# mc-dynamic-fusion
Can we do a Noita to Minecraft?

A PaperMC plugin for a family server: combine a **Target** item with an
**Ingredient** item to create weapons with emergent, composable magical
properties. See [`docs/design.md`](docs/design.md) for the full design.

## Status: roadmap complete (Phases 0–5)

Admins can spawn any combination for testing with
`/fusion give <player> <base> <MODIFIER...>`.

Fuse a weapon (Target) with an ingredient (Ingredient → consumed), then swing
to unleash it. **A weapon is a projectile (flight) + a payload (bursts)**,
Noita-style. Modifiers come in two kinds:

- **Emitters** add a concrete element — a burst delivered where the shot lands.
- **Transforms** modify the *nearest preceding* element (RPN, apply-to-previous):
  they scale a burst, or shape the projectile's flight. A transform with nothing
  before it does nothing.

| | Modifier | Sample ingredients | Effect |
|---|---|---|---|
| **emit** | **Push** | Nether Star, Piston | a knockback burst where it lands |
| **emit** | **Damage** | Fire Charge, Flint | a damaging burst where it lands |
| *xf (aoe)* | **Expand** | Heart of the Sea, Magma Cream | ×radius of the previous burst |
| *xf (aoe)* | **Amplify** | Glowstone Dust, Blaze Powder | ×force/damage of the previous burst |
| *xf (aoe)* | **Chain** | String, Echo Shard | previous burst hops to more entities |
| *xf (aoe)* | **Invert** | Fermented Spider Eye | previous push implodes — two cancel |
| *xf (aoe)* | **Persist** | Blaze Rod, Dragon's Breath | previous burst lingers and re-pulses |
| *xf (fly)* | **Multishot** | Rabbit's Foot, Slime Ball | launches extra projectiles |
| *xf (fly)* | **Spread** | Feather, Sugar | scatters the aim |
| *xf (fly)* | **Pierce** | Arrow, Quartz | punches through blocks & every entity in its path |
| *xf (fly)* | **Lifetime** | Gunpowder, Redstone | flies farther before it expires |
| *xf (fly)* | **Mining** | Amethyst Shard | a short, fast piercing ray that bores soft blocks |

Because these are small primitives, weapons **compose**: a nova is
`Push · Expand · Expand`; a fireball is `Damage · Amplify`; a shotgun is
`Damage · Multishot · Spread`; a ray gun is `Pierce · Lifetime`; a mining laser
is `Mining`. Many ingredients are **bundles** — a ready-made recipe in one item
(TNT = `Damage · Expand · Expand`, End Crystal = the works). See
[`latent_registry.yml`](src/main/resources/latent_registry.yml) for the roster.
Bounce and gravity are seamed for later builds (grenades, cluster bombs).

**Fused bows** become wands: releasing fires the same projectiles downrange,
their speed scaled by draw force (so a Multishot bow fans a volley). Fusing can
cost XP levels (`fusion.cost`, off by default).

## Requirements

- **Java 25** (Minecraft 26.1+ requires it)
- **Paper 26.1.2+** server (build pins `26.1.2.build.72-stable`, the latest
  stable line; a plugin built against it runs fine on newer 26.x servers)

## Build

```bash
./gradlew build
```

The plugin jar lands in `build/libs/`. Drop it in your server's `plugins/`
folder and restart.

> Note: building needs access to Paper's Maven repo (`repo.papermc.io`). If
> you're on a restricted network you may need to allowlist it.

## Try it in-game

Two ways to fuse:

- **Fusion Machine** — `/fusion machine` (op) gives a **Fusion Machine** (an
  enchanting table). Place it, right-click, and use it like an anvil: **left
  slot = Target** (kept/upgraded), **right slot = Ingredient** (consumed), take
  the result.
- **Quick command** — `/fusion fuse` (op): main hand = Target, off hand =
  Ingredient.

Then swing the fused weapon near some mobs. **Stack it:** fuse again with
another ingredient to compound effects (Nova + Expand + Expand = a huge shove);
order and duplicates both matter. A fused weapon can itself be the Ingredient,
handing its whole modifier list to the next Target.

## Tests

```bash
./gradlew test      # or: make test
```

- **Pure unit tests** (no server): modifier logic, latent registry, lore,
  cooldown timing.
- **MockBukkit tests**: PDC round-trip and the fusion engine.

## Functional smoke test (Docker)

Boots a real Paper server with the plugin installed and asserts it loads
cleanly (enables, is listed, no errors). Requires Docker. Runs in CI and
locally:

```bash
make smoke
```

It then runs an **in-process functional self-test** — `/fusion test`
(console/op only) drives the *real* projectile and burst code against the live
world and asserts the mechanics MockBukkit can't reach: a PUSH burst knocks a
mob back, a DAMAGE burst lowers its health, a MINING ray breaks a run of blocks,
and a flight-only shot delivers an empty payload. Results are logged as
`[fusion-selftest] RESULT: PASS|FAIL`; the smoke script fails the build unless it
sees PASS. You can run it by hand on any server too (`/fusion test`) — it spawns
a couple of disposable mobs, so use it on a scratch world.

## End-to-end test (Mineflayer bot)

Where the self-test drives the code directly, the end-to-end test drives the
**real player input path** — a live bot with a real fused item in hand, sending
real client events through our Bukkit listeners. It boots Paper (with
ViaVersion + ViaBackwards, so a 1.21.x bot — the newest protocol the bot library
speaks — can bridge up to the newer server), connects a
[Mineflayer](https://github.com/PrismarineJS/mineflayer) bot, and asserts:

- **swing** — a fused mining sword, `swingArm` at a dirt wall → the wall gets
  carved (proves `PlayerAnimationEvent` → weapon fires a projectile);
- **bow** — a fused mining bow, draw + release → the wall gets carved **and** no
  vanilla arrow entity spawns (proves the bow override replaces the arrow).

Requires Docker + Node. Runs in CI and locally:

```bash
make e2e
```

## Local UAT — play it yourself

Spin up a real Paper server with the plugin, joinable from your own Minecraft
client, so you can swing the Nova Sword for real.

```bash
cp docker/.env.example docker/.env   # then set MC_USERNAME to your username
make uat                             # builds the jar and starts localhost:25565
```

Then:

1. Connect your Minecraft client to **localhost**. The server runs Paper
   26.1.2 with **ViaVersion**, so newer clients (e.g. 26.2) connect fine —
   you don't need a client that exactly matches the server version.
2. You're opped on join. Give yourself the ingredients:
   `/give @s minecraft:diamond_sword` and `/give @s minecraft:nether_star`.
3. Sword in main hand, Nether Star in off hand (press **F** to swap), then
   `/fusion fuse` (or use a `/fusion machine`).
4. Swing the Nova Sword near some mobs and watch them fly outward.

Iterate after code changes with `make rebuild` (rebuilds the jar and restarts
the server), `make logs` to tail output, and `make down` to stop it.

> Automated *gameplay* assertions (a bot that joins and fuses) aren't included
> yet: headless clients like mineflayer don't speak the 26.x protocol at the
> time of writing. The smoke test covers clean loading; human UAT covers the
> fun part.
