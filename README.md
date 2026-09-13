# RelicPrison

RelicPrison is the Paper prison core for RelicWorld.

## Documentation

The public documentation site is live at **https://sfg246.github.io/RelicPrison/** and its source lives in [`wiki/`](wiki/README.md).

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

RelicPrison currently builds for Paper 1.21.10 with Java 25 using the Maven Wrapper.

```bash
./mvnw -B -ntp clean verify
```

## Documentation build

```bash
cd wiki
npm install
npm run docs:build
```

The docs build first regenerates YAML/API references from the live source, then builds VitePress into `wiki/.vitepress/dist`. GitHub Pages deploys the result automatically from `main`.
