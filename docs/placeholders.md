# PlaceholderAPI Placeholders

Identifier: `prismwardrobe`

## Supported Placeholders
- `%prismwardrobe_slots_used%`: Number of saved slots in profile cache.
- `%prismwardrobe_slots_max%`: Effective max slots for player.
- `%prismwardrobe_cooldown_remaining%`: Equip cooldown remaining in seconds.
- `%prismwardrobe_selected_slot%`: Currently selected/equipped slot (`-1` when none).
- `%prismwardrobe_set_name_<slot>%`: Set display name for a slot (empty if unset).

## Notes
- Placeholders resolve from in-memory cache; a player profile must be loaded.
- Returns empty string if profile is not cached yet.
