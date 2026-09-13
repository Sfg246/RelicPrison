# Selling, Mining Features & Boosters

The economy loop turns mined output into progression money.

## The simplest selling test

Start with three commands:

```text
/sellvalue
/sellhand
/sellall
```

### `/sellvalue`

Answers: “How much are these configured items worth if I sell them?”

It is useful before you change inventory.

Use:

```text
/sellvalue
/sellvalue hand
```

### `/sellhand`

Sells the held item when it is configured as sellable.

### `/sellall`

Sells configured sellable items in the player's inventory.

## Vault's role

RelicPrison uses Vault as the economy bridge.

When selling succeeds, money is deposited through the registered Vault economy provider. If Vault is present but no economy provider is registered, economy-dependent features cannot behave normally.

## Global mining feature switches

The packaged config starts these disabled:

```yaml
features:
  autosell: false
  autopickup: false
  autosmelt: false
  autoblock: false
  fortune: false
  custom-block-drops: false
  mining-xp: false
  block-events: false
```

### Recommended activation order

1. Normal vanilla drops.
2. AutoPickup.
3. AutoSell.
4. AutoSmelt.
5. AutoBlock.
6. Fortune.
7. Custom drops.
8. Mining XP.
9. Block events.
10. ItemsAdder/AdvancedEnchantments paths.

If you enable ten interacting features at once, one wrong result can be hard to trace.

## AutoPickup

When enabled, supported mined output can be delivered through the pickup path rather than normal ground drops.

The current implementation distinguishes partial pickup/overflow from a fully successful AutoPickup result instead of pretending every item was picked up.

## AutoSell

When enabled, supported mined output can be sold through the mining pipeline.

Unsellable output is not silently counted as sold. Partial sales only count the items that actually sold.

## AutoSmelt and AutoBlock

These are processing layers, not “magic value multipliers.”

Test the actual output and conversion behavior with a small controlled inventory before enabling them for everyone.

## Fortune and custom drops

Fortune/custom drops can change the amount or type of output generated from a block.

That means they should be verified **before** you judge AutoPickup or AutoSell totals. If the upstream output is wrong, every downstream number can look wrong too.

## Boosters

Player-facing commands include:

```text
/booster status
/booster list
```

Administrative commands include:

```text
/booster give <player> <personal|server> <multiplier> <duration>
/booster activate server <multiplier> <duration>
/booster activate personal <player> <multiplier> <duration>
/booster remove <player>
/booster setmultiplier <player> <multiplier>
```

### Booster types

RelicPrison supports personal and server-scoped booster behavior, plus a permanent multiplier administration path.

Runtime-managed boosters persist enough state to recover active/scheduled/disabled instances after startup or database reconnect.

## Useful permissions

```text
relicprison.sellall
relicprison.sellhand
relicprison.sellvalue
relicprison.booster.status
relicprison.booster.activate.server
relicprison.admin.booster
relicprison.admin.sell
```

## Useful PlaceholderAPI values

```text
%relicprison_inventory_base_value%
%relicprison_inventory_final_value%
%relicprison_hand_base_value%
%relicprison_hand_final_value%
%relicprison_personal_booster_multiplier%
%relicprison_personal_booster_time%
%relicprison_server_booster_multiplier%
%relicprison_server_booster_time%
%relicprison_combined_multiplier%
```

## Debugging a bad sell total

Work from left to right:

```text
What block broke?
  ↓
What raw drops were generated?
  ↓
Did fortune/custom drops modify them?
  ↓
Did smelting/block conversion modify them?
  ↓
How many items were sellable?
  ↓
What base price was configured?
  ↓
What final multiplier/booster applied?
  ↓
What did Vault deposit?
```

Do not begin by changing the final multiplier if the raw drop count is already wrong.
