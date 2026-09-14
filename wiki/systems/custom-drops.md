# Custom Drops

Custom Drops control what players receive when RelicPrison processes mined blocks. They are designed to work alongside vanilla materials and optional custom-block providers while keeping reward calculations inside one predictable pipeline.

## Configure a drop

1. Open `custom-drops.yml`.
2. Copy the closest existing example.
3. Set the source block/material or supported custom-block reference.
4. Configure the resulting item/reward and any amount or chance options present in the file.
5. Save the file and reload RelicPrison.
6. Test the exact block in a staging mine.
7. Confirm inventory, AutoSell, fortune, and bulk-mining behavior separately.

## Vanilla and custom blocks

For vanilla blocks, use the material names expected by the shipped configuration. For ItemsAdder-backed blocks, first verify the ItemsAdder integration reports available before relying on a namespaced custom identifier.

## Fortune and bulk mining

Do not test only a single manual break. RelicPrison contains separate fortune, drop transformation, and bulk-mining logic. Verify a custom drop under normal mining, fortune, AutoSell, and any configured bulk-mining enchant.

## Avoid duplication bugs

A custom drop should have one owner. If another plugin also replaces drops for the same block, disable one path or deliberately configure the interaction. Two plugins independently granting rewards can create duplication.

## Troubleshooting

If the wrong item drops, confirm the configured source block, custom provider availability, and whether another plugin intercepts the event. If the item drops but sells for zero, add or verify its sell-price mapping.

## Related pages

- [Mining System](/systems/mining)
- [Economy & Selling](/systems/economy)
- [Integrations](/configuration/integrations)
- [Recipes](/recipes/)
