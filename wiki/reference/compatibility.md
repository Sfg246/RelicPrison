# Compatibility Matrix

This page separates **implemented support** from **verified production behavior**. Those are not the same thing.

| Component | Role | Current documentation target | Status | What that status means |
| --- | --- | --- | --- | --- |
| Paper | Required server | 1.21.10 | <span class="compat-ok">Build target</span> | The project compiles against this target; real Paper staging remains part of the RC gate. |
| Java | Required runtime/build | 25 | <span class="compat-ok">Required</span> | Maven/CI enforce Java 25 for the current build. |
| Vault | Required dependency | Current compatible API/provider | <span class="compat-stage">Implemented + stage</span> | Economy calls are integrated; failure/ambiguity cases still deserve live staging. |
| Economy provider | Required behind Vault | Provider-specific | <span class="compat-stage">Operator choice</span> | Vault alone is not an economy. A registered provider must exist. |
| LuckPerms | Required dependency | 5.4.x integration; internal verification notes include 5.4.164 | <span class="compat-stage">Implemented + stage</span> | Rank/prestige group mutation and repair exist; real failure/repair paths still need staging confidence. |
| PlaceholderAPI | Optional | Expansion code references 2.11.6 | <span class="compat-ok">Implemented</span> | `relicprison` expansion is registered when available/enabled. |
| WorldEdit | Optional | Runtime integration | <span class="compat-ok">Implemented</span> | Used for selections/structure operations when available. |
| FastAsyncWorldEdit | Optional | Runtime integration | <span class="compat-ok">Implemented</span> | Supported as the WorldEdit/FAWE integration path. |
| WorldGuard | Optional | Runtime integration | <span class="compat-ok">Implemented</span> | Region synchronization/flags are configurable. |
| ItemsAdder | Optional | Runtime integration | <span class="compat-stage">Manual verification pending</span> | Custom block/item paths exist; current RC notes still require real-server ItemsAdder testing. |
| AdvancedEnchantments | Optional | Verification notes reference 9.22.9 | <span class="compat-stage">Manual verification pending</span> | Bridge exists; Drill/Trench/Seismic-style behavior, fortune, durability and outage cases require real staging. |
| Geyser/Floodgate | Optional awareness | Client/provider-specific | <span class="compat-stage">Client testing pending</span> | Awareness exists; every GUI/chat workflow still needs Java + Bedrock visual/behavior checks. |
| SQLite | Built-in storage | sqlite-jdbc 3.53.2.0 | <span class="compat-ok">Implemented</span> | Best starting point for one server; backup verification includes SQLite readability when available. |
| MySQL | Optional storage | mysql-connector-j 9.7.0 | <span class="compat-stage">Implemented + concurrency stage</span> | Shared DB support exists; two-real-server race testing is still an explicit RC item. |
| Combat-tag integration | Optional | Provider configured in `integrations.yml` | <span class="compat-stage">Provider-dependent</span> | `none` is the safe default. Unsupported providers fail open by design where documented. |

## Status definitions

**Implemented** means the integration/service exists in the current source.

**Manual verification pending** means code/tests are not being misrepresented as a real Paper/client/integration test.

**Required** means RelicPrison expects the dependency/runtime for the current build.

## Before enabling an optional integration

1. Make the equivalent vanilla RelicPrison workflow pass first.
2. Enable exactly one optional integration.
3. Restart if that integration is startup-sensitive.
4. Run `/rp validate` and `/rp diagnose`.
5. Test one simple positive case and one failure/outage case.
6. Only then combine it with bulk mining, boosters, custom drops, or cross-server behavior.
