# PlaceholderAPI Placeholders

Identifier: `ruinedwardrobe`

## Supported Placeholders
- `%ruinedwardrobe_slots_used%`: Number of saved slots in profile cache.
- `%ruinedwardrobe_slots_max%`: Effective max slots for player.
- `%ruinedwardrobe_cooldown_remaining%`: Equip cooldown remaining in seconds.
- `%ruinedwardrobe_selected_slot%`: Currently selected/equipped slot (`-1` when none).
- `%ruinedwardrobe_set_name_<slot>%`: Set display name for a slot (empty if unset).

## Notes
- Placeholders resolve from in-memory cache; a player profile must be loaded.
- Returns empty string if profile is not cached yet.
