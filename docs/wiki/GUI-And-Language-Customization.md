# GUI And Language Customization

RuinedWardrobe separates layout from text.

| File | Purpose |
| --- | --- |
| `plugins/RuinedWardrobe/gui.yml` | Inventory rows, columns, buttons, navigation slots, item templates. |
| `plugins/RuinedWardrobe/lang/en_US.yml` | Titles, names, lore, command messages, errors, and diagnostics text. |

Reload config-only changes with:

```text
/wardrobe reload
```

## GUI Layout

Default layout is a six-row chest-style menu.

```yaml
layout:
  rows: 6
  slot-display-indices:
    - 0
    - 1
    - 2
    - 3
    - 4
    - 5
    - 6
    - 7
    - 8
```

`slot-display-indices` are column indexes from `0` to `8`. The default shows nine wardrobe sets per page.

Armor display rows:

```yaml
armor-layout:
  helmet-row: 0
  chestplate-row: 1
  leggings-row: 2
  boots-row: 3
```

Equip buttons:

```yaml
equip-buttons:
  row: 4
  slots:
    - 36
    - 37
    - 38
    - 39
    - 40
    - 41
    - 42
    - 43
    - 44
```

Navigation:

```yaml
navigation:
  previous-page-slot: 45
  close-slot: 49
  next-page-slot: 53
```

## Item Templates

Templates control the material, custom model data, glow, name key, and lore keys:

```yaml
templates:
  saved-slot:
    material: PLAYER_HEAD
    model-data: 0
    glow: false
    name-key: gui.slot-saved-name
    lore-keys:
      - gui.slot-saved-lore
```

Useful templates:

| Template | Used for |
| --- | --- |
| `empty-slot` | Empty armor cells. |
| `saved-slot` | Stored armor pieces. |
| `locked-slot` | Slots above the player's slot cap. |
| `cooldown-slot` | Equip cooldown feedback. |
| `equip-button-unequipped` | Equip button for inactive sets. |
| `equip-button-equipped` | Unequip button for active set. |
| `control-filler` | Cosmetic filler panes. |

## Language Files

Default language file:

```text
plugins/RuinedWardrobe/lang/en_US.yml
```

Active language:

```yaml
language:
  active: en_US
```

Missing keys fall back to `lang/en_US.yml`.

## Message Formatting

```yaml
messages:
  format-mode: BOTH
```

| Mode | Use |
| --- | --- |
| `LEGACY` | Legacy `&` color codes. |
| `MINIMESSAGE` | MiniMessage tags such as `<green>`. |
| `BOTH` | Mixed support. Best for most servers. |

## Built-In Placeholders In Messages

Common internal placeholders:

```text
{page}
{max_page}
{slot}
{max}
{name}
{seconds}
{world}
{gamemode}
{player}
{old}
{new}
{root}
{reason}
{message}
```

Only use placeholders that the specific message already supports.

## Design Tips

- Keep `layout.rows: 6` unless you are changing the whole layout.
- Keep equip button count aligned with `slot-display-indices`.
- Keep navigation buttons away from armor columns.
- Use short names and short lore. Long lore gets noisy fast in a chest GUI.
- Test with a normal player account so locked slots and permissions are visible.
- If a resource pack uses CustomModelData, set `model-data` on templates instead of changing code.
