# Stage 04

## Goal
Implement Stage 16 Custom Drops and Block Events.

## Implemented Systems
- Custom drop catalog and evaluation.
- Block Event catalog and runtime service.
- Durable Block Event trigger reservation rows.
- Deterministic logical claim keys for first-time and daily entitlements.
- Full-operation chance-per-block probability.

## Important Files
- `CustomDropCatalog.java`
- `BlockEventCatalog.java`
- `BlockEventService.java`
- `BlockEventRepository.java`

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
- Trigger identity must prevent duplicate event observation.

## Actual Verification Status
- Parsing, probability behavior, logical duplicate prevention, package linkage, and repeated recovery of package-backed triggers are automated at repository level.

## Remaining Limitations
- Block Event rewards are delivered through the shared component ledger; RC6 Stage 2 creates logical claims and reward packages atomically for new triggers. Real Paper restart and integration staging remain required.
