# Stage 17

## Goal
Official Stage 17 PlaceholderAPI and GUI system.

## Implemented Systems
- PlaceholderAPI expansion with progression, mine, selling, booster, statistics, and leaderboard placeholders.
- Player GUIs for primary workflows.
- Admin GUI editors for ranks, prestiges, sell prices, boosters, Block Events, reset settings, and mine composition.

## Important Files
- `RelicPrisonExpansion.java`
- `PrisonGuiManager.java`
- `AdminGuiEditorService.java`
- `src/main/resources/guis/admin.yml`

## Database Migrations
- none specific.

## Config Migrations
- GUI defaults and placeholder formatting settings.

## Commands
- Command alternatives exist for primary player workflows and important admin editor actions.

## Permissions
- player GUI and admin permissions in `plugin.yml`.

## Tests
- placeholder and GUI guardrail tests.
- `AdminGuiEditorServiceTest`

## Exit Requirements
- Every important GUI action has a command alternative.
- Admin editor saves must validate candidate configuration, reject stale edits, save atomically, roll back on reload failure, and audit staff actions.

## Actual Verification Status
- RC6 Stage 3 behavioral service tests cover all seven admin editors. Java/Bedrock GUI staging remains pending.

## Remaining Limitations
- Real Paper Java and Bedrock/Geyser click/input workflows remain staging-required before public `1.0.0`.
