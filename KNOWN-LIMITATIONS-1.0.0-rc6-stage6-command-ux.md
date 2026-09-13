# Known Limitations - 1.0.0-rc6-stage6 Command UX

- Real Paper command dispatch and chat rendering were not exercised in this environment.
- Java and Bedrock line wrapping, font width, resource-pack contrast, and console color behavior remain unverified.
- Bukkit legacy color codes are retained for Paper 1.21.10 compatibility; this pass does not migrate messages to Adventure components.
- Existing deployed `messages.yml` files are not overwritten. The new `gang-prefix` setting uses the main prefix as a compatibility fallback until an operator adds it.
- This remains a release candidate and is not production-approved without the repository staging matrix.
