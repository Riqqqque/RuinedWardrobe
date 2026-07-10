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
| `%ruinedwardrobe_slots_unlocked%` | Alias for effective max slots. |
| `%ruinedwardrobe_slots_free%` | Remaining unlocked empty set slots. |
| `%ruinedwardrobe_cooldown_remaining%` | Equip cooldown remaining in seconds. |
| `%ruinedwardrobe_cooldown_ready%` | `true` when the player is not on wardrobe cooldown. |
| `%ruinedwardrobe_open%` | `true` when the player currently has a RuinedWardrobe GUI open. |
| `%ruinedwardrobe_wardrobe_open%` | Alias for `%ruinedwardrobe_open%`. |
| `%ruinedwardrobe_selected_slot%` | Currently selected slot, or `-1` when none is selected. |
| `%ruinedwardrobe_equipped%` | `true` when the selected slot still exists. |
| `%ruinedwardrobe_equipped_slot%` | Equipped slot number, or empty when none is equipped. |
| `%ruinedwardrobe_equipped_name%` | Equipped set name, or empty when none is equipped. |
| `%ruinedwardrobe_set_name_<slot>%` | Display name for the given slot, or empty if unset. |
| `%ruinedwardrobe_set_exists_<slot>%` | `true` when a saved set exists in that slot. |
| `%ruinedwardrobe_set_piece_count_<slot>%` | Number of saved armor pieces in that set. |
| `%ruinedwardrobe_set_piece_<slot>_helmet%` | Saved helmet material name, or empty. |
| `%ruinedwardrobe_set_piece_<slot>_chestplate%` | Saved chestplate material name, or empty. |
| `%ruinedwardrobe_set_piece_<slot>_leggings%` | Saved leggings material name, or empty. |
| `%ruinedwardrobe_set_piece_<slot>_boots%` | Saved boots material name, or empty. |

Example:

```text
%ruinedwardrobe_set_name_3%
```

## Placeholder Cache Behavior

Most placeholders resolve from the in-memory profile cache. If a profile has not loaded yet, cached-profile placeholders can return an empty string.

These placeholders do not need a cached profile:

```text
%ruinedwardrobe_open%
%ruinedwardrobe_wardrobe_open%
%ruinedwardrobe_slots_max%
%ruinedwardrobe_slots_unlocked%
%ruinedwardrobe_cooldown_remaining%
%ruinedwardrobe_cooldown_ready%
```

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

## Custom Item And Cosmetic Plugins

RuinedWardrobe stores and restores full Bukkit `ItemStack` data for armor pieces. That means display names, lore, enchantments, item meta, custom model data, trims, PersistentDataContainer data, components exposed through Bukkit, and plugin-specific item metadata that survives Bukkit item serialization are preserved.

This works best with custom-item and resource-pack plugins that expose their armor as normal Bukkit `ItemStack`s, including setups built around plugins such as ItemsAdder, Oraxen, Nexo, MythicMobs, MMOItems, ExecutableItems, EcoItems, CraftEngine, and ModelEngine. RuinedWardrobe does not create or manage those items; it stores, equips, and returns the exact item stack the server gives it.

`/wardrobe doctor` reports common optional plugins it can see on the server. Use that line when checking compatibility reports from live servers.

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
