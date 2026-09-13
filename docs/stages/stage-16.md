# Stage 16

## Goal
Official Stage 16 Custom Block Drops and Block Events.

## Implemented Systems
- Custom Drops with restrictions, priorities, chance, item rewards, command rewards, limits, and ItemsAdder ID resolution where available.
- Block Events with restrictions, chance, cooldown/first/daily state, trigger reservations, and deterministic logical claim keys.

## Important Files
- `CustomDropCatalog.java`
- `BlockEventCatalog.java`
- `BlockEventService.java`

## Database Migrations
- `rp_block_event_state`
- `rp_block_event_triggers`

## Config Migrations
- `custom-drops.yml`
- `block-events.yml`

## Commands
- `/rp reload custom-drops`
- `/rp reload block-events`

## Permissions
- `relicprison.admin.reload`

## Tests
- `BlockEventCatalogTest`
- `BlockEventChanceTest`
- `BlockEventRepositoryTest`

## Exit Requirements
- No duplicate reward for one block/action after restart.

## Actual Verification Status
- Trigger reservation, chance math, duplicate logical-claim prevention, package ID linkage, and repeated package-backed recovery are tested.
- RC6 Stage 2 Block Events no longer rely on random trigger UUIDs for first-time/daily rewards; new triggers reserve state and create shared reward packages/components in one database transaction.

## Remaining Limitations
- Real restart-after-trigger, cooldown recovery, milestone recovery, and announcement-once behavior must still be staged on Paper before final `1.0.0`.
