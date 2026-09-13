# Stage 05

## Goal
Implement Stage 17 PlaceholderAPI and player GUI workflows.

## Implemented Systems
- PlaceholderAPI expansion.
- Cached sell/booster/stat/leaderboard placeholders.
- Player menus for main workflow, mines, progression, selling, boosters, statistics, and leaderboards.
- RC6 Stage 3 admin editors for rank, prestige, sell-price, booster, Block Event, reset-settings, and mine-composition configuration.

## Important Files
- `RelicPrisonExpansion.java`
- `PrisonGuiManager.java`
- `AdminGuiEditorService.java`
- `src/main/resources/guis/*.yml`

## Database Migrations
- None specific beyond statistics/progression tables.

## Config Migrations
- GUI defaults and placeholder cache settings.

## Commands
- `/prison`
- `/mine`
- `/rankup`
- `/rankupmax`
- `/prestige`
- `/sellall`
- `/sellhand`
- `/stats`
- `/leaderboard`

## Permissions
- player workflow permissions from `plugin.yml`.

## Tests
- `PhaseFiveSixSourceTest`
- parser/config tests where applicable.
- `AdminGuiEditorServiceTest`

## Exit Requirements
- Placeholder evaluation must not synchronously query SQL.

## Actual Verification Status
- Automated guardrails exist; real scoreboard/PAPI refresh tests remain staging.

## Remaining Limitations
- Bedrock/Geyser usability requires manual client testing.
- The approved corrected 33-mine packaged `mines.yml` asset remains unavailable and must be supplied before final public release.
