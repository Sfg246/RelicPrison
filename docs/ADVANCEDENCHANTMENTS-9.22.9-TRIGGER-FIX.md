# AdvancedEnchantments 9.22.9 trigger compatibility

AE's `MiningTrigger#onBlockBreak` runs at `EventPriority.HIGH` and exits when
`BlockBreakEvent#isDropItems()` is false. RelicPrison Hotfix 5 suppressed vanilla drops at
`LOWEST`, so AE never evaluated the pickaxe enchantments.

Hotfix 6 keeps drop items enabled through AE's HIGH listener. RelicPrison performs its final
`setDropItems(false)` and XP suppression at HIGHEST. Synthetic AE block events remain routed
through RelicPrison and are consumed/cancelled after their rewards are delivered.
