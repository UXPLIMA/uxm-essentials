package com.uxplima.uxmessentials.skin.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The completion source behind {@code /skin file}: what it lists, and how rarely it goes back to the disk. */
class SkinFolderNamesTest {

    private final CountingScheduler scheduler = new CountingScheduler();

    @Test
    void theKeystrokeIsAnsweredWithoutWaitingForTheDisk(@TempDir Path folder) throws IOException {
        Files.createFile(folder.resolve("knight.png"));
        DeferringScheduler deferred = new DeferringScheduler();
        SkinFolderNames names = new SkinFolderNames(folder, deferred, new NoopLogger(), Clock.systemUTC());

        assertThat(names.get()).isEmpty();

        deferred.runQueued();

        assertThat(names.get()).containsExactly("knight");
    }

    @Test
    void theNamesComeBackInOrder(@TempDir Path folder) throws IOException {
        Files.createFile(folder.resolve("pirate.png"));
        Files.createFile(folder.resolve("knight.png"));
        SkinFolderNames names = new SkinFolderNames(folder, scheduler, new NoopLogger(), Clock.systemUTC());

        names.get();

        assertThat(names.get()).isEqualTo(List.of("knight", "pirate"));
    }

    @Test
    void onlyPngFilesAreOffered(@TempDir Path folder) throws IOException {
        Files.createFile(folder.resolve("knight.png"));
        Files.createFile(folder.resolve("notes.txt"));
        Files.createDirectory(folder.resolve("archive"));
        SkinFolderNames names = new SkinFolderNames(folder, scheduler, new NoopLogger(), Clock.systemUTC());

        names.get();

        assertThat(names.get()).containsExactly("knight");
    }

    @Test
    void aFolderNobodyCreatedIsNotAnError(@TempDir Path parent) {
        SkinFolderNames names =
                new SkinFolderNames(parent.resolve("absent"), scheduler, new NoopLogger(), Clock.systemUTC());

        names.get();

        assertThat(names.get()).isEmpty();
    }

    @Test
    void keystrokesWithinTheWindowReadTheDiskOnce(@TempDir Path folder) throws IOException {
        Files.createFile(folder.resolve("knight.png"));
        MovingClock clock = new MovingClock(Instant.parse("2026-08-19T09:00:00Z"));
        SkinFolderNames names = new SkinFolderNames(folder, scheduler, new NoopLogger(), clock);

        for (int keystroke = 0; keystroke < 10; keystroke++) {
            names.get();
        }

        assertThat(scheduler.runs.get()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(31));
        Files.createFile(folder.resolve("pirate.png"));
        names.get();

        assertThat(scheduler.runs.get()).isEqualTo(2);
        assertThat(names.get()).containsExactly("knight", "pirate");
    }

    /** Runs an async task straight away and counts how often it was asked to. */
    private static final class CountingScheduler implements Scheduler {
        private final AtomicInteger runs = new AtomicInteger();

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            runs.incrementAndGet();
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** Holds every async task until the test runs it, the way a real pool leaves the tick thread unblocked. */
    private static final class DeferringScheduler implements Scheduler {
        private final List<Runnable> queued = new ArrayList<>();

        private void runQueued() {
            List<Runnable> due = List.copyOf(queued);
            queued.clear();
            due.forEach(Runnable::run);
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            queued.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            queued.add(task);
        }
    }

    /** A clock the test moves by hand, so the staleness window is exercised without waiting for it. */
    private static final class MovingClock extends Clock {
        private final AtomicReference<Instant> now;

        private MovingClock(Instant start) {
            this.now = new AtomicReference<>(start);
        }

        private void advance(Duration by) {
            now.updateAndGet(instant -> instant.plus(by));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    /** A logger nobody reads: the failure paths under test report through it and nothing else. */
    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
