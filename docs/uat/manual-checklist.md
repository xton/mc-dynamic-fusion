# UAT — the short list (only what a human still needs to check)

Most of the weapon model is now checked automatically, so this list is only the
things a bot/headless test **can't** judge: cosmetics, feel, and a few bits that
depend on config or a server restart.

## Run the automated suites first

Before touching this list, run the harnesses — if they're green, the mechanics
below the "already automated" line are covered and you can skip them:

```
make smoke   # boots Paper + runs /fusion test: 33 in-process checks
make e2e     # a real Mineflayer bot: swing/bow input + the anvil GUI (10 checks)
```

**Already automated (don't hand-test these):** emitter bursts (PUSH/DAMAGE),
every transform's scaling and RPN nearest-previous binding (EXPAND, AMPLIFY,
MULTISHOT, SPREAD, LIFETIME, INVERT, CHAIN, PERSIST), PIERCE pass-through,
MINING carving + stopping at obsidian + no-pop terminus, FIRE melting snow &
igniting a mob, ICE freezing water, DEPOSIT backfilling air, DEPOSIT·TRAIL
filling the flight path, a BOUNCE bolt rebounding off a wall into a mob, a SPAWN
child ricocheting off a wall, the DEPOSIT:<block> parameter parsing and
BOUNCE/SPAWN wiring at compile time, the swing and bow input paths, and the anvil
machine: shows-result, take ("Fusion complete!"), rejects-junk (red barrier),
and close-returns-inputs. See `SelfTest.java` and `mineflayer/e2e.js`.

Build test weapons the fast way: `/fusion give <you> <BASE> <MOD...>` (op only).

---

## A. Cosmetics & feel (needs your eyes)

1. **Particle shedding.** Hold any fused weapon and stand still → subtle
   enchant-style particles rise off you; holding a non-fused item shows nothing.
   Toggle with `effect.particle-shedding: false`.
2. **Machine glow.** `/fusion machine`, place it → a subtle END_ROD/enchant glow
   rises above it. A *normal* enchanting table placed elsewhere has none. Toggle
   with `effect.machine-glow: false`.
3. **Fusion sound/particles.** Fuse something → anvil sound + a totem burst play
   at the machine. Does it feel satisfying? The GUI title reads **✦ Fusion**.
4. **Spread looks like a spray.** `/fusion give <you> DIAMOND_SWORD DAMAGE
   MULTISHOT MULTISHOT SPREAD` → the 5 bolts should visibly fan into a cone (the
   *count* and *angle* are asserted; the *look* is yours to judge).
5. **Melee vs. bow feel.** A fused melee weapon (`DIAMOND_SWORD DAMAGE`) delivers
   **at arm's length, near-instantly, with no visible bolt** — it should read as
   a swing, the burst going off right in front of you. A fused **bow**
   (`BOW DAMAGE`) throws the same burst downrange in a **gravity arc**: tap =
   short weak lob, full draw = long fast arc. Do both read right?
6. **Mining tunnels.** `DIAMOND_PICKAXE MINING PIERCE` bores a 1-wide tunnel; add
   `EXPAND EXPAND` for a **fat** one, and `LIFETIME` for a longer one — each
   LIFETIME adds a fixed ~12-block reach (the same whether the shot is fast or
   slow). `MINING` *alone* (no Pierce) breaks just the block in front and stops.
   Aim it at a **grass patch** → the plant *and* the ground beneath should carve
   (not just the plant). Blocks drop items; silk/fortune apply. (Grief check:
   only where you aim — no region protection yet, see DECISIONS.md.)
7. **PERSIST grenade.** `DIAMOND_SWORD DAMAGE PERSIST` → where it lands, a glowing
   dot sits and **blinks faster** until a small **explosion** marks each
   retrigger. Reads as a charging grenade, and the boom is *at the point* (not a
   scatter of sparks)?

## B. Performance (needs a running server + judgement)

