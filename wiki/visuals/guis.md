# GUI Gallery

RelicPrison has a shared inventory visual system plus feature-specific player/admin menus. This page gives you the menu map, configuration ownership, and a client-capture checklist.

::: warning Live screenshot status
The repository does not contain real Java/Bedrock client screenshots for every redesigned inventory yet, and the RC checklist explicitly says that visual client inspection is still pending. This wiki will **not fabricate screenshots and label them as real**. The gallery below is source-driven; actual client captures should be inserted after the staging matrix is performed.
:::

## Inventory anatomy

The exact slots vary by menu, but the common visual language is:

<div class="gui-shell">
  <div class="gui-grid">
    <span class="gui-slot">N</span><span class="gui-slot"></span><span class="gui-slot"></span><span class="gui-slot"></span><span class="gui-slot active">TITLE</span><span class="gui-slot"></span><span class="gui-slot"></span><span class="gui-slot"></span><span class="gui-slot">N</span>
    <span class="gui-slot"></span><span class="gui-slot active">A</span><span class="gui-slot active">A</span><span class="gui-slot active">A</span><span class="gui-slot active">A</span><span class="gui-slot active">A</span><span class="gui-slot active">A</span><span class="gui-slot active">A</span><span class="gui-slot"></span>
    <span class="gui-slot"></span><span class="gui-slot active">I</span><span class="gui-slot active">I</span><span class="gui-slot active">I</span><span class="gui-slot active">I</span><span class="gui-slot active">I</span><span class="gui-slot active">I</span><span class="gui-slot active">I</span><span class="gui-slot"></span>
    <span class="gui-slot"></span><span class="gui-slot active">A</span><span class="gui-slot active">A</span><span class="gui-slot active">A</span><span class="gui-slot active">A</span><span class="gui-slot active">A</span><span class="gui-slot active">A</span><span class="gui-slot active">A</span><span class="gui-slot"></span>
    <span class="gui-slot"></span><span class="gui-slot"></span><span class="gui-slot"></span><span class="gui-slot active">BACK</span><span class="gui-slot active">PAGE</span><span class="gui-slot active">NEXT</span><span class="gui-slot"></span><span class="gui-slot"></span><span class="gui-slot"></span>
    <span class="gui-slot">F</span><span class="gui-slot">F</span><span class="gui-slot">F</span><span class="gui-slot">F</span><span class="gui-slot">F</span><span class="gui-slot">F</span><span class="gui-slot">F</span><span class="gui-slot">F</span><span class="gui-slot">F</span>
  </div>
</div>

`A` = action/collection entry, `I` = information/state, `N` = navigation/context, `F` = filler/theme. This diagram explains the visual vocabulary; it is not pretending to be a screenshot of one exact inventory.

## Menu/config catalog

| Surface | Config source | What to inspect |
| --- | --- | --- |
| Shared theme | [`guis/theme.yml`](/generated/config/guis-theme) | materials, accents, sound/visual defaults |
| Main prison menu | [`guis/main.yml`](/generated/config/guis-main) | title / main entry surface |
| Mine menus | [`guis/mines.yml`](/generated/config/guis-mines) | mine entries, state visuals, navigation |
| Rank/progression | [`guis/progression.yml`](/generated/config/guis-progression) | progression presentation |
| Prestige | [`guis/prestige.yml`](/generated/config/guis-prestige) | prestige presentation/confirmation context |
| Selling | [`guis/selling.yml`](/generated/config/guis-selling) | sell/value surfaces |
| Statistics | [`guis/statistics.yml`](/generated/config/guis-statistics) | player/leaderboard statistics presentation |
| Boosters | [`guis/boosters.yml`](/generated/config/guis-boosters) | booster status/management surfaces |
| Gangs | [`guis/gangs.yml`](/generated/config/guis-gangs) | gang main/submenus, member/rank/bank/mission flows |
| Admin | [`guis/admin.yml`](/generated/config/guis-admin) | protected staff/editor menus |

## Client screenshot capture matrix

When staging is available, capture these at the same resource-pack/client scale and replace the pending cells with real images:

| Surface | Java | Bedrock via Geyser/Floodgate | Special case |
| --- | --- | --- | --- |
| Main menu | Pending | Pending | navigation/sound |
| Mines | Pending | Pending | locked/unlocked/resetting states |
| Ranks | Pending | Pending | current/next/max rank |
| Prestiges | Pending | Pending | confirmation and max state |
| Stats / leaderboards | Pending | Pending | pagination/loading |
| Boosters | Pending | Pending | active/expired/server/personal |
| Gang main | Pending | Pending | in-gang / no-gang |
| Gang members/ranks | Pending | Pending | permission-limited controls |
| Gang bank/missions/upgrades | Pending | Pending | confirmation/limits |
| Admin menus | Pending | Pending | denied/destructive confirmation |

## Screenshot standard

For every real capture:

1. Use the production resource pack you intend to ship.
2. Record Java/Bedrock client and version.
3. Include one normal state and every meaningful locked/error/confirmation state.
4. Do not crop away the menu title or context needed to understand the image.
5. Store images under `wiki/public/screenshots/` so Pages versions them with the docs.
