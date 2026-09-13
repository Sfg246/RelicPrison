# Permissions

RelicPrison uses Bukkit permission nodes for player features, staff tools, bypasses, and gang-wide authorization.

## Default player permissions

These are declared with `default: true` in the current `plugin.yml`:

```text
relicprison.use
relicprison.mine.teleport
relicprison.rankup
relicprison.rankupmax
relicprison.prestige
relicprison.sellall
relicprison.sellhand
relicprison.sellvalue
relicprison.autosell
relicprison.autopickup
relicprison.autosmelt
relicprison.autoblock
relicprison.booster.status
relicprison.menu
relicprison.stats
relicprison.leaderboard
relicprison.gang.use
relicprison.gang.create
relicprison.gang.join
relicprison.gang.chat
```

## Main admin permission

```text
relicprison.admin
```

It is `op` by default and currently inherits these children:

```text
relicprison.admin.reload
relicprison.admin.mine
relicprison.admin.rank
relicprison.admin.prestige
relicprison.admin.booster
relicprison.admin.sell
relicprison.admin.backup
relicprison.admin.diagnostic
relicprison.admin.diagnostic.sensitive
relicprison.admin.audit
relicprison.admin.repair
relicprison.admin.leaderboard
relicprison.admin.transfer
relicprison.admin.mine.preview
relicprison.admin.mine.reset.alerts
relicprison.gang.admin
```

## Admin permission reference

| Permission | Purpose |
|---|---|
| `relicprison.admin.reload` | Controlled config reload paths |
| `relicprison.admin.mine` | Mine administration |
| `relicprison.admin.mine.preview` | Mine preview-related admin behavior |
| `relicprison.admin.mine.reset.alerts` | Reset alert administration/visibility |
| `relicprison.admin.rank` | Rank administration |
| `relicprison.admin.prestige` | Prestige administration |
| `relicprison.admin.booster` | Booster administration |
| `relicprison.admin.sell` | Selling administration |
| `relicprison.admin.backup` | Backup/restore operations |
| `relicprison.admin.diagnostic` | Diagnostic operations |
| `relicprison.admin.diagnostic.sensitive` | Detailed/sensitive diagnostic output |
| `relicprison.admin.audit` | Staff audit query |
| `relicprison.admin.repair` | Repair operations |
| `relicprison.admin.leaderboard` | Leaderboard administration |
| `relicprison.admin.transfer` | Data import/export administration |
| `relicprison.gang.admin` | `/relicgang` administration |

## Bypass permissions

```text
relicprison.bypass.mine-access
relicprison.bypass.teleport-warmup
relicprison.bypass.combat-teleport
relicprison.bypass.build
```

Give bypasses to staff intentionally. They exist to bypass gameplay restrictions, which means they can make a staff test look successful when a normal player would still be denied.

## Booster/server permissions

```text
relicprison.booster.activate.server
relicprison.admin.booster
```

Server booster activation is `op` by default.

## Gang permissions

Global Bukkit permissions:

```text
relicprison.gang.use
relicprison.gang.create
relicprison.gang.join
relicprison.gang.chat
relicprison.gang.admin
relicprison.gang.limit.20
relicprison.gang.limit.30
```

The `.limit.20` and `.limit.30` nodes are declared false by default and are intended as configurable creation-time/member-limit permissions.

### Do not confuse these with gang-rank permissions

Inside a gang, ranks such as Owner/Officer/Member use RelicPrison's own internal permission names like `invite`, `kick`, `withdraw_bank`, and `manage_ranks`.

Those are not LuckPerms nodes.

## LuckPerms examples

Give a staff rank full RelicPrison administration:

```text
/lp group admin permission set relicprison.admin true
```

Give a moderator diagnostic access without all administration:

```text
/lp group moderator permission set relicprison.admin.diagnostic true
```

Give a builder mine-access bypass:

```text
/lp group builder permission set relicprison.bypass.mine-access true
```

::: warning Test as a non-op
Operators can inherit/default through permission behavior that normal players do not. Always test the player experience on a non-op account or test group.
:::
