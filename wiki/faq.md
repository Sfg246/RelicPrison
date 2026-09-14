# FAQ & “I Want To…”

Use this page when you know the result you want but do not know which RelicPrison system owns it.

## I want to install RelicPrison

Start with [Installation](/guide/installation), then follow the [Zero-to-Launch Course](/course/). Do not enable every optional integration before the base plugin starts cleanly.

## I want to create a mine

Follow [Create Your First Mine](/guide/first-mine). Prove region selection, spawn, composition and reset behavior before adding custom blocks or bulk enchants.

## I want to change what a mine contains

Use the mine composition settings/editor described in [Mines & Resets](/systems/mines). For ItemsAdder blocks, confirm the integration and block ID first.

## I want mines to reset automatically

Configure the reset settings for that mine and review the reset engine options. Test countdown, evacuation and retry behavior on staging.

## I want players to rank up

Configure `ranks.yml`, a working Vault economy and LuckPerms integration as needed, then use [Ranks & Prestiges](/systems/progression).

## I want prestiges

Use `prestiges.yml` and [Ranks & Prestiges](/systems/progression). Test the reset/reward behavior with a disposable staging profile before enabling it for real players.

## I want AutoSell

First prove Vault and your base sell prices. Then enable/test the route using [Mining Pipeline](/systems/mining) and [Economy](/systems/economy).

## I want AutoPickup or AutoBlock

See [Mining Pipeline](/systems/mining). Test full inventories and conversion failures so you know the fallback behavior is safe.

## I want custom ItemsAdder blocks or items

Install a compatible ItemsAdder version, enable the integration, confirm the namespace/ID exists, then use the relevant mine/drop/reward configuration. See [Integrations](/configuration/integrations).

## I want AdvancedEnchantments mining effects

Use the supported integration path and verify affected blocks are routed through RelicPrison's bulk mining pipeline. See [Mining Pipeline](/systems/mining) and [Compatibility](/reference/compatibility).

## I want timed sell boosters

Use [Boosters](/systems/boosters). Start with one small multiplier and prove stacking/restart behavior before selling large boosters.

## I want mining events

Use [Block Events](/systems/block-events). Build one small event first, then verify the claim/reward is not duplicated.

## I want leaderboards and prizes

Use [Leaderboards](/systems/leaderboards). Confirm raw statistics first, then ranking, then period finalization, then rewards.

## I want gangs

Use [Gangs](/systems/gangs). Configure permissions, limits, bank/upgrades/missions and test ownership/disband safety before launch.

## I want to change menus

Use [GUIs & Admin Editors](/systems/guis) and [GUI YAML](/visuals/guis). Existing GUI files are not automatically replaced during upgrades.

## I want a placeholder for a scoreboard/tab list

Open [Placeholders](/reference/placeholders). PlaceholderAPI must be installed and the RelicPrison expansion must be active.

## I want to know a command or permission

Use the [Command Explorer](/reference/commands) and [Permissions](/reference/permissions). They are better than copying commands from an old Discord message or build.

## I want to upgrade an existing server

Use the [Upgrade, Migration & Rollback Center](/upgrading/). Always stage the upgrade against a copy of production data first.

## My mine is not resetting

Go to [Troubleshooting](/troubleshooting/) and [Error Encyclopedia](/troubleshooting/errors). Check the mine definition, reset state, region, integration availability and diagnostics before manually changing files.

## My server will not start after installing RelicPrison

Read the first RelicPrison-related exception in the startup log, not only the final “plugin disabled” line. Then use [Installation](/guide/installation), [Compatibility](/reference/compatibility), and [Error Encyclopedia](/troubleshooting/errors).

## A player says they did not get a reward

Do not immediately pay them manually. Check [Rewards & Recovery](/systems/rewards) and [Admin & Recovery](/admin/operations) first so you do not duplicate a reward that already committed externally.

## A player says they were paid twice

Preserve logs and transaction/reward records. Do not delete database rows to “clean it up.” Use diagnostics and determine whether the duplicate came from RelicPrison, an external command, or an economy provider.

## Can I use this on production right now?

`1.0.0` is the official final version, but automated success does not replace the still-pending real-server and integration matrix. Check [Release Status](/releases/) and [Known Limitations](/known-limitations) before deciding where to run it.

## I still cannot find the answer

Search the site first. If the documentation is missing or wrong, use the **Report docs feedback** link at the bottom of the relevant page or open a GitHub issue with the exact version, server software, error/log excerpt and steps to reproduce.
