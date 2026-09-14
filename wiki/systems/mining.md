# Mining Pipeline

RelicPrison's mining pipeline is the path a broken mine block follows from validation to rewards. Understanding this page is important before combining custom drops, AutoSell, fortune, block events, ItemsAdder, or AdvancedEnchantments.

## The flow

1. A player attempts to break a block.
2. RelicPrison confirms the block belongs to a managed mine and the player is allowed to interact with it.
3. The plugin resolves the block type, including supported custom-block providers.
4. Mining rules determine the base result.
5. Fortune and configured drop transformations are applied.
6. Block Events and supported external bulk-mining providers are evaluated where applicable.
7. Rewards are committed through the protected transaction path.
8. Items are delivered or routed into AutoSell according to configuration.
9. Statistics and related state are updated.

This ordering matters. Avoid adding a second plugin that independently performs the same reward step unless the interaction is explicitly supported.

## Core configuration

The main mining settings live in `mining.yml`. Keep changes small and test one behavior at a time. A safe workflow is:

1. Back up the server.
2. Change one setting.
3. Reload or restart staging.
4. Mine a controlled set of blocks.
5. Verify inventory, balance, durability, statistics, and console output.
6. Add optional integrations only after the vanilla path works.

## AutoSell

AutoSell should be tested with a known sellable vanilla block first. If the player's balance does not change, confirm Vault/economy availability and the sell-price catalog before investigating mining itself.

## Auto-block conversion

Inventory conversion can compact eligible drops into block form. Test edge cases where the inventory is almost full and where the amount is not evenly divisible by the recipe size.

## Tool durability

RelicPrison has dedicated durability handling in the mining pipeline. Test custom tools and external enchants in staging before assuming their durability semantics match vanilla behavior.

## Bulk mining

Bulk mining is the highest-risk interaction because one player action can represent many blocks. Verify that rewards are neither skipped nor duplicated, that reset boundaries are respected, and that a failed delivery can recover safely.

## Troubleshooting checklist

- Is the block inside a registered RelicPrison mine?
- Is the mine currently resetting or unavailable?
- Did player data finish loading?
- Is the mining feature enabled?
- Does the block resolve to a supported type?
- Is another plugin cancelling or replacing the event?
- Is AutoSell routing the result away from inventory?
- Are Vault and the economy provider healthy?
- Are ItemsAdder or AdvancedEnchantments actually detected?

## Related pages

- [Mines](/systems/mines)
- [Custom Drops](/systems/custom-drops)
- [Block Events](/systems/block-events)
- [Economy & Selling](/systems/economy)
- [Integrations](/configuration/integrations)
