# Stages 7-10 Completion Matrix

## Stage 7: Mine reset engine

Completed:

- Manual, warning-delayed, timed, and mined-percentage reset requests
- One global reset queue with configurable concurrent-mine limit
- One controlled synchronous slice per active mine per tick
- Adaptive per-reset block budget constrained by a nanosecond time target
- Server-MSPT pause/resume thresholds
- Chunk-grouped bottom-up or top-down traversal, including negative coordinates
- Direct `World#getBlockAt(x,y,z)` and `Block#setType(material,false)` vanilla placement
- Precompiled mine-composition sampling with no per-block material-name parsing
- No custom block wrapper or Bukkit `Location` allocation in the placement loop
- Warning, queue, prepare, reset, complete, and failed runtime states
- Player evacuation with invalid/inside-region spawn fallback
- Mine-local, radius, global, or disabled reset notifications
- Chat, title, action-bar, and sound notification controls
- Before/start/complete/failed console command hooks
- Runtime persistence: remaining blocks, reset count, timestamps, duration, state
- Startup recovery for interrupted warning/queued/preparing/resetting/completing states
- Configurable failed-reset retry delay
- Manual pending-reset cancellation
- Update/delete protection while a reset is pending or active
- Public reset lifecycle events and read-only reset API

Custom provider placement is intentionally rejected with a clear failure until Stage 15.

## Stage 8: Mine access, teleporting, and WorldGuard

Completed:

- Rank-based mine entry, teleport, and mining checks
- Optional access to all previously unlocked A-Z mines
- Independent prestige-mine requirement checks
- Optional per-mine permission requirement
- Staff access, build, warmup, and evacuation bypass permissions
- Block-break denial in locked mines
- Block-place denial inside registered mines
- Entry enforcement when walking into a locked mine
- `/mine` current-rank teleport and `/mine <id>` selected teleport
- Configurable teleport warmup
- Movement and damage cancellation
- Destination world, coordinate, height, access, and teleport-result validation
- Optional WorldGuard region create/update/delete integration
- Configurable WorldGuard block-place, block-break, explosion, fire, and fluid flags
- RelicPrison remains the authority for rank access; WorldGuard is general region protection only

## Stage 9: A-Z ranks and Rankup

Completed:

- Ordered rank definitions loaded from `ranks.yml`
- Configurable display name, cost-to-next, linked mine, LuckPerms group, enter commands, and leave commands
- `/rankup`
- `/rankupmax` with one balance read, one cumulative calculation, one Vault withdrawal, and one final profile update
- Prestige rank-cost multiplier support
- Cancellable public rankup event
- Database-first progression source of truth
- Refund attempt if the profile save fails after withdrawal
- One direct LuckPerms rank group per player
- Lower-rank inheritance chain synchronization
- Permission repair when LuckPerms update fails
- Console/player command hooks and bounded delayed command hooks
- Rankup messages, titles, sounds, and optional broadcasts
- Admin rank info, set, promote, and demote commands

## Stage 10: Prestige

Completed:

- Coal, Iron, Gold, Redstone, Diamond, Emerald, and Immortal definitions from `prestiges.yml`
- Rank Z requirement checked before confirmation and again during the transaction
- Text confirmation with expiration
- Prestige cost withdrawal through Vault
- Rank reset to the configured starting rank
- One direct prestige group plus one direct rank group
- Prestige inheritance synchronization
- Cancellable public prestige event
- Refund attempt if profile persistence fails after withdrawal
- Prestige enter/leave command hooks
- Titles, sounds, messages, and optional broadcasts
- Admin prestige info, set, promote, demote, and clear-to-none support
- Repair-on-join for invalid profile values and incorrect LuckPerms groups
- Optional first-join starter commands and starting-mine teleport

## Stage 1-10 integration hardening

Completed:

- Rank, prestige, mine requirements, starting rank, and LuckPerms group uniqueness are validated together
- Config reload can safely change group formats by previewing rank/prestige definitions before applying
- Progression definition reload repairs all cached profiles
- Mine reload reconciles runtime rows and WorldGuard regions
- Offline admin profile loads are saved and evicted after use
- Future Stage 11+ feature toggles default to disabled in this package

## Not included yet

- SellAll and AutoSell
- Sell multipliers and booster items
- AutoPickup, AutoSmelt, AutoBlock, Fortune, custom drops, durability, and mining XP
- AdvancedEnchantments bulk-block integration
- ItemsAdder custom block placement
- Full PlaceholderAPI implementation
- Complete player/admin progression GUIs
- Statistics and leaderboards
- Backups, diagnostics, migration, and production rollout tooling
