# Mining Pipeline

RelicPrison's mining system is the path a broken mine block follows from **player action** to **delivered reward**. Understanding this page makes AutoPickup, AutoSell, Fortune, bulk enchants and custom blocks much easier to configure.

## What happens when a player mines

1. RelicPrison confirms the block belongs to a managed mine.
2. The mining route is validated before rewards are committed.
3. The block identity and configured drop behavior are resolved.
4. Fortune/custom-drop logic determines the output.
5. AutoBlock, AutoSell, AutoPickup or fallback-drop routing is selected.
6. Money, XP, item and statistic results are committed through the appropriate services.
7. Events/statistics are recorded from the committed result rather than from a guessed result.

For bulk mining, the plugin uses durable transaction records so interrupted operations can be diagnosed and recovered instead of blindly paying twice.

## First-time setup

### Step 1: Build and test a normal mine

Create a mine using [Create Your First Mine](/guide/first-mine). Stand inside it and verify ordinary block breaking works before adding automation.

### Step 2: Review `mining.yml`

Open [`mining.yml`](/generated/config/mining). Enable only the routes you actually want. Test one feature at a time.

### Step 3: Configure prices and drops

- Sell values live in `sell-prices.yml`.
- Custom drop definitions live in `custom-drops.yml`.
- Mine block composition lives in `mines.yml`.
- ItemsAdder blocks require the ItemsAdder integration to be available and valid.

### Step 4: Test delivery routes

Test with an empty inventory, a nearly full inventory, AutoSell enabled/disabled, and the same tool with and without Fortune. You want to prove that the final result matches the route you intended.

## Normal mining vs bulk mining

**Normal mining** handles the player's individual block break. **Bulk mining** handles a provider reporting multiple affected blocks, such as a supported AdvancedEnchantments mining effect. Bulk operations are deliberately more defensive because a partial failure could otherwise duplicate money/items or consume blocks without delivering rewards.

::: warning Do not treat a visual explosion as proof of payment
For bulk effects, verify both the world mutation and the final reward/statistics result. A flashy effect does not prove the transaction committed correctly.
:::

## AutoPickup

AutoPickup attempts to place delivered item output into the player's inventory. If your configured route cannot safely deliver there, the mining pipeline must use its fallback behavior rather than silently deleting output.

**Test:** fill most inventory slots, mine a known block, and confirm the expected remainder is handled correctly.

## AutoSell

AutoSell converts eligible output into money using the configured sell-price catalog and multiplier stack. Vault must have a working economy provider.

**Test:** record balance, mine a known quantity, then compare the balance change against the configured price and multiplier.

## AutoBlock

AutoBlock converts eligible inventory output into block form when conversion succeeds. The committed mining statistics are based on the route that actually succeeded, not merely the fact that AutoBlock was enabled.

## Fortune and custom drops

Fortune calculation and custom-drop transformation happen before final delivery routing. If a custom block or item cannot be resolved, fix the provider/integration problem first instead of compensating by inflating sell prices.

## AdvancedEnchantments bulk mining

The integration submits supported affected-block collections into RelicPrison's real bulk pipeline. Keep AdvancedEnchantments and RelicPrison integration versions aligned with the [Compatibility Matrix](/reference/compatibility).

## How to verify the system

Use this small acceptance test:

1. Mine one vanilla block with all automation off.
2. Enable AutoPickup and repeat.
3. Enable AutoSell and verify balance change.
4. Enable Fortune and compare expected output.
5. Test a full/near-full inventory.
6. Test an ItemsAdder block if used.
7. Test one supported bulk enchant.
8. Restart the server after a completed test and check diagnostics for unfinished transactions.

## Common mistakes

- Enabling AutoSell without a functioning Vault economy provider.
- Testing multiple automation features at once and not knowing which one failed.
- Assuming an AdvancedEnchantments animation means RelicPrison received the affected blocks.
- Using an ItemsAdder block ID that does not exist on the current server.
- Editing generated/default files while the server is still running without following the plugin's reload/validation workflow.

## Related pages

- [Economy](/systems/economy)
- [Rewards & Recovery](/systems/rewards)
- [Statistics](/systems/statistics)
- [Integrations](/configuration/integrations)
- [Troubleshooting](/troubleshooting/)