8. **PERSIST doesn't lag.** `/fusion give <you> DIAMOND_SWORD DAMAGE PERSIST
   PERSIST PERSIST`, swing in a crowd, walk away → the field pulses then **stops**
   cleanly; no pile-up or TPS drop. (That it re-pulses at all is asserted; that
   it stays performant under stacking is yours.)

## C. Not-yet-automated mechanics (worth a manual pass)

9. **XP cost gate.** Set `fusion.cost: 3` in the plugin config, restart. With
   < 3 levels a fuse is refused ("Fusing costs 3 XP levels."); with ≥ 3 it
   succeeds and drops your level by 3. (Config + restart, so it's not in the bot
   suite.)
10. **Anvil rename.** *Rename-only:* put just a Target (no ingredient) and type a
    name → the result slot shows the renamed item, takeable, with no flicker.
    *Fusion + rename:* a valid fusion with a typed name keeps the custom name on
    the fused output.
11. **Machine survives a restart.** Place a machine, fuse once, restart the
    server, right-click it → it still opens. Break it → it drops the **Fusion
    Machine** item, not a plain enchanting table.
12. **Fused item as ingredient.** Fuse a sword, then use *that fused sword* as
    the Ingredient on a fresh weapon → no "has no magic"; the target gains the
    ingredient's whole stack plus its base-material latents. The **Fused from**
    line should accumulate (e.g. `... + 3× Nether Star + Heart of the Sea`).
13. **Ingredient roster.** Data-driven — the plugin no longer writes
    `latent_registry.yml`; defaults come from the jar and a reference copy is
    refreshed at `latent_registry.example.yml` each boot. Spot-check a few map
    right: **Nether Star → Push**, **TNT → Damage·Expand·Expand**, **Amethyst
    Shard → Mining**. (A custom `latent_registry.yml`, if you make one, *fully
    replaces* the defaults — copy the example and edit.)

## D. New emitters — feel & the player-only bits (worth a manual pass)

The block/mob *outcomes* of FIRE/ICE/DEPOSIT/TRAIL are asserted headlessly, but
their **feel**, spread, and the **caster-facing** effects (TELEPORT needs a real
player; SPAWN clusters are best judged by eye) still want a look:

14. **Fire feels like fire.** `DIAMOND_SWORD FIRE PIERCE LIFETIME` swept across
    grass/snow → it should **spread** real fire, melt snow/ice, and set mobs
    alight. Radius reads a touch wider than a bare MINING bore. (Grief check: real
    fire spreads — try it somewhere you don't mind.)
15. **Ice is the inverse.** `... ICE PIERCE` over water → freezes to ice; over
    lava → obsidian; over a fire → snuffs it; mobs get the blue **freeze** shiver.
16. **Deposit mounds & traps.** `DEPOSIT:DIRT` raises a dirt blob where it lands;
    `DEPOSIT:SAND` over a mob's head drops suffocating sand; `DEPOSIT:WATER` /
    `DEPOSIT:LAVA` splash a pool. Only **air** is filled — it never replaces your
    build.
17. **Block-replacement bolt (order matters).** `DIAMOND_PICKAXE MINING PIERCE
    DEPOSIT:DIRT` bores *and* backfills in one pass (solid tunnel of dirt). Swap to
    `DEPOSIT:DIRT ... MINING` and it fills then re-digs — confirm order is honored.
18. **Trail lays a line.** `DEPOSIT:WATER TRAIL` (or `FIRE TRAIL`) draws a
    continuous wake along the whole flight, not just at the end. Deduped, so no
    spammy stutter.
19. **Spawn cluster.** `DIAMOND_SWORD DAMAGE SPAWN MULTISHOT SPREAD FIRE` → where
    the first bolt lands it should **burst into a fanned volley** of fiery
    children. Bump `spawn.max-generation` for more chaos; confirm it can't run away.
20. **Teleport (player-only).** `DIAMOND_SWORD TELEPORT` → you warp to where the
    bolt lands. `... PIERCE LIFETIME TELEPORT` blinks you to the **far end of a
    bored tunnel**. Under `MULTISHOT` you teleport **once** (first bolt to land),
    and you never end up stuck in a wall or a mob (safe offset). Ender Pearl is the
    ingredient.
21. **Bounce feel.** `DIAMOND_SWORD DAMAGE BOUNCE LIFETIME` → the bolt should
    **ricochet** off floors/walls (a soft *tick* and crit sparks at each bounce),
    losing a little speed each time, and **only go off** when it finally expires
    or strikes a mob head-on. Fire it into a corner and watch it rattle around.
    Slime Block is the ingredient.
22. **Spawn ricochet.** `DIAMOND_SWORD DAMAGE SPAWN MULTISHOT SPREAD` fired
    **straight at a wall** → the children should spray **back off the wall into
    the room**, not vanish into it. (Compare: before this, a wall-hit cluster
    wasted its children against the surface.) `... BOUNCE ... SPAWN ...` is a
    grenade that bounces to rest, then scatters.

---

_If you automate any of section C, move its line up under "already automated"
above and delete it here._
