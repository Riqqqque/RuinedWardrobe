# RuinedWardrobe Permissions

## Base
- `ruinedwardrobe.use`: Access `/wardrobe` and `/rw`.
- `ruinedwardrobe.command.help`: Use `/wardrobe help`.
- `ruinedwardrobe.command.list`: Use `/wardrobe list [player]`.
- `ruinedwardrobe.command.doctor`: Use `/wardrobe doctor`.
- `ruinedwardrobe.command.reload`: Use `/wardrobe reload`.
- `ruinedwardrobe.command.migrate`: Use `/wardrobe migrate ...`.

## Admin
- `ruinedwardrobe.admin`: Access admin subcommands.
- `ruinedwardrobe.admin.*`: Includes `ruinedwardrobe.admin` and command admin nodes.
- `ruinedwardrobe.command.*`: Includes all non-admin command nodes.

## Slot Tiers
- `ruinedwardrobe.slots.<n>`: Sets slot cap tier by highest value node on player.
- Example: `ruinedwardrobe.slots.8`, `ruinedwardrobe.slots.16`.

## Bypass
- `ruinedwardrobe.bypass.cooldown`
- `ruinedwardrobe.bypass.restrictions`
- `ruinedwardrobe.bypass.emptycheck`
- `ruinedwardrobe.bypass.combat`

## Optional
- `ruinedwardrobe.notify.update`
- `ruinedwardrobe.api.listen`
