# UAT — Round 1 fixes

Validates the five UAT-feedback fixes. `make rebuild`, join, `/op`.

## Anvil machine (legible UI + takeable result + distinctive look)
1. `/fusion machine` → you get a **Fusion Machine** (an **anvil**, glowing).
2. Place it.
   - ✅ A subtle particle glow rises above it (easy to spot). A *normal* anvil
     placed elsewhere has no glow and behaves vanilla.
3. Right-click the machine.
   - ✅ A normal **anvil GUI** opens (not a glass-pane chest).
4. Put a **diamond sword** in the **left** slot and a **Nether Star** in the
   **right** slot.
   - ✅ The **result slot** shows the fused Nova Sword (with lore).
5. Take the result.
   - ✅ It actually comes out (no snap-back). The sword is upgraded; the Nether
     Star drops by one. No XP needed unless `fusion.cost` > 0.
6. Close the anvil with items still in the inputs.
   - ✅ They're returned to you — nothing lost.

## Fused ingredient contributes its magic
7. Make a fused sword (e.g. sword + Nether Star = Nova). Now use **that fused
   sword as the Ingredient** (right slot) on a fresh weapon in the left slot.
   - ✅ No more "has no magic to give." The Target gains the ingredient's whole
     fused stack **plus** that item's base-material latent modifiers.

## /fusion fuse (no more /fuse)
8. Type `/f` then Tab.
   - ✅ Only `/fusion` completes — no competing `/fuse`.
9. `/fusion fuse` with a Target in main hand + Ingredient in off hand (op).
   - ✅ Fuses as before. A non-op gets "no permission."
10. `/fusion ` + Tab → `machine`, `fuse`, `give`.

## Notes
- The old `docs/uat/phase-2.md` (custom-GUI steps) is superseded by the anvil
  flow above.
