# RuinedWardrobe

[![Release](https://img.shields.io/github/v/release/Riqqqque/RuinedWardrobe?label=release&cacheSeconds=60)](https://github.com/Riqqqque/RuinedWardrobe/releases/latest)
[![Download](https://img.shields.io/badge/download-jar-2ea44f)](https://github.com/Riqqqque/RuinedWardrobe/releases/latest/download/RuinedWardrobe.jar)
[![Paper/Folia](https://img.shields.io/badge/Paper%2FFolia-26.1.1-2f3136)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-25-b07219)](https://adoptium.net/)
[![License](https://img.shields.io/badge/license-free%20use%2C%20no%20resale-blue)](LICENSE)

RuinedWardrobe is a high-performance Paper/Folia wardrobe plugin built for large Minecraft servers that need a polished armor wardrobe, strict anti-dupe behavior, safe storage, and useful diagnostics when something goes wrong.

It is free to use on any server, including monetized servers. The source and jar can be used, modified, and shared for free, but the plugin itself cannot be resold or commercially redistributed. See [LICENSE](LICENSE).

## Highlights

- Hypixel-style wardrobe GUI with drag/drop armor editing, page navigation, locked slots, and equip/unequip buttons.
- Full Paper and Folia scheduler support.
- Target API: `io.papermc.paper:paper-api:26.1.1.build.29-alpha`.
- Java target: `25`.
- Permission-tier slots through `ruinedwardrobe.slots.<amount>` plus admin bonus slots.
- SQLite by default, optional MySQL/MariaDB for networks.
- Async database pipeline with queue limits, retries, health metrics, and batch sync.
- Bound-armor anti-dupe protection for inventory clicks, drags, drops, swaps, armor dispense, and optional container locking.
- Vanilla-compatible death behavior by default, with a config option to preserve wardrobe sets on death.
- Wardrobe audit log for tracing equips, edits, deaths, sanitizer removals, sync changes, storage errors, and item deserialization failures.
- Config, GUI, language, and permission templates designed for server owners instead of just developers.
- Automatic config backup/regeneration on template version changes.
- Legacy schema import with verified backup before old tables are removed.
- Snapshot-based storage migration with digest verification.
- PlaceholderAPI expansion and optional Vault/combat integration hooks.

## Download

Latest jar:

[Download RuinedWardrobe](https://github.com/Riqqqque/RuinedWardrobe/releases/latest/download/RuinedWardrobe.jar)

## Install

1. Download `RuinedWardrobe.jar` from the latest GitHub release.
2. Put the jar in your server `plugins` folder.
3. Start the server once so RuinedWardrobe creates its config files.
4. Edit `plugins/RuinedWardrobe/config.yml`, `gui.yml`, `lang/en_US.yml`, and permissions as needed.
5. Restart the server, or use `/wardrobe reload` after config-only changes.

Jar output when building locally:

```bash
./gradlew clean build
```

```text
build/libs/RuinedWardrobe-1.0.0.jar
```

## Commands

- `/wardrobe` or `/rw` opens the wardrobe GUI.
- `/wardrobe help` shows command help.
- `/wardrobe list [player]` lists saved wardrobe sets.
- `/wardrobe doctor` prints runtime diagnostics.
- `/wardrobe reload` reloads config, GUI, and language files.
- `/wardrobe migrate <sqlite|mysql> [--dry-run] [--force]` migrates storage with snapshot verification.
- `/wardrobe admin open <player>` opens another player's wardrobe.
- `/wardrobe admin setslots <player> <amount>` adds admin bonus slots.
- `/wardrobe admin clearslots <player>` removes admin bonus slots.

## Main Permissions

- `ruinedwardrobe.use`
- `ruinedwardrobe.command.help`
- `ruinedwardrobe.command.list`
- `ruinedwardrobe.command.doctor`
- `ruinedwardrobe.command.reload`
- `ruinedwardrobe.command.migrate`
- `ruinedwardrobe.admin.*`
- `ruinedwardrobe.slots.<amount>`
- `ruinedwardrobe.bypass.cooldown`
- `ruinedwardrobe.bypass.restrictions`
- `ruinedwardrobe.bypass.emptycheck`
- `ruinedwardrobe.bypass.combat`

See [docs/permissions.md](docs/permissions.md) and [src/main/resources/permissions.yml](src/main/resources/permissions.yml).

## Configuration

Core config lives in `plugins/RuinedWardrobe/config.yml`.

Important sections:

- `wardrobe`: slot caps, pages, equip cooldown.
- `death`: vanilla-loss behavior or protected wardrobe behavior.
- `storage`: SQLite or MySQL/MariaDB setup.
- `performance`: cache, session, sync, armor scan, DB queue, and health logging.
- `audit`: separate activity log for debugging missing armor or bad sync behavior.
- `restrictions`: world, gamemode, combat, and PlaceholderAPI rules.
- `integrations`: PlaceholderAPI, Vault, and combat hooks.
- `anti-dupe`: strict container interaction lock.

GUI layout is in `gui.yml`. Player-facing messages are in `lang/en_US.yml`.

## Audit Logs

The audit log is meant for real debugging on live servers.

Default path:

```text
plugins/RuinedWardrobe/logs/wardrobe-audit-YYYY-MM-DD.log
```

It records meaningful wardrobe actions and failures without flooding the console. If armor disappears, send the audit log for the same timestamp plus the server console around that time.

Useful actions to look for:

- `EQUIP`
- `UNEQUIP`
- `ARMOR_PLACE`
- `ARMOR_TAKE`
- `DEATH_VANILLA_LOSS`
- `DEATH_PRESERVE`
- `FOREIGN_BOUND_ARMOR_REMOVED`
- `ITEM_DESERIALIZE_ERROR`
- `ARMOR_SYNC_ERROR`
- `SAVE_SET_ERROR`
- `EQUIP_ERROR`

## Server Monetization

You may use RuinedWardrobe on servers that make money from ranks, donations, cosmetics, memberships, stores, ads, or paid access.

You may not sell the RuinedWardrobe jar, source code, fork, modified copy, update access, or a paid bundle that charges for the plugin itself.

See [LICENSE](LICENSE) and [docs/wiki/License-And-Server-Monetization.md](docs/wiki/License-And-Server-Monetization.md).

## Documentation

Start with the wiki-style docs:

- [Wiki Home](docs/wiki/Home.md)
- [Quick Start](docs/wiki/Quick-Start.md)
- [Configuration Guide](docs/wiki/Configuration.md)
- [Permissions And Commands](docs/wiki/Permissions-And-Commands.md)
- [GUI And Language Customization](docs/wiki/GUI-And-Language-Customization.md)
- [Placeholders And Integrations](docs/wiki/Placeholders-And-Integrations.md)
- [Storage, Migration, And Backups](docs/wiki/Storage-Migration-And-Backups.md)
- [Audit Logs And Troubleshooting](docs/wiki/Audit-Logs-And-Troubleshooting.md)
- [Upgrade And Release Checklist](docs/wiki/Upgrade-And-Release-Checklist.md)
- [License And Server Monetization](docs/wiki/License-And-Server-Monetization.md)
- [FAQ](docs/wiki/FAQ.md)

Reference docs:

- [Config Reference](docs/config-reference.md)
- [Migration Guide](docs/migration-guide.md)
- [Permissions](docs/permissions.md)
- [Placeholders](docs/placeholders.md)
- [Ruined Ecosystem Parity](docs/ruined-ecosystem-parity.md)

## Development

Requirements:

- JDK 25
- Gradle wrapper included

Build and test:

```bash
./gradlew clean test build
```

The shaded jar includes SQLite, MariaDB, HikariCP, Caffeine, and bStats dependencies so normal installs do not need extra jars.
