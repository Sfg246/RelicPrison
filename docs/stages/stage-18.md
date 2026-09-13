# Stage 18

## Goal
Official Stage 18 leaderboards and competitive rewards.

## Implemented Systems
- Async/cached leaderboards.
- Daily/weekly/monthly/season period utilities.
- Atomic period reservation guard.
- Resumable finalization and deterministic winner reward package IDs.
- Reward history and pending/offline retry commands.

## Important Files
- `StatisticsServiceImpl.java`
- `LeaderboardRewardService.java`
- `LeaderboardRewardRepository.java`

## Database Migrations
- `rp_leaderboard_reward_periods`
- `rp_leaderboard_reward_ledger`
- `rp_leaderboard_winner_snapshots`

## Config Migrations
- `leaderboards.yml`
- `leaderboard-rewards.yml`

## Commands
- `/leaderboard`
- `/rp rewards preview|finalize|history|pending|retry`

## Permissions
- `relicprison.admin`

## Tests
- `LeaderboardPeriodsTest`
- `LeaderboardRewardRepositoryTest`

## Exit Requirements
- RC6 Stage 2 finalization persists or resumes winner snapshots, reward rows, packages, and components before marking periods finalized.

## Actual Verification Status
- Atomic finalization, duplicate reward-row tests, incomplete-period recovery, package-existence verification, and legacy recoverable-period tests exist.
- RC6 Stage 2 creates deterministic frozen reward packages and uses the shared reward component ledger for external delivery.
- `mvn clean test` passed with 108 unit tests.

## Remaining Limitations
- Legacy periods with insufficient winner/package data enter `FAILED_RECOVERABLE` and require staff review.
- Real rollover, offline winner, restart-during-finalization, and announcement-once tests remain manual staging requirements.

