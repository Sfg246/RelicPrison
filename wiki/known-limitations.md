# Known Limitations

> This page is regenerated from the current repository limitation reports during every documentation build.

## RC6 Stage 6 client/staging limitations

- Real Paper 1.21.10 Java/Bedrock/Geyser client rendering has not yet been exercised for this build.
- GUI proportions, clipping, click routing, sounds and custom-model/resource-pack behavior still require real client staging.
- Existing `guis/*.yml` files are not overwritten on upgrade, so established servers must merge new defaults deliberately.
- The shared GUI builder supports existing custom model data but does not create or require a new resource pack.
- Backend limitations in the current-state documentation, including external Vault/command commit gaps and multi-server staging requirements, remain relevant.

## Command UX limitations

- Real Paper command dispatch/chat rendering still requires staging verification.
- Java/Bedrock wrapping, font width, resource-pack contrast and console color behavior remain unverified.
- Legacy Bukkit color codes are retained for compatibility in this pass.
- Existing `messages.yml` files are not overwritten; new settings may require a manual merge.

## Release status

The currently documented build is a **release candidate for controlled staging**, not final `1.0.0` and not production-certified. Use the [RC6 staging matrix](/releases/rc6) before promotion.
