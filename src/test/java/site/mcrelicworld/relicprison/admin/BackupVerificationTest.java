package site.mcrelicworld.relicprison.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BackupVerificationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void validBackupArchivePassesVerification() throws Exception {
        Path archive = temporaryDirectory.resolve("valid.zip");
        try (ZipOutputStream zip = zip(archive)) {
            add(zip, "config.yml", "config-version: 3\n");
            add(zip, "storage.yml", "file-version: 1\n");
            add(zip, "mines.yml", "mines: {}\n");
        }

        BackupVerificationReport report = BackupServiceImpl.verifyArchiveForTest("valid-full", archive);

        assertTrue(report.valid(), report.format());
        assertTrue(report.metadata().containsKey("entry.config.yml.sha256"));
    }

    @Test
    void pathTraversalBackupIsRejected() throws Exception {
        Path archive = temporaryDirectory.resolve("traversal.zip");
        try (ZipOutputStream zip = zip(archive)) {
            add(zip, "../evil.yml", "config-version: 3\n");
            add(zip, "config.yml", "config-version: 3\n");
            add(zip, "storage.yml", "file-version: 1\n");
        }

        BackupVerificationReport report = BackupServiceImpl.verifyArchiveForTest("traversal-full", archive);

        assertFalse(report.valid());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("Unsafe ZIP entry path")));
    }

    @Test
    void absoluteBackupPathIsRejected() throws Exception {
        Path archive = temporaryDirectory.resolve("absolute.zip");
        try (ZipOutputStream zip = zip(archive)) {
            add(zip, "/tmp/evil.yml", "config-version: 3\n");
            add(zip, "config.yml", "config-version: 3\n");
            add(zip, "storage.yml", "file-version: 1\n");
        }

        BackupVerificationReport report = BackupServiceImpl.verifyArchiveForTest("absolute-full", archive);

        assertFalse(report.valid());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("Unsafe ZIP entry path")));
    }

    @Test
    void corruptBackupIsRejected() throws Exception {
        Path archive = temporaryDirectory.resolve("corrupt.zip");
        Files.writeString(archive, "not a zip", StandardCharsets.UTF_8);

        BackupVerificationReport report = BackupServiceImpl.verifyArchiveForTest("corrupt-full", archive);

        assertFalse(report.valid());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("Required file missing")
                || error.contains("ZIP integrity")));
    }

    private static ZipOutputStream zip(Path archive) throws Exception {
        return new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(archive)), StandardCharsets.UTF_8);
    }

    private static void add(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
