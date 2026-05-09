# GUI And Language Customization

## Files

```text
plugins/RuinedWardrobe/gui.yml
plugins/RuinedWardrobe/lang/en_US.yml
```

`gui.yml` controls layout and item templates. `lang/en_US.yml` controls player-facing text.

## GUI Layout

RuinedWardrobe uses a page-based inventory GUI. Armor rows are configured separately from equip buttons and navigation controls.

Common layout parts:

- `layout.rows`
- `layout.slot-display-indices`
- `layout.equip-buttons.slots`
- `layout.navigation.previous-slot`
- `layout.navigation.next-slot`
- `layout.navigation.close-slot`
- `templates.*`

## Templates

Templates control the material, name key, lore keys, glow, and optional custom model data.

This lets resource-pack servers replace default visuals without code changes.

## Language Keys

All normal player messages should come from language files. Missing keys fall back to `lang/en_US.yml`.

Message parser mode is controlled by:

```yaml
messages:
  format-mode: BOTH
```

Modes:

- `LEGACY`: legacy `&` color codes.
- `MINIMESSAGE`: MiniMessage tags.
- `BOTH`: mixed support.

## Tips

- Keep GUI rows at 6 unless you know the layout math.
- Keep navigation controls away from armor columns.
- Use short item names and concise lore for readability.
- Test the GUI on a normal player account, not only as op.
- If players cannot click a slot, check whether it is locked by their slot cap.
