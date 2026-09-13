---
layout: home

title: RelicPrison Documentation

titleTemplate: false

hero:
  name: RelicPrison Docs
  text: Build the prison server without guessing.
  tagline: Beginner-first setup guides, complete command and permission references, deep configuration explanations, integrations, recovery tools, and a developer API guide.
  actions:
    - theme: brand
      text: Start the Setup Journey
      link: /guide/installation
    - theme: alt
      text: Commands & Permissions
      link: /reference/commands

features:
  - icon: 🧭
    title: Setup Journey
    details: Follow one path from an empty Paper server to a working mine, ranks, selling, prestige, gangs, and production checks.
  - icon: ⛏️
    title: Every Major System
    details: Mines, resets, progression, economy, boosters, leaderboards, gangs, GUIs, backups, diagnostics, and integrations.
  - icon: 🧩
    title: Copyable Reference
    details: Commands, permissions, placeholders, YAML examples, recommended defaults, and what each setting changes in-game.
  - icon: 🛟
    title: Recovery First
    details: Validation, diagnostics, audit logs, backups, verification, restore staging, and safe troubleshooting are documented as first-class features.
  - icon: 🧱
    title: Beginner Language
    details: Every setup page explains what you are doing, why it matters, what success looks like, and what to check when it fails.
  - icon: 🧪
    title: RC-Aware
    details: The wiki clearly separates implemented behavior from staging-only or still-unverified behavior instead of pretending compilation means production-ready.
---

## Pick your path

<div class="journey-grid">
  <div class="quest-card"><span class="quest-number">1</span><strong>I am brand new</strong><br><br>Start with <a href="/guide/installation">Installation</a>. It assumes you have never configured RelicPrison before.</div>
  <div class="quest-card"><span class="quest-number">2</span><strong>I am building my server</strong><br><br>Use the <a href="/guide/first-server">First Server Setup</a> checklist and build each system in order.</div>
  <div class="quest-card"><span class="quest-number">3</span><strong>I need one answer</strong><br><br>Jump to <a href="/reference/commands">Commands</a>, <a href="/reference/permissions">Permissions</a>, or <a href="/reference/placeholders">Placeholders</a>.</div>
  <div class="quest-card"><span class="quest-number">4</span><strong>Something is broken</strong><br><br>Go directly to <a href="/troubleshooting/">Troubleshooting</a> or <a href="/admin/operations">Diagnostics & Recovery</a>.</div>
</div>

## The RelicPrison mental model

RelicPrison is easier to understand when you picture it as six layers:

1. **World layer**: mines, cuboids, blocks, reset schedules, spawn points, WorldGuard, WorldEdit/FAWE, and optional ItemsAdder blocks.
2. **Progression layer**: player profile, rank, prestige, mine access, LuckPerms groups, and Vault costs.
3. **Mining layer**: normal and bulk mining, fortune, drops, AutoPickup, AutoSell, AutoSmelt, AutoBlock, XP, events, and statistics.
4. **Social layer**: gangs, invitations, ranks, bank, missions, chat, upgrades, homes, seasons, and gang leaderboards.
5. **Presentation layer**: GUIs, messages, sounds, PlaceholderAPI, player menus, admin menus, and configurable formatting.
6. **Safety layer**: validation, durable transaction records, diagnostics, staff audit, backups, restore verification, retries, and repair tools.

If a feature behaves strangely, identify which layer owns it first. That usually tells you which config file and command to inspect.

## Current documented build

<span class="status-pill">RelicPrison 1.0.0-rc6-stage6</span>
<span class="status-pill">Paper 1.21.10</span>
<span class="status-pill">Java 25</span>
<span class="status-pill">Maven</span>

::: warning Staging build
RC6 Stage 6 is documented for controlled Paper staging. Several real-server, integration, Bedrock/Java client, crash-recovery, and multi-server tests are still listed as manual verification work. See [RC6 Staging Status](/releases/rc6).
:::

## The rule used by this wiki

When old notes disagree with the running code, **the current source and current default configuration win**. That matters because older internal install notes still referenced Java 21, while the current build enforces Java 25.
