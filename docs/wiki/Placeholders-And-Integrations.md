# Placeholders And Integrations

RuinedWardrobe can work with PlaceholderAPI, Vault, and combat providers. All integrations are optional.

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

These toggles allow discovery. The matching plugin still needs to be installed and enabled on the server.

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

## Placeholder Cache Note

Placeholders resolve from the in-memory profile cache. If a profile has not loaded yet, placeholders return an empty string.

If you need placeholders to be warm right after login, enable:

```yaml
performance:
  session:
    preload-profile-on-join: true
```

That makes joins do more DB work. Keep it false on large servers unless immediate placeholders matter.

## Placeholder Restrictions

PlaceholderAPI can also block equip based on another plugin's placeholder output.

Example:

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

## Combat Integration

Combat checks are controlled here:

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

Vault discovery is available and can be left enabled. Current configuration does not require an economy setup for normal wardrobe use.

## Troubleshooting Integrations

| Problem | Check |
| --- | --- |
| Placeholder returns empty | Player profile may not be cached yet. Open `/wardrobe` once or enable preload. |
| Placeholder rule never blocks | Make sure PlaceholderAPI is installed and the placeholder itself returns the expected value. |
| Combat restriction never blocks | Confirm the combat plugin is installed, enabled, and supported by the detected provider. |
| Messages show raw placeholder text | Confirm PlaceholderAPI is installed and `integrations.placeholderapi.enabled` is true. |
