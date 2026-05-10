# Permissions And Commands

RuinedWardrobe is designed around simple player access, rank-based slot tiers, and staff-only support tools.

## Player Commands

| Command | What it does | Permission |
| --- | --- | --- |
| `/wardrobe` | Opens your wardrobe GUI. | `ruinedwardrobe.use` |
| `/rw` | Alias for `/wardrobe`. | `ruinedwardrobe.use` |
| `/wardrobe help` | Shows available commands. | `ruinedwardrobe.command.help` |
| `/wardrobe list [player]` | Lists saved sets for yourself or a known player. | `ruinedwardrobe.command.list` |

## Staff Commands

| Command | What it does | Permission |
| --- | --- | --- |
| `/wardrobe doctor` | Shows runtime diagnostics. | `ruinedwardrobe.command.doctor` |
| `/wardrobe reload` | Reloads config, GUI, and language files. | `ruinedwardrobe.command.reload` |
| `/wardrobe migrate <sqlite|mysql> [--dry-run]` | Migrates storage with snapshot verification. | `ruinedwardrobe.command.migrate` |
| `/wardrobe admin open <player>` | Opens another online player's wardrobe GUI. | `ruinedwardrobe.admin` |
| `/wardrobe admin setslots <player> <amount>` | Sets admin bonus slots. | `ruinedwardrobe.admin` |
| `/wardrobe admin clearslots <player>` | Resets admin bonus slots to `0`. | `ruinedwardrobe.admin` |

## Core Nodes

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

`ruinedwardrobe.admin.*` includes admin access plus doctor, reload, and migrate command nodes.

`ruinedwardrobe.command.*` includes the command nodes, but it does not grant `ruinedwardrobe.admin`.

## Slot Nodes

```text
ruinedwardrobe.slots.<amount>
```

Examples:

```text
ruinedwardrobe.slots.3
ruinedwardrobe.slots.6
ruinedwardrobe.slots.9
ruinedwardrobe.slots.15
```

The highest assigned slot tier wins, then admin bonus slots are added, then `wardrobe.max-slots-cap` is enforced.

## Bypass Nodes

| Permission | Use |
| --- | --- |
| `ruinedwardrobe.bypass.cooldown` | Ignores equip cooldown. |
| `ruinedwardrobe.bypass.restrictions` | Ignores world, gamemode, and placeholder restrictions. |
| `ruinedwardrobe.bypass.emptycheck` | Allows equip when armor slots would normally need to be empty. |
| `ruinedwardrobe.bypass.combat` | Ignores combat restriction checks. |

Keep bypass nodes off normal ranks. They are best for staff, testing, and support cases.

## Suggested Rank Layout

| Rank | Nodes |
| --- | --- |
| Default | `ruinedwardrobe.use`, `ruinedwardrobe.command.help`, `ruinedwardrobe.command.list`, `ruinedwardrobe.slots.3` |
| VIP | `ruinedwardrobe.slots.6` |
| MVP | `ruinedwardrobe.slots.9` |
| Legend | `ruinedwardrobe.slots.15` |
| Staff | Staff command nodes plus carefully chosen bypass nodes |

## LuckPerms Examples

Default players:

```text
/lp group default permission set ruinedwardrobe.use true
/lp group default permission set ruinedwardrobe.command.help true
/lp group default permission set ruinedwardrobe.command.list true
/lp group default permission set ruinedwardrobe.slots.3 true
```

VIP slot tier:

```text
/lp group vip permission set ruinedwardrobe.slots.6 true
```

Admin tools:

```text
/lp group admin permission set ruinedwardrobe.admin.* true
/lp group admin permission set ruinedwardrobe.command.* true
/lp group admin permission set ruinedwardrobe.bypass.cooldown true
```

## Common Permission Fixes

| Problem | Check |
| --- | --- |
| Player cannot open `/wardrobe` | They need `ruinedwardrobe.use`. |
| Player sees too few slots | Check their highest `ruinedwardrobe.slots.<amount>` node and `wardrobe.max-slots-cap`. |
| Staff cannot run `/wardrobe doctor` | Add `ruinedwardrobe.command.doctor` or `ruinedwardrobe.command.*`. |
| Staff cannot open another player's wardrobe | Add `ruinedwardrobe.admin` or `ruinedwardrobe.admin.*`. |
| Player bypasses world/combat rules | Remove bypass nodes from that rank. |
