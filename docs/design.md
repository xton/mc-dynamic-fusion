# Fusion Weapon Plugin — Design Document

> A PaperMC plugin for a family Minecraft server. Players combine a **target** item with an **ingredient** item in a Fusion Machine to create weapons with emergent, composable magical properties. Every special vanilla item has a latent modifier identity; fusion merges the ingredient's identity onto the target weapon.

---

## Table of Contents

1. [Core Concept](#core-concept)
2. [Architecture Overview](#architecture-overview)
3. [The Modifier System](#the-modifier-system)
4. [Latent Attribute Registry](#latent-attribute-registry)
5. [Fusion Machine](#fusion-machine)
6. [Fused Item Identity](#fused-item-identity)
7. [Weapon Behaviors](#weapon-behaviors)
8. [Visual Effects](#visual-effects)
9. [Example Weapons](#example-weapons)
10. [Testing Strategy](#testing-strategy)
11. [Resolved Design Decisions](#resolved-design-decisions)
12. [Implementation Roadmap](#implementation-roadmap)

---

## Core Concept

Special items in the game carry a hidden **latent modifier list** defined in config. Fusion is **asymmetric**, modelled on the vanilla anvil:

- The **Target** slot holds the item you keep and upgrade. Its base type (sword, bow, axe…) defines the output weapon, and it carries forward any modifiers it already has. The output *is* this item, enhanced.
- The **Ingredient** slot holds the item you sacrifice. It is **destroyed** on fusion, contributing its latent modifiers to the target.

When a fusion is performed, the plugin:

1. Takes the Target's existing modifiers (if already fused) as the base of the stack
2. Appends the Ingredient's latent modifiers
3. Merges them according to stacking rules (dedupe, cap)
4. Bakes the resulting modifier list into PDC on the output item
5. Generates lore describing the modifiers
6. Applies any declarative stat boosts (enchantments, attribute modifiers)

The Target's **base type** determines what the modifier list is applied *to* when the weapon is used. The Ingredient determines *what new powers it gains*.

This creates emergent combinations the designer doesn't need to explicitly author — players discover them.

### The Target slot accepts anything

There is no weapon-type gate on the Target slot. You can fuse onto a sword, a bow, a pumpkin, or a block of dirt. Combinations that don't map to a weapon behavior simply produce an inert or cosmetic item (glint + lore + particle shedding). Some combinations will be powerful, some pointless, and some genuinely dangerous — all by design.

### Determinism and Backfire — there are no malfunctions

**Fusion always produces a working item, and that item always behaves exactly as its modifier stack specifies.** There is no failure roll, no jam, no "your weapon misfired" moment. Every outcome is a deterministic function of the stack, which makes weapons *learnable*: the same ingredients always yield the same weapon, and that weapon always does the same thing.

Within that guarantee there are three outcome classes:

- **Functional** — the stack maps to a coherent, useful weapon.
- **Inert** — the stack is coherent but pointless (e.g. AMPLIFY with nothing after it to amplify). The weapon simply does nothing remarkable. This is *not* a malfunction and is never messaged as a failure.
- **Backfire** — the effect executes faithfully, but its geometry or targeting includes the wielder (a NOVA that counts the caster, an EXPAND radius you're standing inside, a delayed blast you don't walk away from). You take the consequences. The weapon didn't fail — *you built something unsafe.*

**Invariant: no random failure modes.** If a fusion weapon hurts its owner, it is because the owner created a genuinely unsafe thing, executed correctly — never because of luck. This keeps the system fair (important on a server with kids) and makes dangerous discoveries feel earned rather than punishing.

---

## Architecture Overview

Current layout (the emitter/transform compile model — see *The Modifier System*):

```
plugin/
├── FusionPlugin.java               # Main class, lifecycle, wiring
├── command/
│   └── FusionCommand.java          # /fusion machine | fuse | give (op-only)
├── fusion/
│   ├── FusionEngine.java           # Core merge logic
│   └── FusionResult.java           # Success/refusal + output
├── machine/
│   ├── FusionMachineMenu.java      # Anvil-based fusion GUI (enchanting-table block)
│   └── MachineListener.java        # Place/break/right-click/GUI events
├── modifier/
│   ├── Modifier.java               # Interface: id/name + Category + apply(WeaponBuilder)
│   ├── WeaponBuilder.java          # RPN compile: stack → ProjectileSpec
│   ├── ProjectileSpec.java         # Compiled flight + payload (List<AoeSpec>)
│   ├── AoeSpec.java / AoeKind.java  # One burst element (PUSH/DAMAGE) + its transforms
│   ├── ModifierRegistry.java       # ID → Modifier implementation map
│   ├── ModifierStack.java          # Ordered list (no dedupe)
│   └── impl/                       # One class per modifier
│       ├── PushModifier.java  DamageModifier.java          # emitters
│       ├── ExpandModifier.java  AmplifyModifier.java  ...  # AOE transforms
│       └── MultishotModifier.java  PierceModifier.java  …  # flight transforms
├── item/
│   ├── LatentRegistry.java         # Material → List<ModifierId>
│   ├── FusedItemFactory.java / FusedItemReader.java        # PDC write/read
│   ├── FusionKeys.java             # NamespacedKeys
│   └── LoreGenerator.java          # Builds Adventure API lore lines
├── projectile/
│   ├── ProjectileLauncher.java     # compile(stack) + buildPayload(spec) + launch (pure core)
│   ├── FusionProjectile.java       # Custom self-ticked projectile (pierce/mining/lifetime)
│   ├── Payload.java / PayloadEffect.java / BurstEffect.java # terminal effects
│   └── AoeBurst.java               # Fires one AoeSpec (shove or damage, chain/persist)
├── weapon/
│   ├── WeaponEventListener.java    # Swing → launch projectiles
│   ├── ProjectileListener.java     # Fused bow → launch projectiles (draw-scaled)
│   └── ShedParticleTask.java       # Cosmetic particle trail
└── util/
    ├── CooldownMap.java            # Per-player cooldown (injected time source)
    ├── Scheduler.java              # Thin wrapper over Bukkit scheduler (injectable)
    └── BukkitTaskScheduler.java
```

### Key design principle: draw the test boundary where it's free

The math- and string-level logic is genuinely server-free and unit-testable in plain JUnit, because the types involved are not server-bound:

- `org.bukkit.util.Vector` is a plain POJO — `new Vector(x,y,z)` and all its math (`normalize`, `crossProduct`, `rotateAroundAxis`, `angle`) run with no server. `VectorUtil` is fully pure.
- `Material` is an enum and loads fine in JUnit, so `LatentRegistry` lookups are pure.
- `ModifierStack` merge/dedupe operates on `String` IDs — pure.
- Adventure `Component` construction (lore) uses a shaded library — pure.

What *does* require MockBukkit is anything that touches **ItemStack meta / PDC** (`getItemMeta()` calls `Bukkit.getItemFactory()`) or live **`Entity`/`Block`** instances. So `FusedItemFactory`, `FusedItemReader`, `FusionEngine`, the weapon behaviors, and any `ModifierContext`-based pipeline test run under MockBukkit — because the context carries Bukkit entity/block references.

We are **not** claiming the modifier pipeline is pure; it isn't, because the context holds Bukkit types. We *are* keeping the geometry/string layer pure at no cost, and accepting MockBukkit everywhere ItemStack-meta/Entity/Block genuinely appear.

### Dependency injection at the cheap seams

Where the real implementation is incidental to the correctness of a test, prefer constructor-injected collaborators — **but only where the wrapper is thin and reads naturally.** Don't abstract away the whole Bukkit API.

Worth it:

- **Time** — `CooldownMap` takes a `LongSupplier`/`Clock` instead of calling `System.currentTimeMillis()`. Deterministic cooldown tests, no sleeps.
- **Randomness** — any tie-breaking ("nearest of N") or future stochastic effect takes an injected `Random`. Seeded tests are reproducible.
- **Scheduling** — a tiny `Scheduler` interface (`runLater`, `runRepeating`) over the Bukkit scheduler. Slightly more lift, but DELAYED/REPEAT/PERSIST are otherwise untestable, so it earns its keep.
- **Config** — pass resolved plain value objects into engine logic, not Bukkit's `FileConfiguration`.

Not worth it: wrapping `ItemStack`/PDC or `Entity`/`Block` behind abstractions. Large lift, little gain — use MockBukkit there.

---

## The Modifier System

> **Implemented model (2026-07 — supersedes the catalog in the rest of this
> chapter, which records the original design intent).** The sections below on
> `ModifierContext`, the NOVA/REPEAT catalog, and AMPLIFY-doubling describe the
> first cut; the shipped model is the emitter/transform compile described here.

### Weapon = projectile (flight) + payload (bursts)

Everything is a projectile, Noita-style. A swing or bow shot launches one or
more projectiles; each has a **flight** (how it travels) and a **payload** (what
it delivers where it terminates). Both can be empty — a zero-length flight
detonates at the origin; an empty payload delivers nothing (a mining ray carves
and stops, no pop).

Modifiers come in two categories (`Modifier.Category`):

- **Emitters** add a concrete element. `PUSH` and `DAMAGE` append an `AoeSpec`
  burst to the current projectile's payload. (A spawn-projectile emitter is the
  seam for cluster bombs — the payload is a `List<PayloadEffect>`, so a spawn
  effect that re-launches child projectiles with a decremented generation drops
  in with no special-casing.)
- **Transforms** modify the **nearest preceding emitter** (RPN, apply-to-previous).
  *AOE transforms* (`EXPAND` ×radius, `AMPLIFY` ×power/damage, `CHAIN`, `INVERT`,
  `PERSIST`) mutate the top `AoeSpec`; *flight transforms* (`MULTISHOT`, `SPREAD`,
  `PIERCE`, `LIFETIME`, `MINING`) mutate the projectile. A transform with no
  matching preceding emitter is **inert** — so a burst is opt-in (Expand alone
  does nothing), and it binds to the *nearest* element only (`PUSH PUSH EXPAND`
  widens just the second push).

Because the primitives are small, weapons compose:
`PUSH·EXPAND·EXPAND` = a nova · `DAMAGE·AMPLIFY` = a fireball ·
`DAMAGE·MULTISHOT·SPREAD` = a shotgun · `PIERCE·LIFETIME` = a ray gun ·
`MINING` = a mining laser. Ingredients are a hybrid roster: atomic *reagents*
(one attribute) plus curated *bundles* (a ready-made spell in one item).

### Compile pipeline

The stack **compiles** — it is no longer a flat fold over a shared context:

```
ModifierStack ──(WeaponBuilder, RPN)──▶ ProjectileSpec
                                          ├─ flight: count, spread, speed,
                                          │   pierce, lifetime, gravity, mining…
                                          └─ payload: List<AoeSpec>  (PUSH/DAMAGE,
                                              each radius/power/chain/invert/persist)
```

- `Modifier.apply(WeaponBuilder)` acts on the compile state: an emitter calls
  `emitPush()`/`emitDamage()`; a transform mutates `topAoe()` (AOE) or
  `projectile()` (flight).
- `ProjectileLauncher.compile(stack)` → `ProjectileSpec` and `buildPayload(spec)`
  → `Payload` (one `BurstEffect` per emitter) are **pure** (no world), so the
  whole model is unit-tested in `WeaponCompileTest` / `ProjectileModelTest`.
- `FusionProjectile` is a custom, particle-rendered, self-ticked projectile (no
  Bukkit entity) that sub-steps each tick so we own pierce/mining/lifetime, then
  delivers the `Payload` at its terminus. `AoeBurst` fires one `AoeSpec` (shove
  or damage, with chain/persist).

Class map: `modifier/{Modifier, WeaponBuilder, ProjectileSpec, AoeSpec, AoeKind}`
and `modifier/impl/*` (one per modifier); `projectile/{ProjectileLauncher,
FusionProjectile, Payload, PayloadEffect, BurstEffect, AoeBurst}`.

---

### Modifier Interface (original design — superseded)

```java
public interface Modifier {
    String getId();
    String getDisplayName();
    String getDescription();
    String getDetailedDescription();  // shown on lore hover
    ModifierContext apply(ModifierContext ctx);
}
```

### ModifierContext

Carries all mutable state through the pipeline:

```java
public class ModifierContext {
    // Input
    Player caster;
    Location origin;
    Vector direction;
    ItemStack weapon;

    // Targets (modifiers can expand or transform this set)
    List<Entity> targetEntities;
    List<Block> targetBlocks;

    // Effect parameters (modifiers can amplify/invert these)
    double damage;
    double radius;
    int repeatCount;
    long delayTicks;
    boolean inverted;

    // Accumulated side effects to execute after pipeline
    List<Runnable> deferredEffects;
}
```

### Where we start (a starting order, not a waterfall)

We open with the modifiers that are clearest to *see* working — NOVA, EXPAND, REPEAT, CHAIN — and bring in the others (INVERT, PERSIST, REVERSE, DELAYED, and the special behaviors) as we go. This is just where we begin, not a frozen sequence: **balance and fun are judged across the whole set**, so we expect to revisit and retune existing modifiers freely as the mix evolves. We iterate, we don't waterfall. INVERT and PERSIST semantics in particular are still open.

### Modifier Categories

**Area modifiers** — change who/what is affected:
| ID | Effect |
|---|---|
| `EXPAND` | Affects a radius around the target instead of single entity/block |
| `ARC` | Affects entities in a cone in the look direction |
| `NOVA` | Affects all directions equally from origin |
| `CHAIN` | Effect jumps to nearest other entity N times after initial hit |

**Timing modifiers** — change when the effect fires:
| ID | Effect |
|---|---|
| `DELAYED` | Effect fires after 2 seconds (configurable) |
| `PERSIST` | Leaves a lingering zone that re-applies the effect *(semantics TBD)* |
| `REPEAT` | Effect triggers N additional times in rapid succession |

**Potency modifiers** — change how strong the effect is:
| ID | Effect |
|---|---|
| `AMPLIFY` | Doubles the potency of subsequent modifiers; compounds when repeated (×3 ⇒ 8×) |
| `INVERT` | Inverts the primary effect (damage → healing, pull → push) *(semantics TBD)* |
| `REVERSE` | Knockback becomes pull toward caster |

**Special modifiers** — unique behaviors:
| ID | Effect |
|---|---|
| `TELEPORT` | Caster teleports to target location on hit |
| `BOUNCE` | Projectiles reflect off block faces up to N times |
| `DRILLING` | Projectiles pass through blocks, breaking them |

> INVERT and PERSIST exact semantics are intentionally deferred — they'll be specified when we reach that batch, after the visible modifiers prove the framework.

### Modifier Ordering

Modifiers apply in the order they appear in the baked PDC list, which is **deterministic and not player-reorderable**: the Target's existing modifiers first (it's the accumulating item), then the Ingredient's latent modifiers appended, each preserving its source's internal order. Order is load-bearing, and a chain can meaningfully repeat the same modifier:

- `AMPLIFY → NOVA` = 2× nova
- `AMPLIFY → AMPLIFY → AMPLIFY → NOVA` = 8× nova (multipliers compound)
- `AMPLIFY → NOVA → AMPLIFY → NOVA` is a different, legitimate weapon from `AMPLIFY → AMPLIFY → NOVA → NOVA`
- `NOVA → AMPLIFY` = nova at base strength, then AMPLIFY applies to nothing (inert tail)

Lore displays the order so players can learn the pattern, but they cannot drag to reorder.

### Stacking rules

- **Duplicates stack — OP is welcome.** Repeated modifiers are *not* deduplicated. Three AMPLIFYs in a row make the following effect 8× as strong (2×2×2). Combined with order mattering, a long chain can deliberately repeat elements to compound or interleave effects. Absurd power is a feature, not a bug, on a family server.
- **Cap:** maximum **8** modifiers per item (configurable) — a technical guard on PDC size and per-swing loop counts, *not* a balance lever. Excess is dropped from the tail.

---

## Latent Attribute Registry

Special `Material`s map to one or more modifier IDs. Defined in `latent_registry.yml` (config-driven so you can tune without recompiling). **Common items (dirt, planks, sticks) have no modifiers** — only special/rare items carry latent identity, so ingredient choices feel meaningful.

### Proposed assignments

```yaml
latent_modifiers:
  NETHER_STAR:      [NOVA, AMPLIFY]
  BLAZE_ROD:        [AMPLIFY, PERSIST]
  ENDER_PEARL:      [CHAIN, TELEPORT]
  SLIME_BALL:       [REPEAT, BOUNCE]
  MAGMA_CREAM:      [PERSIST, EXPAND]
  STRING:           [CHAIN]
  GUNPOWDER:        [DELAYED, EXPAND]
  FERMENTED_SPIDER_EYE: [INVERT]
  RABBIT_FOOT:      [REPEAT]
  GHAST_TEAR:       [REVERSE]
  PHANTOM_MEMBRANE: [DELAYED, TELEPORT]
  DRAGON_BREATH:    [PERSIST, NOVA]
  PRISMARINE_SHARD: [BOUNCE]
  CHORUS_FRUIT:     [TELEPORT, REPEAT]
  HEART_OF_THE_SEA: [EXPAND, AMPLIFY]
  ECHO_SHARD:       [CHAIN, DELAYED]
```

Items with no entry contribute no modifiers but can still serve as a Target base type.

> Phase Zero ships exactly one entry — `NETHER_STAR: [NOVA]` — to prove the config-load path with the minimum surface.

---

## Fusion Machine

### Anvil-style, asymmetric

The interaction mirrors the vanilla anvil — a pattern every Minecraft player already understands. **Target** is the item you keep and upgrade; **Ingredient** is the item you sacrifice.

### Physical form (Phase 2)

Right-clicking a renamed/tagged crafting table opens a custom inventory:

```
[ TARGET ]  [ + ]  [ INGREDIENT ]  [ => ]  [ Output Preview ]
 (kept,             (consumed,
  upgraded)          destroyed)
```

- Slots are labelled to make the roles explicit — Target = *"Weapon to enhance"*, Ingredient = *"Reagent (consumed)"*.
- Output preview shows the resulting weapon, with the modifiers that *will be added* highlighted distinctly from those already on the Target. This telegraphs "this one survives, this one dies" before the player confirms.
- Output slot is a non-interactive ghost preview.
- Confirm button (named emerald or similar) executes the fusion.
- Fusion plays a ~2s progress build-up with ramping particles, then a burst + sound.
- The Ingredient is consumed; the Target is replaced by the fused output.

> A multi-block structure form is a Phase 5 stretch.

### Phase Zero form — no GUI

Before the GUI exists, fusion is performed via `/fuse`: **main hand = Target, off hand = Ingredient.** The command resolves the recipe, consumes the off-hand ingredient, and replaces the main-hand item with the fused output. This proves the full pipeline without the custom-inventory investment.

### Fusion resolution

```
output_material  = target.material          // Target always defines the base
output_modifiers = merge(
    fusedReader.get(target),                // target's existing modifiers (if any)
    latentRegistry.get(ingredient.material) // + ingredient's latent modifiers
                                            //   (ingredient need not be previously fused)
)
```

No "higher tier" comparison exists — the Target slot is authoritative for the base type, which removes that ambiguity entirely.

### Cost

**Free to start** (`fusion-cost: 0`) so it's frictionless for a young player. The XP-level cost path is stubbed in config so it can be enabled later by changing one value, not code.

---

## Fused Item Identity

### PDC keys (all namespaced under plugin key `fusion`)

| Key | Type | Purpose |
|---|---|---|
| `fusion:is_fused` | BOOLEAN | Marks item as fusion output |
| `fusion:modifier_stack` | STRING | JSON array of modifier IDs in order |
| `fusion:shed_material` | STRING | Material name for particle shedding effect |
| `fusion:generation` | INTEGER | How many fusions deep (0 = first fusion) |
| `fusion:fused_from` | STRING | Display string of source materials |

### Reading and writing

`FusedItemReader` wraps all PDC access. `FusedItemFactory` handles creation. Neither is called directly from modifier logic — only from the weapon behavior layer and fusion engine.

### Generation cap

Maximum fusion depth is **5** (configurable). Past gen 5 the machine refuses further fusion with a clear message, preventing an unbounded power spiral.

### Lore format

```
§6✦ Fusion Weapon §7[Gen 1]
§8─────────────────────────
§b✦ Nova  §7— affects all directions equally
§b✦ Amplify  §7— doubles potency
§b✦ Delayed  §7— triggers after 2 seconds
§8─────────────────────────
§7Fused from: Diamond Sword + Nether Star
```

Hover text on each modifier line shows `getDetailedDescription()`.

---

## Weapon Behaviors

### Swing detection

`PlayerAnimationEvent` (arm swing) is the primary trigger for melee effects, combined with a per-player cooldown map (with injected time source) to prevent rapid-fire:

```java
Map<UUID, Long> lastCast; // UUID → millis from injected time source
long COOLDOWN_MS = 200;    // per-behavior-type, from config
```

Also listen to `EntityDamageByEntityEvent` for on-hit effects and `PlayerInteractEvent` for right-click behaviors.

### Nova (Phase Zero behavior)

Triggered on swing with a tagged weapon. Pushes all entities within a small radius outward from the caster, with a particle burst. Self-contained, visually obvious, and safe by construction (it pushes mobs *away*, so there's no backfire path to build yet).

### Cooldowns

Per-behavior-type, defined in `config.yml` (not per individual weapon instance).

### PvP

Fusion weapon effects on other players are **disabled by default**, configurable per-world via a world whitelist. Safe default for a server with kids.

### Mining Ray

Triggered on swing with tagged weapon. Casts rays in an arc:

- Sweep yaw -45° to +45° relative to look direction in 5° increments
- Optionally sweep pitch -15° to +15° for vertical coverage (2-block tall arc)
- Each ray travels 3–4 blocks, stops at first solid block and marks it for breaking
- `block.breakNaturally(weapon)` respects fortune and silk touch
- Cancel `BlockDamageEvent` while weapon is held to suppress vanilla break animation
- Particle sweep along arc immediately before breaking for visual telegraph
- **Respects block hardness up to a configurable cap** (default hardness ≤ 3, so stone breaks but obsidian survives)

### Bow Override

`EntityShootBowEvent` — cancel vanilla arrow, spawn custom entity instead. Charge level available as `event.getForce()` (0.0–1.0) for scaling velocity. Custom projectiles are tracked entities with PDC tags; `ProjectileHitEvent` or a repeating ground-check task handles landing logic.

### Portal Gun

Two-portal state machine per player:
- Left click: place blue portal on targeted block face
- Right click: place orange portal on targeted block face
- `PlayerMoveEvent`: detect entry, teleport to exit with velocity transform

Velocity transform rotates the incoming vector from entry-face space to exit-face space using face normals. Edge cases: portal-in-portal, placing portal on portal block, player too large for portal gap. A third portal replaces the oldest of that color.

Visual: nether portal blocks for the shimmer effect, or a particle rectangle drawn by a repeating task.

---

## Visual Effects

### Enchantment glint

Any `addUnsafeEnchantment()` call produces the glint. Use a zero-level dummy enchantment if no real enchantment is desired.

### Continuous particle shedding

*(Phase 4 — not in initial scope)*

Repeating task every 3–4 ticks. For each online player, check main hand item for `fusion:shed_material` PDC key. If present, spawn `ITEM_CRACK` particles of that material at player location + (0, 1, 0). Tuning: count=2–3, spread=0.2, speed=0.05. Subtle drift rather than spray.

**Burst variant:** on swing/hit, spawn count=20–30 of same particle for impact feedback.

### Arc particle sweep (Mining Ray)

`REDSTONE` dust or `CRIT_MAGIC` swept along the same angular range as the ray calculation, fired ~1 tick before block breaking, as a visual telegraph.

### Fusion burst

On successful fusion: `TOTEM` or `SPELL_MOB` particles in a sphere around the machine, `ITEM_CRACK` of the ingredient, custom sound, preceded by a ~2s ramping build-up.

### Color-tinted effects

`Particle.REDSTONE` with `DustOptions(Color, size)` allows full RGB. `DUST_COLOR_TRANSITION` interpolates between two colors. Primary tools for modifier-specific color coding without a resource pack.

**Proposed modifier color palette:**
| Modifier | Color |
|---|---|
| AMPLIFY | Gold |
| NOVA | White |
| CHAIN | Cyan |
| INVERT | Purple |
| DELAYED | Yellow |
| PERSIST | Green |
| TELEPORT | Magenta |
| EXPAND | Light blue |

---

## Example Weapons

### Nova Sword (Sword + Nether Star) — Phase Zero
- On swing: pushes nearby mobs outward with a particle burst
- The minimum meaningful fusion; safe by construction

### Cow Launcher (Bow + Slime Ball) — Phase 4
- Fires a cow entity with arrow velocity scaled to bow charge
- On ground contact: cow becomes normal (or explodes after timer)

### Mining Ray Sword (Sword + Gunpowder + Blaze Rod) — Phase 3
- Swing mines an arc of blocks 3–4 ahead
- Gunpowder contributes DELAYED + EXPAND; Blaze Rod contributes AMPLIFY + PERSIST

### Bouncing Spark (Bow + Prismarine Shard + String) — Phase 3+
- Projectile bounces off block faces (BOUNCE), jumps to nearby entity on impact (CHAIN)

### Chaos Nova (Any weapon + Nether Star + Fermented Spider Eye) — later
- NOVA + AMPLIFY + INVERT → amplified nova that heals all nearby entities. Counterintuitive heal bomb.

### The Annihilator (Sword + Heart of the Sea + Dragon Breath) — later
- EXPAND + AMPLIFY + PERSIST + NOVA → expanding persistent nova. Needs a hard radius cap, and is a prime **backfire** candidate if the caster is inside the radius.

---

## Testing Strategy

### Pure unit tests (plain JUnit, no server)

These touch only `Vector`, `Material`, `String` IDs, and Adventure `Component`s:

- `ModifierStack` merge and deduplication logic
- `LatentRegistry` lookup correctness
- `LoreGenerator` output format
- `VectorUtil` arc calculation and velocity transforms
- `CooldownMap` timing logic (via injected time source)

### MockBukkit tests (ItemStack-meta / Entity / Block)

```java
@BeforeEach void setUp() { MockBukkit.mock(); plugin = MockBukkit.load(FusionPlugin.class); }
@AfterEach void tearDown() { MockBukkit.unmock(); }
```

- Fusion engine produces correct output item given Target + Ingredient
- PDC round-trip: write modifier stack, read it back, matches original
- `ModifierContext` pipeline — each modifier transforms context correctly (context carries Entity/Block, so this needs the server)
- `WeaponEventListener` fires correct behavior on `PlayerAnimationEvent`
- Cooldown correctly suppresses rapid repeat triggers
- `BlockDamageEvent` cancelled while fusion sword is held

### DI seams that keep tests deterministic

- Injected time source → cooldown tests need no sleeps
- Injected `Random` → reproducible tie-breaks / future stochastic effects
- `Scheduler` interface → DELAYED/REPEAT/PERSIST testable with a fake scheduler
- Resolved config value objects → engine tests need no config file

### What MockBukkit cannot easily test

- Particle visuals (can assert `spawnParticle` called with correct args)
- Entity AI / pathfinding
- Repeating task timing (works but fiddly)
- Packet-level interactions

### Suggested test structure

```
src/test/java/
├── modifier/
│   ├── NovaModifierTest.java
│   ├── ModifierStackMergeTest.java
│   └── PipelineIntegrationTest.java
├── item/
│   ├── LatentRegistryTest.java
│   ├── FusedItemFactoryTest.java
│   └── LoreGeneratorTest.java
├── fusion/
│   └── FusionEngineTest.java
└── util/
    ├── VectorUtilTest.java
    └── CooldownMapTest.java
```

---

## Resolved Design Decisions

| # | Decision | Resolution |
|---|----------|-----------|
| 1 | Machine physical form | Crafting-table custom GUI (Phase 2); multi-block is a Phase 5 stretch |
| 2 | Output base type | The **Target** slot defines it — always authoritative, no "higher tier" logic |
| 3 | Ingredient consumption | Asymmetric: Ingredient destroyed, Target upgraded in place |
| 4 | Fusion cost | Free now (`fusion-cost: 0`); XP-level cost stubbed in config for later |
| 5 | Duplicate stacking | Duplicates stack, no dedupe; AMPLIFY×3 ⇒ 8×. OP is intentional |
| 6 | Stack ordering | Deterministic: Target mods then Ingredient mods; visible in lore, not reorderable |
| 7 | Modifier count cap | 8 (configurable) — technical guard on PDC/loop size, not balance |
| 8 | Common-item modifiers | Special/rare items only; common items contribute nothing |
| 9 | Mining ray hardness | Respects hardness up to configurable cap (default ≤ 3) |
| 10 | Cooldowns | Per-behavior-type, in config |
| 11 | PvP | Off by default; per-world whitelist |
| 12 | Generation cap | 5 (configurable) |
| 13 | Resource pack | Vanilla-only; pack is a Phase 5 stretch |
| 14 | Particle shedding | Phase 4 |
| 15 | Fusion animation | ~2s ramping progress + particle burst |
| 16 | Paper target | 26.1.2 stable (Java 25) — Minecraft 26.1+ requires Java 25 |
| 17 | Config format | YAML + `/fusion reload`; hardcoded defaults overridable by YAML |
| 18 | Machine persistence | Flat `machines.yml` in plugin data folder, loaded on startup |
| 19 | Multi-world | World whitelist, default `*` |

### Foundational principles (not numbered above)

- **Asymmetric fusion** — Target kept and upgraded, Ingredient destroyed (anvil mental model).
- **Determinism, no malfunctions** — every outcome is a function of the stack; backfire happens only when the wielder builds something genuinely unsafe.
- **Iterate, don't waterfall** — start with the most visible modifiers and judge balance and fun across the whole set; existing behaviors are fair game to revise as the mix evolves. OP combinations are welcome.
- **Test boundary where it's free** — keep the math/string layer pure; use MockBukkit only where ItemStack-meta/Entity/Block appear; inject time/random/scheduler/config at thin seams.

### Still deferred

- **INVERT and PERSIST exact semantics** — to be specified when that modifier batch is reached.

---

## Implementation Roadmap

### Phase Zero — Walking Skeleton (one real fusion, end to end)

A single vertical slice that proves the whole loop with the minimum surface.

**The fusion:** Diamond Sword (Target) + Nether Star (Ingredient) → Nova Sword. Swing → nearby mobs pushed outward + particle burst.

- [x] Project scaffold (Gradle Kotlin DSL, Paper 26.1.2 dependency, plugin.yml)
- [ ] `Modifier` interface + `ModifierContext` (only the fields NOVA uses)
- [ ] `ModifierRegistry` with exactly one modifier: NOVA
- [ ] `LatentRegistry` loading one config entry: `NETHER_STAR: [NOVA]`
- [ ] `FusedItemFactory` / `FusedItemReader` (PDC write/read of the stack)
- [ ] `LoreGenerator` (one line)
- [ ] `FusionEngine` (append ingredient latents to target, dedupe)
- [ ] `FuseCommand` (`/fuse`: main hand = Target, off hand = Ingredient; ingredient consumed, main-hand replaced)
- [ ] `NovaBehavior` on swing + `CooldownMap` with injected time source
- [x] Tests: pure (stack / registry / lore) + MockBukkit (PDC round-trip, fusion, caps)
- [x] Acceptance: spawn pigs, `/fuse` sword + nether star, swing, watch them fly back

Deliberately excluded: the GUI machine, every other modifier, generation cap / XP cost / PvP toggle / multi-world (hardcode safe defaults), all other behaviors, particle shedding, backfire.

### Phase 1 — Foundation breadth
- [x] Remaining first-batch modifiers: EXPAND, REPEAT, CHAIN
- [x] Flesh out `ModifierContext` with the effect fields these need
- [x] `LatentRegistry` from config, expanded to multiple ingredients
- [x] `Scheduler` seam (pulled forward from Phase 3) to drive REPEAT
- [ ] Config reload command (`/fusion reload`)
- [x] Generation cap, modifier cap, duplicate-stacking enforced
- [x] Unit tests for the new modifiers and stack composition

### Phase 2 — Fusion Machine
- [ ] Fusion Machine block/GUI (anvil-style Target/Ingredient/Output)
- [ ] Output preview with added-vs-existing modifier highlighting
- [ ] `FusionMachineListener`
- [ ] Fusion build-up + burst visual effect
- [ ] Machine persistence (`machines.yml`)
- [ ] MockBukkit integration tests

### Phase 3 — Weapon Behaviors
- [x] `WeaponEventListener` (swing routing; mining dispatch) + `ProjectileListener` (bow)
- [x] Mining Ray behavior (hardness cap, config-driven arc)
- [x] Bow override behavior (fused bow stamps stack → impact burst)
- [x] `Scheduler` abstraction (Phase 1); DELAYED modifier added (REPEAT in Phase 1)

### Phase 4 — Polish & deferred modifiers
- [x] INVERT (implosion) + PERSIST (lingering field) + backfire (emergent, via INVERT)
- [ ] REVERSE — deferred (redundant with INVERT under knockback-only effects; see DECISIONS.md)
- [x] Particle shedding task (toggleable)
- [ ] Portal Gun behavior — deferred (large stateful behavior)
- [ ] Cow Launcher — deferred
- [x] Lore hover text (since Phase 0)
- [ ] Sound design pass
- [x] XP-cost option (`fusion.cost`); PvP via `effect.affect-players` (Phase 1)
- [ ] Multi-world whitelist — deferred (low value for single-world server)

### Phase 5 — Stretch Goals
- [ ] Resource pack with custom textures — deferred (needs authored art)
- [ ] Multi-block fusion structure — deferred (single-block machine suffices)
- [x] `/fusion give <player> <base> <modifiers...>` admin command (with tab-complete)
