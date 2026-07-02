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
2. **Machine glow.** `/fusion machine`, place it → a particle glow rises above
   it. A *normal* enchanting table placed elsewhere has none.
3. **Fusion sound/particles.** Fuse something → anvil sound + a totem burst play
   at the machine. Does it feel satisfying?
4. **Spread looks like a spray.** `/fusion give <you> DIAMOND_SWORD DAMAGE
   MULTISHOT MULTISHOT SPREAD` → the 5 bolts should visibly fan into a cone (the
   *count* and *angle* are asserted; the *look* is yours to judge).
5. **Bow draw feel.** `/fusion give <you> BOW DAMAGE AMPLIFY` → tap = slow lob,
   full draw = fast bolt. Feels like a bow?
6. **Mining feel & drops.** `/fusion give <you> DIAMOND_PICKAXE MINING` → carving
   a wall should *feel* like a ray; confirm blocks drop items and silk/fortune on
   the tool apply. (Grief check: only breaks where you aim — no region protection
   yet, see DECISIONS.md.)

## B. Performance (needs a running server + judgement)

7. **PERSIST doesn't lag.** `/fusion give <you> DIAMOND_SWORD DAMAGE PERSIST
   PERSIST PERSIST`, swing in a crowd, walk away → the field pulses then **stops**
   cleanly; no pile-up or TPS drop. (That it re-pulses at all is asserted; that
   it stays performant under stacking is yours.)

## C. Not-yet-automated mechanics (worth a manual pass)

8. **XP cost gate.** Set `fusion.cost: 3` in the plugin config, restart. With
   < 3 levels a fuse is refused ("Fusing costs 3 XP levels."); with ≥ 3 it
   succeeds and drops your level by 3. (Config + restart, so it's not in the bot
   suite.)
9. **Anvil rename sticks.** In the machine, load a valid Target + Ingredient,
   type a name in the anvil's rename field → the result stays valid and carries
   your custom name; take it and confirm.
10. **Machine survives a restart.** Place a machine, fuse once, restart the
    server, right-click it → it still opens (location persisted in
    `machines.yml`). Break it → it drops the **Fusion Machine** item, not a plain
    enchanting table.
11. **Fused item as ingredient.** Fuse a sword, then use *that fused sword* as
    the Ingredient on a fresh weapon → no "has no magic"; the target gains the
    ingredient's whole stack plus its base-material latents.
12. **Ingredient roster spot-check.** Data-driven (`latent_registry.yml`), so
    just eyeball a few map correctly: e.g. **Nether Star → PUSH**, **TNT →
    DAMAGE·EXPAND·EXPAND**, **Amethyst Shard → MINING**. Lore should list the
    modifiers.

---

_If you automate any of section C, move its line up under "already automated"
above and delete it here._
