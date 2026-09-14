# Zero-to-Launch Course

This is the long path for a server owner who wants to build RelicPrison in the correct order instead of jumping between random YAML files.

<div class="pathline">Empty Paper server → dependencies → first mine → economy → ranks → prestige → gangs → leaderboards → backups → staging launch</div>

## Module 1: Foundation

### Goal

Reach a clean startup before changing gameplay.

1. Install **Paper 1.21.10** and run it on **Java 25**.
2. Install Vault, a Vault economy provider, and LuckPerms.
3. Add the RelicPrison JAR.
4. Start the server once so `plugins/RelicPrison/` is created.
5. Stop the server and back up that new folder.
6. Start again and run:

```text
/rp status
/rp validate
/rp diagnose
```

**Pass condition:** RelicPrison is enabled, required dependencies are healthy, and validation does not report a problem you are ignoring.

## Module 2: Build Mine A

### Goal

Create one boring, reliable vanilla mine before adding custom content.

```text
/relicmine wand
```

Select two opposite corners, then:

```text
/relicmine create a
/relicmine confirm
/relicmine setspawn a
/relicmine requirement a rank a
/relicmine composition a list
/relicmine info a
```

Start with a simple composition. A known-good first test is 100% cobblestone. Force one reset:

```text
/relicmine reset a
```

**Pass condition:** a normal player with Rank A can teleport, mine, leave, return, and watch the mine reset without staff intervention.

## Module 3: Economy and Selling

### Goal

Prove the money path before introducing multipliers and automation.

1. Set one known vanilla sell price in `sell-prices.yml`.
2. Restart/reload only through the supported path.
3. Give yourself one known item.
4. Compare:

```text
/sellvalue hand
/sellhand
```

5. Verify Vault balance actually changes by the expected amount.

Only after that should you test AutoSell, boosters, custom drops, fortune, AutoBlock, or ItemsAdder selling.

**Pass condition:** one vanilla item has a predictable value and deposits correctly.

## Module 4: Rank Progression

### Goal

Make A → B work before tuning all 26 ranks.

Check `ranks.yml`, Vault cost, Rank B, and LuckPerms behavior. Then test:

```text
/relicrank info <player>
/rankup
/relicrank info <player>
```

Test a failed rankup with too little money too. Failure behavior matters as much as success.

**Pass condition:** the player pays once, advances once, has the expected mine access, and LuckPerms remains consistent.

## Module 5: Prestige

### Goal

Verify the end-of-rank-loop transition.

Use a staging account, place it at Rank Z with admin tools, give the exact required balance, and test the prestige confirmation flow.

```text
/relicrank set <player> z
/prestige
/prestige confirm
```

Then verify rank reset/progression state, sell multiplier, prestige mine access, Vault withdrawal, and LuckPerms state.

**Pass condition:** one prestige completes once and survives reconnect/restart.

## Module 6: Mining Features

Enable features one at a time in `config.yml` and test a known block after each change:

1. AutoPickup
2. AutoSmelt
3. AutoBlock
4. Fortune
5. AutoSell
6. mining XP
7. custom drops
8. block events

Do **not** turn everything on at once. If a result becomes wrong, you want exactly one new variable to investigate.

## Module 7: Gangs

Create two test accounts and walk through the real player lifecycle:

```text
/gang create Relics RLC
/gang invite <player>
/gang invites
/gang accept <invite-id>
/gang bank deposit 1000 test
/gang ranks
/gang missions
/gang sethome
/gang home
```

Then test staff inspection:

```text
/relicgang inspect RLC
/relicgang audit RLC
```

**Pass condition:** membership, ranks, bank history, permissions, home, missions, and audit survive reconnect/restart.

## Module 8: Leaderboards and Placeholders

Install PlaceholderAPI if you use it. Start with simple player placeholders, then leaderboard placeholders.

Useful checks:

```text
%relicprison_rank%
%relicprison_blocks_lifetime%
%relicprison_combined_multiplier%
```

Then configure one leaderboard and verify its cached result. Remember that cached values may temporarily show the configured unavailable text rather than blocking the server thread.

## Module 9: Backups and Recovery

A backup system you never restore-tested is only a hope.

```text
/rp backup create full
/rp backup list
/rp backup verify <id>
```

On a staging copy, schedule/perform the documented restore path and prove the server starts cleanly afterward. MySQL data restore has different limitations than SQLite; read the recovery page before testing it.

## Module 10: Optional Integrations

Only now introduce optional plugins one at a time:

- WorldEdit / FAWE
- WorldGuard
- PlaceholderAPI
- ItemsAdder
- AdvancedEnchantments
- Geyser/Floodgate awareness
- combat-tag provider

Use the [Compatibility Matrix](/reference/compatibility) to see what is implemented versus what still needs real-server verification.

## Module 11: Launch rehearsal

Run a private-access rehearsal with normal player accounts and no operator bypasses.

### Minimum launch checklist

- [ ] `/rp validate` is understood and acceptable.
- [ ] `/rp diagnose` has no unexplained red flags.
- [ ] Mine A works for a non-op.
- [ ] Mine resets under normal load.
- [ ] Selling matches expected value.
- [ ] A → B rankup works and fails safely when unaffordable.
- [ ] Prestige path is tested.
- [ ] LuckPerms state survives reconnect.
- [ ] SQLite/MySQL persistence survives restart.
- [ ] Gangs survive restart.
- [ ] Placeholders/leaderboards update as expected.
- [ ] One full backup verifies successfully.
- [ ] Restore has been rehearsed on staging.
- [ ] Java client GUI/chat is inspected.
- [ ] Bedrock client GUI/chat is inspected if you support Bedrock.
- [ ] ItemsAdder/AdvancedEnchantments are tested if enabled.

::: warning Verification status
The current documented release is `1.0.0`. Passing this course improves confidence, but it does not erase the explicit manual-verification items recorded for the release.
:::
