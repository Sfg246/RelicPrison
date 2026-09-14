# Block Events

Block Events let RelicPrison attach controlled special behavior to mining rather than treating every broken block identically. They are useful for rare rewards, effects, announcements, bonus actions, and other mining-driven events.

## How the system works

When a qualifying block is processed, RelicPrison evaluates the configured event catalog and chance rules. The event service then applies the selected outcome through the same guarded mining pipeline used by the rest of the plugin. This keeps event rewards auditable and avoids bypassing normal mining protections.

## Setup

1. Back up the server.
2. Open `block-events.yml`.
3. Copy an existing event entry instead of inventing keys from memory.
4. Give the event a unique stable identifier.
5. Set its chance and reward/effect options.
6. Reload in staging.
7. Mine enough blocks to prove the event can trigger.
8. Test with and without external enchant/custom-block integrations.

## Chance design

Use low event chances carefully. A one-percent event may still trigger constantly on a high-throughput prison server. Balance against blocks broken per second, bulk-mining enchants, player count, and the value of the reward.

## Safe testing

For staging, temporarily raise the chance so the event is easy to trigger. Once behavior is confirmed, restore the intended production chance and reload again.

## Common failures

If an event never triggers, verify the file parsed successfully, the event is enabled, its chance is nonzero, and the tested mining route reaches RelicPrison. If it triggers multiple times unexpectedly, test without bulk-mining integrations and compare the result.

## Related pages

- [Mining System](/systems/mining)
- [Custom Drops](/systems/custom-drops)
- [Advanced Enchantments Integration](/configuration/integrations)
- [Troubleshooting](/troubleshooting/)
