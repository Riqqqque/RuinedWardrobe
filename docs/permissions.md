# FluxWardrobe Permissions

## Base
- `fluxwardrobe.use`: Access `/wardrobe` and `/fw`.
- `fluxwardrobe.command.help`: Use `/wardrobe help`.
- `fluxwardrobe.command.list`: Use `/wardrobe list [player]`.
- `fluxwardrobe.command.doctor`: Use `/wardrobe doctor`.
- `fluxwardrobe.command.reload`: Use `/wardrobe reload`.
- `fluxwardrobe.command.migrate`: Use `/wardrobe migrate ...`.

## Admin
- `fluxwardrobe.admin`: Access admin subcommands.
- `fluxwardrobe.admin.*`: Includes `fluxwardrobe.admin` and command admin nodes.
- `fluxwardrobe.command.*`: Includes all non-admin command nodes.

## Slot Tiers
- `fluxwardrobe.slots.<n>`: Sets slot cap tier by highest value node on player.
- Example: `fluxwardrobe.slots.8`, `fluxwardrobe.slots.16`.

## Bypass
- `fluxwardrobe.bypass.cooldown`
- `fluxwardrobe.bypass.restrictions`
- `fluxwardrobe.bypass.emptycheck`
- `fluxwardrobe.bypass.combat`

## Optional
- `fluxwardrobe.notify.update`
- `fluxwardrobe.api.listen`
