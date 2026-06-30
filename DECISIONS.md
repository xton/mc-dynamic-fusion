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
