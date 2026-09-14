# Rewards & Delivery

RelicPrison uses a persistent reward ledger for operations that must survive failures and restarts. This is especially important for bulk mining, progression rewards, gang rewards, and leaderboard payouts where repeating an operation can duplicate value.

## The key idea

A reward is not just "give item now." RelicPrison records enough state to know what was planned, what was delivered, and what still needs recovery. Administrators should investigate the stored delivery state before manually compensating a player.

## Safe reward workflow

1. Define the reward in the owning system's configuration.
2. Test it on staging with a disposable player.
3. Verify money, items, commands, and messages independently.
4. Restart and confirm already-delivered components are not repeated.
5. Simulate a failure where practical and verify recovery.
6. Only then enable the reward in production.

## Reward components

Different systems may build a reward package from multiple components. A package can therefore be partially delivered if an external provider fails. This is why manual "just give everything again" fixes are dangerous.

## Troubleshooting a missing reward

Check, in this order:

1. Was the triggering action actually committed?
2. Was a reward package created?
3. Which components are pending, delivered, or failed?
4. Did Vault, inventory delivery, or a command dependency fail?
5. Did the player disconnect during delivery?
6. Did the server restart during the operation?

Use admin diagnostics and logs before issuing compensation.

## Related pages

- [Leaderboards](/systems/leaderboards)
- [Progression](/systems/progression)
- [Gangs](/systems/gangs)
- [Admin Operations](/admin/operations)
- [Backup & Restore](/admin/backup-restore)
