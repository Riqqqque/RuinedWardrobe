# GUI And Language Customization

RuinedWardrobe keeps layout and text separate so you can change the menu without touching code.

| File | Controls |
| --- | --- |
| `plugins/RuinedWardrobe/gui.yml` | Inventory rows, armor cells, equip buttons, navigation, filler items, templates. |
| `plugins/RuinedWardrobe/lang/en_US.yml` | Titles, item names, lore, command messages, errors, and diagnostics text. |

Reload config-only changes with:

```text
/wardrobe reload
```

> [!IMPORTANT]
> After layout changes, test with a normal player so locked slots, permissions, page buttons, and equip buttons all render correctly.

## Layout Model

The default GUI is a six-row chest-style inventory.

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

`slot-display-indices` are column indexes from `0` to `8`. The default layout shows nine wardrobe sets per page.

## Armor Cells

```yaml
armor-layout:
  helmet-row: 0
  chestplate-row: 1
  leggings-row: 2
  boots-row: 3
```

Keep armor rows aligned with the same columns used by `slot-display-indices`.

## Equip Buttons

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

The number of equip button slots should match the number of displayed wardrobe columns.

## Navigation

```yaml
navigation:
  previous-page-slot: 45
  close-slot: 49
  next-page-slot: 53
```

Keep navigation away from armor and equip cells so clicks stay predictable.

## Item Templates

Templates define material, CustomModelData, glow, name key, and lore keys:

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

| Template | Used for |
| --- | --- |
| `empty-slot` | Empty armor cells. |
| `saved-slot` | Stored armor pieces. |
| `locked-slot` | Slots above the player's slot cap. |
| `cooldown-slot` | Equip cooldown feedback. |
| `equip-button-unequipped` | Equip button for inactive sets. |
| `equip-button-equipped` | Unequip button for the active set. |
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

Missing keys fall back to bundled `lang/en_US.yml`.

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

## Built-In Message Placeholders

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

Only use placeholders supported by that specific message key.

## Design Checklist

- Keep `layout.rows: 6` unless you are redesigning the whole inventory.
- Keep equip button count aligned with `slot-display-indices`.
- Keep navigation buttons out of armor columns.
- Keep names and lore short enough for a chest GUI.
- Use `model-data` for resource pack styling instead of code edits.
- Test locked slots, cooldown slots, equipped slots, and empty slots.

## Related Pages

- [Configuration](Configuration.md)
- [Permissions And Commands](Permissions-And-Commands.md)
- [Audit Logs And Troubleshooting](Audit-Logs-And-Troubleshooting.md)
