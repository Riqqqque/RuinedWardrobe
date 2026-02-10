# Config Reference

## Version Guards
- `config.yml` and `gui.yml` both require `config-version: 1`.
- If version mismatches, PrismWardrobe disables itself with a clear startup error.

## `config.yml` Highlights
- `language.active`: global active language file in `lang/`.
- `messages.format-mode`: `LEGACY`, `MINIMESSAGE`, `BOTH`.
- `wardrobe.default-slots`: base unlocked slots (default `8`).
- `wardrobe.max-pages`: max GUI pages (default `2`).
- `storage.type`: `SQLITE` or `MYSQL`.
- `performance.database.*`: async write queue/retries/workers.
- `restrictions.*`: world/gamemode/combat/placeholder checks.
- `anti-dupe.strict-container-lock`: blocks container interaction while wearing bound armor.

## `gui.yml` Highlights
- `layout.rows`: recommended `6`.
- `layout.slot-display-indices`: visible slot columns per page.
- `layout.equip-buttons.slots`: equip button slots.
- `layout.navigation.*`: page controls and close slot.
- `templates.*`: item material/name/lore/glow/model-data for each UI state.

## Localization
- All player-visible strings are in `lang/*.yml`.
- Missing keys fall back to `lang/en_US.yml`.
