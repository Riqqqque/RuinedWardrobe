# Upgrade And Release Checklist

Use this page when updating RuinedWardrobe, changing storage, or publishing a new jar to a live server.

> [!IMPORTANT]
> Back up before the first startup with a new jar. Config regeneration and schema migration are safer when rollback is simple.

## Before Updating

1. Read the changelog or commit notes.
2. Stop the server.
3. Back up `plugins/RuinedWardrobe`.
4. Back up MySQL/MariaDB if used.
5. Keep the old jar until the update is confirmed.
6. Copy the new jar into `plugins`.

## First Startup

1. Start the server.
2. Watch console for config backup or regeneration messages.
3. Run `/wardrobe doctor`.
4. Open `/wardrobe` as a normal player.
5. Save a set.
6. Equip and unequip the set.
7. Switch between two sets.
8. Test death behavior in a safe world.
9. Run `/wardrobe reload`.
10. Check the audit log.

## Config Version Changes

RuinedWardrobe backs up old config templates when `config-version` changes, then writes the bundled template.

After a version bump:

1. Open the regenerated config.
2. Reapply only the changes you still need.
3. Do not paste an old config over the new file.
4. Compare old backups carefully.
5. Restart or reload after edits.

## Storage Migration Release

For SQLite to MySQL:

```text
/wardrobe migrate mysql --dry-run
/wardrobe migrate mysql
```

For MySQL to SQLite:

```text
/wardrobe migrate sqlite --dry-run
/wardrobe migrate sqlite
```

If the target already contains wardrobe data, the real migration stops unless you add `--force`. When `--force` is used, target data is backed up before overwrite.

## Rollback

If the update is bad:

1. Stop the server.
2. Restore the old jar.
3. Restore `plugins/RuinedWardrobe` from backup.
4. Restore the external database backup if used.
5. Start the server.
6. Run `/wardrobe doctor`.
7. Test one player before reopening.

## Release Smoke Test

For local source builds on Windows:

```powershell
.\gradlew.bat clean build
```

Expected jar:

```text
build/libs/RuinedWardrobe-1.0.1.jar
```

Check these before sending a jar to anyone:

- Build passes.
- Tests pass.
- `plugin.yml` has the correct name, main class, API version, author, commands, and permissions.
- The jar contains `META-INF/LICENSE.txt`.
- The server starts without config errors.
- `/wardrobe doctor` works.
- A normal player can save, equip, unequip, and switch sets.

## Related Pages

- [Quick Start](Quick-Start.md)
- [Storage, Migration, And Backups](Storage-Migration-And-Backups.md)
- [Audit Logs And Troubleshooting](Audit-Logs-And-Troubleshooting.md)
