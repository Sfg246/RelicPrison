# Known Limitations - 1.0.0-rc6-stage6

- No real Paper 1.21.10 server or Java/Bedrock/Geyser client was available, so inventories were not rendered and screenshots were not captured.
- Visual proportions, title/lore clipping, click routing, sound balance, custom resource-pack custom-model behavior, and all protected confirmation flows still require real client staging.
- The requested `WarpMenuPlugin-v1.6.0` files were not present anywhere in the supplied workspace; its `config.yml`, `MenuManager`, and `ItemUtil` could not be inspected directly.
- Existing configuration files are not overwritten on upgrade. Operators with established `guis/*.yml` files must merge the new Stage 6 defaults, especially `guis/theme.yml` and `guis/main.yml`, or regenerate missing files deliberately.
- The shared builder supports existing custom model data, but Stage 6 does not invent a new resource pack or require custom assets.
- Backend limitations documented in `docs/CURRENT_STATE.md`, including external Vault/command commit gaps and real multi-server staging requirements, are unchanged.
- This build is a release candidate for controlled staging, not final `1.0.0` and not production-certified.
