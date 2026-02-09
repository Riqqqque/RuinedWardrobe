# PrismWardrobe

PrismWardrobe is a Paper 1.21.x + Folia-compatible wardrobe plugin focused on premium UX, scalability, and full customization.

## Features
- Hypixel-style paged wardrobe GUI with favorites, quick-save, and delete confirmation.
- Armor-set wardrobe slots (helmet, chestplate, leggings, boots).
- Permission-tier slot system (`prismwardrobe.slots.<n>`) with additive admin bonus slots.
- SQLite by default, optional MySQL/MariaDB via HikariCP.
- Async persistence with bounded write queue, retries, and version-based sync polling.
- Full language customization (`lang/*.yml`) with fallback to `en_US`.
- Message formatting modes: `LEGACY`, `MINIMESSAGE`, `BOTH`.
- Restriction engine (world, gamemode, combat, PlaceholderAPI rules) and cooldowns.
- Public API + Bukkit events for third-party integrations.
- Optional PlaceholderAPI and Vault integration.

## Build
```bash
./gradlew clean shadowJar
```

Output jar:
- `build/libs/PrismWardrobe-1.0.0.jar`

## Commands
- `/wardrobe`
- `/wardrobe equip <slot|name>`
- `/wardrobe save <slot|name>`
- `/wardrobe rename <slot> <newName>`
- `/wardrobe delete <slot|name>`
- `/wardrobe list [player]`
- `/wardrobe reload`
- `/wardrobe migrate <sqlite|mysql> [--dry-run]`
- `/wardrobe admin open <player>`
- `/wardrobe admin setslots <player> <amount>`
- `/wardrobe admin clearslots <player>`

## Permissions
Main:
- `prismwardrobe.use`
- `prismwardrobe.command.*`
- `prismwardrobe.admin.*`
- `prismwardrobe.slots.<n>`

Bypass:
- `prismwardrobe.bypass.cooldown`
- `prismwardrobe.bypass.restrictions`
- `prismwardrobe.bypass.emptycheck`
- `prismwardrobe.bypass.combat`

## Config and language files
- `config.yml` core behavior, storage, restrictions, performance, integrations.
- `gui.yml` GUI layout and icon templates.
- `permissions.yml` permission-rank mapping reference.
- `lang/en_US.yml` full default text catalog.

## PlaceholderAPI placeholders
- `%prismwardrobe_slots_used%`
- `%prismwardrobe_slots_max%`
- `%prismwardrobe_cooldown_remaining%`
- `%prismwardrobe_selected_slot%`
- `%prismwardrobe_set_name_<slot>%`
