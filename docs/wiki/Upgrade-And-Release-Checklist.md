# Upgrade And Release Checklist

Use this whenever you update RuinedWardrobe, change storage, or publish a new jar to a live server.

## Before Updating

1. Read the changelog or commit notes.
2. Stop the server.
3. Back up `plugins/RuinedWardrobe`.
4. Back up MySQL/MariaDB if used.
5. Keep the old jar until the update is confirmed.
6. Copy the new jar into `plugins`.

## First Startup

1. Start the server.
2. Watch console for config backup/regeneration messages.
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
3. Do not paste an old config over the new one.
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

Do not skip the dry-run. If the target already contains wardrobe data, the real migration stops unless you add `--force`, and a target backup is written before overwrite.

## Rollback

If the update is bad:

1. Stop the server.
2. Restore the old jar.
3. Restore `plugins/RuinedWardrobe` from backup.
4. Restore the external database backup if used.
5. Start the server.
6. Run `/wardrobe doctor`.

## Release Smoke Test

For local source builds:

```powershell
.\gradlew.bat clean build
```

Expected jar:

```text
build/libs/RuinedWardrobe-1.0.0.jar
```

Check these before sending a jar to anyone:

- Build passes.
- Tests pass.
- `plugin.yml` has the correct name, main class, API version, author, commands, and permissions.
- The jar contains `META-INF/LICENSE.txt`.
- The server starts without config errors.
- `/wardrobe doctor` works.
