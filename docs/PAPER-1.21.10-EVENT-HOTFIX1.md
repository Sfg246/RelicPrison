# Paper 1.21.10 event linkage hotfix

RelicPrison 0.4.0 was built partly against a local offline Bukkit compile surface. Three method descriptors in that surface did not match Paper 1.21.10 exactly.

Corrected runtime descriptors:

- `EntityDamageEvent#getEntity(): Entity`
- `InventoryClickEvent#getWhoClicked(): HumanEntity`
- `Player#openInventory(Inventory): InventoryView`

The first mismatch caused `NoSuchMethodError` whenever a player took damage. The second would have caused the same error in the legacy mine administration GUI. The third would have caused a linkage failure when that GUI opened.

No database, configuration, mine, rank, prestige, economy, booster, mining, ItemsAdder, PlaceholderAPI, AdvancedEnchantments, or player-data formats changed.
