# Stage 03

## Goal
Harden progression, economy, XP, AutoBlock, statistics, and provider boundaries.

## Implemented Systems
- Progression transaction records.
- Vault withdrawal intent and ambiguity states.
- Mining XP configuration.
- Buffered statistics.
- AutoBlock remainder and rollback helpers.

## Important Files
- `ProgressionServiceImpl.java`
- `ProgressionTransactionRepository.java`
- `MiningServiceImpl.java`
- `StatisticsServiceImpl.java`
- `InventoryAutoBlockConverter.java`

## Database Migrations
- `rp_progression_transactions`
- mining statistics tables.

## Config Migrations
- mining XP, bulk limits, AutoBlock, progression defaults.

## Commands
- `/rankup`
- `/rankupmax`
- `/prestige`
- `/rp progression`

## Permissions
- progression and admin repair permissions in `plugin.yml`.

## Tests
- `ProgressionTransactionRepositoryTest`
- `InventoryAutoBlockConverterTest`
- `DropTransformerTest`

## Exit Requirements
- No known automatic double-charge path; ambiguous Vault outcomes must require staff review.

## Actual Verification Status
- Repository state behavior and refund-claim duplicate protection are tested.
- RC6 Stage 1 bulk mining persists a bounded transaction row with block snapshots before mutation, routes command rewards through the reward ledger, defers AutoSell statistic recording until successful commit, and exposes incomplete transaction diagnostics.
- Real Vault failure and crash-window tests remain manual/staging.

## Remaining Limitations
- Generic Vault reconciliation cannot be automated without an economy-specific transaction-history API.
