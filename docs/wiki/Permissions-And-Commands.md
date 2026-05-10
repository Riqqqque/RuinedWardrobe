# Permissions And Commands

RuinedWardrobe uses small, explicit permission nodes: one base node for players, separate command nodes for staff tools, slot tier nodes for ranks, and bypass nodes for trusted support cases.

> [!TIP]
> Give normal ranks one slot tier node. The highest `ruinedwardrobe.slots.<amount>` value wins.

## Command Reference

| Command | Purpose | Permission |
| --- | --- | --- |
| `/wardrobe` | Opens the player's wardrobe GUI. | `ruinedwardrobe.use` |
| `/rw` | Alias for `/wardrobe`. | `ruinedwardrobe.use` |
| `/wardrobe help` | Shows available commands. | `ruinedwardrobe.command.help` |
| `/wardrobe list [player]` | Lists saved sets for yourself or a known player. | `ruinedwardrobe.command.list` |
| `/wardrobe doctor` | Shows runtime diagnostics. | `ruinedwardrobe.command.doctor` |
| `/wardrobe reload` | Reloads config, GUI, and language files. | `ruinedwardrobe.command.reload` |
| `/wardrobe migrate <sqlite\|mysql> [--dry-run] [--force]` | Migrates storage with backup and digest verification. | `ruinedwardrobe.command.migrate` |
| `/wardrobe admin open <player>` | Opens another online player's wardrobe GUI. | `ruinedwardrobe.admin` |
| `/wardrobe admin setslots <player> <amount>` | Adds admin bonus slots. | `ruinedwardrobe.admin` |
| `/wardrobe admin clearslots <player>` | Resets admin bonus slots to `0`. | `ruinedwardrobe.admin` |

## Core Nodes

```text
ruinedwardrobe.use
ruinedwardrobe.command.help
ruinedwardrobe.command.list
ruinedwardrobe.command.doctor
ruinedwardrobe.command.reload
ruinedwardrobe.command.migrate
ruinedwardrobe.command.*
ruinedwardrobe.admin
ruinedwardrobe.admin.*
```

| Node | Grants |
| --- | --- |
| `ruinedwardrobe.use` | Base wardrobe access. |
| `ruinedwardrobe.command.*` | Non-admin command nodes. It does not grant `ruinedwardrobe.admin`. |
| `ruinedwardrobe.admin` | Admin subcommands such as open and slot adjustment. |
| `ruinedwardrobe.admin.*` | Admin access plus non-admin command nodes. |

## Slot Nodes

```text
ruinedwardrobe.slots.<amount>
```

Good rank examples:

```text
ruinedwardrobe.slots.3
ruinedwardrobe.slots.6
ruinedwardrobe.slots.9
ruinedwardrobe.slots.15
```

Effective slots are calculated like this:

```text
min(max-slots-cap, max(default-slots, highest ruinedwardrobe.slots.<amount>) + admin bonus slots)
```

## Bypass Nodes

| Permission | Use |
| --- | --- |
| `ruinedwardrobe.bypass.cooldown` | Ignores equip cooldown. |
| `ruinedwardrobe.bypass.restrictions` | Ignores world, gamemode, and PlaceholderAPI restrictions. |
| `ruinedwardrobe.bypass.emptycheck` | Allows equip when armor slots would normally need to be empty. |
| `ruinedwardrobe.bypass.combat` | Ignores combat restriction checks. |

Keep bypass nodes off normal ranks. They are for staff, testing, and support recovery.

## Recommended Rank Layout

| Rank | Nodes |
| --- | --- |
| Default | `ruinedwardrobe.use`, `ruinedwardrobe.command.help`, `ruinedwardrobe.command.list`, `ruinedwardrobe.slots.3` |
| VIP | `ruinedwardrobe.slots.6` |
| MVP | `ruinedwardrobe.slots.9` |
| Legend | `ruinedwardrobe.slots.15` |
| Moderator | `ruinedwardrobe.command.doctor`, selected bypass nodes if needed |
| Admin | `ruinedwardrobe.admin.*` |

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
/lp group admin permission set ruinedwardrobe.bypass.cooldown true
```

Temporary support slots:

```text
/wardrobe admin setslots <player> 5
/wardrobe admin clearslots <player>
```

## Hardening Checklist

- Keep migration permission staff-only.
- Keep bypass permissions off paid or default ranks.
- Avoid assigning multiple slot tier nodes to one rank unless intentional.
- Test with a normal player after every permission change.
- Use `/wardrobe list <player>` before editing a player's wardrobe manually.

## Common Permission Fixes

| Problem | Check |
| --- | --- |
| Player cannot open `/wardrobe` | Add `ruinedwardrobe.use`. |
| Player sees too few slots | Check highest slot node, admin bonus slots, and `wardrobe.max-slots-cap`. |
| Staff cannot run `/wardrobe doctor` | Add `ruinedwardrobe.command.doctor` or `ruinedwardrobe.command.*`. |
| Staff cannot open another player's wardrobe | Add `ruinedwardrobe.admin` or `ruinedwardrobe.admin.*`. |
| Player bypasses world or combat rules | Remove bypass nodes from that rank. |

## Related Pages

- [Configuration](Configuration.md)
- [Quick Start](Quick-Start.md)
- [Audit Logs And Troubleshooting](Audit-Logs-And-Troubleshooting.md)
