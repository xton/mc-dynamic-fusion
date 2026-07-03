# Decision Log

A running log of judgment calls made while implementing the roadmap, for
review. Newest phase at the bottom. "↳" marks a call made without explicit
direction where I picked what seemed best.

## Carried over from design + Phases 0–1 (already merged)

- **Asymmetric fusion (anvil model):** Target slot is kept/upgraded and defines
  the output base; Ingredient is consumed.
- **Determinism, no malfunctions:** outcomes are a pure function of the stack;
  "backfire" only when the wielder builds something genuinely unsafe.
- **Duplicates stack (no dedupe); OP is welcome.** Modifier cap (8) is a
  technical guard, not balance.
- **Target = 5 (Java 25), Paper 26.1.2 stable** (26.2 is alpha). Gradle 9.6.1
  (8.14 can't run on Java 25).
- **Test boundary:** pure math/string layer unit-tested; MockBukkit only where
  ItemStack-meta/Entity/Block appear; time/scheduler injected.

## Phase 2 — Fusion Machine (this PR)

- ↳ **Machine block = a tagged Crafting Table**, obtained via `/fusion machine`
  (op-only). Placing it registers its location; right-click opens the GUI;
  breaking it returns the machine item. Normal crafting tables are unaffected.
- ↳ **Persistence = flat `machines.yml`** (set of `world;x;y;z` keys), per design
  decision #18. Saved on every add/remove.
- ↳ **GUI layout:** single 9-slot row — Target(0), Ingredient(2), Output
  preview(4, view-only), Confirm/emerald(8); rest are inert glass panes.
- ↳ **Defensive item handling:** only Target/Ingredient slots accept items;
  shift/number-key/double/swap clicks are blocked to stop items routing into
  locked slots; items are returned to the player on close (dropped if the
  inventory is full). Goal: never dupe or lose items.
- ↳ **Fusion build-up animation deferred.** Design decision #15 wanted a ~2s
  progress bar; I kept Confirm an immediate fusion + burst to keep the flow
  robust. The animation is a Phase 4 polish item.
- ↳ **"Added-vs-existing" preview highlighting simplified.** The Output preview
  shows the full would-be result item (its lore already lists every modifier).
  Distinct highlighting of *newly added* modifiers is deferred to polish.
- ↳ **Target stacks > 1 collapse to the single upgraded item.** The Target slot
  is meant for one weapon; if a stack is placed, fusing yields one output and
  drops the rest of the stack count. Acceptable for a fusion station; revisit
  if it bites.
- ↳ **Engine failure messages neutralized** ("No weapon to enhance." / "No
  ingredient to fuse.") so they read correctly from both `/fuse` and the GUI.
- ↳ **`/fuse` kept** as a quick alternative to the machine.

### Verification gap (please UAT)
The GUI's click/close behavior can't be exercised here (no client; CI only
checks that the plugin loads + unit tests + a smoke boot). Item-handling logic
is written defensively but is **unverified in a live client** — see
`docs/uat/archived/phase-2.md`. MachineStore persistence *is* unit-tested.

## Phase 3 — Weapon Behaviors (2026-06-30)

- ↳ **DELAYED modifier** added (ingredient: **Gunpowder**). Adds a fuse before
  the swing burst fires, via the Scheduler; stacks for a longer fuse. Composes
  with REPEAT (delay = base offset, then repeats spaced after it).
- ↳ **MINING modeled as a modifier** (`MINING`, ingredient: **Amethyst Shard**)
  rather than a tool archetype, to fit the modifier-driven design. On swing it
  sweeps a yaw arc of raytraces and breaks the first block each ray hits.
  - ↳ **Hardness-capped** at config `mining.max-hardness` (default 3.0): stone
    breaks, obsidian/bedrock resist. Unbreakable (hardness < 0) always skipped.
    Honors design decision #9 (respect hardness).
  - ↳ Uses `breakNaturally(tool)` so fortune/silk on the weapon apply.
  - ↳ **No region/claim protection** (no WorldGuard etc.). On a family server
    this is acceptable; noted as a limitation. A mining weapon will break
    soft blocks wherever the player aims.
- ↳ **Bow override = stamp + impact burst.** A fused bow stamps its modifier
  CSV onto the projectile (PDC); on impact, `SwingEffectBehavior.burstAt(loc)`
  fires the same shove/area/chain at the landing point. No new modifier — any
  fused bow throws its effect downrange.
  - ↳ Projectile burst is a **single** burst (honors NOVA/EXPAND/CHAIN); it
    does **not** replay REPEAT/DELAYED. Kept simple; revisit if wanted.
- ↳ **`SwingEffectBehavior` refactored** to a location-based `applyBurst` with
  an injectable "exclude" entity, so swing (centre on caster, exclude caster)
  and projectile (centre on impact, exclude none) share one code path.

### Verification gap (please UAT)
Mining (block breaking) and the bow projectile/impact burst need a live client;
CI only confirms the plugin loads + units + smoke boot. The pure modifier logic
(DELAYED accumulation, MINING flag) **is** unit-tested. See `docs/uat/archived/phase-3.md`.
**Watch especially:** that the mining ray can't grief protected/valuable areas
and that hardness-capping behaves as intended.

## Phase 4 — Polish + deferred modifiers (2026-06-30)

Implemented the highest-value, safe, testable subset; deferred the big toys.

- ↳ **INVERT modifier** (Fermented Spider Eye). Flips the shove into an
  implosion (pull toward the burst centre). **Toggles** — two INVERTs cancel,
  honoring no-dedupe + order. This *is* the backfire path: an inverted NOVA on
  your own swing drags mobs onto you. Deterministic, not a malfunction. Pure +
  unit-tested.
- ↳ **PERSIST modifier** (Blaze Rod, Dragon's Breath). Drops a lingering field
  at the burst point that re-pulses every `persist.interval-ticks` for a
  stacking duration. The field is **fixed at the cast location** (the caster
  can walk out), excludes the caster on swing. Pure accumulation unit-tested;
  the scheduling/pulsing needs UAT.
- ↳ **REVERSE deferred.** Under the current knockback-only effect model,
  "knockback → pull toward caster" is identical to INVERT. Implementing both
  would duplicate behavior. REVERSE waits until there are distinct effects
  (damage/heal) for it to reverse. Logged rather than shipped.
- ↳ **XP cost wired** (`fusion.cost`, default 0). `/fuse` and the GUI now check
  `player.getLevel() >= cost` and deduct on success. Default 0 = free (no
  behavior change).
- ↳ **Particle shedding** added as a toggleable repeating task
  (`effect.particle-shedding`, default true) using `Particle.ENCHANT`. Added
  `Scheduler.runRepeating` for it. Cosmetic; UAT to confirm it reads well.
- ↳ **Deferred to keep this PR focused (logged, not built):** multi-world
  whitelist (low value for a single-world family server), Portal Gun (large
  stateful behavior), Cow Launcher (fun extra). These can be a later pass.

### Verification gap (please UAT)
INVERT pull direction, PERSIST field pulsing, XP deduction, and particle
shedding all need a live client. Pure modifier logic (INVERT toggle, PERSIST
accumulation) **is** unit-tested. See `docs/uat/archived/phase-4.md`. **Watch:** that a
persistent field's pulses stop after its duration and don't pile up with heavy
stacking.

## Phase 5 — Stretch (2026-06-30)

- ↳ **`/fusion give <player> <base> <MODIFIER...>`** admin command (op-only)
  added — builds a fused weapon directly from a modifier list and gives it,
  so the whole modifier space is testable without grinding ingredients. Unknown
  modifier IDs are skipped (with a "Known: ..." hint); unknown base/player are
  rejected. Includes tab completion (subcommands → online players → base hints
  → modifier IDs). The arg→known-id parsing is extracted and **unit-tested**.
- ↳ **`/fusion` op gate hoisted** to cover both `machine` and `give`.
- ↳ **Multi-block fusion structure deferred** (design's optional Phase 5 item):
  the single-block machine from Phase 2 already delivers the GUI; a multi-block
  build adds detection complexity for little gameplay gain right now. Logged.
- ↳ **Resource pack deferred** — needs authored art assets, out of scope for
  an autonomous code pass.

### Verification gap (please UAT)
`/fusion give` item creation needs a live op to run the command; CI covers the
pure parsing + load + smoke. See `docs/uat/archived/phase-5.md`.

---

## Roadmap complete

Phases 0–5 are merged. Remaining design-doc items intentionally left for a
human pass: REVERSE semantics, Portal Gun, Cow Launcher, multi-world whitelist,
multi-block machine, resource pack, and a sound-design polish pass. All flagged
above. The big **verification caveat** across Phases 2–5: anything requiring a
live client (GUIs, particles, projectiles, block-breaking, knockback feel) was
implemented defensively and is **unverified in-game** — please run the
`docs/uat/archived/phase-*.md` plans before trusting gameplay.

---

## UAT round 1 — feedback fixes (2026-06-30)

From hands-on UAT. See `docs/uat/archived/uat-round-1.md` for validation steps.

- ↳ **Machine GUI rebuilt on the vanilla anvil** (was an illegible custom
  9-slot chest with glass panes + a confirm emerald). Now: machine block is an
  **anvil**; right-click opens a real anvil view; **left = Target, right =
  Ingredient, result slot = the fused weapon you take**. Result is computed in
  `PrepareAnvilEvent`; taking it is intercepted to apply XP cost + consume
  inputs; the anvil returns unused inputs on close. This single change fixes
  three reports: illegible UI, "can't take the output" (snap-back), and gives
  the machine a distinctive look.
- ↳ **Machine block is now an ANVIL** (was a crafting table) + an **ambient
  glow** above placed machines (`MachineGlowTask`, config `effect.machine-glow`)
  so they're easy to spot. (Block glint isn't possible on placed blocks.)
- ↳ **Fused ingredients now contribute their magic.** Ingredient contribution =
  `latent(material) + fusedStack(ingredient)` (was latent-only, which gave the
  "has no magic to give" on a fused sword). Per the user's call, a fused
  ingredient gives **both** its base-material latent modifiers and its fused
  stack — consistent with how an unfused ingredient always gives its latent.
  Unit-tested.
- ↳ **`/fuse` folded into `/fusion fuse`** (op-only) and the top-level `/fuse`
  command removed, to fix the tab-completion collision between `/fuse` and
  `/fusion`. All fusion entry points are now op-gated admin subcommands;
  regular players fuse via the machine.
- ↳ Used the modern `AnvilView#setRepairCost` (not the deprecated
  `AnvilInventory#setRepairCost`, which is marked for removal in 26.x) so a
  vanilla level cost never blocks taking the result.

### Verification gap (please UAT)
The anvil GUI (preview, taking the result, input consumption, item safety on
close) and the machine glow need a live client. The engine change (fused
ingredient contributes its stack) and command parsing **are** unit-tested
(29 tests). See `docs/uat/archived/uat-round-1.md`.

---

## UAT round 2 — feedback fixes (2026-06-30)

- ↳ **Invalid fusions now explain themselves.** When the anvil can't fuse, the
  reason (e.g. "Amethyst Shard has no magic to give.", "No ingredient to fuse.")
  is shown on the **action bar**, throttled so it isn't re-sent every keystroke.
  Previously you just got an empty result + X with no explanation.
- ↳ **Renaming no longer invalidates the fusion.** Root cause: we recomputed the
  result on every `PrepareAnvilEvent` (including rename keystrokes) and ignored
  the rename text, so the renamed result was dropped. Now the anvil's rename
  text is applied to the fused output, so you get a **valid, custom-named**
  weapon.
- ↳ **Stale `latent_registry.yml` fixed (the real cause of "can't fuse sword +
  amethyst shard").** `saveResource(..., false)` only writes the file when
  absent, so a server that first ran on an early version never received
  ingredients added later (AMETHYST_SHARD→MINING, GUNPOWDER→DELAYED,
  FERMENTED_SPIDER_EYE→INVERT, BLAZE_ROD/DRAGON_BREATH→PERSIST). Now the loader
  **merges the bundled defaults** (`setDefaults` + `copyDefaults`) under the
  on-disk file, so new ingredients always appear while user edits still win on
  conflict. (A user can disable a default ingredient by setting it to `[]`.)

### Verification gap (please UAT)
Action-bar reason text and rename-to-name need a live client. The registry
merge is a load-time fix; confirm sword + amethyst shard now fuses (MINING) and
the other new ingredients work. See `docs/uat/archived/uat-round-2.md`.

---

## UAT round 3 — feedback fixes (2026-06-30)

- ↳ **Right-clicking the machine no longer triggers the weapon.** Right-clicking
  an interactive block fires an arm-swing animation, which `PlayerAnimationEvent`
  (our swing trigger) sees — so opening the anvil fired the held weapon's effect.
  `WeaponEventListener` now records each player's last right-click tick and
  ignores a swing on the same/next tick as a right-click. (Would also have
  misfired on any interactive block, not just ours.)
- ↳ **Invalid-fusion feedback moved from the action bar into the result slot.**
  The round-2 action bar was **invisible** because the in-game HUD isn't drawn
  while a container/anvil screen is open. Now, when both inputs are present but
  can't fuse, the **result slot shows a red barrier** named "Can't fuse:
  <reason>" — persistent and in-screen. Nothing is shown mid-setup (only one
  input). The barrier isn't takeable (clicking the result re-checks and refuses).
  Removed the dead action-bar/throttle code.
- ↳ **Rename now sticks on the taken item.** Round 2 applied the rename only in
  the preview (`onPrepare`); `onClick` delivered the raw fusion output, so the
  taken weapon kept the default "Fusion Weapon" name. Extracted a shared
  `applyRename` helper used by both the preview and the take.

### Verification gap (please UAT)
All three need a live client (right-click vs attack timing; the in-GUI barrier;
the renamed taken item). Build green, 29 tests.

---

## UAT round 4 — repair cost, logging, machine block (2026-06-30)

- ↳ **Repair-cost fix.** The anvil result was blocked as "Too Expensive" in
  survival because the `setRepairCost(0)` call was gated behind an
  `instanceof AnvilView` that was false on 26.x. Now set on the
  `AnvilInventory` directly. (In practice the user's "can't fuse" was a *dead
  machine* — see below — but this was a real latent bug regardless.)
- ↳ **`debug-logging`** (default true) logs every fusion attempt (anvil
  preview/take, `/fusion fuse`) to the console for diagnosis.
- ↳ **Machine is now an ENCHANTING TABLE tagged via block-entity PDC**, retiring
  `machines.yml`, `MachineStore`, and `MachineGlowTask`. Why not PDC on the old
  anvil? Anvils aren't block entities (no `TileState`), so they can't hold PDC —
  hence the side file, which could drift from the block and produce "dead
  machines" (an unregistered block fell through to a vanilla anvil). An
  enchanting table *is* a block entity: the marker lives on the block, so place
  tags it, break removes it, and there's no registry to desync. Also more
  thematic (magic block, floating-book ambiance replaces the glow task).
  - **Migration:** anvil machines from older builds are no longer recognized;
    re-obtain with `/fusion machine` (now gives enchanting tables).

### Verification gap (please UAT)
Placing/right-clicking/breaking the enchanting-table machine, and that a normal
enchanting table still enchants, need a live client. Build green, 27 tests
(the MachineStore persistence test was removed with the file store).

---

## Projectile model refactor — everything is a projectile (2026-07-01)

Reworked the effect model to be Noita-like: instead of hooking swing/bow effects
directly, a swing or bow shot now **launches one or more projectiles carrying
the modifier stack**, and each projectile **triggers its burst where it lands**.
This turns the old special-cased behaviors into composable primitives.

**Model (all live in `ModifierContext`):**
- Projectile spec — `count` (MULTISHOT), `spreadDegrees` (SPREAD), `speed`,
  `pierce` (PIERCE) + `pierceMaxHardness`, `lifetimeTicks` (LIFETIME = the expiry
  primitive), plus `mining`. Seams present but not yet wired: `gravity`,
  `bounces`.
- Burst spec — the old `radius/power/expandBonus/chainCount/inverted/persist`
  fields, unchanged; NOVA/EXPAND/CHAIN/INVERT/PERSIST still feed them.

**New classes (`com.xton.fusion.projectile`):**
- `AoeBurst` — the on-trigger payload; the old `SwingEffectBehavior.applyBurst`
  (shove/chain/persist/particles) extracted verbatim.
- `FusionProjectile` — a custom, particle-rendered projectile ticked by us (no
  Bukkit entity), sub-stepping each tick so we own pierce/mining/lifetime. Not a
  Bukkit `Projectile`, because Bukkit projectile physics can't express
  pierce/no-gravity/custom-lifetime cleanly.
- `ProjectileLauncher` — builds the context from base config + stack (pure,
  unit-tested) and spawns `count` projectiles with the SPREAD cone.

**Primitive mapping / ↳ calls:**
- ↳ **REPEAT dropped, replaced by MULTISHOT** (`addCount`). Per the design
  refinement, repeat isn't a useful projectile primitive; number-of-projectiles
  is. Old items tagged `REPEAT` simply resolve to nothing now (harmless; they
  lose that one effect). Ingredients remapped (Rabbit's Foot/Slime/Chorus →
  MULTISHOT).
- ↳ **DELAYED renamed to LIFETIME and repurposed** (`addLifetimeTicks`) rather
  than a pre-fire delay — the user noted "Delayed already IS the expiry
  primitive," and "Delayed" wrongly implied the shot waits to be *fired* instead
  of waiting to *trigger*. A longer lifetime now means the shot flies farther
  before it goes off. (Ingredient unchanged: Gunpowder → LIFETIME.)
- ↳ **MINING rebuilt from primitives**: it sets `pierce` + `mining` + a short
  lifetime + fast speed (config `mining.lifetime-ticks/speed/max-hardness`). A
  "true mining ray" is pierce + very short expiry, exactly as described. Retired
  the arc-raycast `MiningRayBehavior`.
- ↳ **New PIERCE and SPREAD modifiers** with new ingredients (Arrow/Quartz →
  PIERCE; Feather/Sugar → SPREAD).
- ↳ **Melee = a short fast bolt that triggers where it lands.** Non-piercing
  shots stop at the first block/entity and burst there; base lifetime (30t) is
  the fallback when they hit open air.
- ↳ **Pierce + AOE = contact-hits along the line, one burst at the end.** A
  piercing shot applies a light along-travel shove to each entity it passes
  (`contactShove`), then fires the full `AoeBurst` once where it finally stops.
- ↳ **Fused bows now cancel the vanilla arrow** and launch our projectiles
  instead, speed scaled by draw force (`0.35 + 0.65·force`). A fused bow is a
  wand; Multishot bows fan a volley. (Previously we stamped PDC on the vanilla
  arrow and burst on its natural hit — removed.)
- ↳ **Bounce/gravity left as documented seams** (fields + a `TODO(bounce seam)`
  branch in `FusionProjectile`), per "we don't need to implement all these from
  the start, but the model needs the seams." Grenade/cluster-bomb (spawn-children
  on trigger, carrying a decremented `generation`) are the next builds on top.

### Flight + payload model (follow-up, same day)

Corrected the model per feedback: a projectile is a **flight** (how it travels)
plus a **payload** (a list of effects delivered where it terminates). Both can be
empty — a zero-length flight detonates at the origin; an empty payload delivers
nothing. This replaces the earlier "every triggered shot fires the base burst"
call (which made a Mining-only ray pop at its terminus — unwanted).

- **`Payload` = `List<PayloadEffect>`**, delivered at termination. Today the only
  effect is `BurstEffect` (the AOE). The list *is* the extensibility seam: a
  future spawn effect (cluster bomb) re-launches child projectiles from the
  terminus carrying a decremented `generation`, with no special-casing elsewhere.
- ↳ **Burst is opt-in, not a base default.** A burst is delivered only if a burst
  modifier asked for it (`ModifierContext#enableBurst()`, set by NOVA / EXPAND /
  CHAIN / INVERT / PERSIST). The flight modifiers (MULTISHOT / SPREAD / PIERCE /
  LIFETIME / MINING) don't. So a **mining ray delivers an empty payload — no pop
  at its terminus** — while Mining + Nova still bursts. Base radius/power still
  seed the burst, but only when one is enabled.
- ↳ **Mining does its work along the flight, not at the end.** Each block it
  bores plays a break sound + block particles (on top of `breakNaturally`'s own
  effects); the terminus is silent. Removed the unconditional arrow-hit sound
  from `stop()` — the burst plays its own sound, an empty payload none.

### Verification gap (please UAT)
All flight/collision behaviour needs a live client — pierce through walls,
mining tunnels (and that they *don't* pop at the end), multishot spread, bow
volleys, burst-on-land. Build green (35 tests); the flight/payload spec-building
is unit-tested (`ProjectileModelTest` asserts mining/pierce deliver empty
payloads), but the world interaction is not testable here. See
`docs/uat/archived/projectile-model.md`.

---

## Emitter/transform model — RPN compile (2026-07-01)

Reworked the modifier model per feedback: modifiers were conflating two roles.
Now they split cleanly, and the stack **compiles** (it is no longer a flat fold
over one shared context).

- **Emitters** add a concrete element: `PUSH` and `DAMAGE` bursts (and, seamed,
  a future spawn-projectile). **Transforms** modify the nearest preceding
  element: AOE transforms (`EXPAND` ×radius, `AMPLIFY` ×power/damage, `CHAIN`,
  `INVERT`, `PERSIST`) scale/decorate the last burst; flight transforms
  (`MULTISHOT`, `SPREAD`, `PIERCE`, `LIFETIME`, `MINING`) shape the projectile.
- ↳ **Binding is RPN / apply-to-previous** (user's call): a transform binds to
  the nearest *preceding* emitter, because fusion appends the ingredient's
  latents — so you build by adding an emitter, then piling transforms on it. A
  transform with no matching preceding emitter is **inert** (Expand alone does
  nothing). This is exactly the "burst is opt-in" property, now falling out of
  the model instead of a special flag.
- ↳ **Scope = nearest previous only** (user's call): `PUSH PUSH EXPAND` widens
  only the second push. AOE transforms bind to the top AOE element; flight
  transforms bind to the (single, implicit) current projectile.
- The stack compiles to a `ProjectileSpec` (flight + a `List<AoeSpec>` payload)
  via `WeaponBuilder`. `Modifier.apply(WeaponBuilder)` replaces the old
  `apply(ModifierContext)`; `ModifierContext` is deleted. Emitter vs transform
  is tagged by `Modifier.Category`.
- ↳ **NOVA retired, split into `PUSH` + Expand/Amplify.** The monolithic Nova
  (radius 4 / power 1.4 baked in) becomes the recipe `PUSH · EXPAND · EXPAND`.
  Smaller primitives, more to build with. Added `DAMAGE` (a real damaging burst)
  and `AMPLIFY` (×power/damage) so there's something to scale. `EXPAND` is now
  **multiplicative** (×1.6/apply) rather than additive.
- ↳ **Hybrid ingredient roster** (user: "go nuts"). `latent_registry.yml` now
  has *reagents* (one atomic attribute each) for composing, plus *bundles* —
  curated multi-attribute "spells" (TNT = `DAMAGE·EXPAND·EXPAND`, Firework Star =
  `DAMAGE·MULTISHOT·SPREAD`, End Crystal = the works). The engine already
  appended multi-value latent lists, so this was pure content.
- ↳ **Pierce contact impulse is now a fixed constant** (`CONTACT_IMPULSE`) — a
  piercing shot nudges entities along the line; the real damage/push is the
  payload at the terminus. There's no longer a single projectile "power" to
  derive it from (power lives per-AOE).
- Old items tagged `NOVA`/`REPEAT`/`DELAYED` resolve to nothing now — re-fuse.

### Verification gap (please UAT)
The compile is fully unit-tested (`WeaponCompileTest` covers emitter/transform
binding, RPN scope, inert transforms, flight; `ProjectileModelTest` covers
payload building). Damage bursts, the bundle ingredients, and all flight/world
behaviour still need a live client. Build green (32 tests). See
`docs/uat/archived/projectile-model.md`.

---

## In-process functional self-test (2026-07-02)

Added `/fusion test` (console/op only) + a `SelfTest` runner that exercises the
**real** projectile/burst code against a live world — the gameplay layer neither
MockBukkit (no world/entities) nor the smoke boot (only checks clean load) could
reach. Motivated by wanting functional coverage while UAT-by-hand wasn't
possible.

- ↳ **In-process command over an external bot.** Researched Mineflayer (a real
  player-bot; viable here only via the ViaVersion bridge already on the UAT
  image) and Scenamatica (purpose-built PaperMC YAML scenarios, but last release
  Mar 2025 and, being a plugin, can't be version-bridged to our Paper build). An
  in-process command sidesteps the protocol/version problem entirely, runs
  headless in the smoke boot we already have, and tests the real Bukkit-world
  effects. It does **not** cover the input path (a real swing/bow-draw event) —
  a Mineflayer end-to-end pass remains the follow-on for that.
- Scenarios: PUSH burst imparts knockback (`getVelocity()` non-zero), DAMAGE
  burst lowers health, a MINING ray breaks a scratch dirt corridor (asserted
  ~25t after firing, since flight is async), and payload opt-in (MINING → empty,
  PUSH → non-empty). Spawns AI-off zombies as dummies and removes them after.
- Reports `[fusion-selftest] RESULT: PASS|FAIL (k/n)` to the server log;
  `scripts/smoke-test.sh` runs `rcon-cli fusion test`, waits, and fails the
  build unless it sees PASS. The command never runs on its own — normal servers
  are untouched unless someone invokes it.
- ↳ **Assert velocity/health synchronously** in the same tick as the burst, so
  no game tick elapses to introduce AI/fire/gravity noise; only the mining ray
  (inherently multi-tick flight) is asserted on a delay.

### Verification gap
Can't run the smoke boot here (no Docker in this sandbox), so the self-test is
written and wired but **unrun** — CI's smoke job is its first real execution.
The pure compile remains unit-tested; this adds the live-world layer in CI.

---

## Mineflayer end-to-end pass (2026-07-02)

Added an end-to-end test layer: a real [Mineflayer](https://github.com/PrismarineJS/mineflayer)
bot connects to a Paper server and drives the **player input path** the
in-process `/fusion test` bypasses — a real held fused item plus real client
events (arm-swing, bow draw/release) flowing through our Bukkit listeners.

- ↳ **1.21.11 client + ViaBackwards bridge.** `minecraft-data` lists `26.1.2`,
  but the lower-level `node-minecraft-protocol` only speaks a curated set of
  protocol versions (newest 1.21.11) — a native connect throws "unsupported
  protocol version". So the bot joins as **1.21.11** (closest nmp supports) and
  the server bridges it up. The bridge took a few CI rounds to get right:
  ViaVersion alone bridges *newer* clients to *older* servers; an **older** client
  (1.21.x) joining a **newer** server (26.x) needs **ViaBackwards** — without it
  the vanilla server just kicks "Outdated client". So the e2e server loads
  `viaversion,viabackwards`. (Version overridable via `MC_BOT_VERSION`.)
- ↳ **Assert on block changes, not entity state.** A bot observes world blocks
  far more reliably than mob health/knockback (which lean on entity metadata and
  physics). Both scenarios point a **MINING** weapon at a `/fill` dirt wall and
  check it gets carved:
  - `swing-mining` — `bot.swingArm()` fires `PlayerAnimationEvent(ARM_SWING)` →
    `WeaponEventListener` → projectile → wall carved. This is the real event the
    self-test skips (it calls the launcher directly).
  - `bow-mining` — `activateItem`/`deactivateItem` → `EntityShootBowEvent` →
    the vanilla arrow is cancelled and a fusion projectile launched. Asserts the
    wall is carved **and** no `arrow` entity exists (proves the override).
- ↳ **Creative + peaceful + no mob spawning + fixed daylight** for a
  deterministic sandbox: creative lets the bow draw without arrows and spares the
  bot damage; the gamerules keep stray mobs/among out of the scene.
- New `e2e` CI job (`needs: build`) runs `scripts/e2e-test.sh` (boots Paper with
  the bot opped, then `node mineflayer/e2e.js`). `make e2e` runs it locally.
  Overall + per-connection watchdogs fail fast instead of hanging.
- **Not covered yet (follow-on):** the fusion-machine GUI flow (open the anvil,
  place items, take the result) — higher mineflayer effort (window handling), so
  deferred. The swing/bow input paths were the clearest can't-test-in-process win.

### Verification gap
No Docker in this sandbox, so the e2e job is written and wired but **first runs
in CI**. Node install + `node --check` pass locally, and `minecraft-data`
confirms native `26.1.2` support. Bot timing/aim may need a tuning pass from CI
feedback (as the self-test did).

---

## Mineflayer e2e: Fusion Machine (anvil) GUI scenario (2026-07-02)

Added the deferred GUI scenario — the flow that historically had the most manual
UAT failures (dead machines, no result preview, "Too Expensive" blocking the
take, the result slot snapping back). The bot now drives the whole thing.

- ↳ **The bot places the machine itself.** Only a real `BlockPlaceEvent` tags
  the enchanting table as a machine, so `/setblock` won't do — the bot equips the
  machine item and `placeBlock`s it against a `/setblock` stone reference beside
  it, then right-clicks (`openBlock`) to open the anvil.
- ↳ **Low-level window driving.** mineflayer's high-level `anvil.combine()` bakes
  in vanilla anvil cost logic and rejects a sword+nether-star as "not
  anvil-able", so we drive slots directly: `moveSlotItem` the Target into slot 0
  and the Ingredient into slot 1, poll slot 2 for the fused preview, then
  `clickWindow(2)` to take it.
- ↳ **Assert on the plugin's chat, not item NBT.** Taking the result makes our
  `onClick` deliver the item and message "Fusion complete!"; the bot asserts it
  received that message (robust across the ViaBackwards item translation, unlike
  reading fused lore/components off the client). This is the assertion that
  catches a "Too Expensive" or snap-back regression — no take, no message.
- ↳ `/clear` at the scenario start so the earlier swing/bow items don't shadow
  the plain `diamond_sword` we fuse.

### Verification gap
Block placement + window interaction are the fiddliest bot operations; like the
swing/bow pass, this may want a tuning round from CI feedback. The diagnostics
(window slot names, chat dump) will pinpoint any step that stalls.

---

## Run the Docker harness in the Claude Code cloud sandbox (2026-07-02)

Goal: run `make smoke` / `make e2e` in-session instead of only in GitHub CI, to
drop the push-and-wait loop. Now possible because the web sandbox ships Docker.

- ↳ **Daemon started per session via the SessionStart hook.** The sandbox has the
  docker binary but doesn't start the daemon at boot, and a running daemon
  doesn't survive across sessions — so `scripts/docker-up.sh` starts it each
  session (idempotent, root-only, best-effort so it never blocks startup). The
  hook's file writes (JDK, caches) persist; the daemon is the one thing that must
  re-start, which the hook handles. Verified: cold start + the full hook both
  bring the daemon up.
- ↳ **No daemon proxy/CA config needed.** dockerd inherits the session env
  (`HTTPS_PROXY` + the agent CA in the system trust store), so registry auth
  already works through the policy proxy (verified: Docker Hub `registry-1`/`auth`
  reachable). The dynamic proxy port is inherited live, not hardcoded.
- ↳ **The real blocker is the network allowlist, not Docker.** The default
  Trusted allowlist misses the Docker Hub blob CDN
  (`production.cloudfront.docker.com` → 403) and the Mojang/Modrinth hosts the
  server needs at boot. That's a claude.ai/settings change (full internet, or the
  allowlist in `docs/cloud-sandbox.md`) — can't be set from the repo. Documented
  the exact hosts, probed against the live policy.
- ↳ **`scripts/cloud-setup.sh`** (`make cloud-setup`) is the one-time cache warm
  (pull image + warm Gradle/npm); its disk writes cache across sessions. Kept
  separate from the per-session hook so a big image pull doesn't slow every
  session start.
- ↳ **Named-volume for `uat` data deferred.** The compose binds `/data` to the
  (re-cloned) `docker/data/`, so `make uat` re-downloads Paper each session; a
  named volume would cache it. Left as a documented follow-up — the smoke/e2e
  scripts already use ephemeral per-run containers, so it doesn't block the goal.

### Verification gap
Daemon start + Gradle build verified in-sandbox. `make smoke` / `make e2e`
end-to-end need the network widening above (image pull currently 403s on the
Docker Hub CDN), which is a per-environment settings action.

## UAT round 3 — model & GUI refinements (batch)

A batch of UAT feedback fixes and model refinements. Each landed as its own
commit; the notable decisions:

- **Fusion "generation" removed.** The `[Gen N]` counter and the max-generation
  depth cap weren't useful concepts; the only cap now is `max-modifiers`
  (default raised 8 → 24, configurable). The unrelated projectile-recursion
  generation (cluster-bomb seam) is untouched.
- **"Fused from" accumulates the real lineage,** collapsed with counts
  ("Diamond Sword + 3× Nether Star + Heart of the Sea") via a `Lineage` helper.
  It previously only ever showed the last pair.
- **Latent config ownership.** The plugin no longer generates `latent_registry.yml`
  (an ambiguously plugin/user-owned file let a stale mapping shadow a renamed
  modifier — e.g. `NETHER_STAR → NOVA` after NOVA was removed → a "fused but
  inert" weapon). Defaults live in the jar; a plugin-owned
  `latent_registry.example.yml` is refreshed each boot; a user `latent_registry.yml`
  is a **full replacement** (no merge). Unknown modifier IDs are warned, not
  silently dropped.
- **Rename-only fusion.** A target with no ingredient is a valid rename (item
  back, renamed) — which also removed the one-frame result-slot flicker. The
  machine anvil is titled "✦ Fusion" (Paper `MenuType.ANVIL` builder).
- **Melee vs. bow flight by weapon type.** A melee swing delivers at arm's length
  (short base lifetime, no visible trail); a bow throws the same weapon downrange.
  **Gravity is the launcher's call, never a modifier:** bows arc, melee is
  straight. Seeded before the stack compiles so a future gravity modifier can
  override it. (Until then it's fixed by weapon type.)
- **Pierce delivers its payload along the path.** A piercing shot now fires its
  full burst at every entity it passes through (Expand/Amplify splash on each),
  not only at the terminus. **Future:** split the pierce-contact effect from the
  terminus effect — kept unified and simple for now.
- **MINING reframed as an emitter.** MINING adds a MINING-kind AOE of base radius
  1 (a one-block bore) so **Expand widens the tunnel**. It no longer pierces on
  its own: alone it breaks what it hits and stops; add Pierce to bore, Lifetime
  to reach. Carved along the path (excluded from the terminus payload, so a bare
  ray still doesn't pop). Bore radius is capped so a heavily-Expanded ray can't
  level a region.
- **PERSIST "grenade" visual.** A glowing dot sits at the retrigger point and
  blinks faster until a small explosion sprite marks each pulse (the moment the
  effect applies). Single bursts get a centred boom sprite too; CRIT trails stay
  only for CHAIN. Visual-only — damage timing unchanged.
- **Machine glow.** A config-gated task scans nearby chunk block-entities and
  glows tagged machines (no location registry needed).

## UAT round 4 — mining/melee feel (batch)

- **`DIAMOND_PICKAXE`** added to the `/fusion give` base autocomplete.
- **Mining carves through vegetation.** The bore now triggers on any breakable
  block, not just solid ones — a ray through grass/crops clears the plant and
  the ground within its radius (previously it sailed through non-solid blocks
  without triggering, leaving the ground untouched). Still won't chew fluids or
  obsidian (hardness cap).
- **Melee is invisible and instant.** A melee swing shows no flight trail at all
  (mining sparks included) and uses a high base speed (`projectile.melee-speed`)
  with a 1-tick life, so it reaches its arm's-length terminus within the first
  tick — it reads as a swing, not a travelling bolt. Ranged shots keep their
  trail.
- **LIFETIME adds a fixed distance, not ticks.** Tunnel length was speed ×
  lifetime, so the faster melee speed inflated every LIFETIME tunnel. LIFETIME
  now contributes `range ÷ speed` ticks (config `lifetime.range-per-apply`,
  ~12 blocks), so one LIFETIME adds the same length regardless of speed —
  ~25% of the old length, and velocity-proof.
