# RuinedWardrobe Wiki

RuinedWardrobe is a server-ready armor wardrobe plugin for Paper and Folia. It is designed around safe item handling, scalable storage, clean configuration, and strong troubleshooting logs.

## What RuinedWardrobe Does

Players save armor into wardrobe slots, then equip those slots from a GUI. Each equipped wardrobe item is bound to the player while worn so it cannot be moved, dropped, traded, or duplicated through normal inventory tricks. When the player unequips or switches sets, the plugin clears the currently worn wardrobe-bound armor and applies the selected set.

## Recommended Reading Order

1. [Quick Start](Quick-Start.md)
2. [Permissions And Commands](Permissions-And-Commands.md)
3. [Configuration](Configuration.md)
4. [GUI And Language Customization](GUI-And-Language-Customization.md)
5. [Storage, Migration, And Backups](Storage-Migration-And-Backups.md)
6. [Audit Logs And Troubleshooting](Audit-Logs-And-Troubleshooting.md)
7. [License And Server Monetization](License-And-Server-Monetization.md)

## Key Concepts

- Wardrobe slots are saved sets stored in SQLite or MySQL.
- Equipped wardrobe armor is temporarily bound with persistent metadata.
- Bound armor is not meant to be traded, moved into containers, dropped, or merged with other item flows.
- Switching from a full set to a partial set replaces the whole previous wardrobe set. Empty pieces in the new set stay empty.
- By default, death follows vanilla behavior when keepInventory is off: wardrobe armor drops and is removed from the saved set.
- If `death.keep-wardrobe-on-death` is enabled, equipped wardrobe armor does not drop and the saved set remains available after respawn.
- The audit log is the first place to check when a player says armor disappeared.

## Files Created On First Start

```text
plugins/RuinedWardrobe/config.yml
plugins/RuinedWardrobe/gui.yml
plugins/RuinedWardrobe/lang/en_US.yml
plugins/RuinedWardrobe/permissions.yml
plugins/RuinedWardrobe/data/wardrobe.db
plugins/RuinedWardrobe/logs/wardrobe-audit-YYYY-MM-DD.log
```

## Best Defaults For Most Servers

- Keep SQLite for a single server.
- Use MySQL for networks or shared wardrobe data.
- Keep audit logging enabled.
- Leave `audit.log-successful-syncs` disabled unless you are investigating sync behavior.
- Keep `anti-dupe.strict-container-lock` disabled unless you want maximum protection while wardrobe armor is equipped.
- Keep `performance.session.preload-profile-on-join` disabled on large servers.
- Use one `ruinedwardrobe.slots.<amount>` permission per rank.
