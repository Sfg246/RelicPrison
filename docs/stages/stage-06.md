# Stage 06

## Goal
Implement Stage 18 leaderboards and competitive rewards.

## Implemented Systems
- Async leaderboard refresh and cached snapshots.
- Rank/prestige ordering from configured order.
- Period calculations.
- Reward period reservation and reward ledger rows.
- Resumable finalization for non-finalized periods with matching frozen standings snapshots.

## Important Files
- `StatisticsServiceImpl.java`
- `LeaderboardRewardService.java`
- `LeaderboardRewardRepository.java`
- `LeaderboardPeriods.java`

## Database Migrations
- `rp_leaderboard_reward_periods`
- `rp_leaderboard_reward_ledger`
- `rp_leaderboard_winner_snapshots`

## Config Migrations
- `leaderboards.yml`
- `leaderboard-rewards.yml`

## Commands
- `/leaderboard`
- `/rp rewards preview`
- `/rp rewards finalize`
- `/rp rewards history`
- `/rp rewards retry`
- `/rp rewards pending`

## Permissions
- `relicprison.admin`

## Tests
- `LeaderboardPeriodsTest`
- `LeaderboardRewardConfigTest`
- `LeaderboardRewardRepositoryTest`

## Exit Requirements
- Period rewards finalize once and do not query full database per placeholder.

## Actual Verification Status
- Period math, duplicate reservation, RC6 Stage 2 package verification recovery, and resumable finalization are automated at repository level. Real restart staging remains required.

## Remaining Limitations
- Leaderboard rewards use deterministic package IDs and the shared reward component ledger. Real rollover, offline winner, and restart-during-finalization staging remain required.
