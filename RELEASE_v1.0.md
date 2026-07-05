# PvPRating v1.0

Release for Minecraft 1.20.1 / Forge 47.x.

## Highlights

- Hardened rating writes against invalid numeric values such as `NaN`, infinity and overflow.
- Added safer audit-log JSON escaping.
- Added atomic offline playerdata writes with backup fallback.
- Added bounded spawn-kill protection tracking to avoid unbounded pair state growth.
- Added safer leaderboard/name suggestions with limits for command suggestions.
- Delayed task failures are logged instead of being allowed to crash the server.
- Command blocks and other non-player command sources are blocked from PvPRating admin commands by default.
- Added `/pvprating system command-blocks [true|false]` to explicitly enable command-block admin access when a server owner wants it.
- Changed the default `Rating Loss On Death` value from `1.0` to `0.0`; by default, death loss now comes only from `targetMultiplier`.
- Added unit tests for sanitizer, spawn-kill tracking, audit JSON, admin command access and player-name suggestions.

## New or Changed Settings

- `allowCommandBlockAdminCommands = false`
- `"Rating Loss On Death" = 0.0`

Existing generated `pvprating-server.toml` files keep their current values. To use the new defaults, update the existing config manually or regenerate it after stopping the server.

## Artifact

Upload the jar from `build/libs` after building:

```text
pvprating-1.0-&-1.20.1.jar
```
