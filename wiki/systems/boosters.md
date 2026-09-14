# Boosters

Boosters temporarily increase configured RelicPrison multipliers. They can be activated through booster items or managed by staff, and their active state is persisted so a restart does not silently forget them.

## Packaged defaults

The packaged `boosters.yml` currently uses these important defaults:

- stacking mode: `MULTIPLY_ALL`
- maximum final multiplier: `100.0`
- maximum single-booster multiplier: `25.0`
- maximum duration: `2592000` seconds (30 days)
- offline time continues: `true`
- strength stacking: `false`
- duration stacking: `true`
- booster item material: `NETHER_STAR`

Permission multipliers are also supported. The packaged examples include `relicprison.multiplier.vip: 1.10` and `relicprison.multiplier.mvp: 1.20`.

## Setup from zero

### 1. Decide what your booster policy is

Before touching YAML, decide whether multiple boosters should extend time, increase strength, or both. The packaged defaults extend duration but do not stack strength.

### 2. Configure `boosters.yml`

Use the [generated booster reference](/generated/config/boosters) for every available field. Keep maximum values realistic so a typo cannot create a server-breaking multiplier.

### 3. Style the booster item

The default item supports placeholders such as `{multiplier}`, `{type}` and `{duration}` in its name/lore. Custom model data can be used when your resource pack provides a matching model.

### 4. Configure permission multipliers

Add only permissions that your permission system actually grants. A multiplier entry does nothing useful if nobody receives that permission.

### 5. Test activation

Give yourself a booster, record your normal sell result, activate the booster, then sell the same known quantity again. Confirm the displayed duration and actual money result both match the configuration.

## Persistence and restarts

Active/scheduled boosters are stored persistently. Runtime management supports cancellation, enable/disable, scope/target changes and remaining-duration tracking. Startup and database reconnect paths reload persisted booster state.

::: tip Test the restart path
Activate a short test booster, restart the server, and verify that remaining time and multiplier behavior are still correct.
:::

## Safe balancing checklist

1. Start with a small multiplier such as `1.25x` or `1.5x`.
2. Confirm the base sell price first.
3. Confirm permission multipliers separately.
4. Activate exactly one booster.
5. Verify the final result does not exceed your intended cap.
6. Test stacking only after single-booster behavior is proven.

## Common mistakes

- Making the maximum cap so high that one configuration typo destroys the economy.
- Giving overlapping permission multipliers without understanding the stacking policy.
- Assuming offline time pauses while `offline-time-continues` is enabled.
- Expecting custom model data to create artwork by itself. A matching resource pack is still required.
- Editing existing deployed YAML and assuming new defaults will overwrite it automatically.

## Related pages

- [Economy](/systems/economy)
- [Commands](/reference/commands)
- [Permissions](/reference/permissions)
- [All YAML Settings](/generated/config/)
