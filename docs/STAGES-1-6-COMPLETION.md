# Stages 1-6 Completion Matrix

## Stage 1: Repository and build foundation

Completed:

- Maven project (`pom.xml`)
- Java 21 release target
- Paper 1.21.10 API dependency with `provided` scope
- Plugin metadata, permissions, commands, structured package layout, and logging
- Maven verification plugins for Java version, compilation, unit tests, Checkstyle, JAR creation, and source JAR creation
- Maven bootstrap scripts for Linux/macOS and Windows
- GitHub Actions Maven verification workflow

## Stage 2: Core services and API structure

Completed:

- Public `RelicPrisonApi`
- Public mine and player-data services
- Read-only API models for mines and player profiles
- Service contracts reserved for reset, access, rank, prestige, progression, selling, multipliers, boosters, mining, statistics, backups, and diagnostics
- Bukkit `ServicesManager` registration
- Mine lifecycle events used by the implemented mine system
- Stable event contracts for later reset, rank, prestige, sell, booster, and mining stages
- Internal services separated from commands, storage, GUIs, and integrations

Later-stage service contracts are intentionally interfaces only. Their gameplay implementations begin in Stage 7 and later.

## Stage 3: Configuration, messages, and formatting

Completed:

- Validated immutable configuration snapshots
- Safe per-module reloads
- Atomic `all` reload: configuration, messages, mines, and mine GUI are parsed before any are applied
- Validation of reserved later-stage YAML files so malformed files fail early
- Lightweight editable messages with legacy and hex colors
- Feature toggles for every approved later system
- Full, abbreviated, currency, percentage, and progress-bar formatting
- Storage changes detected during reload and marked restart-required
- Config-driven mine administration GUI settings

## Stage 4: Player data and database foundation

Completed:

- UUID-based player profiles
- SQLite and MySQL/MariaDB configuration
- Asynchronous single-writer database queue
- Lightweight connection pool
- Race-safe dirty revision tracking
- Race-safe disconnect handling when a profile load is still in flight
- Periodic batched profile flushes
- Join load, quit save, already-online player load, and shutdown flush
- Schema tables reserved for profiles, boosters, mine runtime, and migration history
- SQLite WAL and busy-timeout settings
- Database remains the planned progression source of truth

Progression, booster, and statistics gameplay is intentionally implemented in later stages, while the durable profile fields and schema foundation already exist.

## Stage 5: Mine creation and management

Completed:

- Internal two-corner selection wand using PersistentDataContainer identity
- Optional WorldEdit/FAWE selection adapter through reflection
- Create, resize, move, delete, rename, sort, enable, disable, info, list, requirement, setspawn, teleport, and GUI commands
- Particle-only cuboid outline before confirmation
- Adaptive particle spacing and hard particle caps for very large selections
- Confirm/cancel workflow
- Per-admin isolated sessions
- Timeout, disconnect, cancel, confirm, and shutdown cleanup
- World-height and cross-world selection validation
- Overlapping-mine validation
- Atomic mine YAML writes with rollback on save failure
- Normalized coordinates plus world UUID and name storage
- Mine display names, sort order, rank/prestige requirement fields, enabled state, and optional spawn
- Protected, config-driven administration GUI with click/drag/shift-click safeguards
- Public mine create/update/delete events

## Stage 6: Mine compositions

Completed:

- Weighted vanilla and namespaced custom block references
- Validation for invalid blocks, duplicate entries, zero/negative/nonfinite weights, and empty compositions
- Precompiled cumulative-weight arrays
- Binary-search sampling with no material-name parsing in the hot selection path
- Set/add, remove, normalize, copy, list, reload, effective-percentage display, and persistence support
- Vanilla block validation now; custom provider placement is deferred to the ItemsAdder stage

## Stage 0 audit completion

The source package also contains:

- Exact live-stack audit for the installed Prison build
- Mine, rank, player, prestige, sell-price, and LuckPerms inventories
- Legacy-to-RelicPrison data map
- Recorded migration risks and source-of-truth decisions

## Not included yet

This Stage 6 JAR does not reset mines, rank players up, prestige, sell items, process mining drops, synchronize LuckPerms progression, integrate AdvancedEnchantments/ItemsAdder gameplay, or migrate live player data. Those systems begin at Stage 7 and later. This is deliberate: Stages 0-6 are the audited, storage-backed mine-definition foundation, not a disguised incomplete live replacement.
