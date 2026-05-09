# Configuration

Main config:

```text
plugins/RuinedWardrobe/config.yml
```

RuinedWardrobe backs up and regenerates config files when their `config-version` no longer matches the plugin template. This prevents broken startup behavior after config schema changes.

## Wardrobe

```yaml
wardrobe:
  default-slots: 8
  max-slots-cap: 54
  max-pages: 2
  equip-cooldown-seconds: 3
```

- `default-slots` is the baseline for players without slot permissions.
- `max-slots-cap` is the hard safety limit.
- `max-pages` controls how many GUI pages are shown.
- `equip-cooldown-seconds` reduces spam switching unless a player has `ruinedwardrobe.bypass.cooldown`.

Final slot count is:

```text
max(default-slots, highest ruinedwardrobe.slots.<amount>) + admin bonus slots
```

## Death

```yaml
death:
  keep-wardrobe-on-death: false
```

When false, RuinedWardrobe respects vanilla loss if keepInventory is off. Equipped wardrobe armor drops as normal unbound items and is removed from the saved wardrobe slot.

When true, equipped wardrobe armor is removed from death drops, the saved wardrobe slot remains intact, and selected/equipped state is cleared so the player can re-equip after respawn.

Minecraft keepInventory always wins. If keepInventory is true, equipped armor is left untouched.

## Storage

```yaml
storage:
  type: SQLITE
```

Use SQLite for one server. Use MySQL for multi-server networks or when multiple servers need shared wardrobe data.

SQLite file:

```yaml
storage:
  sqlite:
    file: data/wardrobe.db
```

MySQL settings:

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

## Performance

Cache:

```yaml
performance:
  cache:
    max-size: 100000
    expire-after-seconds: 600
```

Larger cache means fewer DB reads. Smaller cache means lower memory use.

Session behavior:

```yaml
performance:
  session:
    preload-profile-on-join: false
    touch-player-row-on-join: false
```

Keep both false on large servers unless you need placeholders or another tool to see player rows immediately after login.

Armor sync:

```yaml
performance:
  armor-sync:
    enabled: true
    scan-interval-ticks: 40
    scan-batch-size: 500
    shutdown-flush-timeout-seconds: 10
```

This safety net saves equipped wardrobe armor changes that may not be caught by direct events. Lower `scan-batch-size` if you want flatter load spikes on huge servers.

Database queue:

```yaml
performance:
  database:
    write-queue-capacity: 2048
    write-retries: 3
    retry-delay-ms: 150
    worker-threads: 4
```

Do not set worker threads far above your connection pool. More workers than DB capacity usually makes performance worse.

## Audit

```yaml
audit:
  enabled: true
  directory: logs
  queue-size: 4096
  log-successful-syncs: false
  log-blocked-actions: true
  include-item-summaries: true
```

Keep audit enabled. It is the best way to diagnose item loss reports.

Turn on `log-successful-syncs` only while investigating armor sync behavior because it can be noisy.

## Anti-Dupe

```yaml
anti-dupe:
  strict-container-lock: false
```

When true, players wearing wardrobe-bound armor cannot interact with non-player containers. This is stricter than most servers need, but it is available for high-risk environments.
