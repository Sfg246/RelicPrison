# RelicPrison Public Wiki

This directory contains the public VitePress documentation site for RelicPrison.

## Local development

```bash
cd wiki
npm install
npm run docs:dev
```

## Production build

```bash
cd wiki
npm run docs:build
```

The build regenerates source-driven YAML and Java API reference pages before VitePress compiles the site.

Public documentation lives here under `wiki/`. Engineering history, implementation notes, verification reports, and internal handoffs remain under the repository-level `docs/` directory.
