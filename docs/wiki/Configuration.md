# Configuration

Main config:

```text
plugins/RuinedWardrobe/config.yml
```

RuinedWardrobe backs up and regenerates config files when their `config-version` no longer matches the bundled template. That protects startup from old or missing keys after updates.

## Quick Decision Table

| Section | Usually change? | Notes |
| --- | --- | --- |
| `language` | Sometimes | Pick the active file from `lang/`. |
| `messages` | Rarely | `BOTH` is the easiest mode for mixed legacy and MiniMessage text. |
| `wardrobe` | Yes | Slot count, pages, and cooldown live here. |
| `death` | Maybe | Decide vanilla loss or protected wardrobe behavior. |
| `storage` | Yes | SQLite for one server, MySQL for networks. |
| `performance` | Only when needed | Defaults are meant to stay stable under normal load. |
| `audit` | Usually no | Keep enabled unless you have a strong reason. |
| `restrictions` | Server-specific | World, gamemode, combat, and PlaceholderAPI rules. |
| `anti-dupe` | Server-specific | Strict container lock is powerful but changes normal player flow. |

## Slots And Pages

```yaml
wardrobe:
  default-slots: 8
  max-slots-cap: 54
  max-pages: 2
  equip-cooldown-seconds: 3
```

Effective slot count:

```text
min(max-slots-cap, max(default-slots, highest ruinedwardrobe.slots.<amount>) + admin bonus slots)
```

Use `default-slots` for your baseline. Use `ruinedwardrobe.slots.<amount>` permissions for ranks. Use `/wardrobe admin setslots <player> <amount>` for one-off support adjustments.

## Death Behavior

```yaml
death:
  keep-wardrobe-on-death: false
```

| Value | Behavior |
| --- | --- |
| `false` | Vanilla-style loss when keepInventory is off. Equipped wardrobe armor drops and is removed from the saved slot. |
| `true` | Wardrobe armor is removed from death drops, the saved slot stays intact, and selected state is cleared for re-equip after respawn. |

Minecraft's `keepInventory` gamerule wins either way. When keepInventory is true, equipped armor is left alone.

## Storage

```yaml
storage:
  type: SQLITE
```

Use SQLite when the data belongs to one server:

```yaml
storage:
  sqlite:
    file: data/wardrobe.db
```

Use MySQL/MariaDB when more than one server needs the same wardrobe data:

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

Pool settings:

```yaml
storage:
  pool:
    max-size: 10
    min-idle: 2
    connection-timeout-ms: 10000
```

Keep DB worker count and pool size close. More workers than connections usually adds waiting instead of speed.

## Performance

Cache:

```yaml
performance:
  cache:
    max-size: 100000
    expire-after-seconds: 600
```

Session:

```yaml
performance:
  session:
    preload-profile-on-join: false
    touch-player-row-on-join: false
```

Keep both session values false on large servers unless you need PlaceholderAPI or another tool to see player data right after login.

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

DB queue:

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

Keep audit enabled. It writes to:

```text
plugins/RuinedWardrobe/logs/wardrobe-audit-YYYY-MM-DD.log
```

Turn on `log-successful-syncs` only while investigating armor sync behavior. It can get noisy.

## Restrictions

```yaml
restrictions:
  enabled: true
  blocked-worlds: []
  allowed-worlds: []
  blocked-gamemodes:
    - SPECTATOR
```

Allow-list wins before blocked-worlds. If `allowed-worlds` is not empty, only those worlds can use equip.

Combat checks use the detected combat provider when enabled:

```yaml
restrictions:
  combat-check:
    enabled: true
```

Placeholder rules require PlaceholderAPI and `integrations.placeholderapi.enabled: true`:

```yaml
restrictions:
  placeholder-rules:
    region_block_example:
      placeholder: "%worldguard_region_name%"
      disallow-values:
        - spawn
      reason-key: restriction.placeholder
```

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

These are discovery toggles. The optional plugin still has to be installed and enabled on the server.

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

The actual inventory layout is in `gui.yml`, not this section.

## Anti-Dupe

```yaml
anti-dupe:
  strict-container-lock: false
```

When true, players wearing wardrobe-bound armor cannot interact with non-player containers. Test this before using it on a live economy server because it intentionally changes how players can interact while wearing wardrobe sets.
