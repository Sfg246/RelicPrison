# Rewards & Recovery

RelicPrison uses a shared reward-ledger model for important gameplay payouts. The goal is simple: if the server stops at the worst possible moment, staff should be able to determine what was planned, what was claimed, what was confirmed and what still needs attention.

## Reward package model

A **reward package** is the durable container. A package can contain multiple **components**, such as money, items, ItemsAdder items, commands, permissions, boosters, announcements or statistics. Components have delivery state so recovery logic does not need to assume the entire package succeeded or failed as one opaque action.

## Why this exists

Without durable state, a crash can create two bad outcomes:

- the world/gameplay action commits but the reward disappears;
- the reward is delivered, then the server restarts before recording success and pays it again.

RelicPrison's transaction/reward records are designed to make these cases observable and recoverable.

## Where the reward system is used

The shared reward path is involved in systems such as progression rewards, Block Events, leaderboard rewards and other durable package flows. Bulk mining also uses durable transaction/reward state for money, XP and item delivery decisions.

## Staff workflow when something looks wrong

1. **Do not immediately pay the player manually.** First determine whether a package/component is pending, retry-ready, confirmed or requires staff review.
2. Run the relevant diagnostics from [Admin & Recovery](/admin/operations).
3. Identify the transaction/package and affected player.
4. Check whether the external effect already happened, especially for economy or arbitrary command components.
5. Use the plugin's supported retry/repair/recovery path only after you understand the recorded state.
6. Re-run diagnostics and document the result.

## External-side-effect warning

RelicPrison can make its own database transitions replay-aware, but it cannot magically make every third-party console command or external economy implementation transactional. An external provider may complete an action and fail before RelicPrison can observe a clean acknowledgement.

::: danger Never “fix” an ambiguous payout by guessing
On production data, guessing can duplicate high-value rewards. Preserve the records, inspect state and use a controlled repair path.
:::

## Staging acceptance test

For each important reward type you plan to use:

1. Deliver it normally.
2. Verify the expected player-facing result.
3. Restart and verify it is not delivered again.
4. Test an intentionally invalid target on staging.
5. Confirm diagnostics expose the failure.
6. Correct the cause and exercise the documented recovery path.

## Related pages

- [Block Events](/systems/block-events)
- [Leaderboards](/systems/leaderboards)
- [Mining Pipeline](/systems/mining)
- [Admin & Recovery](/admin/operations)
- [Known Limitations](/known-limitations)
