# UAT — the short list (only what a human still needs to check)

Most of the weapon model is now checked automatically, so this list is only the
things a bot/headless test **can't** judge: cosmetics, feel, and a few bits that
depend on config or a server restart.

## Fast start — fresh world + a chest of every weapon

```
make uat-newworld      # wipes the world for a clean slate, then boots the server
```

Join (you're op), then in-game:

```
/fusion showcase       # fills chests in front of you with every labelled weapon below
```

Each item is **renamed** to the **bold name** used in this checklist — grab the
one you want and swing. The roster lives in `Showcase.java` (the smoke test
asserts every entry still resolves, so it can't silently rot). Prefer to build
by hand? `/fusion give <you> <base> <MOD...>`, or `/fusion give <you> <base>
from:<item>` to pull an ingredient's latents.

## Run the automated suites first

Before touching this list, run the harnesses — if they're green, the mechanics
below the "already automated" line are covered and you can skip them:

```
make smoke   # boots Paper + runs /fusion test: 52 in-process checks
make e2e     # a real Mineflayer bot: swing/bow input + the anvil GUI (10 checks)
```

**Already automated (don't hand-test these):** emitter bursts (PUSH/DAMAGE),
every transform's scaling and RPN nearest-previous binding (EXPAND, AMPLIFY,
MULTISHOT, SPREAD, LIFETIME, INVERT, CHAIN, PERSIST), PIERCE pass-through,
MINING carving + stopping at obsidian + no-pop terminus, FIRE melting snow &
igniting a mob, ICE freezing water (and dressing bare ground with snow), DEPOSIT backfilling air,
DEPOSIT·TRAIL filling the flight path, a BOUNCE rebound, a SPAWN child ricocheting
off a wall, a HEAL mending a hurt cow, a DELAY charge re-detonating, a HOMING bolt
curving into an off-axis mob, a MOB:Cow launch, a deep MINING stack boring through
obsidian, the parameter parsing (`Deposit:Dirt`/`Mob:Cow`) and complement/flag
wiring at compile time, the swing and bow input paths, and the anvil machine:
shows-result, take ("Fusion complete!"), rejects-junk (red barrier), and
close-returns-inputs. See `SelfTest.java` and `mineflayer/e2e.js`.

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
    spammy stutter. **Warm-up:** the wake starts well downrange (~4.5 blocks) — a
    **lava** trail (`DEPOSIT:LAVA TRAIL`) is the litmus test: you should be able
    to fire one without ever catching fire yourself. **Self-cleaning:** LAVA/WATER
    are placed as real source blocks, which vanilla physics would otherwise let
    spread indefinitely (including back toward you over time) — each one should
    revert to air on its own a few seconds after being placed
    (`deposit.fluid-revert-ticks`), rather than lingering/flowing forever. Other
    DEPOSIT materials (DIRT, SAND, ...) don't flow, so they stay put permanently.
19. **Spawn cluster.** `DIAMOND_SWORD DAMAGE SPAWN MULTISHOT SPREAD FIRE` → where
    the first bolt lands it should **burst into a fanned volley** of fiery
    children. Bump `spawn.max-generation` for more chaos; confirm it can't run away.
20. **Teleport (player-only).** `DIAMOND_SWORD TELEPORT` → you warp to where the
    bolt lands — as a brief **~0.3s dash/zoom** to the destination rather than an
    instant snap, and you should be **invulnerable** for that transit (walk a
    mob into your path mid-zoom and confirm you take no damage). `... PIERCE
    LIFETIME TELEPORT` blinks you to the **far end of a bored tunnel**. Under
    `MULTISHOT` you teleport **once** (first bolt to land), and you never end
    up stuck in a wall or a mob (safe offset). Ender Pearl is the ingredient.
    **Rapid-fire check:** swing a TELEPORT weapon twice in quick succession
    (faster than the ~0.3s zoom) — invulnerability should still turn off once
    both dashes land, not get stuck on permanently (regression: overlapping
    zooms used to leave the caster invulnerable forever, with no vanilla way
    to clear it — confirm with `/kill` or a big fall afterward that you can
    still take damage).
    **Velocity check:** jump off a ledge (or teleport yourself high up) and
    fire a `TELEPORT` shot on the way down — you should land at a dead stop,
    with **no fall damage** and no residual momentum, not keep falling from where
    you land.
21. **Bounce feel.** `DIAMOND_SWORD DAMAGE BOUNCE LIFETIME` (or the showcase's
    **Bouncing Grenade**, `DURATION:10 DAMAGE BOUNCE GRAVITY SPEED:1` — slow
    and long-lived so the physics stays easy to watch) → the bolt should
    **ricochet** off floors/walls (a soft *tick* and crit sparks at each
    bounce), losing a good chunk of speed each time (a dropped rock, not a
    superball on hardwood — it should settle in a few bounces, not skate
    around for ages). Once the hop is basically gone it should **roll for a
    bit** — sliding along the floor, bleeding off speed — rather than stopping
    dead the instant it stops bouncing. Once it's fully settled it should **sit
    there armed** with a **visible sprite** (a small primed-looking block) the
    whole time it's rolling/armed — it should never just vanish between "it
    stopped bouncing" and "it goes off" — not go off immediately: it should
    only detonate when its `Duration` runs out or a mob bumps it directly. Fire
    it into a corner and watch it rattle around, then settle and wait out the
    rest of its `Duration:10` before it finally pops. Tune `bounce.restitution`
    / `bounce.floor-friction` if it still feels too bouncy or too dead, or
    `bounce.rest-speed` if it's calling "settled" too early while still
    visibly skating. Slime Block is the ingredient.
22. **Spawn ricochet.** `DIAMOND_SWORD DAMAGE SPAWN MULTISHOT SPREAD` fired
    **straight at a wall** → the children should spray **back off the wall into
    the room**, not vanish into it. (Compare: before this, a wall-hit cluster
    wasted its children against the surface.) `... BOUNCE ... SPAWN ...` is a
    grenade that bounces to rest, then scatters.
23. **Lob a mortar (Gravity).** `DIAMOND_SWORD DAMAGE GRAVITY VISIBLE SPEED:0.8
    DURATION:4` → a plain sword should now **throw a slow, visible, arcing shot**
    that drops to the ground — no bow needed. (Without `DURATION`, melee's
    default 1-tick life ends the shot before gravity has time to bend it —
    `SPLASH_POTION` is the ready-made lob bundle and already includes it.)
24. **Visible / Invisible.** A long-range melee build with `VISIBLE` shows a
    travelling bolt; `INVISIBLE` on a bow hides its trail. Confirm each overrides
    the weapon-type default.
25. **Absolute Speed / Duration.** `SPEED:3` is a fast bolt, `SPEED:0.4` a crawl;
    `DURATION:5` makes it live ~5s regardless of range. Handy on a `BOUNCE` build
    to set how long it rattles and, once it settles, how long it sits armed
    before going off.

## E. Latest batch — feel & the player-facing bits

The mechanics below are asserted headlessly; these notes are the feel/looks and
the bits that need a real player or an eye:

26. **Heal is friendly.** `DIAMOND_SWORD HEAL AMPLIFY` on a hurt **animal/teammate**
    mends them (and you), with hearts — hostiles are skipped. `Pull` (Fishing Rod)
    vacuums entities *inward* where `Push` shoves out.
27. **Lure-and-blast (Delay).** `DIAMOND_SWORD PULL DELAY:2 DAMAGE EXPAND` → gathers
    mobs, waits ~2s — the charge should sit there **blinking** (the same red
    pulse a DETECT mine shows while armed) — then blasts them. The
    `SCULK_CATALYST` bundle is the ready-made version.
28. **Cow Launcher (Mob).** An axe with `MOB:COW LIFETIME` **launches a live cow**
    on a swing that arcs, lands, and wanders off. `MULTISHOT` throws a herd.
    (Bosses are blocked.)
29. **Homing.** `BOW DAMAGE HOMING LIFETIME` → the bolt should **curve to chase**
    the nearest creature, not snap onto it. Stack `HOMING` for a tighter turn.
30. **Break harder blocks (stacked Mining).** `DIAMOND_PICKAXE MINING PIERCE` still
    stops at obsidian; add more `MINING` (≈×4) and it **chews through** it. Bedrock
    is always safe.
31. **Golden Brush (son's toy).** Fuse a **Brush** with gold: `BRUSH TREASURE
    TREASURE` (or `from:gold_block`). Right-click/brush **any** block → a chance to
    drop loot with a sparkle. More gold = procs more often *and* rarer finds
    (diamonds, enchanted apples at the top). Watch the cooldown so it's not a
    firehose. Every stroke — loot or not — should also leave the block scoured:
    **solid ground** (stone, dirt, ...) turns to **Coarse Dirt**, and brushing
    that same spot again should do nothing (no sound, no roll, no re-transform)
    since there's nothing left to find. A **non-solid** plant/decoration (grass,
    ferns, flowers, ...) should instead just **break**, like a quick brush would
    — not turn into a big block of dirt. A placed Fusion Machine should never
    turn to dirt even if you brush it.
32. **VFX pass (by eye).** A base `DAMAGE` hit is a small **red spark** (not an
    explosion) — `EXPAND` it a few times to grow a real blast; `PUSH` keeps the
    explosive shove. `FIRE`/`ICE` now show a flame/frost **poof**. The `PERSIST`
    charge is a **glowing block** that swells to each pulse (no smoky fire).
    Melee fires a **subtle energy ball**; all trails now **hang and fade in place**
    (no gravity), and a fused **bow left-click is a plain vanilla melee** (fusion
    only fires on the arrow). Every flight trail (any style, not just the melee
    one) is **invisible for the first ~2.5 blocks** — it clears the caster's
    face/arm's reach before showing anything, so a fast bow shot doesn't leave a
    lingering wake right in front of you, and a melee poke (which rarely travels
    that far before hitting its target) reads as fully invisible.
33. **Glowing armor (Glow Helmet).** Wear a `DIAMOND_HELMET GLOW` (or any armor
    with `GLOW`) → two things:
    - **For others:** a **strong glowing outline** (the vanilla
      see-through-walls Glowing effect). Solo in first person you won't see
      your own outline (Minecraft doesn't render your own body model there) —
      press **F5** for third person, or have a second player look at you, to
      confirm it.
    - **For you:** even solo, in first person, you should notice the area
      right in front of your face is **lit up** — walk into a dark cave or
      close your eyes at night and hold GLOW armor up to a dark spot; it
      should brighten as you look at it and track where you look (updates
      every tick, but still snaps to the block grid — a client-side light
      only you can see, not a real placed block, and not night vision: your
      surroundings elsewhere stay dark). Face a wall up close: the light
      should **back off toward you and relocate** in front of the wall's face
      rather than just turning off. Walk right up nose-to-the-wall (as close as
      collision lets you get): it should still light up — right around your
      own head — rather than going dark, since even the closest point in front
      of you is now inside the wall. **Corners and overhangs:** stand somewhere
      the straight line in front of you *and* behind you are both walled off
      (a tight corner, or crouched under a low overhang) — it should still find
      an opening to the side rather than going dark; it no longer only searches
      the single line you're looking along. **Cobwebs:** a structure thick with
      them (a generated mineshaft is a good test) shouldn't starve it either —
      a cobweb is a fine place for the light to sit, not a wall. It shouldn't
      **flicker** rapidly between two spots as you look around; a settled spot
      should hold until something clearly better appears. Take the armor off
      and both effects lapse (the outline after a few seconds; the light
      immediately). Lantern is the ingredient.
34. **Jetpack (Jet Elytra).** Wear an `ELYTRA LIFT` (or a `LIFT` chestplate) →
    a normal ground jump is unchanged (tap, hop, land — vanilla). This is a
    **thruster, not a glider**: double-tapping jump to deploy the elytra glide
    should do nothing (no wings-out, no auto-forward-as-you-descend) while
    LIFT is worn.
    - **Vertical:** jump, then **hold jump while airborne** — you rise
      smoothly, capped at a max climb speed, for as long as you hold it.
      **Hold crouch** to brake the climb and fall normally instead; press jump
      again to resume rising.
    - **Lateral:** while airborne, holding forward/back/strafe-left/right
      nudges your horizontal velocity in whichever direction you're
      **currently** facing. Build up speed holding forward, then **turn
      around without touching a direction key** — you should keep drifting
      the old way while now facing the new way (momentum doesn't follow your
      view; only fresh thrust does).
    - Tune `worn.jetpack-thrust-per-tick` / `worn.jetpack-max-velocity`
      (vertical) and `worn.jetpack-lateral-thrust-per-tick` /
      `worn.jetpack-lateral-max-velocity` (lateral). Breeze Rod is the
      ingredient.
    - **Fall damage stays real.** The jetpack is thrust, not immunity — fly up
      high, stop thrusting, and fall: you should still take normal fall damage
      landing. (Regression: the `AllowFlight` grant needed to dodge the
      anti-fly kick also lets the client's double-tap-space gesture toggle
      real creative-style flight, which suppresses fall damage outright.
      That toggle is now blocked proactively — `JetpackFlightListener`
      cancels the transition into real flight the moment the client
      requests it, the same way `JetpackGlideListener` already blocks
      vanilla gliding — rather than reacting to it after the fact. Confirm
      fall damage is still real even if you mash jump while airborne.)
35. **World filter (`allowed-worlds`).** Set `allowed-worlds: [world]` and
    restart. In `world`, fusion still works as normal (swing/shoot a fused
    weapon, wear GLOW/LIFT armor, brush a Golden Brush). Teleport or portal to
    any other world (e.g. the Nether) with the same fused gear: swings/shots
    should act **plain vanilla** (a fused sword just swings, a fused bow just
    shoots a normal arrow — no cancelled arrow, no burst), GLOW/LIFT/particle
    shedding/machine glow should all stop, and Golden Brush shouldn't drop
    loot. The item itself is untouched — bring it back to `world` and
    everything works again. Set `allowed-worlds: []` (the default) to lift the
    restriction entirely.
36. **Landmine (Detect).** Swing a `NETHERITE_SWORD LIFETIME GRAVITY VISIBLE
    DETECT DAMAGE AMPLIFY AMPLIFY EXPAND MINING EXPAND FIRE EXPAND` (the
    showcase's Landmine) — a visible, lobbed throw that lands and arms, sitting
    there **blinking** (a slow red pulse). Walk away and nothing happens. Walk
    a mob (or yourself) back within `detect.range` and it should **detonate**:
    a big amplified DAMAGE blast, a widened MINING bore, and FIRE. Tune
    `detect.range` / `detect.max-wait-ticks` (a mine that never triggers should
    quietly disarm after `max-wait-ticks`, rather than sitting forever).
    Tripwire Hook is the ingredient.
37. **Wand (Potion).** Grab the showcase's **Wand** (a Stick pre-loaded with
    Poison), or build one yourself: `/fusion give <you> STICK POTION:POISON`.
    **Swing it** (left-click, like any other weapon) at a block → a small
    cloud should appear there, particles/color matching the potion (green
    swirl for Poison), holding a **steady** size/density for its whole life
    (no shrinking-then-vanishing), and anything standing in it should
    periodically take the effect. It should **not break the block** you swing
    at. Left as-is it should sit there for a long while (`wand.cloud-duration-
    ticks`, ~5 min by default) — fuse `DURATION:<n>` onto the Wand (e.g.
    `STICK POTION:POISON DURATION:20`) and confirm the cloud instead lasts
    about that many seconds. Fuse `EXPAND` onto it and confirm the cloud comes
    out bigger (`wand.radius` is a real burst radius, same as Push/Damage).
    Then check the **real** path this exists for: get a Lingering Potion of a
    *different* effect (e.g. Regeneration — craft one or grab it from
    creative), fuse it onto a plain Stick at the Fusion Machine, and confirm
    the resulting Wand casts *that* effect/color — the potion's actual data is
    read at fuse time, not just its material. Tune `wand.radius` /
    `wand.cloud-duration-ticks` / `wand.effect-duration-ticks` /
    `wand.amplifier` / `wand.cooldown-ms` to taste.
38. **Potion on any weapon.** POTION isn't Wand/Stick-only — fuse it onto a
    normal weapon, e.g. `DIAMOND_SWORD POTION:POISON` or `BOW POTION:POISON
    LIFETIME`, and confirm a cloud appears **wherever that weapon's shot
    actually lands** (arm's reach for the sword, downrange for the bow) — the
    same cloud the Wand casts, just delivered by a real shot instead of an
    instant point-and-cast. The Wand itself (a fused `STICK`) should be
    unaffected: it still casts instantly at the crosshair on a swing, and
    shouldn't *also* fire a duplicate cloud from a melee bolt on that same
    swing.
39. **Armor auras — no modifier is off-limits.** Armor is just another
    possible *source* of a shot: fuse **anything** onto a piece — not just
    FIRE/ICE, but PUSH/DAMAGE/MULTISHOT/HOMING/SPREAD/GRAVITY/MOB/... — and
    wearing it periodically fires a real shot rooted at you, the exact same
    compile/payload pipeline a weapon's own swing/shot uses.
    - **Simple aura (`DIAMOND_CHESTPLATE FIRE`):** with no flight modifiers,
      a pulse defaults to a **stationary, zero-duration burst right at you**
      — `EXPAND` on the armor widens the radius, and so on, just centred on
      you instead of a landing spot.
    - **Real flying shots:** fuse `MULTISHOT HOMING DAMAGE SPEED:2
      DURATION:2` onto a piece and confirm each pulse **genuinely fires a
      volley of homing bolts out from you** that chase and hit nearby
      mobs — not an inert burst at your feet. (HOMING needs SPEED and
      DURATION to have real velocity and time to fly/curve, same as it would
      pairing with LIFETIME on any other weapon.)
    - **Cast frequency:** fuse `RATE:<seconds>` onto a piece to tune how
      often *that* armor's aura re-casts, independent of the server default
      (`worn.aura-period-ticks`) — low for a rapid heartbeat, high for a slow
      one. Confirm a low `RATE` visibly pulses faster than the default.
    - **Cast distance:** fuse `DISTANCE:<blocks>` to tune how far *that*
      armor's wearer has to walk to force an early pulse, independent of the
      server default (`worn.aura-distance-blocks`) — low so even a couple
      steps re-casts, high so only real movement does. `RATE` and `DISTANCE`
      are independent knobs (fuse either, both, or neither) meant for quick
      iteration on what an aura's cadence should feel like without touching
      the server config.
    - **Two ways to trigger a pulse regardless of RATE/DISTANCE:** stand
      still and it still pulses on the timer; walk around and it pulses
      *more* often, leaving a denser trail — whichever threshold you cross
      first fires the next one.
    - **Combining pieces:** fuse `FIRE` on a helmet and `EXPAND` on boots and
      confirm the aura comes out with the *widened* radius — all four armor
      pieces' modifiers combine into one stack, same RPN nearest-previous
      binding a weapon's own fused ids get.
    - **Immunity:** you should be immune to your own FIRE aura — nearby
      mobs/players catch fire, but you never do, even the real fire blocks it
      drops underfoot (you should notice you're carrying Fire Resistance the
      whole time it's worn). ICE needs no equivalent (it only ever lays a
      harmless snow layer, never a real hazard).
40. **Right-clicking an entity doesn't fire your weapon.** Hold a fused sword
    (or Wand) and **trade with a villager** (or right-click any entity — a
    horse, an item frame, ...) → the trade GUI should open normally and your
    weapon should **not** fire. (Regression: right-clicking an entity plays
    its own arm-swing animation, which used to slip past the same-tick
    right-click filter that already protects against opening the Fusion
    Machine — that filter only watched right-clicks on a block/air, not on an
    entity.)

---

_If you automate any of section C, move its line up under "already automated"
above and delete it here._
