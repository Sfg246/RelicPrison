# Statistics

RelicPrison records mining and progression data used by placeholders, GUIs, leaderboards, diagnostics, and server balancing. Statistics are operational data, so test resets and migrations as carefully as economy data.

## What statistics are used for

- player mining totals and related dimensions;
- mine-level analytics;
- leaderboard ranking;
- PlaceholderAPI values;
- GUI summaries;
- administration and balancing decisions.

## Configuration and operation

Keep leaderboard configuration in `leaderboards.yml`; statistics themselves are produced by gameplay. If a displayed value looks wrong, first identify whether the source statistic is wrong or only the presentation layer is wrong.

## Verification procedure

1. Use a fresh test player.
2. Record the starting value.
3. Break a known number of blocks using plain mining.
4. Confirm the statistic increases by the expected amount.
5. Repeat with AutoSell.
6. Repeat with supported bulk-mining behavior.
7. Restart the server and verify persistence.
8. Compare the PlaceholderAPI value, GUI value, and leaderboard entry.

## Performance

Avoid building external scripts that query player statistics on every tick. Prefer the public API, cached placeholders, or controlled intervals. High-frequency database reads can turn a harmless display into a performance problem.

## Troubleshooting

If statistics are missing, check player data load state and storage health. If values are doubled, test without external mining plugins to find whether more than one system is counting the same block. If the leaderboard is wrong while the raw statistic is correct, investigate leaderboard period/filter configuration instead.

## Related pages

- [Leaderboards](/systems/leaderboards)
- [Placeholders](/reference/placeholders)
- [Developer API](/developers/api)
- [Admin Operations](/admin/operations)
