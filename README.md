# mc-dynamic-fusion
Can we do a Noita to Minecraft?

A PaperMC plugin for a family server: combine a **Target** item with an
**Ingredient** item to create weapons with emergent, composable magical
properties. See [`docs/design.md`](docs/design.md) for the full design.

## Status: Phase Zero (walking skeleton)

One real fusion, end to end: **Diamond Sword (Target) + Nether Star (Ingredient)
→ Nova Sword**. Swing the result and nearby mobs are pushed outward with a
particle burst.

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
2. Hold a **Nether Star** in your off hand (the Ingredient — consumed).
3. Run `/fuse`.
4. Swing the resulting Nova Sword near some mobs and watch them fly outward.

## Tests

```bash
./gradlew test
```

- **Pure unit tests** (no server): modifier logic, latent registry, lore,
  cooldown timing.
- **MockBukkit tests**: PDC round-trip and the fusion engine.
