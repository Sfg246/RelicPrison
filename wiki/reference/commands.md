# Commands

This page is the quick reference. The system pages explain *why* and *when* to use the commands.

## Player command index

| Command | What it does | Main permission |
|---|---|---|
| `/mine [mine]` | Open mine menu or teleport to an unlocked mine | `relicprison.mine.teleport` |
| `/rankup` | Buy the next rank | `relicprison.rankup` |
| `/rankupmax` | Buy every currently affordable rank | `relicprison.rankupmax` |
| `/prestige [confirm]` | Start/confirm prestige after final rank | `relicprison.prestige` |
| `/sellall` | Sell configured inventory items | `relicprison.sellall` |
| `/sellhand` | Sell held item | `relicprison.sellhand` |
| `/sellvalue [hand]` | Estimate inventory/hand sell value | `relicprison.sellvalue` |
| `/autosell` | Show global AutoSell state | `relicprison.autosell` |
| `/autopickup` | Show global AutoPickup state | `relicprison.autopickup` |
| `/autosmelt` | Show global AutoSmelt state | `relicprison.autosmelt` |
| `/autoblock` | Show global AutoBlock state | `relicprison.autoblock` |
| `/booster status [player]` | View booster state/final multiplier | `relicprison.booster.status` |
| `/booster list` | List active server boosters | booster command access |
| `/prison` | Open main prison menu | `relicprison.menu` |
| `/ranks` | Open rank progression menu | `relicprison.menu` |
| `/prestiges` | Open prestige progression menu | `relicprison.menu` |
| `/stats` | Open player statistics | `relicprison.stats` |
| `/leaderboard [metric] [period]` | Browse leaderboards | `relicprison.leaderboard` |
| `/gang` | Open gang menu | `relicprison.gang.use` |
| `/gc [message]` | Send/toggle gang chat | `relicprison.gang.chat` |

## Gang player commands

### Getting started

```text
/gang
/gang create <name> <tag>
/gang invites
/gang accept <invite-id>
/gang deny <invite-id>
/gang join <name|tag>
```

### Members and identity

```text
/gang invite <player>
/gang leave
/gang kick <player>
/gang promote <player>
/gang demote <player>
/gang transfer <player>
/gang settings <field> <value>
```

### Progression and utilities

```text
/gang bank [balance|history]
/gang bank deposit <amount> [reason]
/gang bank withdraw <amount> [reason]
/gang upgrades [upgrade]
/gang missions
/gang missions claim <mission>
/gang ranks
/gang rank ...
/gang chat [toggle|message]
/gang sethome
/gang home
/gang disband confirm
```

## RelicPrison administration

Root aliases:

```text
/relicprison
/rp
```

### System

```text
/rp status
/rp admin
/rp reload [module]
/rp validate
/rp diagnose [detail]
/rp diagnostic
```

### Data and recovery

```text
/rp backup <list|create|info|verify|delete|restore>
/rp audit [filters]
/rp repair <player|all>
/rp progression <list|info|retry>
/rp rewards <preview|finalize|history|pending|retry>
```

### Transfer

```text
/rp export <player>
/rp import <file.yml> confirm
```

## Mine administration

Aliases:

```text
/relicmine
/rmine
```

### Selection and creation

```text
/relicmine wand
/relicmine create <mine>
/relicmine resize <mine>
/relicmine move <mine>
/relicmine copy <source-mine> <new-mine> [display name]
/relicmine delete <mine>
/relicmine confirm
/relicmine cancel
```

### Mine management

```text
/relicmine list
/relicmine info <mine>
/relicmine tp <mine>
/relicmine setspawn <mine>
/relicmine enable <mine>
/relicmine disable <mine>
/relicmine rename <mine> <new-id> [display name]
/relicmine sort <mine> <order>
/relicmine requirement <mine> <rank|prestige|permission|clear> <value|all>
/relicmine metadata <mine> <list|set|remove> ...
/relicmine gui
```

### Composition and reset

```text
/relicmine composition <mine> <list|set|remove|normalize|copy> ...
/relicmine reset <mine>
/relicmine recount <mine>
/relicmine resetconfig <mine> ...
/relicmine resethook <mine> ...
/relicmine retryfailed <mine>
```

### Structure recovery

```text
/relicmine structure list
/relicmine structure info <operation-id>
/relicmine structure retry <operation-id>
/relicmine structure rollback <operation-id>
```

## Rank administration

```text
/relicrank info <player>
/relicrank set <player> <rank>
/relicrank promote <player> [amount]
/relicrank demote <player> [amount]
```

## Prestige administration

```text
/relicprestige info <player>
/relicprestige set <player> <prestige|none>
/relicprestige promote <player> [amount]
/relicprestige demote <player> [amount]
```

## Booster administration

```text
/booster give <player> <personal|server> <multiplier> <duration>
/booster activate server <multiplier> <duration>
/booster activate personal <player> <multiplier> <duration>
/booster remove <player>
/booster setmultiplier <player> <multiplier>
```

## Gang administration

```text
/relicgang list [query]
/relicgang inspect <name|tag>
/relicgang audit <gang>
/relicgang addmember <gang> <player>
/relicgang removemember <gang> <player>
/relicgang setowner <gang> <player>
/relicgang disband <gang> confirm
/relicgang setlevel <gang> <amount>
/relicgang addlevel <gang> <amount>
/relicgang setxp <gang> <amount>
/relicgang addxp <gang> <amount>
/relicgang setpoints <gang> <amount>
/relicgang addpoints <gang> <amount>
/relicgang adjustbank <gang> <signed-amount> [reason]
/relicgang rename <gang> <name>
/relicgang tag <gang> <tag>
/relicgang season create <id> <starts-ms> <ends-ms> <categories-csv> [reward-plan]
/relicgang season finalize <id>
/relicgang season results <id>
/relicgang reload
```

## Help commands

The current command UX has categorized/paginated help.

```text
/prison help [page]
/gang help [page]
/booster help [page]
/relicmine help [page]
/relicgang help [page]
```

When in doubt, use the in-game help because it reflects the command handler shipped with the JAR you are actually running.
