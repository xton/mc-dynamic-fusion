# Archived UAT docs

These are superseded and kept only for history. **Don't run them** — many use
the pre-refactor modifier language (`NOVA`, `REPEAT`, `DELAYED`) that no longer
exists, and most of what they checked by hand is now covered by the automated
suites (`make smoke` → in-process `/fusion test`; `make e2e` → the Mineflayer
bot).

For a live UAT pass, use **`docs/uat/manual-checklist.md`** — it's just the
cosmetics/feel/config items a test harness can't judge.

- `projectile-model.md` — the emitter/transform model UAT; still accurate as a
  model reference, but its checks are now automated.
- `uat-round-1.md`, `uat-round-2.md` — anvil GUI + fix rounds; mostly automated.
- `phase-2.md` … `phase-5.md` — pre-refactor phase docs (old modifier names).
