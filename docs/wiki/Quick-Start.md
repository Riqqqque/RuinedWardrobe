# Quick Start

Use this page when you want RuinedWardrobe installed, tested, and ready for a first group of players without reading every advanced option first.

> [!IMPORTANT]
> Test with a normal player account before opening the server. Operator accounts can hide permission and locked-slot problems.

## Requirements

| Requirement | Value |
| --- | --- |
| Server software | Paper or Folia |
| Target API | `1.21` |
| Runtime support | Paper/Folia `1.21` through `26.1.2` |
| Java | `21+` |
| Permission plugin | Strongly recommended |
| Optional hooks | PlaceholderAPI, Vault, CombatLogX |

## Install Checklist

1. Download `RuinedWardrobe.jar` from the [latest release](https://github.com/Riqqqque/RuinedWardrobe/releases/latest/download/RuinedWardrobe.jar).
2. Put `RuinedWardrobe.jar` in the server `plugins` folder.
3. Start the server once so RuinedWardrobe creates its files.
4. Stop the server.
5. Open `plugins/RuinedWardrobe/config.yml`.
6. Review storage, slots, death behavior, restrictions, audit, and anti-dupe settings.
7. Start the server again.
8. Give players `ruinedwardrobe.use`.
9. Give players one slot tier, such as `ruinedwardrobe.slots.3`.
10. Run `/wardrobe` as a normal player.

## First Player Test

| Step | Expected result |
| --- | --- |
| Put armor in the player inventory | The armor can be moved into wardrobe armor cells. |
| Run `/wardrobe` | The wardrobe GUI opens. |
| Save armor into one wardrobe column | The column shows the saved set. |
| Click that column's equip button | The set equips and becomes the active wardrobe set. |
| Click the active equip button again | The set unequips cleanly. |
| Save a second set and switch between sets | Only one wardrobe set stays active at a time. |
| Check a locked slot | Locked slots appear above the player's slot tier. |

## First Staff Test

Run these after the player test:

```text
/wardrobe help
/wardrobe list <player>
/wardrobe doctor
/wardrobe admin setslots <player> 5
/wardrobe admin clearslots <player>
```

`/wardrobe doctor` should report storage status, cache stats, DB queue depth, sync timing, and whether a real DB probe succeeded.

## First Config Decisions

| Decision | Safe first choice |
| --- | --- |
| One server or a network? | SQLite for one server, MySQL/MariaDB for a network. |
| How many default slots? | Keep `wardrobe.default-slots: 3`, then expand with rank permissions. |
| Should wardrobe armor survive death? | Keep `death.keep-wardrobe-on-death: false` unless you want protected sets. |
| Need region, gamemode, or combat blocks? | Leave restrictions enabled and configure only the rules you use. |
| Need strict container blocking? | Leave `anti-dupe.strict-container-lock: false` until tested. |
| Need detailed sync noise? | Keep `audit.log-successful-syncs: false`. |

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
build/libs/RuinedWardrobe-1.0.2.jar
```

The shaded jar includes SQLite, MariaDB, HikariCP, Caffeine, and bStats dependencies.

## Before Opening To Players

- Back up the full `plugins/RuinedWardrobe` folder.
- Confirm every rank has the intended `ruinedwardrobe.slots.<amount>` tier.
- Test save, equip, unequip, switch, delete, death, reload, and restart.
- Run `/wardrobe doctor`.
- Check `plugins/RuinedWardrobe/logs/wardrobe-audit-YYYY-MM-DD.log`.
- Keep the first backup until real players have used the plugin for a while.

## Next Pages

| Need | Page |
| --- | --- |
| Rank and staff setup | [Permissions And Commands](Permissions-And-Commands.md) |
| Storage and performance tuning | [Configuration](Configuration.md) |
| GUI and messages | [GUI And Language Customization](GUI-And-Language-Customization.md) |
| Migration or backups | [Storage, Migration, And Backups](Storage-Migration-And-Backups.md) |
