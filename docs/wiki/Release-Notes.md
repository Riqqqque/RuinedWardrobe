# Release Notes

## 1.0.16

This release focuses on failure safety and predictable operations.

### Item Safety

- Vanilla-style death loss now withholds bound armor until all affected wardrobe pieces and selected state commit in one transaction.
- Only one canonical unbound copy of each legitimate death drop is released.
- A failed death write keeps the saved copy recoverable and does not release another physical copy.
- GUI pickup and placement actions stay serialized through delivery or database rollback.
- Rollback rejects malformed slots, piece types, stack sizes, and mismatched armor.

### Runtime

- `/wardrobe reload` flushes asynchronously and no longer blocks the Paper main thread or a Folia player scheduler.
- Folia retirement callbacks finish abandoned player tasks instead of leaving futures and action locks pending.
- Shutdown uses scheduler-safe armor capture and drains queued database work.
- SQLite uses WAL mode, full synchronous durability, foreign keys, and a busy timeout on every connection.
- Database retries are limited to transient connection, transaction, busy, and locked failures.

### Upgrades

- Config template upgrades preserve configured values, including storage settings.
- Configs from a newer unsupported plugin version stop startup instead of being overwritten.
- Gradle, Shadow, PlaceholderAPI, Caffeine, HikariCP, SQLite JDBC, MariaDB JDBC, and JUnit were updated to current stable releases.
- The compatibility build is verified against Paper `26.2.build.84-stable` while keeping the Paper `1.21` API and Java `21` baseline.

## Compatibility

| Platform | Versions |
| --- | --- |
| Paper | `1.21` through `26.2` |
| Folia | `1.21` through `26.2` |
| Java | `21+` |

Full `ItemStack` serialization remains the storage contract, including Bukkit-exposed PDC, custom model data, components, attributes, trims, damage, enchantments, display data, and plugin metadata.

## Upgrade

1. Stop the server and back up `plugins/RuinedWardrobe`.
2. Replace only the plugin jar.
3. Start the server and run `/wardrobe doctor`.
4. Test save, equip, pickup, placement, death, reload, and restart with a normal player account.

See [Upgrade And Release Checklist](Upgrade-And-Release-Checklist.md) for the complete production procedure.
