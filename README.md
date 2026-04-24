# FluxWardrobe

FluxWardrobe is a Paper 26.1.1 + Folia wardrobe plugin for Java 25 with Hypixel-style UX, anti-dupe protections, and production-grade storage/runtime safety.

## Core Features
- GUI-first wardrobe with drag/drop armor editing and equip/unequip toggle.
- Page navigation, locked-slot rendering, and configurable templates via `gui.yml`.
- Permission-tier slot system (`fluxwardrobe.slots.<n>`) plus additive admin bonus slots.
- SQLite default storage, optional MySQL/MariaDB via HikariCP.
- Async write pipeline with retries, queue limits, and version-based cross-server sync polling.
- Bound-armor anti-dupe protections (inventory, drag, drop, swap, container lock option).
- Armor durability/unbreakable/custom metadata preserved via version-resilient item payload serialization.
- Config and GUI templates auto-back up and regenerate on version mismatches instead of hard-failing startup.
- Automatic startup import for the older local schema, with a verified backup before legacy tables are removed.
- Storage migration writes a snapshot backup and verifies row digests after copy.
- Fully configurable language keys with fallback to `lang/en_US.yml`.
- Message parser modes: `LEGACY`, `MINIMESSAGE`, `BOTH`.
- Optional PlaceholderAPI and Vault hooks.

## Build
```bash
./gradlew clean test shadowJar
```

Requires JDK `25`.

Jar output:
- `build/libs/FluxWardrobe-1.0.0.jar`

Target API:
- `io.papermc.paper:paper-api:26.1.1.build.29-alpha`

## Commands
- `/wardrobe` or `/fw` (open wardrobe GUI)
- `/wardrobe help`
- `/wardrobe list [player]`
- `/wardrobe doctor`
- `/wardrobe reload`
- `/wardrobe migrate <sqlite|mysql> [--dry-run]`
- `/wardrobe admin open <player>`
- `/wardrobe admin setslots <player> <amount>`
- `/wardrobe admin clearslots <player>`

## Config Files
- `src/main/resources/config.yml`: core behavior, storage, integrations, anti-dupe, performance.
- `src/main/resources/gui.yml`: layout and item template mapping.
- `src/main/resources/lang/en_US.yml`: full fallback language catalog.
- `src/main/resources/permissions.yml`: reference permission grouping for rank setups.

## Docs
- `docs/permissions.md`
- `docs/placeholders.md`
- `docs/config-reference.md`
- `docs/migration-guide.md`
- `docs/github-security-checklist.md`
