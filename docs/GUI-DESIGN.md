# RelicPrison Inventory Design Audit

## Shared Design System

- `GuiItemBuilder` owns colored names/lore, item amounts, player heads, custom model data, persistent actions, hidden flags, and semantic enchantment glow.
- `GuiVisuals` selects contextual materials and applies consistent visual states: current/selected/active/enabled use aqua or green glow; locked/disabled/dangerous use red or gray without glow; premium/economy values use purple or gold.
- `GuiLayout` standardizes 54-slot pagination at previous `45`, page status `48`, back `49`, close `50`, and next `53`; confirmations use cancel `10`, information `13`, and confirm `16`.
- `GuiThemeConfig` loads validated filler, navigation, confirmation, and sound settings from `guis/theme.yml` during atomic GUI reload.
- Neutral, informational, economy, prestige, dangerous, and administration contexts use distinct but restrained filler accents.

## Complete Inventory Audit

The codebase has two inventory creation surfaces: `PrisonGuiManager` and `MineAdminGui`. Every view dispatched by those surfaces was reviewed and restyled.

### Player Inventories

- Main menu: section-specific icons for mines, progression, prestige, gang, selling, boosters, statistics, and administration.
- Mine directory: contextual ore/block icons, red-glass locked entries, glowing current mine, rank/reset/remaining-block values, and warp/locked instructions.
- Rank progression: visually progressive materials, completed/current/locked states, page status, and rankup actions.
- Rankup and rankup-max confirmations: dedicated red-framed 27-slot layouts.
- Prestige progression and confirmation: purple/rare-material identity, completed/current/locked states, and protected confirmation.
- Selling: emerald/gold economy framing, glowing current multiplier, and enabled/disabled AutoSell styling.
- Active boosters: sell, mining XP, gang, duration, active, and empty states use distinct icons and glow only while active.
- Statistics: period and metric-specific icons with compact colored values.
- Player leaderboards: gold/iron/copper podium blocks, player heads, and highlighted current player.

### Gang Inventories

- Gang headquarters: shield identity plus distinct member, bank, upgrade, rank, mission, booster, leaderboard, statistic, contribution, setting, and audit icons.
- Members and member profile: real player heads, online/offline styling, rank colors, contributions, promote/demote/kick, and protected ownership transfer.
- Ranks, rank editor, and rank permissions: owner/default/custom distinction, enabled/disabled permission wool, owner protection, creation/editing, and confirmed deletion.
- Upgrades: tier-specific icons with maxed, affordable, prerequisite-locked, and unaffordable states based on current gang data.
- Bank: gold framing, balance/capacity, deposits, withdrawals, and green/red transaction history.
- Statistics and contributions: metric icons, player heads, online/current-player highlighting, and pagination.
- Boosters: active/scheduled/expired states with semantic sell/mining/gang icons.
- Missions: active/in-progress, completed, claimed, and locked/reset states with objective/reward values.
- Gang leaderboards: podium materials and highlighted current gang.
- Settings: privacy, home, identity, MOTD, and dangerous disband controls.
- Audit log: success/failure state icons and paginated actor/action details.
- Dangerous gang confirmations: kick, ownership transfer, rank deletion, and disband use the shared protected layout.

### Administration Inventories

- Administration control center: visually distinct mine, composition, reset, rank, prestige, sell, booster, Block Event, integration, diagnostic, and player sections.
- Mine administration: contextual mine materials, explicit enabled/disabled state, status map, consistent close navigation, and configured filler.
- Mine composition editor: mine/material context, selection glow, property icons, create/edit/remove actions, and protected normalization.
- Reset settings editor: clock, redstone, compass, recovery compass, TNT, and state-aware property controls with protected high-impact changes.
- Rank editor: progressive rank icons, enabled/current styling, contextual properties, creation, and confirmed deletion.
- Prestige editor: rare progression materials, purple state language, contextual properties, creation, and confirmed deletion.
- Sell-price editor: the actual vanilla material where available, gold values, enabled/disabled state, creation, search, and confirmed deletion.
- Booster manager: sell/mining/gang/time icons, active/disabled styling, schedule/runtime details, creation, and confirmed cancellation/deletion.
- Block Event editor: trigger/objective-related icons, enabled/disabled state, reward/property detail, creation, and confirmed deletion.
- Integration status, diagnostics, and player progression: comparator/status, diagnostic, recovery, and player-oriented icons instead of generic paper entries.
- All growing administration lists use search, result counts, bounded pages, disabled unavailable arrows, predictable back/close locations, and consistent action sounds.

## Representative Text Layouts

### Mine Directory - 54 Slots

- Top/content grid: up to 36 mines with their progression material; current mine glows aqua, unlocked mines are green, and locked mines are red glass.
- Lore: one-line destination description, blank line, required rank/reset/blocks remaining, blank line, status and action.
- Bottom row: previous at `45`, page/results at `48`, back at `49`, close at `50`, and next at `53`.

### Gang Headquarters - 54 Slots

- Center identity: glowing shield with name, tag, level, XP, points, membership, and bank summary.
- Functional groups: members/ranks/contributions, bank/upgrades/boosters, missions/stats/leaderboards, settings/audit.
- Dangerous disband is not exposed on the overview; it lives in settings and requires confirmation.

### Dangerous Confirmation - 27 Slots

- Red stained-glass frame communicates danger before text is read.
- Slot `10`: red wool cancel; slot `13`: TNT information summary; slot `16`: lime wool explicit confirmation.
- Close/cancel returns safely without executing the mutation.

### Administration Editor List - 54 Slots

- Content uses the managed object itself where possible: sell material, mine ore, progressive rank/prestige icon, booster icon, event trigger, or reset property.
- Bottom row contains previous, page/result/filter status, back, close, and next; create/search controls are stable and visually distinct.

## Configuration

- Global theme and sounds: `src/main/resources/guis/theme.yml`.
- Main, mine, progression, prestige, selling, booster, statistics, gang, and administration titles/defaults: the matching files under `src/main/resources/guis/`.
- Defaults are complete; operators do not need to configure each item before the menus are usable and polished.

## Staging Boundary

- No real Paper client was available, so screenshots could not be captured.
- The supplied workspace did not contain `WarpMenuPlugin-v1.6.0`, its `config.yml`, `MenuManager`, or `ItemUtil`; direct file inspection was therefore impossible. The implementation follows the requested visual language without copying an unavailable source.
- Java and Bedrock client rendering, text clipping, resource-pack model behavior, click routing, and sound balance remain mandatory real Paper staging tasks.
