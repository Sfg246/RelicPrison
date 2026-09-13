# Visual Architecture & Flow Guides

These diagrams are documentation views of the current architecture. They explain *where* to look when something fails without pretending every subsystem is one synchronous transaction.

## Mining pipeline

<div class="flow-diagram">
  <div class="flow-node">Block break / bulk source</div><div class="flow-arrow">→</div>
  <div class="flow-node">Normalize operation</div><div class="flow-arrow">→</div>
  <div class="flow-node">Drops / fortune / custom rules</div><div class="flow-arrow">→</div>
  <div class="flow-node">Smelt / AutoBlock</div><div class="flow-arrow">→</div>
  <div class="flow-node">AutoPickup / AutoSell / overflow</div><div class="flow-arrow">→</div>
  <div class="flow-node">Stats + gang contribution</div>
</div>

The durable mining work introduced in RC6 freezes committed delivery outcomes for bulk operations so recovery does not have to guess from live configuration.

## Rankup / prestige transaction

<div class="flow-diagram">
  <div class="flow-node">Validate profile + cost</div><div class="flow-arrow">→</div>
  <div class="flow-node">Persist withdrawal intent</div><div class="flow-arrow">→</div>
  <div class="flow-node">Vault operation</div><div class="flow-arrow">→</div>
  <div class="flow-node">Profile transition</div><div class="flow-arrow">→</div>
  <div class="flow-node">LuckPerms state</div><div class="flow-arrow">→</div>
  <div class="flow-node">Rewards / completed record</div>
</div>

This is why `/rp progression ...` and repair/reconciliation tools exist. Vault and an SQL database cannot become one magical atomic transaction.

## Mine reset lifecycle

<div class="flow-diagram">
  <div class="flow-node">Timer / mined % / empty / force</div><div class="flow-arrow">→</div>
  <div class="flow-node">Warnings</div><div class="flow-arrow">→</div>
  <div class="flow-node">Evacuate if configured</div><div class="flow-arrow">→</div>
  <div class="flow-node">Queued placement slices</div><div class="flow-arrow">→</div>
  <div class="flow-node">MSPT throttle / pause</div><div class="flow-arrow">→</div>
  <div class="flow-node">Complete or failed record</div>
</div>

## Backup / restore safety path

<div class="flow-diagram">
  <div class="flow-node">Flush supported state</div><div class="flow-arrow">→</div>
  <div class="flow-node">Create archive + metadata</div><div class="flow-arrow">→</div>
  <div class="flow-node">Checksum / ZIP / YAML / DB verification</div><div class="flow-arrow">→</div>
  <div class="flow-node">Safety backup</div><div class="flow-arrow">→</div>
  <div class="flow-node">Stage restore</div><div class="flow-arrow">→</div>
  <div class="flow-node">Apply during startup</div>
</div>

## Six-layer ownership map

| Layer | Owns | First place to inspect |
| --- | --- | --- |
| World | mines, regions, resets, custom blocks | `mines.yml`, `/relicmine`, reset diagnostics |
| Progression | ranks, prestiges, access | `ranks.yml`, `prestiges.yml`, progression diagnostics |
| Mining | break/bulk pipeline, transforms, drops | `mining.yml`, feature gates, integration health |
| Social | gangs, bank, missions, seasons | `gangs.yml`, `/relicgang`, gang audit |
| Presentation | GUIs, messages, placeholders | `guis/*.yml`, `messages.yml`, PlaceholderAPI |
| Safety | transactions, audit, backup, repair | `/rp diagnose`, `/rp audit`, `/rp backup`, `/rp repair` |

Continue to the [GUI Gallery](/visuals/guis) for menu ownership and the client-capture matrix.
