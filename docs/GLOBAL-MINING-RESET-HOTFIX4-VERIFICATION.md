# Global mining and reset evacuation hotfix verification

Version: `0.3.5-stage14-paper-drops-xp-hotfix5`

## Server-authoritative mining features

The following `config.yml` flags are authoritative for every player:

- `features.autosell`
- `features.autopickup`
- `features.autosmelt`
- `features.autoblock`
- `features.fortune`
- `features.mining-xp`

Saved player toggle columns remain in schema version 3 only for backward data compatibility. They are not consulted by the mining reward pipeline.

## AdvancedEnchantments 9.22.9

The bridge was verified against the uploaded `AdvancedEnchantments-9.22.9.jar` runtime surface:

- `AEAPI.setIgnoreBlockEvent(Block)`
- `EffectsActivatedEvent.getExecutionTask()`
- `ExecutionTask.getBuilder()`
- `ActionExecutionBuilder.getBlock()` and `getDrops()`
- `DropsHandler.getSettings()` and `removeBlock(Block)`
- `DropsSettings.isBreakBlocks()`
- `DropsSettings.setAddToInventory(boolean)`
- `DropsSettings.setSmelt(boolean)`
- `DropsSettings.setDropExp(boolean)`
- `DropsSettings.setDropExpAmount(int)`

RelicPrison runs before AE's HIGH-priority mining listener. It handles the original block, then handles AE's marked synthetic break events for additional blocks. Synthetic events are cancelled after their rewards are delivered, causing AE to remove those pending drop entries.

## Reset safety

Mine resets no longer skip operators or players with an evacuation bypass. Every player inside the refill cuboid is teleported before the reset enters `RESETTING`. If any teleport fails, is redirected into the mine, or is cancelled by another plugin, the reset fails before block placement begins.
