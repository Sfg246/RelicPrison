---
layout: home

title: RelicPrison Documentation

titleTemplate: false

hero:
  name: RelicPrison Docs
  text: Build the prison server without guessing.
  tagline: Beginner-first setup, every major system, searchable commands, source-generated YAML/API references, upgrades, recovery playbooks, visual walkthroughs, and developer guidance.
  image:
    src: /plugin-icon-live.webp
    alt: RelicPrison plugin icon
  actions:
    - theme: brand
      text: Start Installation
      link: /guide/installation
    - theme: alt
      text: Browse Full Course
      link: /course/
    - theme: alt
      text: I Want To…
      link: /faq

features:
  - icon: 🧭
    title: Guided Build Path
    details: Follow one path from an empty Paper server to mines, economy, ranks, prestige, gangs, backups, and launch rehearsal.
    link: /course/
    linkText: Open the course →
  - icon: ⛏️
    title: Every Major System
    details: Mines, mining, progression, economy, boosters, Block Events, leaderboards, rewards, statistics, gangs, GUIs, backups, diagnostics, and integrations.
    link: /systems/
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
    details: Diagnostics, audit logs, backup verification, restore staging, reward recovery, repair, error encyclopedia, and safe troubleshooting are first-class docs.
    link: /troubleshooting/
    linkText: Troubleshoot a problem →
  - icon: 🔄
    title: Upgrade Without Guessing
    details: Backup, compare configs, stage migrations, run an acceptance matrix, promote safely, and know how to roll back the whole state.
    link: /upgrading/
    linkText: Open upgrade center →
  - icon: ❓
    title: “I Want To…” FAQ
    details: Start with the outcome you want and jump directly to the right system, config, command, or recovery guide.
    link: /faq
    linkText: Find my task →
---

<div class="home-nav-panel">
  <div class="home-nav-title">Where do you want to go?</div>
  <div class="home-nav-buttons">
    <a class="home-nav-button primary" href="./guide/installation">Start Installation →</a>
    <a class="home-nav-button" href="./course/">Zero-to-Launch Course</a>
    <a class="home-nav-button" href="./systems/">All Systems</a>
    <a class="home-nav-button" href="./faq">I Want To…</a>
    <a class="home-nav-button" href="./reference/commands">Commands</a>
    <a class="home-nav-button" href="./generated/config/">All Config Settings</a>
    <a class="home-nav-button" href="./upgrading/">Upgrade / Migrate</a>
    <a class="home-nav-button" href="./troubleshooting/">Troubleshooting</a>
  </div>
</div>

## Pick your path

<div class="journey-grid">
  <div class="quest-card"><span class="quest-number">1</span><strong>I am brand new</strong><br><br>Start with <a href="./guide/installation">Installation</a>, then follow the <a href="./course/">Zero-to-Launch Course</a>.</div>
  <div class="quest-card"><span class="quest-number">2</span><strong>I am configuring features</strong><br><br>Use <a href="./systems/">System Guides</a>, <a href="./recipes/">Copy-Paste Recipes</a>, and the <a href="./generated/config/">complete generated YAML reference</a>.</div>
  <div class="quest-card"><span class="quest-number">3</span><strong>I know what I want, not where it is</strong><br><br>Use the <a href="./faq">“I Want To…” FAQ</a> to jump straight to the right workflow.</div>
  <div class="quest-card"><span class="quest-number">4</span><strong>Something is broken</strong><br><br>Use <a href="./troubleshooting/errors">Error Encyclopedia</a>, <a href="./troubleshooting/">Troubleshooting</a>, or <a href="./admin/operations">Diagnostics & Recovery</a>.</div>
  <div class="quest-card"><span class="quest-number">5</span><strong>I am upgrading an existing server</strong><br><br>Use the <a href="./upgrading/">Upgrade, Migration & Rollback Center</a> before replacing the live JAR.</div>
  <div class="quest-card"><span class="quest-number">6</span><strong>I need release truth</strong><br><br>Check <a href="./releases/">Release Status</a>, <a href="./known-limitations">Known Limitations</a>, and the <a href="./changelog/">Changelog</a>.</div>
</div>

## The RelicPrison mental model

RelicPrison is easier to understand as six layers:

1. **World layer**: mines, cuboids, blocks, reset schedules, spawn points, WorldGuard, WorldEdit/FAWE, and optional ItemsAdder blocks.
2. **Progression layer**: player profile, rank, prestige, mine access, LuckPerms groups, and Vault costs.
3. **Mining layer**: normal/bulk mining, fortune, drops, AutoPickup, AutoSell, AutoSmelt, AutoBlock, XP, Block Events, rewards, and statistics.
4. **Social layer**: gangs, invitations, ranks, bank, missions, chat, upgrades, homes, seasons, and gang leaderboards.
5. **Presentation layer**: GUIs, messages, sounds, PlaceholderAPI, player menus, admin menus, and formatting.
6. **Safety layer**: validation, durable transaction records, diagnostics, staff audit, backups, restore verification, retries, reward recovery, and repair tools.

See [All Systems](/systems/) for the full system map and [Visual Architecture & Flows](/visuals/) for pipeline diagrams.

## Current documented build

<span class="status-pill">RelicPrison 1.0.0</span>
<span class="status-pill">Paper 1.21.5–26.2</span>
<span class="status-pill">Java 25</span>
<span class="status-pill">Maven</span>

RelicPrison 1.0.0 is compiled against Paper 1.21.10 and has been automated startup-tested with the exact release JAR on Paper 1.21.5 through 26.2. Paper 1.21.4 does not pass the startup probe with this Java 25 build and is not supported.

::: warning Verification boundary
The compatibility matrix verifies that the exact RelicPrison 1.0.0 release JAR loads, initializes its core services/database, connects required dependencies, and reaches `READY` on the supported Paper range. It does not mean every gameplay path, GUI, optional integration, Java/Bedrock client path, crash-recovery scenario, or multi-server deployment was manually regression-tested on every Paper version. See [1.0.0 Release Notes](/releases/1.0.0) and [Known Limitations](/known-limitations).
:::

## Real-client screenshots

The current limitation report says real Java/Bedrock GUI rendering has not yet been captured. The wiki therefore includes [Visual Walkthroughs](/visuals/tutorials) that explain exactly what should be visible and what must be captured during staging, without pretending generated mockups are proof of real client behavior.

## Source-driven documentation rule

When old prose disagrees with current code/configuration, **the current source and packaged defaults win**. YAML/API references, the website changelog, and known-limitations pages are regenerated during the docs build, while human-written pages explain intent, workflow, risks, and recommended operating practice.

<div class="home-next">
  <div><strong>Ready to begin?</strong><br><span>Go to the first real documentation page and continue from there.</span></div>
  <a href="./guide/installation">Next: Install RelicPrison →</a>
</div>
