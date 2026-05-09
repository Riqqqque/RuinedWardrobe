# Storage, Migration, And Backups

## Storage Choices

Use SQLite when:

- You run one server.
- You want the simplest setup.
- You do not need shared wardrobe data across multiple servers.

Use MySQL/MariaDB when:

- You run a network.
- Multiple servers need shared wardrobe data.
- You want external database backups or monitoring.

## SQLite

Default path:

```text
plugins/RuinedWardrobe/data/wardrobe.db
```

Keep the database on fast local storage. Back it up before major Minecraft, Java, plugin, or server updates.

## MySQL/MariaDB

Minimum recommendations:

- Use a dedicated database.
- Use a dedicated DB user.
- Keep credentials private.
- Monitor connection limits.
- Keep pool size and worker threads aligned.

## Migration Command

```text
/wardrobe migrate <sqlite|mysql> [--dry-run]
```

Always run dry-run first:

```text
/wardrobe migrate mysql --dry-run
```

Then run the real migration:

```text
/wardrobe migrate mysql
```

## Safety Behavior

RuinedWardrobe migration:

- Reads a full source snapshot.
- Writes a backup before real migration.
- Normalizes obvious broken values.
- Writes to target storage.
- Reads the target snapshot back.
- Verifies row digests before reporting success.

## Legacy Import

RuinedWardrobe can import older local schema layouts on startup. It backs up the old tables before removing them.

## Backup Checklist

Before a major update:

1. Stop the server.
2. Back up `plugins/RuinedWardrobe`.
3. Back up your external MySQL database if used.
4. Start the server with the new jar.
5. Check console.
6. Check `/wardrobe doctor`.
7. Test save, equip, switch, death, and reload.
8. Keep backups until players have tested real usage.
