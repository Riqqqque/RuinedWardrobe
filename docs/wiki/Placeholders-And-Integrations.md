# Placeholders And Integrations

RuinedWardrobe can discover PlaceholderAPI, Vault, and supported combat providers. These integrations are optional; the plugin works without them.

## Integration Toggles

```yaml
integrations:
  placeholderapi:
    enabled: true
  vault:
    enabled: true
  combat:
    enabled: true
```

These toggles allow hook discovery. The matching plugin still needs to be installed and enabled on the server.

## PlaceholderAPI Expansion

Identifier:

```text
ruinedwardrobe
```

Supported placeholders:

| Placeholder | Output |
| --- | --- |
| `%ruinedwardrobe_slots_used%` | Number of saved sets in the cached player profile. |
| `%ruinedwardrobe_slots_max%` | Effective max slots for the player. |
| `%ruinedwardrobe_cooldown_remaining%` | Equip cooldown remaining in seconds. |
| `%ruinedwardrobe_selected_slot%` | Currently selected slot, or `-1` when none is selected. |
| `%ruinedwardrobe_set_name_<slot>%` | Display name for the given slot, or empty if unset. |

Example:

```text
%ruinedwardrobe_set_name_3%
```

## Placeholder Cache Behavior

Placeholders resolve from the in-memory profile cache. If a profile has not loaded yet, placeholders can return an empty string.

If you need placeholder values warm right after login:

```yaml
performance:
  session:
    preload-profile-on-join: true
```

That makes joins do more DB work. Keep it false on large servers unless immediate placeholder output matters.

## Placeholder Restrictions

PlaceholderAPI can block equip based on another plugin's placeholder output.

```yaml
restrictions:
  placeholder-rules:
    spawn_region:
      placeholder: "%worldguard_region_name%"
      disallow-values:
        - spawn
      reason-key: restriction.placeholder
```

When the placeholder output matches a disallowed value, equip is denied with the configured language key.

## Combat Checks

```yaml
restrictions:
  combat-check:
    enabled: true

integrations:
  combat:
    enabled: true
```

Players with `ruinedwardrobe.bypass.combat` skip combat restriction checks.

## Vault

Vault discovery can be left enabled. Normal wardrobe use does not require an economy setup.

## Troubleshooting Integrations

| Problem | Check |
| --- | --- |
| Placeholder returns empty | Player profile may not be cached yet. Open `/wardrobe` once or enable preload. |
| Placeholder rule never blocks | Confirm PlaceholderAPI is installed and the placeholder itself returns the expected value. |
| Combat restriction never blocks | Confirm the combat plugin is installed, enabled, and supported by the detected provider. |
| Messages show raw placeholder text | Confirm PlaceholderAPI is installed and `integrations.placeholderapi.enabled` is true. |

## Related Pages

- [Configuration](Configuration.md)
- [Permissions And Commands](Permissions-And-Commands.md)
- [FAQ](FAQ.md)
