# Stage 0 Audit: Current RelicWorld Prison Stack

> Audit date: 2026-07-30
> Scope: live Prison data, installed Prison JAR, QuickMine, PrestigePlugin, SellAllCustom, and LuckPerms export.

## Inputs reviewed

- `Prison-3.3.0-alpha.19h.jar`
- Full live `plugins/Prison/` data archive
- `original-QuickMine-1.0-SNAPSHOT.jar`
- `original-prestigeplugin-1.0-SNAPSHOT.jar` plus its config and prestige data
- `SellAllCustom.jar` plus its price config
- LuckPerms JSON export dated 2026-07-30

## Confirmed live versions and dependency split

- The installed Prison build is **3.3.0-alpha.19h**, while the GitHub source ZIP is newer. The live JAR must remain the migration source of truth.
- QuickMine is only a two-point wand and command wrapper around old Prison mine commands. Its useful behavior is replaced directly by RelicPrison Stage 5.
- PrestigePlugin owns the seven prestige levels, prestige costs, prestige sell multipliers, rankup cost multipliers, and its own player prestige file.
- SellAllCustom owns the active custom sell-price table and a separate `/sellall` implementation using Vault.
- LuckPerms currently carries A-Z inheritance, prestige inheritance, warp permissions, WorldGuard build permissions, and several unrelated server permissions.

## Data inventory

| Data set | Count |
|---|---:|
| Active mines | 34 |
| Mine worlds | 3 |
| Total normalized mine volume | 1,153,370 blocks |
| Rank definitions | 34 |
| Prison player files | 70 |
| PrestigePlugin player entries | 6 |
| SellAllCustom prices | 16 |
| LuckPerms groups | 39 |
| LuckPerms users | 57 |

## Mine inventory

- A-Z mines: 26
- Prestige mines: Coal, Iron, Gold, Redstone, Diamond, Emerald, Immortal
- PvP mine: 1
- Smallest mine: O (13,230 blocks)
- Largest mine: W (73,926 blocks)

### World-name issue detected

- 32 mines use `prisonmines`.
- Mine O uses `PrisonMines`.
- The PvP mine uses `PrisonHub`.
- RelicPrison stores both world UUID and name, normalizes cuboid corners, and will report case or UUID mismatches instead of silently trusting the string.

### Mine definitions

| Mine | World | Volume | Reset seconds | Composition |
|---|---|---:|---:|---|
| A | prisonmines | 71,050 | 300 | cobblestone 100% |
| B | prisonmines | 29,889 | 300 | cobblestone 90%, coal_ore 10% |
| C | prisonmines | 18,259 | 900 | cobblestone 70%, coal_ore 30% |
| D | prisonmines | 15,876 | 900 | cobblestone 50%, coal_ore 50% |
| E | prisonmines | 50,225 | 900 | cobblestone 20%, coal_ore 80% |
| F | prisonmines | 22,500 | 900 | coal_ore 100% |
| G | prisonmines | 30,618 | 900 | iron_ore 20%, cobblestone 80% |
| H | prisonmines | 22,500 | 900 | cobblestone 60%, iron_ore 40% |
| I | prisonmines | 18,144 | 900 | iron_ore 60%, cobblestone 40% |
| J | prisonmines | 28,431 | 900 | iron_ore 100% |
| K | prisonmines | 16,380 | 900 | gold_ore 20%, cobblestone 80% |
| L | prisonmines | 29,160 | 900 | cobblestone 60%, gold_ore 40% |
| M | prisonmines | 25,515 | 900 | gold_ore 60%, cobblestone 40% |
| N | prisonmines | 19,855 | 900 | gold_ore 80%, cobblestone 20% |
| O | PrisonMines | 13,230 | 600 | gold_ore 100% |
| P | prisonmines | 26,208 | 900 | redstone_ore 20%, cobblestone 80% |
| Q | prisonmines | 42,875 | 900 | redstone_ore 40%, cobblestone 60% |
| R | prisonmines | 35,937 | 900 | cobblestone 40%, redstone_ore 60% |
| S | prisonmines | 42,284 | 900 | cobblestone 20%, redstone_ore 80% |
| T | prisonmines | 23,400 | 300 | redstone_ore 100% |
| U | prisonmines | 37,479 | 900 | diamond_ore 20%, cobblestone 80% |
| V | prisonmines | 42,284 | 900 | diamond_ore 40%, cobblestone 60% |
| W | prisonmines | 73,926 | 900 | diamond_ore 60%, cobblestone 40% |
| X | prisonmines | 52,022 | 900 | diamond_ore 80%, cobblestone 20% |
| Y | prisonmines | 57,498 | 900 | diamond_ore 90%, cobblestone 10% |
| Z | prisonmines | 61,605 | 900 | diamond_ore 100% |
| coal | prisonmines | 23,064 | 900 | coal_block 100% |
| diamond | prisonmines | 49,972 | 900 | diamond_block 100% |
| emerald | prisonmines | 26,250 | 900 | emerald_block 100% |
| gold | prisonmines | 29,889 | 900 | gold_block 100% |
| immortal | prisonmines | 29,889 | 900 | end_stone 100% |
| iron | prisonmines | 28,980 | 300 | iron_block 100% |
| pvp | PrisonHub | 29,016 | 400 | cobblestone 52.65%, coal_ore 5%, iron_ore 5%, gold_ore 5%, diamond_ore 5%, iron_block 5%, gold_block 5%, redstone_block 5%, diamond_block 7.35%, emerald_block 5% |
| redstone | prisonmines | 29,160 | 900 | redstone_block 100% |

