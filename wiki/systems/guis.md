# GUIs & Admin Editors

RelicPrison uses a shared GUI visual system for player menus and staff tools. Stage 6 added a consistent theme, state-aware icons, structured lore, navigation, confirmation screens and configurable interaction sounds.

## Player-facing GUI states

Menus can communicate states such as:

- current / completed / locked progression;
- affordable / unaffordable actions;
- online / offline players;
- enabled / disabled features;
- active / expired booster state;
- available / unavailable mine access;
- mission / upgrade progress.

The goal is that a player should understand what an item means before clicking it.

## Admin editors

The current admin editor workflows cover ranks, prestiges, sell prices, boosters, Block Events, reset settings and mine composition. Edits are guarded with session-bound actions, validation, stale-revision checks, atomic file replacement/rollback copies, runtime reload and staff audit records.

## Destructive confirmation screens

High-risk actions use dedicated confirmation inventories instead of executing immediately. Examples include rank deletion, gang kick, ownership transfer, gang disband, admin deletion, reset changes and composition normalization.

::: danger Do not bypass confirmation flows in custom staff procedures
A confirmation screen is part of the safety model, not decoration.
:::

## Theme configuration

GUI defaults live under `guis/*.yml`, including shared theme values. Existing server files are not automatically overwritten on upgrade, so older installations must deliberately merge new defaults.

Use [GUI YAML Reference](/visuals/guis) for the file-by-file map.

## Real-client verification checklist

Because rendered inventory proportions depend on the actual Minecraft client/resource pack, verify these on a staging server:

1. Every title fits on Java and Bedrock clients you support.
2. Lore wraps without hiding critical information.
3. Locked/current/success/error states are visually distinct.
4. Click routing matches the icon description.
5. Back/next/close controls are consistent.
6. Confirmation screens cannot be mistaken for ordinary menus.
7. Sounds are noticeable but not excessive.
8. Custom-model-data icons resolve with your resource pack.

## Visual walkthroughs

Open [Visual Walkthroughs](/visuals/tutorials) for a screen-by-screen checklist of the most important setup flows.

## Related pages

- [Visuals & GUI Gallery](/visuals/)
- [Configuration Map](/configuration/)
- [Admin & Recovery](/admin/operations)
- [Known Limitations](/known-limitations)
