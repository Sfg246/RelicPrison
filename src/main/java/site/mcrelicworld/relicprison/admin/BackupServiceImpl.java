package site.mcrelicworld.relicprison.admin;

import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.api.BackupService;
import site.mcrelicworld.relicprison.api.model.BackupView;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Compressed backups with restart-safe restore requests. */
public final class BackupServiceImpl implements BackupService {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final long MAX_VERIFY_ENTRY_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_VERIFY_TOTAL_BYTES = 512L * 1024L * 1024L;
    private final RelicPrisonPlugin plugin;
    private final Path backupDirectory;
    private int scheduledTask = -1;
    private volatile int keep = 20;
    private volatile String scheduledType = "full";

    public BackupServiceImpl(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
        this.backupDirectory = plugin.getDataFolder().toPath().resolve("backups");
    }

    public void initialize() throws IOException { Files.createDirectories(backupDirectory); reconfigure(); }

    public void reconfigure() throws IOException {
        if (scheduledTask != -1) { plugin.getServer().getScheduler().cancelTask(scheduledTask); scheduledTask = -1; }
        File file = new File(plugin.getDataFolder(), "backups.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        keep = Math.max(1, yaml.getInt("retention.keep", 20));
        scheduledType = normalizeType(yaml.getString("automatic.type", "full"));
        if (!yaml.getBoolean("automatic.enabled", true)) return;
        long intervalMinutes = Math.max(60L, yaml.getLong("automatic.interval-minutes", 1440L));
        long ticks = Math.multiplyExact(intervalMinutes, 1200L);
        scheduledTask = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () ->
                create(scheduledType).whenComplete((view, error) -> {
                    if (error != null) plugin.getLogger().warning("Automatic backup failed: " + rootMessage(error));
                    else plugin.getLogger().info("Automatic RelicPrison backup created: " + view.id());
                }), ticks, ticks);
    }

    public void shutdown() { if (scheduledTask != -1) plugin.getServer().getScheduler().cancelTask(scheduledTask); scheduledTask = -1; }

    @Override public CompletableFuture<BackupView> create(String requestedType) {
        String type = normalizeType(requestedType);
        CompletableFuture<Void> flush = CompletableFuture.allOf(plugin.playerProfiles().flushDirty(), plugin.statistics().flush());
        return flush.thenCompose(ignored -> checkpoint())
                .thenCompose(ignored -> needsLogicalDump(type) ? logicalDump() : CompletableFuture.completedFuture(""))
                .thenApplyAsync(logicalDump -> {
            try {
                Files.createDirectories(backupDirectory);
                long created = System.currentTimeMillis();
                String id = FORMAT.format(Instant.ofEpochMilli(created)) + "-" + type;
                Path output = backupDirectory.resolve(id + ".zip");
                Path temporary = backupDirectory.resolve(id + ".zip.tmp");
                try (OutputStream raw = new BufferedOutputStream(Files.newOutputStream(temporary));
                     ZipOutputStream zip = new ZipOutputStream(raw, StandardCharsets.UTF_8)) {
                    Path root = plugin.getDataFolder().toPath().toRealPath(LinkOption.NOFOLLOW_LINKS);
                    try (var stream = Files.walk(root)) {
                        stream.filter(path -> safeBackupFile(root, path))
                                .filter(path -> include(root, path, type))
                                .sorted()
                                .forEach(path -> add(zip, root, path));
                    }
                    if (!logicalDump.isBlank()) addText(zip, "database-export.sql", logicalDump);
                    addText(zip, "backup-meta.txt", "id=" + id + "\ntype=" + type + "\ncreated-at=" + created
                            + "\nplugin-version=" + plugin.getPluginMeta().getVersion() + "\n");
                }
                try { Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException ignoredMove) { Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING); }
                String checksum = sha256(output);
                BackupView view = new BackupView(id, type, created, Files.size(output), checksum);
                writeMetadata(view, entryChecksums(output), "success", "not-verified");
                record(view);
                prune();
                return view;
            } catch (Exception error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        });
    }

