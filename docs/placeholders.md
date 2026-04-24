# PlaceholderAPI Placeholders

Identifier: `fluxwardrobe`

## Supported Placeholders
- `%fluxwardrobe_slots_used%`: Number of saved slots in profile cache.
- `%fluxwardrobe_slots_max%`: Effective max slots for player.
- `%fluxwardrobe_cooldown_remaining%`: Equip cooldown remaining in seconds.
- `%fluxwardrobe_selected_slot%`: Currently selected/equipped slot (`-1` when none).
- `%fluxwardrobe_set_name_<slot>%`: Set display name for a slot (empty if unset).

## Notes
- Placeholders resolve from in-memory cache; a player profile must be loaded.
- Returns empty string if profile is not cached yet.
