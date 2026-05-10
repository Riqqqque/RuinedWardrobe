# Ruined Ecosystem Parity

RuinedWardrobe belongs in the same ecosystem as [RuinedCollections](https://github.com/Riqqqque/RuinedCollections). Use this page as a quick compatibility checklist when changing public behavior, docs, or operational workflows.

## RuinedCollections Reference Points

| Area | RuinedCollections pattern | RuinedWardrobe parity target |
| --- | --- | --- |
| Ownership | Author is `Rique`; plugin is free for server use with no resale. | Keep author/license language aligned. |
| Commands | Short player command plus admin command family with strong tab completion. | Keep `/wardrobe` and `/rw` predictable, tab-complete flags and targets, and avoid silent unknown options. |
| Permissions | Explicit nodes for player access, admin actions, diagnostics, import/export or migration, and views. | Keep permission nodes small, documented, and staff-only for risky operations. |
| Storage | SQLite for simple installs, MySQL/MariaDB for larger servers, SQL schema versioning, export/import safety. | Keep SQLite/MySQL guidance, backups, migration verification, and target overwrite safety clear. |
| Diagnostics | Dedicated log file, debug categories, console warning mirroring, in-game status/tail commands. | Keep `/wardrobe doctor` and audit logs useful enough for live support; consider diagnostics-tail parity if added later. |
| Placeholders | PlaceholderAPI identifier uses the plugin name and cached online data; leaderboard output is cached. | Keep `%ruinedwardrobe_*%` placeholders cached, documented, and explicit about offline behavior. |
| Docs | Wiki-first server-owner documentation with install, configuration, commands, storage, diagnostics, troubleshooting, and playbook pages. | Keep wiki pages operational, checklist-based, and aligned in tone. |
| Safety | Validate before change, export before import, backup before update, avoid duplicate reward payouts. | Keep dry-runs, backups, digest checks, audit logs, and anti-dupe protections prominent. |

## Compatibility Rules

1. Keep Ruined plugin names, authorship, and no-resale license wording consistent.
2. Prefer explicit commands and permissions over hidden behavior.
3. Treat storage changes as high-risk: dry-run, backup, verify, and document rollback.
4. Keep admin workflows copyable from docs.
5. Keep PlaceholderAPI behavior cache-aware and documented.
6. Avoid adding dependencies that conflict with RuinedCollections on the same server.
7. When both plugins expose similar concepts, use matching vocabulary: diagnostics, storage, placeholders, reload, validate/check, export/import/migrate, backup, troubleshooting.

## Current Difference Notes

- RuinedCollections targets Paper `1.21+` and Java `21+`; RuinedWardrobe targets Paper API `1.21`, Java `21+`, and Paper/Folia runtime versions `1.21` through `26.1.2`.
- RuinedCollections uses Maven and produces `target/RuinedCollections.jar`; RuinedWardrobe uses Gradle and produces `build/libs/RuinedWardrobe-1.0.3.jar`.
- RuinedCollections has export/import workflows; RuinedWardrobe has direct storage migration with dry-run, backup, force, and digest verification.
- RuinedCollections diagnostics are a rotating `diagnostics.log`; RuinedWardrobe uses `/wardrobe doctor` plus dated wardrobe audit logs.

These differences are fine. Preserve them unless there is a clear reason to converge.
