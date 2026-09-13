# Stage 07

## Goal
Implement diagnostics, auditing, backup verification, and restore safety.

## Implemented Systems
- `/rp diagnose`
- Redacted diagnostic export with `REDACTION-REPORT.txt`.
- Staff audit repository and viewing commands.
- Backup create/list/info/verify/delete/restore scheduling.
- Restore staging, safety backup, symlink rejection, and rollback journal.

## Important Files
- `DiagnosticServiceImpl.java`
- `RedactionService.java`
- `AuditRepository.java`
- `BackupServiceImpl.java`

## Database Migrations
- `rp_staff_audit`
- `rp_backup_history`

## Config Migrations
- `backups.yml`

## Commands
- `/rp diagnose`
- `/rp diagnostic`
- `/rp audit`
- `/rp backup info|verify|delete|restore`

## Permissions
- `relicprison.admin`
- `relicprison.admin.diagnostic.sensitive`
- `relicprison.admin.backup`

## Tests
- `RedactionServiceTest`
- `BackupVerificationTest`
- `AuditRepositoryTest`

## Exit Requirements
- Diagnostic exports redact secrets and backup verification does not mutate live data.

## Actual Verification Status
- Redaction and backup traversal/absolute-path rejection are automated.

## Remaining Limitations
- Restore apply rollback requires staging-server validation.
