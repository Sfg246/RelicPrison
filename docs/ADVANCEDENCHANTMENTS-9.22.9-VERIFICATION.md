# AdvancedEnchantments 9.22.9 verification

RelicPrison 0.3.3-stage14-ae-hotfix3 was inspected against the exact uploaded
`AdvancedEnchantments-9.22.9.jar` and its live configuration folder.

Verified runtime surfaces:

- `net.advancedplugins.ae.api.AEAPI.setIgnoreBlockEvent(Block)`
- `EffectsActivatedEvent.getExecutionTask()`
- `ExecutionTask.getBuilder()`
- `ActionExecutionBuilder.getDrops()` and `getEvent()`
- `DropsHandler.getAllBlocks()`, `getDropsMap()`, `getSettings()`, and `removeBlock(Block)`
- `DropsSettings.isBreakBlocks()`

AE 9.22.9 executes the selected BREAK_BLOCK effect before calling
`DropsHandler.handle()`. RelicPrison registers a runtime event bridge, takes ownership of the
pending block list, processes it through the shared mine pipeline, clears AE's pending drops, and
cancels the original Bukkit break event to prevent duplicate rewards.

No AdvancedEnchantments classes are bundled in RelicPrison. The bridge remains an optional
soft-dependency and is loaded reflectively at runtime.
