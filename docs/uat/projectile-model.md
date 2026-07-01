# UAT — Projectile model (everything is a projectile)

Run the UAT server (`make rebuild`), join, `/op` yourself. The big change: a
swing/bow shot now **launches projectiles** that fly and **trigger a burst where
they land**, instead of bursting instantly around you.

Fastest way to build test weapons is `/fusion give`. New/changed modifier IDs:
`MULTISHOT`, `SPREAD`, `PIERCE` (new); `LIFETIME` = lifetime/range (was
`DELAYED`); `MINING` = short piercing ray. `REPEAT` is gone.

## Base bolt (melee)
1. `/fusion give <you> DIAMOND_SWORD NOVA`
   - ✅ Swing while looking at a wall/mob a few blocks away → a bolt flies from
     your eyes and the shove burst goes off **where it lands**, not on you.
   - ✅ Swing at open sky → the bolt flies out and bursts when it expires
     (~1.5s), not instantly at your feet.

## Multishot + Spread (shotgun)
2. `/fusion give <you> DIAMOND_SWORD NOVA MULTISHOT`
   - ✅ Each swing fires 3 bolts (1 base + 2).
3. `/fusion give <you> DIAMOND_SWORD NOVA MULTISHOT MULTISHOT SPREAD`
   - ✅ 5 bolts, fanned into a cone (shotgun). Fire into a mob pack → several
     land in a spread. Add another `SPREAD` for a wider fan.

## Pierce + Lifetime (ray gun)
4. `/fusion give <you> DIAMOND_SWORD PIERCE`
   - ✅ The bolt punches **through** soft blocks (dirt/wood) and every entity in
     its path instead of stopping at the first, then bursts at the end / when it
     expires. It stops at hard blocks (obsidian).
5. `/fusion give <you> DIAMOND_SWORD PIERCE LIFETIME LIFETIME`
   - ✅ Reaches noticeably farther before expiring (LIFETIME = longer lifetime).
6. Line up several mobs in a row and fire a piercing shot down the line.
   - ✅ Each mob along the line takes a nudge (contact hit); the full burst
     goes off once at the end.

## Mining ray
7. `/fusion give <you> DIAMOND_PICKAXE MINING` (or any base)
   - ✅ Swing at stone/dirt → a short, fast ray bores a stub tunnel of soft
     blocks ahead (obsidian/bedrock resist). Short by design.
   - ✅ **No burst/pop at the end** — a mining ray delivers an empty payload; it
     just carves and stops (you'll hear the block breaks, not an explosion).
8. `/fusion give <you> DIAMOND_PICKAXE MINING LIFETIME LIFETIME`
   - ✅ The tunnel reaches farther (LIFETIME extends the ray's life).
8b. `/fusion give <you> DIAMOND_PICKAXE MINING NOVA` → mines a tunnel **and**
    bursts at the end (Nova opts the burst back in). Confirms flight + payload
    compose independently.

## Fused bow (wand)
9. `/fusion give <you> BOW NOVA` — draw and release.
   - ✅ No vanilla arrow; instead a fusion bolt flies where you aim and bursts on
     impact. A quick tap fires a slow shot; a full draw fires fast.
10. `/fusion give <you> BOW MULTISHOT MULTISHOT SPREAD` — full draw.
    - ✅ Fans a volley of bolts downrange.

## Compounding / invert / persist still work
11. `/fusion give <you> DIAMOND_SWORD NOVA EXPAND EXPAND` → big burst on landing.
12. `/fusion give <you> DIAMOND_SWORD NOVA INVERT` → landing burst pulls mobs
    toward the impact point (two INVERTs cancel).
13. `/fusion give <you> DIAMOND_SWORD NOVA PERSIST` → a lingering pulsing field
    at the impact point.

## Fuse-path sanity (not just /give)
14. `/fusion machine`, place it, right-click. Fuse a sword (Target) with an
    Amethyst Shard (Ingredient) → Mining weapon. Swing → mining ray. Confirms the
    latent-ingredient remap (Feather/Sugar→Spread, Arrow/Quartz→Pierce,
    Rabbit's Foot→Multishot).

## Known limitations / seams (see DECISIONS.md)
- **Burst is opt-in.** Only NOVA/EXPAND/CHAIN/INVERT/PERSIST deliver a burst; a
  shot with only flight modifiers (Pierce/Lifetime/Mining/Multishot/Spread)
  delivers an empty payload and terminates quietly. A bare Pierce/Mining shot is
  meant to feel kinetic, not explosive.
- **Bounce** and **gravity** are modelled (fields + hook) but not yet wired — no
  grenade/arc builds yet. Bolts fly straight. Cluster bomb (a payload that spawns
  child projectiles) slots into the `Payload` effect list when built.
- Old items tagged `REPEAT` (now `MULTISHOT`) or `DELAYED` (now `LIFETIME`) no
  longer do anything — re-fuse to refresh.
