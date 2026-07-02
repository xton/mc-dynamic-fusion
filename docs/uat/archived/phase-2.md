# UAT — Phase 2: Fusion Machine

Validates the placeable machine + GUI. Run the UAT server (`make rebuild` if
already running, else `make uat`), join, and `/op` yourself if needed.

## Setup
1. `/fusion machine` → you receive a **Fusion Machine** (glowing crafting table).
   - ✅ Item is named "Fusion Machine" and glows.
   - ✅ Non-ops running `/fusion machine` are refused.
2. Place it somewhere.
3. Right-click it.
   - ✅ A "Fusion Machine" GUI opens (Target slot left, Ingredient slot, an
     Output preview, and a green **Fuse!** emerald on the right). It does **not**
     open the vanilla crafting grid.
   - ✅ A *normal* crafting table placed nearby still opens normal crafting.

## Core fusion via GUI
4. Put a **diamond sword** in the Target slot (0) and a **Nether Star** in the
   Ingredient slot (2).
   - ✅ The Output slot (4) previews the resulting **Nova Sword** with its lore.
5. Click the **Fuse!** emerald.
   - ✅ Target becomes the fused weapon; the Nether Star count drops by one;
     anvil sound + totem particles play at the machine.
6. Take the fused weapon out and swing near mobs → the effect fires (Phase 0/1).

## Preview feedback
7. With only a Nether Star (no Target), or with a non-magic ingredient (e.g.
   dirt) in the Ingredient slot:
   - ✅ The Output slot shows a red **barrier** naming the reason ("No weapon to
     enhance." / "Dirt has no latent magic to give.").

## Item-safety (the important checks)
8. Put items in Target/Ingredient, then **close the GUI** (Esc).
   - ✅ Both items are returned to your inventory (or dropped at your feet if
     full). Nothing vanishes.
9. Try to **shift-click**, **double-click**, or **number-key** items around in
   the GUI.
   - ✅ Items never land in the glass-pane/Output/Confirm slots, and nothing
     duplicates. Only the Target and Ingredient slots hold items.
10. Try to take the glass panes or the Output preview item.
    - ✅ They can't be removed.

## Persistence
11. Break the machine.
    - ✅ It drops the **Fusion Machine** item back (not a plain crafting table).
12. Place it again, fuse once, then **restart the server** and right-click it.
    - ✅ It still opens the Fusion GUI (location persisted in `machines.yml`).

## Known limitations (by design, see DECISIONS.md)
- No 2s build-up animation yet (immediate fuse + burst).
- A machine destroyed by explosion/piston (no BlockBreakEvent) leaves a stale
  entry; right-clicking that air does nothing.
- Placing a stack in the Target slot yields one upgraded item; extra count is
  dropped.
