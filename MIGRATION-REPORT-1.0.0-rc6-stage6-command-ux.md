# Migration Report - 1.0.0-rc6-stage6 Command UX

- Database schema remains version 17.
- No database migration is required.
- No command names, permission nodes, argument semantics, gameplay behavior, or public API contracts changed.
- Existing `messages.yml` files remain compatible. Missing `gang-prefix` values fall back to the configured `prefix`; operators may copy the new default to customize gang output independently.
