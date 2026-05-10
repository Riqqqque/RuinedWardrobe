# Config Reference

## Version Guards
- `config.yml` requires `config-version: 6`; `gui.yml` requires `config-version: 2`.
- If version mismatches, RuinedWardrobe backs up the old file and regenerates the latest template automatically.
- Runtime target is Paper/Folia `26.1.1` on Java `25`.

## `config.yml` Highlights
- `language.active`: global active language file in `lang/`.
- `messages.format-mode`: `LEGACY`, `MINIMESSAGE`, `BOTH`.
- `wardrobe.default-slots`: base unlocked slots (default `3`).
- `wardrobe.max-pages`: max GUI pages (default `2`).
- `death.keep-wardrobe-on-death`: optionally prevents equipped wardrobe armor from dropping or being removed from saved slots when keepInventory is off.
- `storage.type`: `SQLITE` or `MYSQL`.
- `storage.sqlite.file`: relative path to the SQLite database file.
- `performance.cache.*`: profile cache size and idle expiry.
- `performance.session.*`: optional join-time DB work for preloading or external player-row tools.
- `performance.sync.*`: MySQL cross-server polling interval and batch size.
- `performance.armor-sync.*`: periodic bound-armor safety scan interval, batch size, and shutdown flush timeout.
- `performance.database.*`: async write queue/retries/workers.
- `performance.health.*`: lightweight runtime health logging controls.
- `audit.*`: wardrobe activity log controls for equips, edits, deaths, sanitizer removals, sync changes, and errors.
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
