# Quick Start

## Requirements

- Paper or Folia compatible with API `26.1.1`.
- Java 25.
- Permission plugin recommended, such as LuckPerms.
- PlaceholderAPI, Vault, and CombatLogX are optional.

## Install

1. Put `RuinedWardrobe-1.0.0.jar` in the server `plugins` folder.
2. Start the server once.
3. Stop the server.
4. Open `plugins/RuinedWardrobe/config.yml`.
5. Confirm storage, slot caps, audit settings, death behavior, and integrations.
6. Start the server again.
7. Give players `ruinedwardrobe.use` and a slot tier such as `ruinedwardrobe.slots.8`.
8. Test with `/wardrobe`.

## First Player Test

1. Put armor in your inventory.
2. Run `/wardrobe`.
3. Drag armor into a wardrobe column.
4. Click the equip button for that column.
5. Try switching to another slot.
6. Confirm the previous wardrobe-bound armor is removed before the new set is equipped.

## First Admin Test

Run:

```text
/wardrobe doctor
/wardrobe list <player>
/wardrobe admin setslots <player> 5
/wardrobe admin clearslots <player>
```

## Recommended Production Setup

- Back up the `plugins/RuinedWardrobe` folder before every major update.
- Keep `audit.enabled: true`.
- Keep generated config backups until the new version has been tested.
- Use `/wardrobe migrate mysql --dry-run` before any real storage migration.
- Test death behavior in a safe world before enabling the plugin for all players.

## Where The Jar Comes From

Local build:

```bash
./gradlew clean build
```

Jar:

```text
build/libs/RuinedWardrobe-1.0.0.jar
```

The jar is larger than a simple plugin because it includes SQLite, MariaDB, HikariCP, Caffeine, and bStats dependencies.
