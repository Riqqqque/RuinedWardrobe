# PlaceholderAPI Placeholders

Identifier: `ruinedwardrobe`

## Supported Placeholders
- `%ruinedwardrobe_slots_used%`: Number of saved slots in profile cache.
- `%ruinedwardrobe_slots_max%`: Effective max slots for player.
- `%ruinedwardrobe_slots_unlocked%`: Alias for effective max slots.
- `%ruinedwardrobe_slots_free%`: Remaining unlocked empty set slots.
- `%ruinedwardrobe_cooldown_remaining%`: Equip cooldown remaining in seconds.
- `%ruinedwardrobe_cooldown_ready%`: `true` when the player is not on wardrobe cooldown.
- `%ruinedwardrobe_open%`: `true` when the player currently has a RuinedWardrobe GUI open.
- `%ruinedwardrobe_wardrobe_open%`: Alias for `%ruinedwardrobe_open%`.
- `%ruinedwardrobe_selected_slot%`: Currently selected/equipped slot (`-1` when none).
- `%ruinedwardrobe_equipped%`: `true` when the selected slot still exists.
- `%ruinedwardrobe_equipped_slot%`: Equipped slot number, or empty when none.
- `%ruinedwardrobe_equipped_name%`: Equipped set name, or empty when none.
- `%ruinedwardrobe_set_name_<slot>%`: Set display name for a slot (empty if unset).
- `%ruinedwardrobe_set_exists_<slot>%`: `true` when a saved set exists in that slot.
- `%ruinedwardrobe_set_piece_count_<slot>%`: Number of saved armor pieces in that set.
- `%ruinedwardrobe_set_piece_<slot>_helmet%`: Saved helmet material name, or empty.
- `%ruinedwardrobe_set_piece_<slot>_chestplate%`: Saved chestplate material name, or empty.
- `%ruinedwardrobe_set_piece_<slot>_leggings%`: Saved leggings material name, or empty.
- `%ruinedwardrobe_set_piece_<slot>_boots%`: Saved boots material name, or empty.

## Notes
- Most placeholders resolve from in-memory cache; a player profile must be loaded.
- `open`, `wardrobe_open`, `slots_max`, `slots_unlocked`, `cooldown_remaining`, and `cooldown_ready` do not need a cached profile.
- Cached-profile placeholders return empty string if the profile is not loaded yet.
