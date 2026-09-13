# Staging Checklist - 1.0.0-rc6-stage6

## Required Before Final 1.0.0

- [ ] Start, stop, reload configuration, reconnect the database, and restart on Paper 1.21.10 with Java 25.
- [ ] Render every inventory listed in `docs/GUI-DESIGN.md` on a Java client and capture representative screenshots.
- [ ] Repeat player and gang menu interaction on a Bedrock client through Geyser/Floodgate.
- [ ] Confirm titles and structured lore do not clip at the configured GUI scale and supported client languages.
- [ ] Verify meaningful mine, sell-material, rank, prestige, booster, Block Event, reset, gang, leaderboard, and administration icons against real data.
- [ ] Verify locked/current/enabled/disabled/active/completed/online/offline/affordable/unaffordable glow and color transitions after live state changes.
- [ ] Verify previous/page/back/close/next placement, disabled page arrows, search filters, and collection counts in every paginated menu.
- [ ] Verify rank deletion, gang kick, ownership transfer, gang disband, admin delete, reset change, and composition normalization confirmations cannot execute accidentally.
- [ ] Verify click, success, denied, teleport, and delete sounds plus reloadable `guis/theme.yml` overrides.
- [ ] Verify player heads, optional custom model data, and any deployed server resource pack.
- [ ] Verify all original commands, permissions, editor actions, gang mutations, progression actions, persistence, and reconnect recovery remain functional.
- [ ] Repeat the existing Vault, LuckPerms, PlaceholderAPI, ItemsAdder, AdvancedEnchantments, backup/restore, rollback, and two-server database staging matrix.

Automated checks are complete. Every unchecked real-server item remains pending until observed and recorded.
