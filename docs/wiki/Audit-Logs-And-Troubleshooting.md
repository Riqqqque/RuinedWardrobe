# Audit Logs And Troubleshooting

## Audit Log Location

Default:

```text
plugins/RuinedWardrobe/logs/wardrobe-audit-YYYY-MM-DD.log
```

This file is separate from console logs. It is meant to answer: what did the plugin do to this player's wardrobe items?

## Useful Actions

Normal player actions:

```text
ARMOR_PLACE
ARMOR_TAKE
SAVE_SET
EQUIP
UNEQUIP
DELETE_SET
RENAME_SET
```

Death handling:

```text
DEATH_VANILLA_LOSS
DEATH_PRESERVE
DEATH_PRESERVE_SYNC_ERROR
```

Safety and corruption signals:

```text
FOREIGN_BOUND_ARMOR_REMOVED
ITEM_DESERIALIZE_ERROR
ARMOR_SYNC_ERROR
SAVE_SET_ERROR
EQUIP_ERROR
DELETE_SET_ERROR
```

Denied actions:

```text
EQUIP_DENIED
DELETE_DENIED
ARMOR_EDIT_DENIED
```

## How To Investigate Missing Armor

1. Ask for the player name, approximate time, and what they clicked.
2. Open the audit log for that day.
3. Search for the player UUID or player name.
4. Check the last `EQUIP`, `UNEQUIP`, `ARMOR_TAKE`, `DELETE_SET`, or death action.
5. Check for `ITEM_DESERIALIZE_ERROR`.
6. Check for `FOREIGN_BOUND_ARMOR_REMOVED`, which means bound armor from another owner was removed.
7. Check normal server console around the same timestamp.

## Common Findings

`EQUIP_DENIED` with `DENIED_NOTHING_SAVED`:

The player clicked an empty slot.

`DEATH_VANILLA_LOSS`:

keepInventory was off and `death.keep-wardrobe-on-death` was false. This is expected vanilla-style behavior.

`DEATH_PRESERVE`:

The plugin removed bound armor from death drops and kept the saved wardrobe slot.

`ITEM_DESERIALIZE_ERROR`:

Stored item data could not be read. This can happen after incompatible server/item changes or corrupted database rows.

`FOREIGN_BOUND_ARMOR_REMOVED`:

The plugin found bound armor owned by another UUID and removed it to prevent abuse.

## Performance Notes

Audit logs are written through an async queue. If disk stalls and the queue fills, RuinedWardrobe drops audit lines instead of freezing the server.

For very large servers:

- Keep `audit.log-successful-syncs: false`.
- Raise `audit.queue-size` if players perform many wardrobe actions at once.
- Keep audit logs on reliable local storage.

## What To Send With A Bug Report

Send:

- `wardrobe-audit-YYYY-MM-DD.log`
- server console around the same timestamp
- `config.yml`
- plugin jar version
- Paper/Folia build
- Java version
- exact player action sequence
