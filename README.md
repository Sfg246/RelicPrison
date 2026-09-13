# RelicPrison

RelicPrison is the Paper prison core for RelicWorld.

## Documentation

A full public-facing documentation site now lives in [`wiki/`](wiki/README.md). It includes:

- beginner setup journey;
- mines, resets, progression, economy, boosters, and gangs;
- configuration and integration guides;
- command, permission, and PlaceholderAPI references;
- administration, diagnostics, backup/restore, troubleshooting, and recovery;
- developer API and thread-safety guidance;
- RC6 staging status.

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

The generated static site is written to `wiki/.vitepress/dist`.

For detailed installation and server-owner instructions, start at `wiki/guide/installation.md` or run the VitePress site locally.
