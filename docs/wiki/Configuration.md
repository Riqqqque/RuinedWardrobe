# Configuration

Main file:

```text
plugins/RuinedWardrobe/config.yml
```

RuinedWardrobe backs up and regenerates config files when their `config-version` does not match the bundled template. That keeps older configs from silently missing new safety settings.

> [!NOTE]
> `gui.yml` controls the inventory layout. `lang/en_US.yml` controls visible text. `config.yml` controls behavior, storage, integrations, and performance.

## Version Guard

| File | Current schema |
| --- | --- |
| `config.yml` | `6` |
| `gui.yml` | `2` |

When a schema changes, copy your old custom values into the new generated file instead of pasting the old file over the new one.

## Safe First Pass

| Section | Usually change? | First decision |
| --- | --- | --- |
| `language` | Sometimes | Pick the active language file. |
| `messages` | Rarely | Keep `BOTH` unless you want only legacy or only MiniMessage parsing. |
| `wardrobe` | Yes | Set baseline slots, page count, and equip cooldown. |
| `death` | Server-specific | Decide vanilla death loss or protected wardrobe sets. |
| `storage` | Yes | SQLite for one server, MySQL/MariaDB for networks. |
| `performance` | Only under load | Keep defaults until `/wardrobe doctor` shows pressure. |
| `audit` | Usually no | Keep enabled for support and item investigations. |
| `restrictions` | Server-specific | Add world, gamemode, combat, or PlaceholderAPI blocks. |
| `integrations` | Server-specific | Enable only hooks that exist on the server. |
| `anti-dupe` | Server-specific | Test strict container lock before production use. |
| `metrics` | Optional | Enable only after setting a bStats plugin id. |
| `debug` | Temporary | Use for troubleshooting, then turn it off. |

## Slots And Pages

```yaml
wardrobe:
  default-slots: 3
  max-slots-cap: 54
  max-pages: 2
  equip-cooldown-seconds: 3
```

Effective slot count:

```text
min(max-slots-cap, max(default-slots, highest ruinedwardrobe.slots.<amount>) + admin bonus slots)
```

Use `default-slots` for the baseline. Use `ruinedwardrobe.slots.<amount>` for ranks. Use `/wardrobe admin setslots <player> <amount>` for one-off support adjustments.

## Death Behavior

```yaml
death:
  keep-wardrobe-on-death: false
```

| Value | Behavior |
| --- | --- |
| `false` | Vanilla-style loss when keepInventory is off. Equipped wardrobe armor drops and is removed from the saved slot. |
| `true` | Wardrobe armor is removed from drops, the saved slot stays intact, and selected state is cleared for re-equip after respawn. |

Minecraft's `keepInventory` gamerule wins either way. When keepInventory is true, equipped armor is left alone.

## Storage

```yaml
storage:
  type: SQLITE
```

SQLite:

```yaml
storage:
  sqlite:
    file: data/wardrobe.db
```

MySQL/MariaDB:

```yaml
storage:
  mysql:
    host: 127.0.0.1
    port: 3306
    database: ruinedwardrobe
    username: root
    password: change_me
    params: useUnicode=true&characterEncoding=utf8&useSSL=false
```

Connection pool:

```yaml
storage:
  pool:
    max-size: 10
    min-idle: 2
    connection-timeout-ms: 10000
```

Keep DB worker count and pool size close. More workers than connections usually adds waiting instead of throughput.

## Performance

Cache:

```yaml
performance:
  cache:
    max-size: 100000
    expire-after-seconds: 600
```

Session behavior:

```yaml
performance:
  session:
    preload-profile-on-join: false
    touch-player-row-on-join: false
```

Keep both values false on large servers unless another tool needs data available immediately after join.

MySQL sync:

```yaml
performance:
  sync:
    poll-seconds: 5
    batch-size: 150
```

Armor safety scan:

```yaml
performance:
  armor-sync:
    enabled: true
    scan-interval-ticks: 40
    scan-batch-size: 500
    shutdown-flush-timeout-seconds: 10
```

DB queue and retries:

```yaml
performance:
  database:
    write-queue-capacity: 2048
    write-retries: 3
    retry-delay-ms: 150
    worker-threads: 4
```

Health logging:

```yaml
performance:
  health:
    enabled: true
    log-seconds: 120
```

## Audit Log

```yaml
audit:
  enabled: true
  directory: logs
  queue-size: 4096
  log-successful-syncs: false
  log-blocked-actions: true
  include-item-summaries: true
```

Audit logs write here:

```text
plugins/RuinedWardrobe/logs/wardrobe-audit-YYYY-MM-DD.log
```

Keep audit enabled. Turn on `log-successful-syncs` only while investigating armor sync behavior because it can get noisy.

## Restrictions

```yaml
restrictions:
  enabled: true
  blocked-worlds: []
  allowed-worlds: []
  blocked-gamemodes:
    - SPECTATOR
```

If `allowed-worlds` is not empty, only those worlds can use equip. That allow-list runs before `blocked-worlds`.

Combat check:

```yaml
restrictions:
  combat-check:
    enabled: true
```

PlaceholderAPI rules:

```yaml
restrictions:
  placeholder-rules:
    region_block_example:
      placeholder: "%worldguard_region_name%"
      disallow-values:
        - spawn
      reason-key: restriction.placeholder
```

Placeholder rules require PlaceholderAPI and `integrations.placeholderapi.enabled: true`.

## Integrations

```yaml
integrations:
  placeholderapi:
    enabled: true
  vault:
    enabled: true
  combat:
    enabled: true
```

These toggles allow hook discovery. The matching plugin still has to be installed and enabled.

## GUI Feedback

```yaml
gui:
  sounds:
    enabled: true
  actionbar:
    enabled: true
  titles:
    enabled: true
  animations:
    enabled: true
    click-delay-ticks: 2
```

The inventory layout itself lives in `gui.yml`.

## Anti-Dupe

```yaml
anti-dupe:
  strict-container-lock: false
```

When true, players wearing wardrobe-bound armor cannot interact with non-player containers. Test this before using it on a live economy server because it intentionally changes normal player flow.

## Metrics And Debug

```yaml
metrics:
  enabled: false
  plugin-id: 0

debug:
  enabled: false
```

Enable debug only for short troubleshooting windows. It is not meant to stay on in normal production.

## Related Pages

- [Storage, Migration, And Backups](Storage-Migration-And-Backups.md)
- [Permissions And Commands](Permissions-And-Commands.md)
- [GUI And Language Customization](GUI-And-Language-Customization.md)
