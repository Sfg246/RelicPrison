# 2. Understand How RelicPrison Works

<div class="pathline">Setup Journey → Installation → <strong>Understand the plugin</strong> → First server setup → First mine</div>

You do not need to memorize the code. You only need a good picture of which system owns which job.

## A player's normal loop

A basic RelicPrison player loop looks like this:

```text
Join server
  ↓
Receive/load RelicPrison profile
  ↓
Enter an unlocked mine
  ↓
Break configured blocks
  ↓
Receive/sell/process rewards
  ↓
Earn money and statistics
  ↓
/rankup
  ↓
Unlock later ranks/mines
  ↓
Reach Rank Z
  ↓
/prestige
  ↓
Repeat with prestige progression and multipliers
```

Gangs, leaderboards, boosters, block events, custom drops, and other systems sit around that core loop.

## The six systems you should recognize

### 1. Mines

A mine is more than a box of blocks. RelicPrison tracks:

- its world and cuboid bounds;
- whether it is enabled;
- its spawn point;
- its block composition;
- reset rules and warnings;
- rank, prestige, or permission requirements;
- metadata and reset hooks;
- current runtime/reset state.

### 2. Progression

A player profile stores the progression state RelicPrison needs. Rank and prestige operations can involve:

- Vault money;
- RelicPrison profile state;
- LuckPerms group updates;
- unlocks;
- configured rewards/commands;
- durable progression transaction records.

That is why rankup is treated as a transaction instead of “subtract money, then hope every later step works.”

### 3. Mining and selling

Mining can pass through several systems depending on your settings:

- normal or bulk block processing;
- configured drops;
- custom block handling;
- fortune;
- AutoPickup;
- AutoSell;
- AutoSmelt;
- AutoBlock;
- mining XP;
- statistics;
- block events;
- gang contributions.

Do not enable every mining feature at once on your first boot. Turn on one feature, test it, then continue.

### 4. Gangs

RelicPrison gangs are **prison progression groups**, not Factions.

They include membership, ranks, bank, upgrades, missions, chat, homes, boosters, leaderboards, statistics, seasons, and audit records. They do not claim chunks or implement TNT raiding/territory warfare.

### 5. Presentation

Players interact with the same underlying services through different surfaces:

- commands;
- GUIs;
- messages;
- sounds;
- PlaceholderAPI;
- menus and leaderboards.

A GUI is not a separate version of the data. It is another way of reaching the same system.

### 6. Safety and recovery

RelicPrison contains operational tooling because prison servers move valuable player state.

The important safety tools are:

```text
/rp validate
/rp diagnose
/rp diagnostic
/rp audit
/rp backup ...
/rp repair ...
/rp progression ...
/rp rewards ...
```

## What is stored where?

<div class="journey-grid">
<div class="reference-card"><strong>YAML configuration</strong><br><br>What you want the server to do: mines, prices, ranks, settings, integrations, GUIs, and feature rules.</div>
<div class="reference-card"><strong>Database</strong><br><br>Runtime/player state: profiles, transactions, statistics, gangs, rewards, audits, reset state, and other persistent records.</div>
<div class="reference-card"><strong>LuckPerms</strong><br><br>Permission/group state used by progression and server authorization.</div>
<div class="reference-card"><strong>Vault economy</strong><br><br>Money balances and economic deposits/withdrawals.</div>
</div>

## Why this matters when troubleshooting

If `/rankup` says a player cannot afford the next rank, you investigate the **economy/progression** side.

If a mine exists but the player cannot enter it, you investigate **mine access**, their **rank/prestige**, and possibly **permissions**.

If the GUI looks wrong but the command works, you investigate the **presentation/configuration** side instead of the database.

If a player has a profile but the LuckPerms group is wrong, you investigate **progression synchronization/repair**, not mine composition.

## The “one change at a time” rule

For your first setup:

1. Get the plugin healthy.
2. Create one mine.
3. Test teleporting.
4. Test one rankup.
5. Test selling.
6. Test a mine reset.
7. Add optional integrations.
8. Add advanced mining features.
9. Add gangs/leaderboards.
10. Only then expand the full server.

This makes problems small enough to identify.

## Next

Continue to [3. First Server Setup](/guide/first-server).
