# Quick Start

This page gets RuinedWardrobe running without forcing you through every advanced option on day one.

## Requirements

| Requirement | Value |
| --- | --- |
| Server | Paper or Folia |
| Target API | `26.1.1` |
| Java | `25` |
| Permission plugin | Recommended |
| Optional plugins | PlaceholderAPI, Vault, CombatLogX |

## Install

1. Put `RuinedWardrobe-1.0.0.jar` in the server `plugins` folder.
2. Start the server once so config files are created.
3. Stop the server.
4. Open `plugins/RuinedWardrobe/config.yml`.
5. Check storage, slot caps, death behavior, restrictions, and audit settings.
6. Start the server again.
7. Give players `ruinedwardrobe.use` and one slot tier such as `ruinedwardrobe.slots.8`.
8. Test with `/wardrobe`.

## First Player Test

Use a normal player account, not only an operator account.

1. Put armor in the player inventory.
2. Run `/wardrobe`.
3. Drag armor into one wardrobe column.
4. Click that column's equip button.
5. Click the same button again to unequip.
6. Save another set and switch between both sets.
7. Confirm locked slots show for slots above the player's permission tier.

## First Staff Test

Run these after the player test:

```text
/wardrobe help
/wardrobe list <player>
/wardrobe doctor
/wardrobe admin setslots <player> 5
/wardrobe admin clearslots <player>
```

`/wardrobe doctor` should show storage ready, basic cache stats, DB queue depth, sync timing, and whether the DB probe succeeded.

## First Config Decisions

| Question | Safe first choice |
| --- | --- |
| One server or network? | Use SQLite for one server, MySQL for a network. |
| Keep wardrobe armor after death? | Leave `death.keep-wardrobe-on-death: false` unless you want protected sets. |
| Need region or combat restrictions? | Keep restrictions enabled and configure only the rules you use. |
| Need strict container blocking? | Leave `anti-dupe.strict-container-lock: false` unless your economy needs maximum lock-down. |
| Need noisy sync logs? | Leave `audit.log-successful-syncs: false`. |

## Build From Source

On Windows:

```powershell
.\gradlew.bat clean build
```

On Linux:

```bash
./gradlew clean build
```

Jar output:

```text
build/libs/RuinedWardrobe-1.0.0.jar
```

The jar is shaded with SQLite, MariaDB, HikariCP, Caffeine, and bStats dependencies.

## Before Opening To Players

- Back up the full `plugins/RuinedWardrobe` folder.
- Check that player ranks have exactly one intended `ruinedwardrobe.slots.<amount>` tier.
- Test save, equip, unequip, switch, delete, death, and reload.
- Check `plugins/RuinedWardrobe/logs/wardrobe-audit-YYYY-MM-DD.log`.
- Keep the first backup until real players have used the plugin for a while.
