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
| **emit** | **Pull** | Fishing Rod | a vacuum burst — drags entities inward (Push's complement) |
| **emit** | **Damage** | Fire Charge, Flint | a damaging burst where it lands |
| **emit** | **Heal** | Golden Apple | mends friendly entities & the caster; skips hostiles (Damage's complement) |
| **emit** | **Mining** | Amethyst Shard | carves a tunnel along the flight (Expand widens it); **stack Mining to break harder blocks** — add Pierce to bore through |
| **emit** | **Fire** | Flint and Steel, Magma Block | spreads fire, melts snow/ice, ignites mobs in a radius (a touch wider than Mining); fused onto **armor**, it becomes a worn aura (see below) that ignites the ground/creatures around the wearer, who's kept immune to their own fire |
| **emit** | **Ice** | Blue Ice, Snowball | freezes water→ice, lava→obsidian, snuffs fire, chills mobs; as a worn aura, freezes a radius around the wearer — no self-immunity needed since it never creates a real hazard |
| **emit** | **Deposit:_block_** | Dirt, Sand, Water/Lava Bucket, Cobweb | fills the empty air in a radius with that block (`Deposit:Dirt`, `Deposit:Water`, …) |
| **emit** | **Spawn** | Firework Rocket, Egg | at the terminus, bursts into a fresh child built from every modifier *after* Spawn |
| **emit** | **Delay:_n_** | Clock | like Spawn, but the child blinks in place for _n_ s then goes off (lure-then-blast) |
| **emit** | **Detect** | Tripwire Hook | like Spawn, but the child arms in place (blinking) and goes off the moment a creature steps within range (Expand widens the range) — a trap/mine |
| **emit** | **Mob:_type_** | Cow Spawn Egg | launches a live creature as the projectile — vanilla physics carry it (the Cow Launcher) |
| **emit** | **Treasure** | Gold Ingot/Block | on a **Brush**, sweeping any block may cough up loot (more gold → rarer finds) and scours it to Coarse Dirt, win or not — Coarse Dirt itself doesn't brush |
| **emit** | **Potion:_effect_** | Lingering Potion | casts a small lingering cloud of that effect (widened by Expand, particles/color matching the potion) wherever the weapon delivers it — a **Stick** (the Wand) casts it instantly at the crosshair on a swing; any other weapon casts it at its own shot's terminus |
| *xf (aoe)* | **Expand** | Heart of the Sea, Magma Cream | ×radius of the previous burst |
| *xf (aoe)* | **Amplify** | Glowstone Dust, Blaze Powder | ×force/damage of the previous burst |
| *xf (aoe)* | **Chain** | String, Echo Shard | previous burst hops to more entities |
| *xf (aoe)* | **Invert** | Fermented Spider Eye | previous push implodes — two cancel |
| *xf (aoe)* | **Persist** | Blaze Rod, Dragon's Breath | previous burst lingers and re-pulses |
| *xf (fly)* | **Multishot** | Rabbit's Foot, Slime Ball | launches extra projectiles |
| *xf (fly)* | **Spread** | Feather, Sugar | scatters the aim |
| *xf (fly)* | **Pierce** | Arrow, Quartz | punches through soft blocks & applies its effects at every occupied cell/entity in its path |
| *xf (fly)* | **Bounce** | Slime Block, Rabbit Hide | ricochets off blocks, rolls out its remaining speed once the hop is gone, then sits armed (with a visible sprite) until it expires or a mob hits it directly |
| *xf (fly)* | **Homing** | Compass | curves to chase the nearest creature mid-flight (stacks sharpen the turn) |
| *xf (fly)* | **Trail** | Trident, Prismarine Shard | inverse of Pierce — applies environmental effects at every *empty-air* cell it flies through |
| *xf (fly)* | **Lifetime** | Gunpowder, Redstone | adds a fixed range (same distance fast or slow) |
| *xf (fly)* | **Teleport** | Ender Pearl, Eye of Ender | dashes the caster to where the shot terminates over a brief ~0.3s zoom (once per cast, safely offset, invulnerable for the transit) |
| *xf (fly)* | **Gravity** | Heavy Core, Lead | turns on the arc — lob from any weapon, no bow needed |
| *xf (fly)* | **Visible** / **Invisible** | Glow Ink Sac / Ink Sac | force the flight trail on or off (override the weapon-type default) |
| *xf (fly)* | **Speed:_n_** | *(parameterized)* | pin the launch speed to an exact value (`Speed:0.6` slow, `Speed:3` fast) |
| *xf (fly)* | **Duration:_n_** | *(parameterized)* | pin the lifetime to _n_ seconds (`Duration:4`) — absolute, unlike Lifetime's added range |
| *xf (worn)* | **Rate:_n_** | *(parameterized)* | only meaningful on armor: seconds between aura pulses (`Rate:0.5` rapid-fire, `Rate:5` a slow heartbeat) — overrides `worn.aura-period-ticks` for that item. No-op on a weapon (a swing/shot doesn't repeat) |
| *xf (worn)* | **Distance:_n_** | *(parameterized)* | only meaningful on armor: blocks walked to force an early aura pulse (`Distance:1` re-casts almost every step, `Distance:10` only on real movement) — overrides `worn.aura-distance-blocks` for that item. No-op on a weapon |
| **worn** | **Glow** | Lantern, Glow Berries | fused onto **armor**, gives you a powerful glowing outline for others to see, plus a client-side light tracked in front of your face so you can actually see by it too (not night vision) |
| **worn** | **Lift** | Breeze Rod | fused onto a **chestplate/elytra**, a directional jetpack: hold jump to rise (capped), crouch to brake and fall, forward/back/strafe to drift — blocks vanilla elytra gliding so its look-tied auto-forward can't fight it |
| **worn** | *(any modifier)* | — | fused onto **armor**, any modifier becomes part of an **aura** — nothing is off-limits, not just Fire/Ice/Push/Damage but Multishot/Homing/Spread/Gravity/Mob/...: armor is just another possible source of a shot, so it periodically fires a real shot rooted at the wearer, the same compile/payload pipeline a weapon uses. With no flight modifiers that's a stationary, zero-duration burst right at the wearer (a bare Fire is a simple fire pulse around you); add Speed/Gravity/Duration and it genuinely flies — `Multishot Homing Damage Speed:2 Duration:2` fires real homing bolts out from the wearer on every pulse. A pulse fires whenever *either* the timer (`worn.aura-period-ticks`, tunable per-item with `Rate:n`) or a distance walked (`worn.aura-distance-blocks`, tunable per-item with `Distance:n`) is crossed, whichever comes first, so standing still still gets a heartbeat and moving fast leaves a denser trail. All four armor pieces' fused ids combine into one stack (Fire on a helmet + Expand on boots widens the aura). The wearer is excluded from their own burst/environmental effects like any caster, and is kept immune to whatever real-world hazard their aura would otherwise expose them to (Fire Resistance for Fire/lava). |

Because these are small primitives, weapons **compose**: a nova is
`Push · Expand · Expand`; a fireball is `Damage · Amplify · Fire`; a shotgun is
`Damage · Multishot · Spread`; a ray gun is `Pierce · Lifetime`; a mining laser
is a pickaxe with `Mining · Pierce · Lifetime`. The new emitters compose the
same way: a flamethrower is `Fire · Pierce · Lifetime`; a **block-replacement
bolt** is `Mining · Pierce · Deposit:Dirt` (carve, then backfill — order
matters); a river-layer is `Deposit:Water · Trail`; a cluster firebomb is
`Damage · Spawn · Multishot · Spread · Fire`; a blink lance is
`Pierce · Lifetime · Teleport`; a bouncing cluster grenade is
`Damage · Bounce · Duration:5 · Spawn · Multishot · Spread · Fire`; a lobbed
mortar from a plain sword is `Damage · Gravity · Visible · Speed:0.8`; a
gravity-well grenade is `Pull · Expand · Delay:2 · Damage · Amplify` (gather,
wait, blast); a heal bomb is `Heal · Expand · Amplify`; a seeking bolt is
`Damage · Homing · Lifetime`; the Cow Launcher is an axe with `Mob:Cow`; a
landmine is `Lifetime · Gravity · Visible · Detect · Damage · Amplify · Expand
· Mining · Expand · Fire` — a lobbed throw that plants itself, arms, blinks,
and blasts (damage, a bore, and fire) when something walks up; a Wand is a
Stick with `Potion:Poison` (or whatever a fused Lingering Potion carried in).
Many ingredients are **bundles** — a ready-made recipe in one item (TNT =
`Damage · Expand · Expand`, End Crystal = the works). See
[`latent_registry.yml`](src/main/resources/latent_registry.yml) for the roster.

**Complements, not just Invert.** Where it reads clearer, atoms come in pairs:
`Push`↔`Pull` (shove vs. vacuum), `Damage`↔`Heal`, `Fire`↔`Ice`,
`Visible`↔`Invisible`. (`Invert` still flips a Push for back-compat, but `Pull`
is the explicit way.)

**Spawn children ricochet off surfaces.** When a shot ends against a block, its
`Spawn` children launch along the *reflected* heading (off the impact normal) and
just clear the face — so a cluster bomb that hits a wall scatters back into the
open instead of wasting its children against the same block. `Bounce` uses the
same reflection to keep a shot alive, ricocheting until it rolls to a rest —
at which point it sits armed rather than detonating, only going off when it
expires or a mob hits it directly.

**Environmental effects apply in stack order.** The block-affecting emitters
(Mining/Fire/Ice/Deposit) run left-to-right at each application point, so
`Mining · Deposit:Dirt` carves *then* backfills (a solid bolt), while
`Deposit:Dirt · Mining` fills then immediately re-digs (and you feel foolish).

**Weapon type sets the flight, not modifiers:** a melee swing delivers at arm's
length in a straight line, near-instantly and with no visible bolt (it reads as
a swing); a bow throws the same weapon downrange in a gravity arc (draw strength
scales the range). A mining ray clears vegetation and the ground it hides, and
LIFETIME adds a fixed *distance* so faster weapons don't get longer tunnels. Bounce and a gravity-toggle modifier are
seamed for later builds (grenades, cluster bombs).

**Every flight trail starts invisible.** Whatever the trail style, the cosmetic
wake only begins ~2.5 blocks out — a shot clears the caster (arm's reach)
before it's seen at all, so a fast bow shot doesn't leave a lingering trail
right in front of you. Melee, which rarely travels that far before hitting its
target, ends up invisible for its whole flight without needing separate logic.

**The Wand is the one latent read off the item itself, not the material.**
Every other ingredient's magic comes from a static Material→modifier table
(`latent_registry.yml`); a Lingering Potion is the exception — Poison, Harming,
Regeneration, ... are all the same material, so fusing one onto a Stick reads
its actual potion data at fuse time and bakes in a `Potion:<effect>` matching
that specific potion. The Wand then casts it with a vanilla `AreaEffectCloud`
(the same entity a thrown lingering potion leaves behind) swung onto a block —
free particle/color theming, no bespoke art needed, and Expand widens it like
any other burst.

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
(console/op only) drives the *real* modifier compiler, projectile, and burst
code against the live world and asserts the mechanics MockBukkit can't reach. It
has two kinds of check (52 in total):

- **compile checks** pin the emitter/transform RPN semantics on the compiled
  spec — EXPAND/AMPLIFY scaling, MULTISHOT/SPREAD/LIFETIME stacking, INVERT
  toggling, CHAIN/PERSIST accumulation, the flight flags (incl. BOUNCE, HOMING,
  GRAVITY, VISIBLE/INVISIBLE and the parameterized SPEED:n/DURATION:n), the
  HEAL/PULL complements, MINING hardness stacking, the FIRE/ICE/DEPOSIT emitters
  and TRAIL/TELEPORT/SPAWN/DELAY/DETECT/MOB/POTION wiring (`Deposit:Dirt` /
  `Mob:Cow` / `Potion:Poison` parameter parsing, Spawn/Delay/Detect pushing a
  fresh child, a following EXPAND widening DETECT's own trigger radius or the
  Wand's cast radius, and that POTION delivers a real cloud at any weapon's
  own terminus, not just the Wand's point-and-cast),
  and the *nearest-previous binding* (a transform touches only the last
  emitter) that's nearly impossible to eyeball in-world;
- **runtime checks** fire real bursts/projectiles at dummies and blocks: PUSH
  knockback, DAMAGE health loss, an inverted PUSH pulling inward, a CHAIN hop, a
  MINING ray carving and *stopping at obsidian* (and a deep MINING stack *boring
  through* it), a PIERCE bolt through two mobs, FIRE melting snow + igniting a
  mob, ICE freezing water and dressing bare ground with snow, DEPOSIT backfilling
  air, a DEPOSIT · Trail wake (its warm-up sparing the caster), a BOUNCE rebound,
  a SPAWN child ricocheting off a wall, a HEAL mending a hurt cow, a DELAY charge
  re-detonating, a HOMING bolt curving into an off-axis mob, a DETECT mine arming
  and triggering on a nearby dummy, a MOB:Cow launch spawning a live cow (both as
  the top-level shot and as a SPAWN child — a cluster-cow-bomb build used to
  silently fly its child as an empty bolt instead).

Results are logged as `[fusion-selftest] RESULT: PASS|FAIL`; the smoke script
fails the build unless it sees PASS. You can run it by hand on any server too
(`/fusion test`) — it spawns a handful of disposable mobs, so use it on a scratch
world.

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
  vanilla arrow entity spawns (proves the bow override replaces the arrow);
- **machine GUI** — place a Fusion Machine, open the anvil, load Target +
  Ingredient, and take the fused result (proves the whole GUI flow, and guards
  the bugs it hit in the past: dead untagged machines, missing result preview,
  "Too Expensive" blocking the take, and the result snapping back);
- **machine rejects junk** — a no-magic ingredient (dirt) yields the
  non-takeable red-barrier "can't fuse" marker, never a weapon;
- **machine item-safety** — closing the anvil with items still in the inputs
  returns them to the player (nothing lost).

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

Most of the weapon model is now covered by `make smoke` + `make e2e`, so a manual
pass only needs the cosmetics/feel/config items a harness can't judge. That short
list lives in **[`docs/uat/manual-checklist.md`](docs/uat/manual-checklist.md)**;
older pre-refactor UAT plans are kept under `docs/uat/archived/`.
