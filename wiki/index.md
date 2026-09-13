---
layout: home

title: RelicPrison Documentation

titleTemplate: false

hero:
  name: RelicPrison Docs
  text: Build the prison server without guessing.
  tagline: Beginner-first setup, searchable commands, source-generated YAML/API references, compatibility status, recovery playbooks, visual flows, and developer guidance.
  image:
    src: /plugin-icon.webp
    alt: RelicPrison plugin icon
  actions:
    - theme: brand
      text: Start Installation
      link: /guide/installation
    - theme: alt
      text: Browse Full Course
      link: /course/
    - theme: alt
      text: Search Commands
      link: /reference/commands

features:
  - icon: 🧭
    title: Guided Build Path
    details: Follow one path from an empty Paper server to mines, economy, ranks, prestige, gangs, backups, and launch rehearsal.
    link: /course/
    linkText: Open the course →
  - icon: ⛏️
    title: Every Major System
    details: Mines, resets, progression, economy, boosters, leaderboards, gangs, GUIs, backups, diagnostics, and integrations.
    link: /systems/mines
    linkText: Browse systems →
  - icon: 🔎
    title: Interactive Command Explorer
    details: Filter commands by audience/category and search syntax, permission, alias, or purpose.
    link: /reference/commands
    linkText: Search commands →
  - icon: ⚙️
    title: Source-Generated YAML Reference
    details: Every packaged YAML leaf is regenerated during the docs build so new settings do not silently disappear from the wiki.
    link: /generated/config/
    linkText: Browse every setting →
  - icon: 🧩
    title: Source-Generated API Reference
    details: Public service and event inventories are regenerated directly from the Java API package.
    link: /developers/api
    linkText: Open developer docs →
  - icon: 🛟
    title: Recovery First
    details: Diagnostics, audit logs, backup verification, restore staging, repair, error encyclopedia, and safe troubleshooting are first-class docs.
    link: /troubleshooting/
    linkText: Troubleshoot a problem →
---

<div class="home-nav-panel">
  <div class="home-nav-title">Where do you want to go?</div>
  <div class="home-nav-buttons">
    <a class="home-nav-button primary" href="./guide/installation">Start Installation →</a>
    <a class="home-nav-button" href="./course/">Zero-to-Launch Course</a>
    <a class="home-nav-button" href="./reference/commands">Commands</a>
    <a class="home-nav-button" href="./generated/config/">All Config Settings</a>
    <a class="home-nav-button" href="./troubleshooting/">Troubleshooting</a>
  </div>
</div>

## Pick your path

<div class="journey-grid">
  <div class="quest-card"><span class="quest-number">1</span><strong>I am brand new</strong><br><br>Start with <a href="./guide/installation">Installation</a>, then follow the <a href="./course/">Zero-to-Launch Course</a>.</div>
  <div class="quest-card"><span class="quest-number">2</span><strong>I am configuring features</strong><br><br>Use <a href="./recipes/">Copy-Paste Recipes</a> plus the <a href="./generated/config/">complete generated YAML reference</a>.</div>
  <div class="quest-card"><span class="quest-number">3</span><strong>I need one answer</strong><br><br>Jump to <a href="./reference/commands">Commands</a>, <a href="./reference/permissions">Permissions</a>, <a href="./reference/placeholders">Placeholders</a>, or <a href="./reference/compatibility">Compatibility</a>.</div>
  <div class="quest-card"><span class="quest-number">4</span><strong>Something is broken</strong><br><br>Use <a href="./troubleshooting/errors">Error Encyclopedia</a>, <a href="./troubleshooting/">Troubleshooting</a>, or <a href="./admin/operations">Diagnostics & Recovery</a>.</div>
</div>

## The RelicPrison mental model

RelicPrison is easier to understand as six layers:

1. **World layer**: mines, cuboids, blocks, reset schedules, spawn points, WorldGuard, WorldEdit/FAWE, and optional ItemsAdder blocks.
2. **Progression layer**: player profile, rank, prestige, mine access, LuckPerms groups, and Vault costs.
3. **Mining layer**: normal/bulk mining, fortune, drops, AutoPickup, AutoSell, AutoSmelt, AutoBlock, XP, events, and statistics.
4. **Social layer**: gangs, invitations, ranks, bank, missions, chat, upgrades, homes, seasons, and gang leaderboards.
5. **Presentation layer**: GUIs, messages, sounds, PlaceholderAPI, player menus, admin menus, and formatting.
6. **Safety layer**: validation, durable transaction records, diagnostics, staff audit, backups, restore verification, retries, and repair tools.

See [Visual Architecture & Flows](/visuals/) for pipeline diagrams.

## Current documented build

<span class="status-pill">RelicPrison 1.0.0-rc6-stage6</span>
<span class="status-pill">Paper 1.21.10</span>
<span class="status-pill">Java 25</span>
<span class="status-pill">Maven</span>

::: warning Staging build
RC6 Stage 6 is documented for controlled Paper staging. Several real-server, integration, Bedrock/Java client, crash-recovery, and multi-server tests are still explicit manual verification work. See [RC6 Staging Status](/releases/rc6).
:::

## Source-driven documentation rule

When old prose disagrees with current code/configuration, **the current source and packaged defaults win**. YAML and API reference pages are generated during the docs build, while human-written pages explain intent, workflow, risks, and recommended operating practice.

<div class="home-next">
  <div><strong>Ready to begin?</strong><br><span>Go to the first real documentation page and continue from there.</span></div>
  <a href="./guide/installation">Next: Install RelicPrison →</a>
</div>
