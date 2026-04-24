# Migration Guide

FluxWardrobe supports storage migration between `SQLITE` and `MYSQL`, and it also auto-imports the older local schema on startup when it finds a legacy database layout.

## Command
- `/wardrobe migrate <sqlite|mysql> [--dry-run]`

## Recommended Flow
1. Set valid target DB credentials in `config.yml`.
2. Run dry run first:
   - `/wardrobe migrate mysql --dry-run`
3. Verify reported row counts (`players`, `sets`, `meta`) and the dry-run digest.
4. Run actual migration:
   - `/wardrobe migrate mysql`
5. Keep the backup file path reported by the command until the target server has been fully validated.
6. Restart plugin/server and confirm with `/wardrobe doctor`.

## Safety Guarantees
- On startup, FluxWardrobe can import the older local schema into the current schema and writes a backup first.
- FluxWardrobe writes a full snapshot backup before copying data.
- Snapshot rows are normalized before write so obviously broken values do not silently transfer.
- The copied target snapshot is read back and verified with a digest before migration reports success.

## Common Failure Causes
- Invalid MySQL credentials.
- Target host unreachable or blocked port.
- Missing MariaDB/MySQL service.

Migration errors are sanitized in chat while full stack traces remain in console logs.
