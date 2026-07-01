# mc-dynamic-fusion
Can we do a Noita to Minecraft?

A PaperMC plugin for a family server: combine a **Target** item with an
**Ingredient** item to create weapons with emergent, composable magical
properties. See [`docs/design.md`](docs/design.md) for the full design.

## Status: roadmap complete (Phases 0–5)

Admins can spawn any combination for testing with
`/fusion give <player> <base> <MODIFIER...>`.

Fuse a weapon (Target) with an ingredient (Ingredient → consumed), then swing
to unleash an effect. **Everything is a projectile** (Noita-style): a swing
launches one or more short, fast bolts that fly, then **trigger a burst where
they land**. Modifiers shape both the flight and the burst, and **re-fusing
stacks them** (duplicates compound):

| Modifier | Ingredients | Effect |
|---|---|---|
| **Nova** | Nether Star | all-directions shove burst where the shot triggers |
| **Expand** | Heart of the Sea, Magma Cream | widens the burst (stacks bigger) |
| **Chain** | String, Echo Shard | hops to nearby entities after the burst |
| **Multishot** | Rabbit's Foot, Slime Ball, Chorus Fruit | launches extra projectiles |
| **Spread** | Feather, Sugar | scatters the aim (Multishot + Spread = shotgun) |
| **Pierce** | Arrow, Quartz | punches through soft blocks & every entity in its path |
| **Lifetime** | Gunpowder | longer lifetime — the shot flies farther before it triggers |
| **Mining** | Amethyst Shard | a short, fast piercing ray that bores soft blocks |
| **Invert** | Fermented Spider Eye | implodes (pulls inward) — two cancel |
| **Persist** | Blaze Rod, Dragon's Breath | leaves a lingering pulsing field |

Because these are primitives, builds **compose**: a ray gun is Pierce + Lifetime;
a shotgun is Multishot + Spread; a mining laser is Mining (Pierce + a very short
expiry). Bounce and gravity are seamed in the model for later builds (grenades,
cluster bombs).

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
