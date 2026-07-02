# UAT — Emitter/transform projectile model

Run the UAT server (`make rebuild`), join, `/op` yourself. Two big ideas to
verify:

1. **A weapon = a projectile (flight) + a payload (bursts).** A swing/bow shot
   launches projectiles that fly and deliver their payload where they land.
2. **Modifiers are emitters or transforms.** Emitters (`PUSH`, `DAMAGE`) add a
   burst; transforms modify the *nearest preceding* emitter (RPN). A transform
   with nothing before it is inert.

Fastest way to build test weapons is `/fusion give <you> <base> <MOD...>`.
Modifier IDs: emitters `PUSH DAMAGE`; AOE transforms `EXPAND AMPLIFY CHAIN
INVERT PERSIST`; flight transforms `MULTISHOT SPREAD PIERCE LIFETIME MINING`.
(`NOVA`, `REPEAT`, `DELAYED` are gone.)

## Emitters land a burst where the shot lands
1. `/fusion give <you> DIAMOND_SWORD PUSH`
   - ✅ Swing toward a mob a few blocks off → a bolt flies from your eyes and a
     small **shove** goes off where it lands, not on you.
2. `/fusion give <you> DIAMOND_SWORD DAMAGE`
   - ✅ Same, but the burst **hurts** mobs in range (watch health) instead of
     shoving.

## Transforms scale the previous emitter (RPN)
3. `/fusion give <you> DIAMOND_SWORD PUSH EXPAND EXPAND` → a nova: a much **wider**
   shove (radius ×1.6 twice).
4. `/fusion give <you> DIAMOND_SWORD DAMAGE AMPLIFY AMPLIFY` → a **harder-hitting**
   damage burst (damage ×1.6 twice).
5. `/fusion give <you> DIAMOND_SWORD EXPAND` (transform only, no emitter)
   - ✅ **Nothing happens** on swing — a transform with no preceding emitter is
     inert (the bolt flies and delivers an empty payload).
6. `/fusion give <you> DIAMOND_SWORD PUSH PUSH EXPAND`
   - ✅ Only the **second** push is widened (nearest-previous binding). Hard to
     see precisely, but the two bursts should differ in size.
7. `/fusion give <you> DIAMOND_SWORD PUSH INVERT` → the landing burst **pulls**
   mobs inward (two INVERTs cancel). `PUSH PERSIST` → a lingering pulsing shove.
   `DAMAGE CHAIN` → damage that leaps to nearby mobs.

## Two bursts in one shot
8. `/fusion give <you> DIAMOND_SWORD PUSH EXPAND DAMAGE AMPLIFY`
   - ✅ On landing, delivers **both** a widened shove and a stronger damage burst.

## Multishot + Spread (shotgun)
9. `/fusion give <you> DIAMOND_SWORD DAMAGE MULTISHOT MULTISHOT SPREAD`
   - ✅ 5 damaging bolts fanned into a cone. Fire into a pack → several land in a
     spread. Add another `SPREAD` for a wider fan.

## Pierce + Lifetime (ray gun)
10. `/fusion give <you> DIAMOND_SWORD DAMAGE PIERCE`
    - ✅ The bolt punches **through** soft blocks and every entity in its path
      (damaging each), stopping at hard blocks (obsidian).
11. `/fusion give <you> DIAMOND_SWORD DAMAGE PIERCE LIFETIME LIFETIME`
    - ✅ Reaches noticeably farther before expiring.

## Mining ray
12. `/fusion give <you> DIAMOND_PICKAXE MINING`
    - ✅ Swing at stone/dirt → a short, fast ray bores a stub tunnel (obsidian
      resists).
    - ✅ **No pop at the end** — a bare mining ray delivers an empty payload; you
      hear block breaks, not an explosion.
13. `/fusion give <you> DIAMOND_PICKAXE MINING PUSH` → bores a tunnel **and**
    shoves at the end. Confirms flight + payload compose independently.

## Fused bow (wand)
14. `/fusion give <you> BOW DAMAGE AMPLIFY` — draw and release.
    - ✅ No vanilla arrow; a fusion bolt flies where you aim and bursts on impact.
      Tap = slow shot, full draw = fast.
15. `/fusion give <you> BOW DAMAGE MULTISHOT MULTISHOT SPREAD` → a shotgun bow.

## Fuse-path + bundle ingredients (not just /give)
16. `/fusion machine`, place it, right-click. Try a few ingredients as the
    Ingredient:
    - **Nether Star** (reagent → `PUSH`), then **Heart of the Sea** (`EXPAND`).
    - **TNT** (bundle → `DAMAGE·EXPAND·EXPAND`) — one item, a big boom.
    - **Firework Star** (`DAMAGE·MULTISHOT·SPREAD`) — flak in one fuse.
    - **End Crystal** (`DAMAGE·EXPAND·AMPLIFY·PERSIST`) — the works. OP is welcome.
    - ✅ Lore lists the modifiers; swinging behaves as the recipe predicts.

## Known limitations / seams (see DECISIONS.md)
- **Burst is opt-in** and falls out of the model: no emitter → empty payload →
  quiet terminus. Pierce/Mining/Multishot/Spread/Lifetime alone are kinetic, not
  explosive.
- **Bounce** and **gravity** are modelled (fields + hook) but not yet wired.
  **Cluster bomb** (a payload effect that spawns child projectiles) slots into
  the `Payload` list when built — the seam is there.
- Old items tagged `NOVA`/`REPEAT`/`DELAYED` no longer do anything — re-fuse.
