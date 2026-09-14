# RelicPrison

## 🌐 [OPEN THE RELICPRISON DOCUMENTATION WEBSITE →](https://sfg246.github.io/RelicPrison/)

**Live docs:** https://sfg246.github.io/RelicPrison/

RelicPrison is the Paper prison core for RelicWorld.

Current release: **RelicPrison 1.0.0** for **Java 25**, startup-tested on **Paper 1.21.5 through 26.2**.

RelicPrison is compiled against Paper 1.21.10. The published 1.0.0 JAR has reached its `READY` state in automated Paper startup probes across the supported range. Paper 1.21.4 and older are not advertised as supported.

## Documentation

The public documentation source lives in [`wiki/`](wiki/README.md).

The wiki includes:

- beginner installation and a zero-to-launch course;
- mines, resets, progression, economy, boosters, gangs, leaderboards, and GUIs;
- an interactive command explorer;
- permissions and PlaceholderAPI references;
- source-generated documentation for every packaged YAML setting;
- source-generated public Java API service/event references;
- compatibility and RC staging matrices;
- copy-paste setup recipes;
- diagnostics, backup/restore, troubleshooting, and an error encyclopedia;
- visual architecture flows and GUI/client capture tracking;
- release/version documentation and developer examples.

The engineering and implementation notes under [`docs/`](docs/) remain the internal technical/history record.

## Build

RelicPrison currently compiles against Paper 1.21.10 with Java 25 using the Maven Wrapper.

```bash
./mvnw -B -ntp clean verify
```

Servers using the SQLite backend on Java 25 should start Paper with native access enabled for `sqlite-jdbc`:

```bash
java --enable-native-access=ALL-UNNAMED -jar paper.jar --nogui
```

That JVM permission must be granted when Paper starts; a plugin cannot grant it after launch.

## Documentation build

```bash
cd wiki
npm install
npm run docs:build
```

The docs build first regenerates YAML/API references from the live source, then builds VitePress into `wiki/.vitepress/dist`. GitHub Pages deploys the result automatically from `main`.