## Rank and prestige data

- The old default ladder contains A-Z.
- The old `prestiges` ladder also contains p1-p7, but the separate PrestigePlugin is the actual source of the named Coal-Immortal system.
- A separate `Kos` ladder/rank exists and is intentionally excluded from the new main progression design.
- Current Prison player rank distribution is heavily concentrated at Rank A; migration must not infer prestige from the current A-Z rank.

### Current Prison rank distribution

| Ladder/rank | Players |
|---|---:|
| Kos / Kos | 1 |
| default / A | 58 |
| default / B | 2 |
| default / C | 2 |
| default / F | 1 |
| default / I | 1 |
| default / J | 1 |
| default / S | 1 |
| default / W | 1 |
| default / Z | 3 |

### Prestige configuration confirmed

| Prestige | Cost | Sell multiplier | Rankup cost multiplier |
|---|---:|---:|---:|
| Coal | 16,000,000 | 1.0x | 3.12x |
| Iron | 23,000,000 | 1.5x | 7.98x |
| Gold | 43,000,000 | 2.0x | 18.0x |
| Redstone | 58,000,000 | 2.5x | 33.6x |
| Diamond | 76,000,000 | 3.0x | 56.7x |
| Emerald | 115,000,000 | 4.0x | 115.2x |
| Immortal | 200,000,000 | 5.0x | 247.5x |

## SellAllCustom price table

| Material | Price |
|---|---:|
| COBBLESTONE | 1 |
| STONE | 1 |
| COAL | 4 |
| IRON_INGOT | 8 |
| RAW_IRON | 7 |
| RAW_GOLD | 9 |
| GOLD_INGOT | 10 |
| REDSTONE | 14 |
| DIAMOND | 20 |
| COAL_BLOCK | 23.21 |
| IRON_BLOCK | 33.93 |
| GOLD_BLOCK | 53.57 |
| REDSTONE_BLOCK | 71.43 |
| DIAMOND_BLOCK | 93.75 |
| EMERALD_BLOCK | 142.86 |
| END_STONE | 245.54 |

## LuckPerms findings

- Groups A-Z already form an inheritance chain. A player only needs one direct rank group.
- Prestige groups also form an inheritance chain from Coal through Immortal.
- Rank A currently carries many explicit negative WorldGuard build permissions; later progression stages should remove the need to grant or deny 26 mine-region permissions per player.
- The export contains no LuckPerms tracks. Old Prison commands reference a `mine-ranks` track, so that command path is stale or incomplete.
- RelicPrison Stage 4 treats its database as the source of truth. LuckPerms synchronization is deferred to the progression stages.

## Dependency decisions from the audit

| Existing component | Decision |
|---|---|
| Old Prison mine storage | Read later by the custom migration stage; not used at runtime |
| QuickMine | Replace with RelicPrison selection sessions and particle confirmation |
| PrestigePlugin | Merge into later RelicPrison progression stages |
| SellAllCustom | Merge price logic into later RelicPrison selling stage |
| LuckPerms A-Z/prestige groups | Preserve concept, simplify synchronization later |
| Old Prison API compatibility | Do not emulate |

## Stage 0 risks recorded for migration

1. The installed Prison JAR differs from the GitHub repository version.
2. Prestige exists in two forms: old p1-p7 rank files and the separate named PrestigePlugin data.
3. Some Bedrock-style player files use unusual UUID/name formatting and must be mapped carefully.
4. Mine world names are not consistently cased.
5. Old mine coordinates can be stored in reversed order. RelicPrison normalizes every cuboid.
6. Rank files contain repeated or stale LuckPerms commands; migration should import progression data, not replay those commands.
7. SellAll prices live in SellAllCustom rather than the old Prison SellAll module.
8. Existing player snapshots contain broad permission lists that must not be imported as authoritative progression data.

## Stage 0 conclusion

The live stack can be replaced without emulating the old Prison API. The migration stage should read the live Prison player/rank/mine files, PrestigePlugin prestige data, SellAllCustom prices, and LuckPerms group structure as separate sources, then merge only the fields approved for RelicPrison.