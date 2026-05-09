# Permissions And Commands

## Player Commands

```text
/wardrobe
/rw
/wardrobe help
/wardrobe list [player]
```

## Admin Commands

```text
/wardrobe doctor
/wardrobe reload
/wardrobe migrate <sqlite|mysql> [--dry-run]
/wardrobe admin open <player>
/wardrobe admin setslots <player> <amount>
/wardrobe admin clearslots <player>
```

## Core Permission Nodes

```text
ruinedwardrobe.use
ruinedwardrobe.command.help
ruinedwardrobe.command.list
ruinedwardrobe.command.doctor
ruinedwardrobe.command.reload
ruinedwardrobe.command.migrate
ruinedwardrobe.admin
ruinedwardrobe.admin.*
ruinedwardrobe.command.*
```

## Slot Permission Nodes

```text
ruinedwardrobe.slots.3
ruinedwardrobe.slots.6
ruinedwardrobe.slots.9
ruinedwardrobe.slots.15
```

The highest assigned slot permission wins. A player with both `ruinedwardrobe.slots.6` and `ruinedwardrobe.slots.15` gets the 15-slot tier.

Admin bonus slots from `/wardrobe admin setslots` are added after rank slot resolution.

## Bypass Nodes

```text
ruinedwardrobe.bypass.cooldown
ruinedwardrobe.bypass.restrictions
ruinedwardrobe.bypass.emptycheck
ruinedwardrobe.bypass.combat
```

Use bypass nodes carefully. They are meant for staff, testing, and support cases.

## LuckPerms Examples

Basic player:

```text
/lp group default permission set ruinedwardrobe.use true
/lp group default permission set ruinedwardrobe.command.help true
/lp group default permission set ruinedwardrobe.command.list true
/lp group default permission set ruinedwardrobe.slots.8 true
```

VIP:

```text
/lp group vip permission set ruinedwardrobe.slots.15 true
```

Admin:

```text
/lp group admin permission set ruinedwardrobe.admin.* true
/lp group admin permission set ruinedwardrobe.command.* true
/lp group admin permission set ruinedwardrobe.bypass.cooldown true
```

## Recommended Rank Strategy

- Give every player `ruinedwardrobe.use`.
- Give one slot tier per rank.
- Avoid stacking many slot tier nodes unless intentional.
- Keep bypass nodes off normal ranks.
- Use admin bonus slots for temporary support adjustments instead of making one-off permission nodes.
