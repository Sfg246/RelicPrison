# Stage Plan

| Stage | Scope | Current Status | Release Mapping |
| --- | --- | --- | --- |
| 1 | Baseline plugin scaffolding and Paper build | Implemented, automated build guarded | pre-0.5 |
| 2 | Mine persistence, reset, and profile safety | Implemented with migration tests | 0.5-0.6 |
| 3 | Progression, economy, mining hardening | RC6 Stage 1 adds durable bulk transaction records and player-statistics separation; real AE/Vault staging pending | 0.7 core hardening |
| 4 | Stage 16 Custom Drops and Block Events | Implemented; RC6 Stage 2 adds deterministic logical claims and recovery for Block Event rewards | 0.8 stage16 |
| 5 | Stage 17 PlaceholderAPI and player/admin GUIs | Player workflows and all seven approved admin editors implemented; real Java/Bedrock staging pending | 0.9 stage17 |
| 6 | Stage 18 leaderboards and competitive rewards | Implemented with async snapshots, deterministic reward packages, and RC6 Stage 2 resumable finalization | 1.0 RC |
| 7 | Diagnostics, auditing, backup/restore | Implemented with redaction, audit, backup verification, staged restore, rollback hardening | 1.0 RC |
| 8 | Final testing, release, monitoring | Automated verification required; real staging pending | 1.0 RC only |

Exit criteria for `1.0.0` require full automated verification, the exact approved 33-mine fresh-install asset, and real Paper 1.21.10 staging with the supported integration versions and Java/Bedrock clients.
