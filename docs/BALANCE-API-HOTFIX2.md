# Formatted Balance API Hotfix 2

RelicPrison now provides three PlaceholderAPI placeholders backed by the active Vault economy provider:

```text
%relicprison_balance%
%relicprison_balance_short%
%relicprison_balance_raw%
```

With a balance of `1250000` and the default formatting configuration, they return:

```text
$1,250,000.00
$1.25M
1250000.00
```

The values use `formatting.currency-symbol`, `formatting.decimals`, `formatting.abbreviated-decimals`, `formatting.use-grouping`, and `formatting.abbreviations` from `config.yml`.

The public `NumberFormatter` API also includes:

```java
api.numbers().plain(value);
api.numbers().abbreviatedCurrency(value);
```

Balance placeholders are resolved before the RelicPrison player profile finishes loading, so scoreboards do not briefly show `loading` for the balance line. Offline PlaceholderAPI contexts return a formatted zero because the active Vault adapter currently reads through an online Bukkit `Player`.
