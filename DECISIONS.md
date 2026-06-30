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
`docs/uat/phase-2.md`. MachineStore persistence *is* unit-tested.

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
(DELAYED accumulation, MINING flag) **is** unit-tested. See `docs/uat/phase-3.md`.
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
accumulation) **is** unit-tested. See `docs/uat/phase-4.md`. **Watch:** that a
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
pure parsing + load + smoke. See `docs/uat/phase-5.md`.

---

## Roadmap complete

Phases 0–5 are merged. Remaining design-doc items intentionally left for a
human pass: REVERSE semantics, Portal Gun, Cow Launcher, multi-world whitelist,
multi-block machine, resource pack, and a sound-design polish pass. All flagged
above. The big **verification caveat** across Phases 2–5: anything requiring a
live client (GUIs, particles, projectiles, block-breaking, knockback feel) was
implemented defensively and is **unverified in-game** — please run the
`docs/uat/phase-*.md` plans before trusting gameplay.

---

## UAT round 1 — feedback fixes (2026-06-30)

From hands-on UAT. See `docs/uat/uat-round-1.md` for validation steps.

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
(29 tests). See `docs/uat/uat-round-1.md`.
