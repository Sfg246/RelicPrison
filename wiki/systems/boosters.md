# Boosters

Boosters temporarily multiply prison rewards without permanently changing a player's progression data. RelicPrison keeps booster definitions, activation state, persistence, expiry, and GUI presentation separate so administrators can change balance without rewriting code.

## What boosters do

A booster has a configured identifier, multiplier, duration, presentation, and activation behavior. Active boosters are persisted so a restart does not silently erase paid or earned time. RelicPrison also exposes booster activation and expiry events for integrations.

## Before you configure one

1. Back up the server and `plugins/RelicPrison/` data.
2. Open `boosters.yml` and identify an existing entry to copy.
3. Keep identifiers lowercase and stable once players may own the booster.
4. Validate multiplier and duration values before giving the booster to players.
5. Reload only through the supported RelicPrison reload flow and check console output.

## Basic workflow

1. Define the booster in `boosters.yml`.
2. Restart or reload RelicPrison using the documented admin command.
3. Give or activate the booster through the supported command/item flow.
4. Confirm the active multiplier in-game.
5. Restart a staging server and verify the remaining duration persists.
6. Test expiry and confirm the multiplier returns to normal.

## Stacking and balance

Treat stacking as a balance decision, not a visual setting. Test combinations with rank, prestige, gang, global, and other configured multipliers before production. A multiplier that looks small by itself can become extreme once several systems are combined.

## Troubleshooting

### Booster does not activate

Check the booster identifier, permissions, configuration parsing, and whether the player data has finished loading.

### Booster activates but reward is unchanged

Verify the reward path you are testing actually consumes RelicPrison's multiplier service. Test a plain mining/selling case first, then add external enchant or custom-block integrations.

### Booster disappears after restart

Do not continue production testing. Verify database/storage health and inspect startup logs for persistence errors.

## Related pages

- [Economy & Selling](/systems/economy)
- [Progression](/systems/progression)
- [Configuration](/configuration/)
- [Troubleshooting](/troubleshooting/)
