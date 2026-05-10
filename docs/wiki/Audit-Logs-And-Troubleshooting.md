# Audit Logs And Troubleshooting

When a player says armor disappeared, duplicated, failed to save, or changed after death, start with the audit log.

Default path:

```text
plugins/RuinedWardrobe/logs/wardrobe-audit-YYYY-MM-DD.log
```

The audit log is separate from console. It is meant to answer one question: what did RuinedWardrobe do around this player's wardrobe items?

## Log Format

Audit lines are timestamped and key-value based:

```text
2026-05-09 19:34:11.123 action=EQUIP playerId=... player="Rique" slot=2 name="Netherite"
```

Search by player name, UUID, action, slot, or the approximate timestamp.

## Useful Actions

| Action | Meaning |
| --- | --- |
| `AUDIT_START` | Audit logging started. |
| `SAVE_SET` | A set was saved. |
| `EQUIP` | A saved set was equipped. |
| `UNEQUIP` | The active wardrobe set was removed. |
| `DELETE_SET` | A saved set was deleted. |
| `RENAME_SET` | A saved set was renamed. |
| `ARMOR_PLACE` | Armor was placed into a wardrobe slot. |
| `ARMOR_TAKE` | Armor was taken from a wardrobe slot. |
| `DEATH_VANILLA_LOSS` | Vanilla-style death loss happened. |
| `DEATH_PRESERVE` | Protected death behavior kept the saved set. |
| `FOREIGN_BOUND_ARMOR_REMOVED` | Armor bound to another owner was removed. |
| `ITEM_DESERIALIZE_ERROR` | Stored item data could not be read. |
| `ARMOR_SYNC_ERROR` | A background armor sync failed. |
| `SAVE_SET_ERROR` | Saving failed. |
| `EQUIP_ERROR` | Equipping failed. |
| `DELETE_SET_ERROR` | Deleting failed. |
| `EQUIP_DENIED` | Equip was blocked by permissions, cooldown, restrictions, or state. |
| `DELETE_DENIED` | Delete was blocked. |
| `ARMOR_EDIT_DENIED` | Editing a wardrobe piece was blocked. |

## Missing Armor Runbook

1. Ask for the player name, approximate time, slot number, and what they clicked.
2. Open the audit log for that date.
3. Search for the player name or UUID.
4. Find the last `SAVE_SET`, `EQUIP`, `UNEQUIP`, `ARMOR_TAKE`, `DELETE_SET`, or death action.
5. Check for `ITEM_DESERIALIZE_ERROR`.
6. Check for `FOREIGN_BOUND_ARMOR_REMOVED`.
7. Check console around the same timestamp.
8. Run `/wardrobe doctor` if storage or queue behavior looks wrong.

## Quick Diagnosis Table

| Finding | What it usually means |
| --- | --- |
| `EQUIP_DENIED` with empty/nothing saved message | The player clicked a slot with no saved armor. |
| `DEATH_VANILLA_LOSS` | keepInventory was off and `death.keep-wardrobe-on-death` was false. This is expected. |
| `DEATH_PRESERVE` | The plugin removed wardrobe armor from drops and kept the saved set. |
| `ITEM_DESERIALIZE_ERROR` | Stored item data could not be read after item/server changes or DB corruption. |
| `FOREIGN_BOUND_ARMOR_REMOVED` | The plugin found bound armor owned by another UUID and removed it. |
| Queue depth high in `/wardrobe doctor` | DB writes are backing up. Check DB health and pool/worker sizing. |
| DB probe failed | Storage is unreachable or credentials/settings are wrong. |

## When Players Cannot Equip

Check these in order:

1. Permission: `ruinedwardrobe.use`
2. Slot tier: `ruinedwardrobe.slots.<amount>`
3. Cooldown: `wardrobe.equip-cooldown-seconds`
4. Restrictions: world, gamemode, combat, PlaceholderAPI rules
5. Empty armor requirement
6. Whether the target slot actually has saved armor
7. Whether another plugin cancelled the equip event

## Performance Notes

Audit writes use an async queue. If disk stalls and the queue fills, RuinedWardrobe drops new audit lines instead of freezing the server.

For very large servers:

- Keep `audit.log-successful-syncs: false`.
- Raise `audit.queue-size` if many wardrobe actions happen at once.
- Keep audit logs on reliable local storage.
- Rotate or archive old log files with your normal server backup process.

## Bug Report Bundle

Send these when reporting a real issue:

- `wardrobe-audit-YYYY-MM-DD.log`
- Server console around the same timestamp
- `config.yml`
- `gui.yml` if the issue involves clicking or layout
- Plugin jar version
- Paper or Folia build
- Java version
- Exact player action sequence
