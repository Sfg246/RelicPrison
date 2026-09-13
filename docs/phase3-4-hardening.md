# Phase 3/4 Hardening Notes

## Progression Transactions

Rankup and prestige now create persistent transaction rows in `rp_progression_transactions`.
The durable checkpoints are:

1. `STARTED`
2. `MONEY_WITHDRAWN`
3. `PROFILE_SAVED`
4. `PERMISSIONS_UPDATED`
5. `COMPLETED`

Additional terminal states are `FAILED` and `MANUAL_REVIEW`.

Recovery behavior:

- `STARTED` has no recorded withdrawal and is marked failed so the player can retry safely.
- `MONEY_WITHDRAWN` with no profile advancement is refunded automatically only while the player is online.
- `PROFILE_SAVED` retries LuckPerms repair before rewards.
- `PERMISSIONS_UPDATED` retries reward delivery only if rewards were not reserved.
- `RESERVED` rewards require staff review because commands or external rewards may have partially run.
- Recovery never rewrites a profile backward if the player already has newer valid progression.

Use `/rp progression list`, `/rp progression info <id>`, and `/rp progression retry <id>` for diagnostics and safe retries.

## Mining Statistics

Mining statistics are buffered and flushed asynchronously. No mining action writes SQL directly.

Period boundaries:

- Daily periods use `server.timezone` from `config.yml`.
- Weekly periods use ISO week boundaries in the same timezone.
- Monthly periods use calendar months in the same timezone.
- Lifetime period key is always `lifetime`.

Dimensions persisted in `rp_mining_statistics` include material, mine, source (`normal` or `bulk`), total blocks, `autosell`, and `autopickup`.

## Mining XP

Direct mining XP is configured in `mining.yml`.
Material XP and mine XP are added to form base XP. Multipliers default to `MULTIPLICATIVE`.
`ADDITIVE_BONUSES` is supported and treats each multiplier as a bonus above `1.0`.
XP is clamped by `mining-xp.per-operation-cap`, and no XP orbs are spawned by RelicPrison.

## Custom Drops

`custom-drops.yml` supports the `custom-drops:` schema and still accepts legacy `drops:`.
Rules are precompiled on load, sorted by priority descending then ID, and command rewards are batched per mining action.

Use `%amount%` for the batched reward amount and `%blocks%` for the number of physical blocks in the mining action.

## Block Events

`block-events.yml` events are loaded only when `features.block-events` is enabled.
When disabled, Block Events do not register listeners, tasks, database polling, or per-block processors.

Cooldown and milestone state is persisted in `rp_block_event_state`.
Console commands run synchronously on the server thread; repository writes are asynchronous.
