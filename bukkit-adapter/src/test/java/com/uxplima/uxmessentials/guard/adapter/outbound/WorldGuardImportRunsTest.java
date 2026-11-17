package com.uxplima.uxmessentials.guard.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.guard.adapter.inbound.command.GuardCommand.ImportKind;
import com.uxplima.uxmessentials.guard.adapter.inbound.command.GuardCommand.ImportOutcome;
import com.uxplima.uxmessentials.guard.application.EditContext;
import com.uxplima.uxmessentials.guard.application.GuardConfig;
import com.uxplima.uxmessentials.guard.application.GuardService;
import com.uxplima.uxmessentials.guard.application.port.GuardBackups;
import com.uxplima.uxmessentials.guard.application.port.GuardStore;
import com.uxplima.uxmessentials.guard.application.port.GuardSync;
import com.uxplima.uxmessentials.guard.application.port.GuardWorlds;
import com.uxplima.uxmessentials.guard.domain.HistoryEntry;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionKey;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Asking for a WorldGuard import while one is still running. */
class WorldGuardImportRunsTest {

    private static final EditContext CONSOLE = new EditContext("console", true);

    /** The server's plugins folder; WorldGuard's files would sit beside this plugin's own folder in it. */
    @TempDir
    Path plugins;

    private final AsyncLane lane = new AsyncLane();
    private final List<String> warnings = new ArrayList<>();

    private WorldGuardImporter importer() {
        GuardService service = new GuardService(
                new UntouchedStore(),
                GuardSync.NONE,
                new NoWorlds(),
                lane,
                GuardConfig.from(new Defaults(), "lobby", warnings::add),
                warnings::add);
        return new WorldGuardImporter(
                plugins.resolve("uxmEssentials"),
                service,
                new UntouchedBackups(),
                lane,
                warnings::add,
                new NoPlayers());
    }

    @Test
    void aSecondRequestWhileOneRunsIsRefusedAndStartsNothing() {
        WorldGuardImporter importer = importer();

        CompletableFuture<ImportOutcome> first = importer.run("undo", "yaml", CONSOLE);
        CompletableFuture<ImportOutcome> second = importer.run("undo", "yaml", CONSOLE);

        assertThat(second).isCompletedWithValueMatching(outcome -> outcome.kind() == ImportKind.BUSY);
        assertThat(lane.queued())
                .as("only the first request put work on the lane")
                .isEqualTo(1);
        assertThat(first).isNotDone();

        lane.runAll();

        assertThat(first).isCompletedWithValueMatching(outcome -> outcome.kind() == ImportKind.NOTHING_TO_UNDO);
    }

    @Test
    void anImportThatFinishedLetsTheNextOneRun() {
        WorldGuardImporter importer = importer();
        var unused = importer.run("undo", "yaml", CONSOLE);
        lane.runAll();

        CompletableFuture<ImportOutcome> next = importer.run("undo", "yaml", CONSOLE);
        lane.runAll();

        assertThat(next).isCompletedWithValueMatching(outcome -> outcome.kind() == ImportKind.NOTHING_TO_UNDO);
    }

    @Test
    void anImportThatFailedLetsTheNextOneRun() {
        WorldGuardImporter importer = importer();
        CompletableFuture<ImportOutcome> failed = importer.run("dry-run", "yaml", CONSOLE);
        lane.runAll();

        assertThat(failed).as("there is no WorldGuard folder to read").isCompletedExceptionally();
        CompletableFuture<ImportOutcome> next = importer.run("undo", "yaml", CONSOLE);
        lane.runAll();

        assertThat(next).isCompletedWithValueMatching(outcome -> outcome.kind() == ImportKind.NOTHING_TO_UNDO);
    }

    /** Holds async work until the test runs it; everything else runs in place. */
    private static final class AsyncLane implements Scheduler {

        private final List<Runnable> pending = new ArrayList<>();

        int queued() {
            return pending.size();
        }

        void runAll() {
            while (!pending.isEmpty()) {
                List<Runnable> due = List.copyOf(pending);
                pending.clear();
                due.forEach(Runnable::run);
            }
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
            pending.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            pending.add(task);
        }
    }

    /** The guard config at its defaults. */
    private static final class Defaults implements ConfigStore {

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    /** A store these imports never reach: an undo with no batch and a failed read stop before the store. */
    private static final class UntouchedStore implements GuardStore {

        @Override
        public List<Region> load(Set<ServerScope> scopes) {
            throw new AssertionError("the import reached the store");
        }

        @Override
        public Optional<Region> find(ServerScope scope, RegionKey key) {
            throw new AssertionError("the import reached the store");
        }

        @Override
        public HistoryEntry write(@Nullable Region before, @Nullable Region after, String actor, String change) {
            throw new AssertionError("the import reached the store");
        }

        @Override
        public List<HistoryEntry> history(@Nullable RegionKey key, int offset, int limit) {
            throw new AssertionError("the import reached the store");
        }

        @Override
        public Optional<HistoryEntry> historyEntry(long id) {
            throw new AssertionError("the import reached the store");
        }

        @Override
        public long stamp(Set<ServerScope> scopes) {
            throw new AssertionError("the import reached the store");
        }

        @Override
        public void logDenials(List<Denial> denials) {
            throw new AssertionError("the import reached the store");
        }

        @Override
        public List<Denial> denials(@Nullable DenialPlayer player, @Nullable String region, int offset, int limit) {
            throw new AssertionError("the import reached the store");
        }

        @Override
        public void pruneDenials(Instant before) {
            throw new AssertionError("the import reached the store");
        }
    }

    private static final class NoWorlds implements GuardWorlds {

        @Override
        public List<String> loaded() {
            return List.of();
        }

        @Override
        public Optional<Height> height(String world) {
            return Optional.empty();
        }
    }

    private static final class UntouchedBackups implements GuardBackups {

        @Override
        public String write(List<Region> regions, String reason, int keep) {
            throw new AssertionError("the import took a backup");
        }

        @Override
        public List<Region> read(String name) {
            throw new AssertionError("the import read a backup");
        }

        @Override
        public List<String> list() {
            return List.of();
        }
    }

    /** Nobody the import could name: these runs never get as far as resolving members. */
    private static final class NoPlayers implements com.uxplima.uxmessentials.shared.application.port.PlayerLookup {

        @Override
        public java.util.Optional<com.uxplima.uxmessentials.shared.domain.PlayerRef> findOnlineByName(String name) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<com.uxplima.uxmessentials.shared.domain.PlayerRef> findByUuid(java.util.UUID uuid) {
            return java.util.Optional.empty();
        }

        @Override
        public boolean isOnline(java.util.UUID uuid) {
            return false;
        }
    }
}
