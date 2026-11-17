package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.LiveNameCase;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps a detected case-sensitive answer in a small file in the plugin's data folder, so the console, a vote listener
 * and the REST API resolve names case-sensitively from the next start on, before any offline-mode player has joined.
 * It is read once at enable and written off the tick.
 */
@NullMarked
public final class NameCaseStateFile implements LiveNameCase.Memory {

    /** The file's name in the data folder. */
    static final String FILE = "name-case.state";

    private static final String SENSITIVE = "sensitive";

    private final Path file;
    private final Scheduler scheduler;
    private final Logger log;

    public NameCaseStateFile(Path dataFolder, Scheduler scheduler, Logger log) {
        this.file = Objects.requireNonNull(dataFolder, "dataFolder").resolve(FILE);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean detected() {
        try {
            return Files.isRegularFile(file)
                    && Files.readString(file, StandardCharsets.UTF_8).strip().equals(SENSITIVE);
        } catch (IOException failure) {
            log.warn("event=name_case_state_unreadable file={} reason={}", file, failure.toString());
            return false;
        }
    }

    @Override
    public void remember() {
        scheduler.async(() -> {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, SENSITIVE + System.lineSeparator(), StandardCharsets.UTF_8);
            } catch (IOException failure) {
                log.warn("event=name_case_state_unwritable file={} reason={}", file, failure.toString());
            }
        });
    }

    @Override
    public void forget() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException failure) {
            log.warn("event=name_case_state_undeletable file={} reason={}", file, failure.toString());
        }
    }
}
