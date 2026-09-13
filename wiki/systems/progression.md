# Ranks & Prestiges

Progression connects money, player profile state, mine access, and LuckPerms.

## Starting point

The current default is:

```yaml
progression:
  starting-rank: a
```

A new profile therefore begins at Rank A unless you intentionally change the progression configuration.

## Rank commands

Player commands:

```text
/rankup
/rankupmax
/ranks
```

- `/rankup` buys the next rank.
- `/rankupmax` keeps advancing through every currently affordable rank.
- `/ranks` opens the rank progression GUI.

## Packaged A-Z rank path

| Rank | Cost to next rank | Mine |
|---|---:|---|
| A | 55,000 | a |
| B | 68,700 | b |
| C | 81,100 | c |
| D | 95,700 | d |
| E | 112,900 | e |
| F | 133,200 | f |
| G | 157,200 | g |
| H | 185,500 | h |
| I | 219,000 | i |
| J | 258,000 | j |
| K | 304,800 | k |
| L | 360,000 | l |
| M | 424,375 | m |
| N | 500,000 | n |
| O | 600,000 | o |
| P | 700,000 | p |
| Q | 820,000 | q |
| R | 970,000 | r |
| S | 1,150,000 | s |
| T | 1,600,000 | t |
| U | 1,900,000 | u |
| V | 2,183,000 | v |
| W | 2,400,000 | w |
| X | 2,700,000 | x |
| Y | 3,100,000 | y |
| Z | 0 | z |

Rank Z is the final packaged rank and has no next-rank cost.

## Prestige

Player commands:

```text
/prestige
/prestige confirm
/prestiges
```

The current config uses a confirmation window before completing the prestige path.

Packaged prestige tiers:

| Prestige | Base cost | Sell multiplier | Rank-cost multiplier | Mine |
|---|---:|---:|---:|---|
| Coal | 16,000,000 | 1.0× | 3.12× | coal |
| Iron | 23,000,000 | 1.5× | 7.98× | iron |
| Gold | 43,000,000 | 2.0× | 18.0× | gold |
| Redstone | 58,000,000 | 2.5× | 33.6× | redstone |
| Diamond | 76,000,000 | 3.0× | 56.7× | diamond |
| Emerald | 115,000,000 | 4.0× | 115.2× | emerald |
| Immortal | 200,000,000 | 5.0× | 247.5× | immortal |

## What happens during progression

A rankup or prestige may need to coordinate multiple systems:

```text
Check current profile
  ↓
Check configured next step
  ↓
Check Vault balance/cost
  ↓
Record progression transaction state
  ↓
Withdraw/commit required economic state
  ↓
Update RelicPrison profile
  ↓
Update LuckPerms state
  ↓
Run configured rewards/notifications
  ↓
Mark progression transaction complete
```

The exact internal recovery states exist so a crash in the middle is visible and repairable instead of silently duplicating or losing progression.

## Administrative rank commands

```text
/relicrank info <player>
/relicrank set <player> <rank>
/relicrank promote <player> [amount]
/relicrank demote <player> [amount]
```

Required permission:

```text
relicprison.admin.rank
```

## Administrative prestige commands

```text
/relicprestige info <player>
/relicprestige set <player> <prestige|none>
/relicprestige promote <player> [amount]
/relicprestige demote <player> [amount]
```

Required permission:

```text
relicprison.admin.prestige
```

## LuckPerms group formatting

The core config currently exposes:

```yaml
progression:
  rank-group-format: '{rank}'
  prestige-group-format: '{prestige}'
  repair-on-join: true
```

If your LuckPerms group naming scheme differs, align these patterns before testing progression.

## Repairing inconsistent progression

If RelicPrison and LuckPerms disagree, do not blindly edit both sides.

First inspect:

```text
/rp diagnose
/relicrank info <player>
/relicprestige info <player>
```

Then use the plugin's repair path when appropriate:

```text
/rp repair <player>
```

The admin tooling also exposes progression transaction inspection/retry under:

```text
/rp progression <list|info|retry>
```

## Testing progression safely

Use a disposable test player.

1. Set or give the exact money needed.
2. Record current rank and LuckPerms state.
3. Run `/rankup` once.
4. Verify balance.
5. Verify RelicPrison rank.
6. Verify LuckPerms group.
7. Verify mine access.
8. Only then test `/rankupmax`.
