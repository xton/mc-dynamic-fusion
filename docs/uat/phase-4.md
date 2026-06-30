# UAT — Phase 4: INVERT, PERSIST, XP cost, particle shedding

Run the UAT server (`make rebuild`), join, `/op`, and grab ingredients.

## INVERT — implosion (Fermented Spider Eye)
1. Fuse a sword with a **Nether Star** (NOVA), then a **Fermented Spider Eye**
   (INVERT).
2. Stand among cows and swing.
   - ✅ Instead of being shoved away, mobs are **pulled toward you** (they pile
     onto you — the intended "backfire" of building a mob magnet).
3. Fuse a **second** Fermented Spider Eye and swing.
   - ✅ The two INVERTs **cancel** — mobs are shoved outward again.

## PERSIST — lingering field (Blaze Rod / Dragon's Breath)
4. Fuse a sword with a **Nether Star** (NOVA) + a **Blaze Rod** (PERSIST).
5. Swing in a crowd, then **walk away** and watch the spot.
   - ✅ A lingering field keeps pulsing the shove burst at the spot you swung
     for a few seconds, then stops.
6. Fuse another **Blaze Rod** and repeat.
   - ✅ The field lasts noticeably longer (duration stacks).
   - ⚠️ **Perf check:** with heavy PERSIST stacking, confirm pulses end and
     don't pile up / lag the server.

## XP cost (config)
7. Edit `docker/data/plugins/DynamicFusion/config.yml`: set `fusion.cost: 3`,
   then `make rebuild` (or `/reload`-equivalent restart).
8. With **< 3 levels**, try to fuse (via `/fusion fuse` or the machine).
   - ✅ Refused: "Fusing costs 3 XP levels."
9. With **≥ 3 levels**, fuse.
   - ✅ Succeeds and your level drops by 3.

## Particle shedding (cosmetic)
10. Hold any fused weapon and stand still.
    - ✅ Subtle enchant-style particles rise off you. Holding a non-fused item
      shows nothing. Toggle off with `effect.particle-shedding: false`.

## Known limitations (see DECISIONS.md)
- REVERSE not implemented (identical to INVERT under knockback-only effects).
- No multi-world whitelist, Portal Gun, or Cow Launcher yet (deferred).
