# Leaderboards

Leaderboards turn stored RelicPrison statistics into ranked views and, when configured, scheduled reward periods. Treat display ranking and reward delivery as separate concerns: a board can display correctly even if its reward configuration is incomplete.

## Configure a board

1. Open `leaderboards.yml`.
2. Copy a shipped board definition.
3. Choose the statistic/dimension the board should rank.
4. Configure its period and presentation options.
5. If rewards are required, configure them separately in `leaderboard-rewards.yml`.
6. Reload in staging.
7. Generate controlled player data and verify ordering.
8. Test the end-of-period reward path with disposable accounts before production.

## Periods

Use a period that matches how frequently players can realistically compete. Very short periods create frequent database and reward activity. Very long periods make mistakes expensive to correct.

## Reward safety

Leaderboard rewards use persistent delivery state so the system can distinguish pending, delivered, and recoverable outcomes. Do not manually edit reward records while the server is running.

Before enabling valuable rewards, test:

- a normal winner delivery;
- an offline winner;
- a restart near period rollover;
- a full inventory if item rewards are used;
- economy-provider failure;
- repeated startup to prove rewards are not duplicated.

## Common problems

If ordering looks wrong, verify that all compared players are using the same statistic and period. If a reward does not arrive, inspect the reward record/state rather than immediately rerunning the period. Re-running without understanding the stored state can create duplicates.

## Related pages

- [Statistics](/systems/statistics)
- [Rewards](/systems/rewards)
- [Admin Operations](/admin/operations)
- [Troubleshooting](/troubleshooting/)
