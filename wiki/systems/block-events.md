# Block Events

Block Events let mining activity trigger configured event progress and rewards. RelicPrison treats event claims as durable operations so concurrent mining or a restart does not intentionally create the same logical reward twice.

## What a Block Event does

A Block Event has a definition, progress rules, trigger/claim state and a reward plan. When a qualifying mining action occurs, RelicPrison updates the event state and, when a reward is due, hands the reward to the shared durable reward system.

## Setup sequence

### 1. Open `block-events.yml`

Start from the packaged file and the [generated configuration reference](/generated/config/). Do not invent field names from an older build.

### 2. Define one simple event

For your first test, use one event with an obvious target and a small, harmless reward. Prove the event lifecycle before creating a large event catalog.

### 3. Validate reward components

Make sure every money, item, ItemsAdder item, command, permission or other configured reward points to something that really exists on the server.

### 4. Trigger it normally

Mine the exact blocks required by the event. Watch progress, trigger state and delivered reward.

### 5. Test duplicate protection

After the reward is delivered, repeat the same qualifying action. The same logical first-time/daily claim should not generate a second package just because the action was repeated or processed concurrently.

## Crash/recovery model

RelicPrison stores logical claim keys and recovery metadata. Trigger progress/reward-created state is written durably, and startup/database-reconnect recovery can verify existing reward packages or recreate recoverable packages from frozen trigger data.

This matters because the dangerous failure case is not simply “the event failed.” It is “the event paid, the server crashed before recording that it paid, then it paid again.” The durable claim/reward design exists to reduce that class of duplication.

## Recommended acceptance test

1. Trigger an event once.
2. Confirm the reward arrives once.
3. Re-run the same logical claim and confirm it does not duplicate.
4. Restart the server after a completed claim.
5. Check diagnostics for missing/recoverable Block Event packages.
6. Test one intentionally invalid reward on staging and confirm validation/recovery information is understandable.

## Common mistakes

- Creating many complex events before proving one small event end-to-end.
- Using commands as rewards without considering whether the external command itself is idempotent.
- Assuming a reward definition can reference a missing ItemsAdder ID safely.
- Changing an event definition mid-test and then comparing old frozen transactions against the new configuration.

## Related pages

- [Rewards & Recovery](/systems/rewards)
- [Mining Pipeline](/systems/mining)
- [Admin & Recovery](/admin/operations)
- [Troubleshooting](/troubleshooting/)
