# GUIs & Visual Configuration

RelicPrison's GUI layer gives players and administrators consistent menus without hard-coding every title, material, slot, and piece of lore in Java. Shipped GUI files live under `guis/` in the plugin data folder after first startup.

## GUI files

The project ships configuration for the main menu, mines, progression, prestige, selling, statistics, boosters, gangs, administration, and the shared theme.

## Safe editing workflow

1. Stop or stage the server before large GUI changes.
2. Back up `plugins/RelicPrison/guis/`.
3. Change one screen at a time.
4. Keep inventory sizes valid for Minecraft inventories.
5. Make sure configured slots fit inside the chosen size.
6. Use valid Bukkit/Paper materials unless the documented field supports something else.
7. Reload and open the menu with both player and admin permission sets.
8. Test every clickable item, back button, next/previous page action, and close path.

## Theme

`guis/theme.yml` contains shared presentation choices. Use it for consistent colors, filler items, navigation language, and visual patterns instead of manually making every menu look different.

## Admin editor

RelicPrison includes an admin GUI editing service and validation safeguards. Use the supported editor for fields it exposes, but keep source-controlled backups of production configuration so accidental edits can be reviewed and rolled back.

## Design rules

- Keep primary actions in predictable positions.
- Use the same material/icon for the same action across menus.
- Never rely on color alone to communicate destructive actions.
- Keep lore short enough to read on common client sizes.
- Test Java and Bedrock clients if Geyser/Floodgate are part of your network.

## Troubleshooting

If a GUI will not open, check its YAML syntax, configured size, materials, and permission. If clicks do nothing, verify the clicked item still maps to the expected action and that another inventory plugin is not cancelling the event.

## Related pages

- [Visual Guide](/visuals/)
- [GUI Gallery & Walkthroughs](/visuals/guis)
- [Configuration](/configuration/)
- [Permissions](/reference/permissions)
