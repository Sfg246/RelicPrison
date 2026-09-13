# RelicPrison Wiki Site

Public-facing documentation for RelicPrison. The engineering notes in `/docs` remain the implementation/history record; this `/wiki` directory is the user-facing documentation site.

## Local preview

```bash
cd wiki
npm install
npm run docs:dev
```

## Production build

```bash
cd wiki
npm install
npm run docs:build
```

The generated static site is written to `wiki/.vitepress/dist`.

## Source-of-truth policy

Public docs should be checked against, in this order:

1. Current source behavior.
2. Current files under `src/main/resources`.
3. Current API interfaces.
4. Current `docs/CURRENT_STATE.md` and verification reports.
5. Historical engineering notes only when they still match the above.

Do not copy stale requirements from old stage documentation without checking the current `pom.xml`, `plugin.yml`, and runtime configuration.
