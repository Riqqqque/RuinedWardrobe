# PrismWardrobe Permissions

## Base
- `prismwardrobe.use`: Access `/wardrobe` and `/pw`.
- `prismwardrobe.command.help`: Use `/wardrobe help`.
- `prismwardrobe.command.list`: Use `/wardrobe list [player]`.
- `prismwardrobe.command.doctor`: Use `/wardrobe doctor`.
- `prismwardrobe.command.reload`: Use `/wardrobe reload`.
- `prismwardrobe.command.migrate`: Use `/wardrobe migrate ...`.

## Admin
- `prismwardrobe.admin`: Access admin subcommands.
- `prismwardrobe.admin.*`: Includes `prismwardrobe.admin` and command admin nodes.
- `prismwardrobe.command.*`: Includes all non-admin command nodes.

## Slot Tiers
- `prismwardrobe.slots.<n>`: Sets slot cap tier by highest value node on player.
- Example: `prismwardrobe.slots.8`, `prismwardrobe.slots.16`.

## Bypass
- `prismwardrobe.bypass.cooldown`
- `prismwardrobe.bypass.restrictions`
- `prismwardrobe.bypass.emptycheck`
- `prismwardrobe.bypass.combat`

## Optional
- `prismwardrobe.notify.update`
- `prismwardrobe.api.listen`
