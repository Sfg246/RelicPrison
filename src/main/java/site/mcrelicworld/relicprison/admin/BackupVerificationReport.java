package site.mcrelicworld.relicprison.admin;

import java.util.List;
import java.util.Map;

public record BackupVerificationReport(String backupId, boolean valid, long verifiedAt, String archiveChecksum,
                                       List<String> errors, List<String> warnings, Map<String, String> metadata) {
    public BackupVerificationReport {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
        metadata = Map.copyOf(metadata);
    }

    public String format() {
        StringBuilder output = new StringBuilder();
        output.append("backup-id=").append(backupId).append('\n');
        output.append("valid=").append(valid).append('\n');
        output.append("verified-at=").append(verifiedAt).append('\n');
        output.append("archive-checksum=").append(archiveChecksum == null ? "unavailable" : archiveChecksum).append('\n');
        metadata.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> output.append("meta.").append(entry.getKey()).append('=')
                        .append(entry.getValue()).append('\n'));
        errors.forEach(error -> output.append("ERROR: ").append(error).append('\n'));
        warnings.forEach(warning -> output.append("WARN: ").append(warning).append('\n'));
        return output.toString();
    }
}