    private CompletableFuture<Void> checkpoint() {
        if (plugin.config().snapshot().storage().type() != site.mcrelicworld.relicprison.config.StorageConfig.Type.SQLITE) {
            return CompletableFuture.completedFuture(null);
        }
        return plugin.database().submitIdempotent(connection -> {
            try (var statement = connection.createStatement()) { statement.execute("PRAGMA wal_checkpoint(FULL)"); }
            return null;
        });
    }

    private boolean include(Path root, Path path, String type) {
        Path relative = root.relativize(path);
        if (relative.getNameCount() == 0) return false;
        String first = relative.getName(0).toString();
        if (first.equals("backups") || first.equals("exports") || first.equals("diagnostics")) return false;
        if (relative.toString().equals("restore-pending.txt")) return false;
        boolean database = relative.toString().startsWith("data" + File.separator) || relative.toString().endsWith(".db")
                || relative.toString().endsWith(".db-wal") || relative.toString().endsWith(".db-shm");
        return switch (type) { case "config" -> !database; case "data" -> database; default -> true; };
    }

    private static void add(ZipOutputStream zip, Path root, Path path) {
        try {
            String name = root.relativize(path).toString().replace(File.separatorChar, '/');
            zip.putNextEntry(new ZipEntry(name));
            Files.copy(path, zip);
            zip.closeEntry();
        } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
    }

