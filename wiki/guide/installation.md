# 1. Install RelicPrison

<div class="pathline">Setup Journey → <strong>Installation</strong> → Understand the plugin → First server setup → First mine</div>

This page assumes you are starting with a Paper server and you want the safest possible first boot.

## What you need

| Requirement | What it is for | Required? |
|---|---|---|
| Paper `1.21.5` through `26.2` | Startup-tested Paper range for RelicPrison 1.0.0 | Yes |
| Java `25` | The Java runtime used and enforced by the current build | Yes |
| Vault | Standard economy bridge | Yes |
| A Vault economy provider | Actually stores player money | Yes |
| LuckPerms | Rank/prestige permission-group synchronization | Yes |
| PlaceholderAPI | RelicPrison placeholders in scoreboards, TAB, holograms, etc. | Optional |
| WorldEdit or FAWE | Selection/structure integration | Optional |
| WorldGuard | Mine-region synchronization and protection | Optional |
| ItemsAdder | Custom items/blocks in supported RelicPrison systems | Optional |
| AdvancedEnchantments | Supported mining/enchantment integration | Optional |
| Geyser/Floodgate | Bedrock-awareness paths | Optional |

::: tip Paper compatibility
The exact RelicPrison 1.0.0 release JAR has reached `READY` in automated Paper startup probes on `1.21.5`, `1.21.6`, `1.21.7`, `1.21.8`, `1.21.9`, `1.21.10`, `1.21.11`, `26.1.1`, `26.1.2`, and `26.2` using Java 25.

Paper `1.21.4` does not pass the compatibility probe with this release. Its plugin remapper fails on the Java 25 class format used by RelicPrison 1.0.0, so `1.21.4` and older versions are not advertised as supported.
:::

::: danger Java 21 is not enough for this build
Some older engineering notes mention Java 21. The current Maven build explicitly compiles for Java 25 and rejects a Java version outside the Java 25 range. Use Java 25 for RelicPrison 1.0.0.
:::

## Step 1: stop the server

Do not install or replace the plugin while Paper is actively running.

A clean stop gives Paper and your existing plugins time to save their data.

## Step 2: install the required plugins

Put these in your server's `plugins/` directory:

```text
Vault.jar
LuckPerms-Bukkit.jar
<your economy plugin>.jar
RelicPrison-1.0.0.jar
```

Your economy plugin is separate from Vault. Vault is a bridge. It does **not** create an economy by itself.

<div class="beginner"><strong>Think of Vault like an adapter.</strong><br>RelicPrison asks Vault to withdraw or deposit money. Vault passes that request to the economy plugin that actually stores the balance.</div>

## Step 3: install optional integrations only if you use them

You do not need to install every plugin listed in `softdepend`.

For a simple first test, keep it small:

```text
Paper
Vault
Economy provider
LuckPerms
RelicPrison
```

Add PlaceholderAPI, WorldGuard, FAWE, ItemsAdder, AdvancedEnchantments, and Geyser/Floodgate after the core server boots correctly.

## Java 25 and SQLite native access

If `storage.yml` uses SQLite, start Paper with the Java 25 native-access permission required by `sqlite-jdbc`:

```text
java --enable-native-access=ALL-UNNAMED -jar paper.jar --nogui
```

This flag must be supplied when the server JVM starts. RelicPrison cannot relaunch Paper or grant native access to itself after startup.

## Step 4: start Paper once

Start the server and watch the console.

Your first goal is not to configure everything. Your first goal is simply:

- Paper reaches a normal running state.
- Vault finds an economy provider.
- LuckPerms loads.
- RelicPrison enables.
- `plugins/RelicPrison/` is created.
- RelicPrison does not stop because of a database or configuration error.

## Step 5: run the first health commands

In game as an operator, or from console where supported:

```text
/rp status
/rp validate
/rp diagnose
```

### What each one means

**`/rp status`** answers: “Is the plugin running and what is its current runtime health?”

**`/rp validate`** answers: “Do my current configuration files make sense to RelicPrison?”

**`/rp diagnose`** answers: “What are the live systems doing right now, including database, integrations, resets, progression, and other runtime counters?”

::: tip Your first success checkpoint
If RelicPrison loads and `/rp validate` does not report a blocking configuration problem, move to [Understand the Plugin](/guide/how-it-works).
:::

## Where the files live

After the first boot, RelicPrison's live files are under:

```text
plugins/
└── RelicPrison/
    ├── config.yml
    ├── storage.yml
    ├── integrations.yml
    ├── mines.yml
    ├── ranks.yml
    ├── prestiges.yml
    ├── sell-prices.yml
    ├── boosters.yml
    ├── mining.yml
    ├── custom-drops.yml
    ├── block-events.yml
    ├── gangs.yml
    ├── leaderboards.yml
    ├── leaderboard-rewards.yml
    ├── backups.yml
    └── guis/
```

You will not edit all of these on day one.

## Safe first-install checklist

<ul class="big-checklist">
<li>☐ Server is stopped before changing JARs.</li>
<li>☐ Java reports version 25.</li>
<li>☐ Paper is within the supported 1.21.5–26.2 startup-tested range.</li>
<li>☐ Vault is installed.</li>
<li>☐ A real Vault economy provider is installed.</li>
<li>☐ LuckPerms is installed.</li>
<li>☐ RelicPrison starts without a blocking error.</li>
<li>☐ `/rp validate` completes.</li>
<li>☐ `/rp diagnose` shows the integrations you expect.</li>
</ul>

## Next

Continue to [2. Understand the Plugin](/guide/how-it-works).
