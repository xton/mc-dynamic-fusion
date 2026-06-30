# mc-dynamic-fusion
Can we do a Noita to Minecraft?

A PaperMC plugin for a family server: combine a **Target** item with an
**Ingredient** item to create weapons with emergent, composable magical
properties. See [`docs/design.md`](docs/design.md) for the full design.

## Status: Phase 3 (weapon behaviors)

Fuse a weapon (Target) with an ingredient (Ingredient → consumed), then swing
to unleash an effect. The ingredient decides what it does, and **re-fusing
stacks modifiers** (duplicates compound):

| Modifier | Ingredients | Effect |
|---|---|---|
| **Nova** | Nether Star | all-directions shove burst on swing |
| **Expand** | Heart of the Sea, Magma Cream | widens the burst (stacks bigger) |
| **Chain** | String, Echo Shard | hops to nearby entities after the burst |
| **Repeat** | Rabbit's Foot, Slime Ball, Chorus Fruit | fires several times in a row |
| **Delayed** | Gunpowder | holds the effect for a fuse, then fires |
| **Mining** | Amethyst Shard | swing carves an arc of soft blocks ahead |

**Fused bows** throw their effect downrange: the burst fires where the arrow
lands.

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

1. Hold a **sword** in your main hand (the Target — kept and upgraded).
2. Hold an **ingredient** in your off hand (Nether Star, String, Magma Cream,
   Slime Ball, … — see the table above). It's consumed.
3. Run `/fuse`.
4. Swing the fused weapon near some mobs and watch the effect.
5. **Stack it:** fuse the weapon again with another ingredient to combine or
   compound effects (e.g. Nova + Expand + Expand = a huge shove). Order and
   duplicates both matter.

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
   `/fuse`.
4. Swing the Nova Sword near some mobs and watch them fly outward.

Iterate after code changes with `make rebuild` (rebuilds the jar and restarts
the server), `make logs` to tail output, and `make down` to stop it.

> Automated *gameplay* assertions (a bot that joins and fuses) aren't included
> yet: headless clients like mineflayer don't speak the 26.x protocol at the
> time of writing. The smoke test covers clean loading; human UAT covers the
> fun part.
