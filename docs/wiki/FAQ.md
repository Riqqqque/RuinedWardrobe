# FAQ

## Does RuinedWardrobe support Folia?

Yes. The plugin declares Folia support and uses scheduler adapters for Paper and Folia behavior.

## What Java version should I use?

Java `21+`.

## What Paper API is this built for?

The project targets Paper API `1.21` and supports Paper/Folia `1.21` through `26.1.2`.

## Should I use SQLite or MySQL?

Use SQLite for one server. Use MySQL or MariaDB when multiple servers need shared wardrobe data.

## How many slots do players get by default?

The bundled config starts at `wardrobe.default-slots: 3`. Give ranks slot tier nodes such as `ruinedwardrobe.slots.6` or `ruinedwardrobe.slots.9` to expand access.

## Why are some slots locked?

The player's effective slot limit is lower than that slot number. Check `wardrobe.default-slots`, `wardrobe.max-slots-cap`, `ruinedwardrobe.slots.<amount>`, and admin bonus slots.

## Why did armor drop on death?

If keepInventory is off and `death.keep-wardrobe-on-death` is false, RuinedWardrobe follows vanilla-style loss. The armor drops and is removed from the saved wardrobe slot.

## Can wardrobe armor be moved or traded while worn?

No. Equipped wardrobe armor is bound while worn and protected from normal movement, dropping, swapping, dispensing, and related abuse paths.

## What does strict container lock do?

When `anti-dupe.strict-container-lock` is true, players wearing wardrobe-bound armor cannot interact with non-player containers. It is stricter than most servers need, but useful for high-risk economies after testing.

## Why do placeholders return empty?

PlaceholderAPI values come from the in-memory profile cache. If the player profile has not loaded yet, the placeholder returns an empty string. Opening `/wardrobe` loads it, or you can enable join preload.

## Why did migration stop because the target has data?

RuinedWardrobe refuses to overwrite non-empty target storage by default. Confirm the target can be replaced, then rerun the migration with `--force`. A target backup is written before overwrite.

## Can I sell ranks that give more wardrobe slots?

Yes. Server monetization is allowed. You cannot sell the plugin jar, source, forks, builds, or paid update access.

## Where do I look when a player reports missing armor?

Start with:

```text
plugins/RuinedWardrobe/logs/wardrobe-audit-YYYY-MM-DD.log
```

Search for the player's name or UUID around the reported time.

## What should I send with a bug report?

Send the audit log for the timestamp, server console around the same time, `config.yml`, plugin version, Paper/Folia build, Java version, and the exact player action sequence.

## Related Pages

- [Quick Start](Quick-Start.md)
- [Configuration](Configuration.md)
- [Audit Logs And Troubleshooting](Audit-Logs-And-Troubleshooting.md)