    private static void addText(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    @Override public Collection<BackupView> backups() {
        if (!Files.isDirectory(backupDirectory)) return List.of();
        List<BackupView> result = new ArrayList<>();
        try (var stream = Files.list(backupDirectory)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".zip")).forEach(path -> {
                try {
                    String file = path.getFileName().toString();
                    String id = file.substring(0, file.length() - 4);
                    int separator = id.lastIndexOf('-');
                    String type = separator < 0 ? "full" : id.substring(separator + 1);
                    result.add(new BackupView(id, type, Files.getLastModifiedTime(path).toMillis(), Files.size(path), sha256(path)));
                } catch (Exception ignored) { }
            });
        } catch (IOException error) { plugin.getLogger().warning("Unable to list backups: " + error.getMessage()); }
        result.sort(Comparator.comparingLong(BackupView::createdAt).reversed());
        return List.copyOf(result);
    }

    public Optional<BackupView> info(String id) {
        String normalized = normalizeId(id);
        return backups().stream().filter(view -> view.id().equals(normalized)).findFirst();
    }

    public CompletableFuture<BackupVerificationReport> verify(String id) {
        String normalized = normalizeId(id);
        Path archive = safeArchivePath(normalized);
        return CompletableFuture.supplyAsync(() -> {
            try {
                BackupVerificationReport report = verifyArchive(normalized, archive, MAX_VERIFY_ENTRY_BYTES,
                        MAX_VERIFY_TOTAL_BYTES, requiresConfigFiles(normalized));
                Path reportFile = backupDirectory.resolve(normalized + ".verify.txt").normalize();
                if (!reportFile.startsWith(backupDirectory)) throw new IOException("Unsafe verification report path");
                Files.writeString(reportFile, report.format(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                updateBackupResult(normalized, report.valid() ? "verified-valid" : "verified-failed");
                return report;
            } catch (IOException error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        });
    }

    public CompletableFuture<Boolean> delete(String id, boolean confirmed) {
        if (!confirmed) return CompletableFuture.failedFuture(new IllegalArgumentException("Backup deletion requires confirmation"));
        String normalized = normalizeId(id);
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path marker = plugin.getDataFolder().toPath().resolve("restore-pending.txt");
                if (Files.isRegularFile(marker) && Files.readString(marker, StandardCharsets.UTF_8).trim().equals(normalized)) {
                    throw new IOException("Refusing to delete the active restore source");
                }
                Path archive = safeArchivePath(normalized);
                boolean removed = Files.deleteIfExists(archive);
                Files.deleteIfExists(safeSidecarPath(normalized, ".meta.properties"));
                Files.deleteIfExists(safeSidecarPath(normalized, ".verify.txt"));
                updateBackupResult(normalized, removed ? "deleted" : "delete-missing-file");
                return removed;
            } catch (IOException error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        });
    }

    public boolean requestRestore(String id) throws IOException {
        if (id == null || !id.matches("[A-Za-z0-9_-]{3,80}")) return false;
        Path backup = backupDirectory.resolve(id + ".zip").normalize();
        if (!backup.startsWith(backupDirectory) || !Files.isRegularFile(backup)) return false;
        String type = backups().stream().filter(view -> view.id().equals(id)).map(BackupView::type).findFirst().orElse("full");
        if (plugin.database().storageType() == site.mcrelicworld.relicprison.config.StorageConfig.Type.MYSQL
                && !type.equals("config")) {
            throw new IOException("Automatic restore of MySQL data is not supported. Import database-export.sql manually, or restore a config backup.");
        }
        Files.writeString(plugin.getDataFolder().toPath().resolve("restore-pending.txt"), id, StandardCharsets.UTF_8);
        return true;
    }

    public CompletableFuture<Boolean> requestRestoreVerified(String id) {
        String normalized = normalizeId(id);
        return verify(normalized).thenApply(report -> {
            if (!report.valid()) throw new java.util.concurrent.CompletionException(
                    new IOException("Backup verification failed; restore was not scheduled"));
            try {
                return requestRestore(normalized);
            } catch (IOException error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        });
    }

    public static void applyPendingRestore(Path dataFolder, java.util.logging.Logger logger) throws IOException {
        dataFolder = dataFolder.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path marker = dataFolder.resolve("restore-pending.txt");
        if (!Files.isRegularFile(marker)) return;
        String id = Files.readString(marker, StandardCharsets.UTF_8).trim();
        if (!id.matches("[A-Za-z0-9_-]{3,80}")) throw new IOException("Invalid restore marker");
        Path backupDirectory = dataFolder.resolve("backups");
        Path archive = backupDirectory.resolve(id + ".zip").normalize();
        if (!archive.startsWith(backupDirectory) || !Files.isRegularFile(archive)) throw new IOException("Requested backup does not exist: " + id);
        BackupVerificationReport verification = verifyArchive(id, archive, MAX_VERIFY_ENTRY_BYTES,
                MAX_VERIFY_TOTAL_BYTES, requiresConfigFiles(id));
        Path restoreReport = backupDirectory.resolve(id + ".restore-plan.txt").normalize();
        if (restoreReport.startsWith(backupDirectory)) Files.writeString(restoreReport, verification.format(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        if (!verification.valid()) throw new IOException("Restore source failed verification: " + verification.errors());
        Path safety = backupDirectory.resolve("pre-restore-" + System.currentTimeMillis() + ".zip");
        createSafetyBackup(dataFolder, safety);
        Path staging = backupDirectory.resolve("restore-staging-" + id + "-" + System.currentTimeMillis()).normalize();
        if (!staging.startsWith(backupDirectory)) throw new IOException("Unsafe restore staging path");
        Files.createDirectories(staging);
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(archive)); ZipInputStream zip = new ZipInputStream(raw, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || entry.getName().equals("backup-meta.txt")) continue;
                Path destination = staging.resolve(entry.getName()).normalize();
                if (!destination.startsWith(staging)) throw new IOException("Unsafe backup entry: " + entry.getName());
                Files.createDirectories(destination.getParent());
                Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Path rollback = backupDirectory.resolve("restore-rollback-" + id + "-" + System.currentTimeMillis()).normalize();
        if (!rollback.startsWith(backupDirectory)) throw new IOException("Unsafe restore rollback path");
        List<RestoreMove> journal = new ArrayList<>();
        try (var stream = Files.walk(staging)) {
            for (Path source : stream.filter(Files::isRegularFile).toList()) {
                Path relative = staging.relativize(source);
                Path destination = dataFolder.resolve(relative).normalize();
                if (!destination.startsWith(dataFolder) || destination.startsWith(backupDirectory)) {
                    throw new IOException("Unsafe staged restore path: " + relative);
                }
                ensureNoSymlinkPath(dataFolder, destination);
                Files.createDirectories(destination.getParent());
                Path rollbackPath = rollback.resolve(relative).normalize();
                if (!rollbackPath.startsWith(rollback)) throw new IOException("Unsafe rollback path: " + relative);
                try {
                    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                        if (Files.isSymbolicLink(destination)) throw new IOException("Refusing to overwrite symlink: " + relative);
                        Files.createDirectories(rollbackPath.getParent());
                        moveAtomic(destination, rollbackPath);
                        journal.add(new RestoreMove(destination, rollbackPath, true));
                    } else {
                        journal.add(new RestoreMove(destination, rollbackPath, false));
                    }
                    moveAtomic(source, destination);
                } catch (IOException | RuntimeException applyError) {
                    rollbackRestore(journal);
                    throw applyError;
                }
            }
        }
        deleteDirectory(staging);
        deleteDirectory(rollback);
        Files.deleteIfExists(marker);
        logger.info("Applied RelicPrison restore " + id + ". Safety backup: " + safety.getFileName());
    }

    private static void createSafetyBackup(Path dataFolder, Path output) throws IOException {
        Files.createDirectories(output.getParent());
        Path root = dataFolder.toRealPath(LinkOption.NOFOLLOW_LINKS);
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(output)), StandardCharsets.UTF_8);
             var stream = Files.walk(root)) {
            stream.filter(path -> safeBackupFile(root, path))
                    .filter(path -> !path.startsWith(root.resolve("backups")))
                    .forEach(path -> add(zip, root, path));
        }
    }

    private void writeMetadata(BackupView view, Map<String, EntrySummary> entries, String result,
                               String verificationStatus) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("backup-id", view.id());
        properties.setProperty("creation-time", Instant.ofEpochMilli(view.createdAt()).toString());
        properties.setProperty("plugin-version", plugin.getPluginMeta().getVersion());
        properties.setProperty("server-version", BukkitVersion.safe());
        properties.setProperty("database-type", plugin.database().storageType().name());
        properties.setProperty("type", view.type());
        properties.setProperty("size-bytes", String.valueOf(view.sizeBytes()));
        properties.setProperty("zip-checksum", view.checksum());
        properties.setProperty("creation-result", result);
        properties.setProperty("verification-status", verificationStatus);
        properties.setProperty("restore-history", "sidecar reports in backups/*.restore-*.txt");
        properties.setProperty("safety-backup-relationship", "pre-restore backups are retained until deliberate deletion");
        properties.setProperty("included-files", String.join(",", entries.keySet()));
        entries.forEach((name, entry) -> {
            properties.setProperty("entry." + name + ".size", String.valueOf(entry.size()));
            properties.setProperty("entry." + name + ".sha256", entry.checksum());
        });
        Path metadata = backupDirectory.resolve(view.id() + ".meta.properties").normalize();
        if (!metadata.startsWith(backupDirectory)) throw new IOException("Unsafe backup metadata path");
        try (OutputStream output = Files.newOutputStream(metadata, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            properties.store(output, "RelicPrison backup metadata");
        }
    }

    private Map<String, EntrySummary> entryChecksums(Path archive) throws IOException {
        Map<String, EntrySummary> result = new LinkedHashMap<>();
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(archive));
             ZipInputStream zip = new ZipInputStream(raw, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                MessageDigest digest = digest();
                long size = copyToDigest(zip, digest, Long.MAX_VALUE);
                result.put(entry.getName(), new EntrySummary(size, HexFormat.of().formatHex(digest.digest())));
            }
        }
        return Map.copyOf(result);
    }

    private static BackupVerificationReport verifyArchive(String backupId, Path archive, long maxEntryBytes,
                                                          long maxTotalBytes, boolean requireConfigFiles) {
        long verifiedAt = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, String> metadata = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        long totalBytes = 0L;
        String archiveChecksum = "unavailable";
        try {
            if (!Files.isRegularFile(archive)) {
                errors.add("Archive file is missing");
                return new BackupVerificationReport(backupId, false, verifiedAt, archiveChecksum, errors, warnings, metadata);
            }
            archiveChecksum = sha256(archive);
            metadata.put("archive-size-bytes", String.valueOf(Files.size(archive)));
            try (InputStream raw = new BufferedInputStream(Files.newInputStream(archive));
                 ZipInputStream zip = new ZipInputStream(raw, StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (!safeEntryName(name)) {
                        errors.add("Unsafe ZIP entry path: " + name);
                        continue;
                    }
                    if (!names.add(name)) {
                        errors.add("Duplicate ZIP entry path: " + name);
                        continue;
                    }
                    if (entry.isDirectory()) continue;
                    MessageDigest digest = digest();
                    long size = copyToDigest(zip, digest, maxEntryBytes);
                    totalBytes += size;
                    if (totalBytes > maxTotalBytes) errors.add("ZIP total extracted size exceeds safety limit");
                    metadata.put("entry." + name + ".size", String.valueOf(size));
                    metadata.put("entry." + name + ".sha256", HexFormat.of().formatHex(digest.digest()));
                }
            }
            if (requireConfigFiles) {
                if (!names.contains("config.yml")) errors.add("Required file missing: config.yml");
                if (!names.contains("storage.yml")) errors.add("Required file missing: storage.yml");
            }
            validateReadableYamlEntries(archive, names, errors);
            validateSqliteEntries(archive, names, errors, warnings);
        } catch (IOException | RuntimeException error) {
            errors.add("ZIP integrity/read failure: " + rootMessage(error));
        } catch (Exception error) {
            errors.add("Verification failure: " + rootMessage(error));
        }
        return new BackupVerificationReport(backupId, errors.isEmpty(), verifiedAt, archiveChecksum,
                List.copyOf(errors), List.copyOf(warnings), Map.copyOf(metadata));
    }

    private static boolean requiresConfigFiles(String id) {
        String lower = id == null ? "" : id.toLowerCase(Locale.ROOT);
        return !lower.endsWith("-data");
    }

    private static boolean safeEntryName(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.startsWith("\\")) return false;
        Path normalized = Path.of(name).normalize();
        if (normalized.isAbsolute()) return false;
        for (Path part : normalized) if (part.toString().equals("..")) return false;
        return !name.contains(":");
    }

    private static void validateReadableYamlEntries(Path archive, Set<String> names, List<String> errors)
            throws IOException {
        if (names.stream().noneMatch(name -> name.endsWith(".yml") || name.endsWith(".yaml"))) return;
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(archive));
             ZipInputStream zip = new ZipInputStream(raw, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || (!entry.getName().endsWith(".yml") && !entry.getName().endsWith(".yaml"))) continue;
                byte[] content = limitedBytes(zip, MAX_VERIFY_ENTRY_BYTES);
                validateYaml(entry.getName(), new String(content, StandardCharsets.UTF_8), errors);
            }
        }
    }

    private static void validateSqliteEntries(Path archive, Set<String> names, List<String> errors,
                                              List<String> warnings) throws IOException {
        List<String> databases = names.stream().filter(name -> name.endsWith(".db")).toList();
        if (databases.isEmpty()) return;
        Path temporaryDirectory = Files.createTempDirectory("relicprison-backup-verify-");
        try {
            try (InputStream raw = new BufferedInputStream(Files.newInputStream(archive));
                 ZipInputStream zip = new ZipInputStream(raw, StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory() || !entry.getName().endsWith(".db")) continue;
                    Path database = temporaryDirectory.resolve(Path.of(entry.getName()).getFileName().toString());
                    Files.write(database, limitedBytes(zip, MAX_VERIFY_ENTRY_BYTES));
                    try {
                        Class.forName("org.sqlite.JDBC");
                        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + database);
                             var statement = connection.createStatement()) {
                            statement.executeQuery("SELECT name FROM sqlite_master LIMIT 1").close();
                        }
                    } catch (Exception error) {
                        errors.add("SQLite database is not readable: " + entry.getName() + " (" + rootMessage(error) + ")");
                    }
                }
            }
        } catch (NoClassDefFoundError error) {
            warnings.add("SQLite JDBC is unavailable; database readability was not checked");
        } finally {
            deleteDirectory(temporaryDirectory);
        }
    }

    private static void validateYaml(String name, String content, List<String> errors) {
        if (content.isBlank()) return;
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(content);
        } catch (org.bukkit.configuration.InvalidConfigurationException error) {
            errors.add("Unreadable YAML: " + name + " (" + rootMessage(error) + ")");
        }
    }

    private static byte[] limitedBytes(InputStream input, long limit) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) throw new IOException("Entry exceeds size limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static long copyToDigest(InputStream input, MessageDigest digest, long limit) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) throw new IOException("Entry exceeds size limit");
            digest.update(buffer, 0, read);
        }
        return total;
    }

    private static MessageDigest digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IOException(error);
        }
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var stream = Files.walk(directory)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static boolean safeBackupFile(Path root, Path path) {
        try {
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false;
            Path realRoot = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path realPath = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!realPath.startsWith(realRoot)) throw new IOException("Backup source escapes plugin data root: " + path);
            return true;
        } catch (IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }

    private static void ensureNoSymlinkPath(Path root, Path destination) throws IOException {
        Path normalizedRoot = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path current = normalizedRoot;
        Path relative = normalizedRoot.relativize(destination.normalize());
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IOException("Restore path contains symlink: " + relative);
        }
    }

    private static void moveAtomic(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignoredMove) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void rollbackRestore(List<RestoreMove> journal) throws IOException {
        IOException failure = null;
        for (RestoreMove move : journal.reversed()) {
            try {
                Files.deleteIfExists(move.destination());
                if (move.hadOriginal() && Files.exists(move.rollbackPath(), LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(move.destination().getParent());
                    moveAtomic(move.rollbackPath(), move.destination());
                }
            } catch (IOException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }

    private Path safeArchivePath(String id) {
        Path archive = backupDirectory.resolve(normalizeId(id) + ".zip").normalize();
        if (!archive.startsWith(backupDirectory)) throw new IllegalArgumentException("Unsafe backup id");
        return archive;
    }

    private Path safeSidecarPath(String id, String suffix) {
        Path sidecar = backupDirectory.resolve(normalizeId(id) + suffix).normalize();
        if (!sidecar.startsWith(backupDirectory)) throw new IllegalArgumentException("Unsafe backup id");
        return sidecar;
    }

    private static String normalizeId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{3,80}")) {
            throw new IllegalArgumentException("Backup id must contain only letters, numbers, underscores, and dashes");
        }
        return id;
    }

    private void updateBackupResult(String backupId, String result) {
        plugin.database().submitIdempotent(connection -> {
            try (var statement = connection.prepareStatement(
                    "UPDATE rp_backup_history SET result=? WHERE backup_id=?")) {
                statement.setString(1, result);
                statement.setString(2, backupId);
                statement.executeUpdate();
                return null;
            }
        }).exceptionally(error -> {
            plugin.getLogger().warning("Unable to update backup history for " + backupId + ": " + rootMessage(error));
            return null;
        });
    }

    static BackupVerificationReport verifyArchiveForTest(String backupId, Path archive) {
        return verifyArchive(backupId, archive, MAX_VERIFY_ENTRY_BYTES, MAX_VERIFY_TOTAL_BYTES, true);
    }

    private record EntrySummary(long size, String checksum) { }
    private record RestoreMove(Path destination, Path rollbackPath, boolean hadOriginal) { }

    private static final class BukkitVersion {
        private static String safe() {
            try {
                return Bukkit.getVersion();
            } catch (RuntimeException error) {
                return "unavailable";
            }
        }
    }

    private void record(BackupView view) {
        plugin.database().submitIdempotent(connection -> {
            boolean mysql = plugin.database().storageType() == site.mcrelicworld.relicprison.config.StorageConfig.Type.MYSQL;
            String sql = mysql ? "INSERT INTO rp_backup_history(backup_id,backup_type,created_at,size_bytes,checksum,result) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE result=VALUES(result)"
                    : "INSERT INTO rp_backup_history(backup_id,backup_type,created_at,size_bytes,checksum,result) VALUES(?,?,?,?,?,?) ON CONFLICT(backup_id) DO UPDATE SET result=excluded.result";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, view.id()); statement.setString(2, view.type()); statement.setLong(3, view.createdAt());
                statement.setLong(4, view.sizeBytes()); statement.setString(5, view.checksum()); statement.setString(6, "success"); statement.executeUpdate();
            }
            return null;
        }).exceptionally(error -> { plugin.getLogger().warning("Unable to record backup history: " + error.getMessage()); return null; });
    }

    private void prune() {
        List<BackupView> values = new ArrayList<>(backups());
        for (int index = keep; index < values.size(); index++) {
            try { Files.deleteIfExists(safeArchivePath(values.get(index).id())); }
            catch (IOException error) { plugin.getLogger().warning("Unable to prune backup " + values.get(index).id()); }
        }
    }

    private boolean needsLogicalDump(String type) {
        return plugin.database().storageType() == site.mcrelicworld.relicprison.config.StorageConfig.Type.MYSQL
                && (type.equals("full") || type.equals("data"));
    }

    private CompletableFuture<String> logicalDump() {
        return plugin.database().submitIdempotent(connection -> {
            StringBuilder out = new StringBuilder("-- RelicPrison logical MySQL backup\n");
            String[] tables = {"rp_schema_version","rp_player_profiles","rp_personal_boosters","rp_server_boosters",
                    "rp_mine_runtime","rp_migration_history","rp_backup_history","rp_personal_multipliers",
                    "rp_booster_pauses","rp_player_statistics","rp_mine_statistics","rp_player_mine_statistics",
                    "rp_reset_failures","rp_progression_transactions","rp_reward_delivery_log",
                    "rp_mining_statistics","rp_block_event_state","rp_leaderboard_reward_periods",
                    "rp_leaderboard_reward_ledger","rp_staff_audit","rp_reward_packages",
                    "rp_reward_components","rp_block_event_triggers","rp_leaderboard_winner_snapshots"};
            for (String table : tables) {
                try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT * FROM " + table)) {
                    var metadata = result.getMetaData(); int columns = metadata.getColumnCount();
                    while (result.next()) {
                        out.append("INSERT INTO ").append(table).append(" VALUES (");
                        for (int column = 1; column <= columns; column++) {
                            if (column > 1) out.append(','); Object value = result.getObject(column);
                            if (value == null) out.append("NULL");
                            else if (value instanceof Number || value instanceof Boolean) out.append(value);
                            else out.append('\'').append(String.valueOf(value).replace("\\", "\\\\").replace("'", "''")).append('\'');
                        }
                        out.append(");\n");
                    }
                }
            }
            return out.toString();
        });
    }

    private static String rootMessage(Throwable error) { Throwable current=error; while(current.getCause()!=null) current=current.getCause(); return String.valueOf(current.getMessage()); }

    private static String normalizeType(String value) {
        String type = value == null ? "full" : value.toLowerCase(Locale.ROOT);
        if (!type.equals("full") && !type.equals("config") && !type.equals("data")) throw new IllegalArgumentException("Backup type must be full, config, or data");
        return type;
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) { input.transferTo(new OutputStream(){@Override public void write(int value){digest.update((byte)value);}@Override public void write(byte[] b,int off,int len){digest.update(b,off,len);}}); }
        return HexFormat.of().formatHex(digest.digest());
    }
}
