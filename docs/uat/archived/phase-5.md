# UAT — Phase 5: /fusion give admin command

Run the UAT server (`make rebuild`), join, `/op` yourself.

## /fusion give
1. `/fusion give <yourname> DIAMOND_SWORD NOVA EXPAND EXPAND`
   - ✅ You receive a fused Diamond Sword whose lore shows Nova + Expand + Expand.
   - ✅ Swing it near mobs → a big shove burst (Expand stacked), no grinding
     ingredients needed.
2. `/fusion give <yourname> BOW NOVA CHAIN`
   - ✅ You get a fused bow; shooting it bursts + chains where the arrow lands.
3. Try every modifier: `NOVA EXPAND CHAIN REPEAT DELAYED MINING INVERT PERSIST`.
   - ✅ Each behaves as in its phase's UAT. This is the fast way to exercise
     the whole modifier space and odd combinations.

## Validation / errors
4. `/fusion give <yourname> DIAMOND_SWORD MYSTERY`
   - ✅ Refused: "No known modifiers given. Known: NOVA, EXPAND, …".
5. `/fusion give <yourname> NOT_A_BLOCK NOVA`
   - ✅ Refused: "Unknown base item: NOT_A_BLOCK."
6. `/fusion give Nobody DIAMOND_SWORD NOVA`
   - ✅ Refused: "No online player named Nobody."
7. A non-op running `/fusion give ...`
   - ✅ Refused (op-only).

## Tab completion
8. Type `/fusion ` then Tab → `machine`, `give`. After `give`, Tab cycles
   online players → base hints → modifier IDs.

## Known limitations (see DECISIONS.md)
- No multi-block machine or resource pack (deferred).
