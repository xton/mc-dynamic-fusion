# UAT — Phase 3: Weapon Behaviors

Validates the DELAYED modifier, the Mining Ray, and the Bow override. Run the
UAT server (`make rebuild`), join, `/op` yourself, and grab ingredients with
`/give` (or use the Fusion Machine).

## DELAYED (Gunpowder)
1. Fuse a sword with **Gunpowder** (→ DELAYED), then with a **Nether Star**
   (→ NOVA) so you can see the burst.
2. Swing near some cows.
   - ✅ Nothing happens for ~1.5s, then the shove burst fires (the fuse).
3. Fuse Gunpowder again and swing.
   - ✅ The delay is noticeably longer (stacks).

## Mining Ray (Amethyst Shard)
4. Fuse a sword (or pickaxe) with an **Amethyst Shard** (→ MINING).
5. Face a wall of **stone/dirt** and swing.
   - ✅ An arc of blocks ahead breaks (a fan ~3–4 deep, swept left-to-right).
   - ✅ Blocks drop normally; silk/fortune on the weapon apply.
6. Face **obsidian or bedrock** and swing.
   - ✅ They do **not** break (hardness cap; bedrock is unbreakable).
7. Fuse an **Expand** ingredient too (Heart of the Sea / Magma Cream) and swing.
   - ✅ The mining reach is a bit longer.
   - ⚠️ **Grief check:** confirm it only breaks where you aim and respects the
     hardness cap — there is no claim/region protection yet (see DECISIONS.md).

## Bow override (fused bow)
8. Fuse a **bow** (Target) with a **Nether Star** (→ NOVA). (Use the Fusion
   Machine, or `/fuse` with the bow in main hand.)
9. Shoot at a group of mobs (or a wall near them).
   - ✅ Where the arrow lands, a shove burst fires, knocking nearby mobs
     outward — the bow "throws" its effect downrange.
10. Try a fused bow with **String** (→ CHAIN).
    - ✅ The impact burst hops to nearby mobs.

## Regression
11. A normal (unfused) sword/bow behaves vanilla. A fused melee weapon still
    does its swing burst (Phases 0–1).

## Known limitations (see DECISIONS.md)
- Mining has no region/claim protection.
- The bow impact burst is a single burst — it does not replay REPEAT/DELAYED.
