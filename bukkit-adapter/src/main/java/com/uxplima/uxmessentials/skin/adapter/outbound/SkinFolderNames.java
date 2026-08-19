package com.uxplima.uxmessentials.skin.adapter.outbound;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The names {@code /skin file} accepts, for the completion a player sees while typing.
 *
 * <p>Brigadier asks for suggestions on the tick thread once per keystroke, and a folder listing is disk I/O, so
 * the answer is always the last snapshot taken: a caller gets it immediately, and a snapshot older than
 * {@link #STALE_AFTER} schedules a fresh read on the async pool for the next keystroke to pick up. A skin folder
 * changes when an operator drops a file into it, so a snapshot half a minute behind costs nothing.
 */
@NullMarked
public final class SkinFolderNames {

    /** How long a listing is served before a fresh one is read behind it. */
    private static final Duration STALE_AFTER = Duration.ofSeconds(30);

    /** The one extension {@code /skin file} reads, stripped from the name a player types. */
    private static final String EXTENSION = ".png";

    private final Path folder;
    private final Scheduler scheduler;
    private final Logger log;
    private final Clock clock;
    /** Owned by whichever async read won {@link #reading}; every other thread only reads it. */
    private volatile List<String> names = List.of();

    /** Owned the same way: stamped once the read that holds {@link #reading} is done, however it ended. */
    private volatile Instant readAt = Instant.EPOCH;

    private final AtomicBoolean reading = new AtomicBoolean();

    public SkinFolderNames(Path folder, Scheduler scheduler, Logger log, Clock clock) {
        this.folder = Objects.requireNonNull(folder, "folder").toAbsolutePath().normalize();
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The names in the folder as of the last read, refreshing behind the caller when that read has aged out. */
    public Collection<String> get() {
        if (stale() && reading.compareAndSet(false, true)) {
            scheduler.async(this::reread);
        }
        return names;
    }

    private boolean stale() {
        return Duration.between(readAt, clock.instant()).compareTo(STALE_AFTER) >= 0;
    }

    /** Replace the snapshot with what the folder holds now. A folder nobody created yet simply holds nothing. */
    private void reread() {
        try {
            if (!Files.isDirectory(folder)) {
                names = List.of();
                return;
            }
            try (Stream<Path> files = Files.list(folder)) {
                names = files.filter(Files::isRegularFile)
                        .map(file -> file.getFileName().toString())
                        .filter(name -> name.endsWith(EXTENSION))
                        .map(name -> name.substring(0, name.length() - EXTENSION.length()))
                        .sorted()
                        .toList();
            }
        } catch (IOException failure) {
            log.warn("skin: could not list the skin folder {}: {}", folder, failure.toString());
        } finally {
            readAt = clock.instant();
            reading.set(false);
        }
    }
}
