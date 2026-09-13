# Stages 11-14 Current Status

This file supersedes older completion-matrix wording. Current stage-level evidence is in `docs/stages/stage-11.md` through `docs/stages/stage-14.md`.

## Implemented

- Selling, multipliers, boosters, mining pipeline, direct XP, AutoPickup, AutoSell, AutoSmelt, AutoBlock, and optional AdvancedEnchantments integration boundaries.
- Runtime optional integration detection for Vault, LuckPerms, WorldEdit/FAWE, WorldGuard, and AdvancedEnchantments.
- Performance counters and diagnostic exposure for relevant subsystems.

## Corrected in `1.0.0-rc3`

- Normal mining commits only after final cancellation state.
- AdvancedEnchantments bridge attempts to submit reflected affected block collections to the real bulk pipeline once per effect event.
- Generic Vault withdrawal ambiguity is explicitly represented and routed to staff review.

## Manual Verification Still Required

- Real AdvancedEnchantments 9.22.9 Drill, Trench, Seismic Drill, Fortune, durability, and outage behavior.
- Real Vault economy outage and crash-window behavior.
- Real LuckPerms failure and repair behavior.
- RelicProfiler/JFR profiling on staging.

## RC6 Stage 3 Supersession

`1.0.0-rc6-stage3` is now the active controlled-staging candidate. Use `docs/CURRENT_STATE.md`,
`BUILD-REPORT-1.0.0-rc6-stage3.md`, and `STAGING-CHECKLIST-1.0.0-rc6-stage3.md` for current evidence.

RC6 Stage 3 keeps the Stage 1 bulk/statistics corrections, Stage 2 Block Event/leaderboard recovery, and adds the approved admin GUI editors. Real Paper and AdvancedEnchantments Drill/Trench/Seismic staging remain required before final `1.0.0`.

