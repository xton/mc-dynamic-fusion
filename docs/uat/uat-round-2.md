# UAT — Round 2 fixes

`make rebuild`, join, `/op`, open a Fusion Machine.

## 1. Reason shown for invalid fusions
1. Put a sword in the left slot and **dirt** (no magic) in the right slot.
   - ✅ The **result slot shows a red barrier** named with the reason
     ("Can't fuse: Dirt has no magic to give.") — visible right in the GUI.
     (An action bar would be hidden behind the open anvil screen.)
2. Remove one input.
   - ✅ The barrier clears (no warning mid-setup, only when both inputs are in).

## 2. Renaming keeps the fusion valid
3. Sword (left) + Nether Star (right) → result previews.
4. Type a name in the anvil's rename field.
   - ✅ The result stays valid and now carries your **custom name** (no X).
5. Take it → you get the named, fused weapon.

## 3. New ingredients fuse (stale registry fixed)
6. Sword (left) + **Amethyst Shard** (right).
   - ✅ Fuses → MINING (swing carves blocks; Phase 3). No more "has no magic."
7. Spot-check the other post-launch ingredients also work now:
   - **Gunpowder** → Delayed, **Fermented Spider Eye** → Invert,
     **Blaze Rod** / **Dragon's Breath** → Persist.

> Note: your on-disk `latent_registry.yml` may still look old — that's fine; the
> plugin now merges in the bundled defaults at load time. Custom entries you add
> still take precedence; set an ingredient to `[]` to disable it.
