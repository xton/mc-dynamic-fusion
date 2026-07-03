# UAT — the short list (only what a human still needs to check)

Most of the weapon model is now checked automatically, so this list is only the
things a bot/headless test **can't** judge: cosmetics, feel, and a few bits that
depend on config or a server restart.

## Run the automated suites first

Before touching this list, run the harnesses — if they're green, the mechanics
below the "already automated" line are covered and you can skip them:

```
make smoke   # boots Paper + runs /fusion test: 18 in-process checks
make e2e     # a real Mineflayer bot: swing/bow input + the anvil GUI (8 checks)
```

**Already automated (don't hand-test these):** emitter bursts (PUSH/DAMAGE),
every transform's scaling and RPN nearest-previous binding (EXPAND, AMPLIFY,
MULTISHOT, SPREAD, LIFETIME, INVERT, CHAIN, PERSIST), PIERCE pass-through,
MINING carving + stopping at obsidian + no-pop terminus, the swing and bow input
paths, and the anvil machine: shows-result, take ("Fusion complete!"),
rejects-junk (red barrier), and close-returns-inputs. See `SelfTest.java` and
`mineflayer/e2e.js`.

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
   **at arm's length** — a short, near-invisible poke, the burst going off right
   in front of you (no long bolt). A fused **bow** (`BOW DAMAGE`) throws the same
   burst downrange in a **gravity arc**: tap = short weak lob, full draw = long
   fast arc. Do both read right?
6. **Mining tunnels.** `DIAMOND_PICKAXE MINING PIERCE` bores a 1-wide tunnel (add
   `LIFETIME` to reach farther); add `EXPAND EXPAND` for a **fat** tunnel. `MINING`
   *alone* (no Pierce) should break just the block in front and stop. Blocks drop
   items; silk/fortune apply. (Grief check: only where you aim — no region
   protection yet, see DECISIONS.md.)
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

---

_If you automate any of section C, move its line up under "already automated"
above and delete it here._
