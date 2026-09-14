# Statistics

Statistics record committed RelicPrison activity and feed player displays, placeholders, analytics and leaderboards. The word **committed** is important: the system aims to count what actually happened, not what a provider merely attempted.

## Main statistic layers

- **Player mining statistics**: player-facing mining totals and dimensions.
- **Mine analytics**: mine-level operational data when enabled.
- **Selling statistics**: sold-item and money-earned totals from committed routes.
- **Playtime/profile data**: persisted player information used by other systems.
- **Gang statistics**: gang/member contributions, progression and leaderboard inputs.

## Mining dimensions

Mining statistics can distinguish routes such as AutoSell, AutoPickup, AutoBlock and fallback-drop behavior. Custom block identities can also be represented so an ItemsAdder block does not have to collapse into an unrelated vanilla identity.

## Setup

### 1. Enable only the data you need

Review the feature toggles in `config.yml` and mining settings. Player mining statistics are independent from the mine-analytics feature gate in the current backend.

### 2. Perform a controlled test

Pick one player and one known block. Record the relevant values before mining, break a known quantity, then compare the exact change.

### 3. Test automated routes

Repeat with AutoSell/AutoPickup/AutoBlock so you know the recorded dimensions match the route that actually committed.

### 4. Validate leaderboard inputs

If a statistic powers a leaderboard, confirm the raw/statistical value first. Do not debug leaderboard ordering until the underlying total is known to be correct.

## Cache and persistence behavior

Runtime statistics can be buffered/cached for performance, but dirty entries are protected from careless eviction and the database remains the authoritative persistence layer for durable records.

## Troubleshooting wrong totals

1. Confirm you are looking at the correct statistic/dimension.
2. Confirm the gameplay operation actually committed.
3. Check whether a bulk operation partially failed or rolled back.
4. Check for custom-block identity differences.
5. Compare PlaceholderAPI output with the underlying statistics command/GUI where available.
6. Run diagnostics before manually editing database values.

## Related pages

- [Mining Pipeline](/systems/mining)
- [Leaderboards](/systems/leaderboards)
- [Placeholders](/reference/placeholders)
- [Troubleshooting](/troubleshooting/)
