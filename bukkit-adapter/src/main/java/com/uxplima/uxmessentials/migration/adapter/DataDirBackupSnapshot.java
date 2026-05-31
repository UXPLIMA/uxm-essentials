package com.uxplima.uxmessentials.migration.adapter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.BackupSnapshot;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Pre-import backup: snapshots the plugin's config/data tree under a timestamped sibling directory before
 * a live import writes anything (docs/12-migration §9, step 1). The snapshot name is recorded on the
 * {@code migration_import_start} audit line so a bad import is always recoverable to the pre-import config.
 * It is a config snapshot, not the off-host DB backup the runbook also demands — that remains the
 * operator's responsibility — but it captures the bundled {@code .conf} state the importer may touch.
 */
@NullMarked
public final class DataDirBackupSnapshot implements BackupSnapshot {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC);

    private final Path dataDir;
    private final Logger log;

    public DataDirBackupSnapshot(Path dataDir, Logger log) {
        this.dataDir = Objects.requireNonNull(dataDir, "dataDir");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public String take() {
        String name = "pre-import-" + STAMP.format(Instant.now());
        Path target = dataDir.resolveSibling(dataDir.getFileName() + "-backups").resolve(name);
        try {
            copyConfigs(target);
            log.info("migration pre-import snapshot written to {}", target);
            return name;
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to write pre-import snapshot to " + target, failure);
        }
    }

    private void copyConfigs(Path target) throws IOException {
        Files.createDirectories(target);
        if (!Files.isDirectory(dataDir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dataDir)) {
            for (Path file : files.filter(DataDirBackupSnapshot::isConf).toList()) {
                Files.copy(file, target.resolve(file.getFileName()));
            }
        }
    }

    private static boolean isConf(Path file) {
        return Files.isRegularFile(file) && file.getFileName().toString().endsWith(".conf");
    }
}
