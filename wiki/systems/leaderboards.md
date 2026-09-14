# Leaderboards

Leaderboards turn committed RelicPrison statistics into ranked boards and, when configured, period rewards. The important idea is that **ranking** and **reward delivery** are separate steps.

## How the lifecycle works

1. A supported statistic produces leaderboard input.
2. Players are ranked for the configured board and period.
3. When a reward period closes, RelicPrison freezes the standings used for finalization.
4. Winner reward packages are created from the frozen plan.
5. Package existence is verified before the period is considered finalized.
6. Recovery can resume incomplete finalization instead of starting from scratch.

## First leaderboard setup

### 1. Choose one metric

Start with a metric you can reproduce easily on staging, such as a known mining statistic. Do not begin with ten boards at once.

### 2. Configure `leaderboards.yml`

Use the [generated configuration reference](/generated/config/) for exact board fields and defaults.

### 3. Configure `leaderboard-rewards.yml`

If the board should pay winners, define the reward separately. Keep the first test reward intentionally small.

### 4. Create controlled data

Use two or three test accounts and deliberately produce different totals. This makes ranking errors obvious.

### 5. Verify period finalization

When the period is finalized, verify the standings snapshot, reward packages and final period state rather than checking only the visible top-player list.

## Recovery behavior

The current backend stores frozen standings/reward plans and can recover incomplete leaderboard finalization. If required legacy information is insufficient, the period can be marked recoverable for staff review instead of guessing a payout.

::: warning External rewards still deserve staging
A reward package can durably track RelicPrison's intent, but arbitrary external console commands may have their own side effects. Test those commands carefully before using them for large seasonal payouts.
:::

## Recommended acceptance test

1. Make Player A clearly first and Player B clearly second.
2. Open the visible leaderboard and verify order.
3. Finalize the test period.
4. Confirm the winner receives exactly one reward package.
5. Restart and verify the finalized period stays finalized.
6. Run diagnostics and confirm no winner package is missing.

## Common mistakes

- Paying both native money and an economy command for the same intended reward.
- Testing rankings with data that is too close to distinguish easily.
- Editing reward definitions after a period has already frozen its plan and expecting old periods to change.
- Treating a visible leaderboard as proof that finalization/rewards are correct.

## Related pages

- [Statistics](/systems/statistics)
- [Rewards & Recovery](/systems/rewards)
- [Gangs](/systems/gangs)
- [Admin & Recovery](/admin/operations)
