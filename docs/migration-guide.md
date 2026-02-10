# Migration Guide

PrismWardrobe supports storage migration between `SQLITE` and `MYSQL`.

## Command
- `/wardrobe migrate <sqlite|mysql> [--dry-run]`

## Recommended Flow
1. Set valid target DB credentials in `config.yml`.
2. Run dry run first:
   - `/wardrobe migrate mysql --dry-run`
3. Verify reported row counts (`players`, `sets`, `meta`).
4. Run actual migration:
   - `/wardrobe migrate mysql`
5. Restart plugin/server and confirm with `/wardrobe doctor`.

## Common Failure Causes
- Invalid MySQL credentials.
- Target host unreachable or blocked port.
- Missing MariaDB/MySQL service.

Migration errors are sanitized in chat while full stack traces remain in console logs.
