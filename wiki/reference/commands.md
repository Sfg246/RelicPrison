# Command Explorer

This is the searchable command reference for the current RelicPrison command surface. Search by syntax, alias, permission, category, or purpose, then copy a concrete example.

<CommandExplorer />

## How to read command syntax

- `<value>` means the argument is required.
- `[value]` means it is optional.
- `<a|b>` means choose one listed option.
- `...` means the command has deeper operation-specific arguments; use tab completion and the matching system guide.

::: tip GUI alternatives
RelicPrison intentionally keeps command alternatives for important GUI actions so administration remains possible from console/staff workflows when a menu is inconvenient.
:::

## Authority rule

Top-level command aliases and permission defaults come from packaged `plugin.yml`. Nested command forms are documented from the command handlers/help definitions. If a command changes in code, the docs should be updated in the same change. The generated YAML reference also exposes `plugin.yml` for exact packaged registration metadata.
