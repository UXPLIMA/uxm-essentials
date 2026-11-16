package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.persistence.worlds.WorldServerClaimer;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The start-up claim of a named backend, over a fake world table and fake world folders. */
class WorldServerAdoptionTest {

    private static final String LOBBY = "lobby";

    private final Table table = new Table();
    private final Log log = new Log();
    private final AtomicReference<Boolean> reconciledAfterClaim = new AtomicReference<>();

    private void start(Map<String, String> loaded, Disk disk, boolean adoptLoaded) {
        adoption(loaded, disk, adoptLoaded).claimThen(reconciledAfterClaim::set);
    }

    private WorldServerAdoption adoption(Map<String, String> loaded, Disk disk, boolean adoptLoaded) {
        return new WorldServerAdoption(
                new Inline(),
                log,
                LOBBY,
                true,
                LOBBY,
                table,
                new WorldServerAdoption.Worlds() {
                    @Override
                    public Map<String, String> loadedUids() {
                        return loaded;
                    }

                    @Override
                    public Map<String, Optional<String>> onDisk() {
                        return disk.scan();
                    }
                },
                world -> table.changes++,
                () -> adoptLoaded);
    }

    @Test
    void aFolderScanThatFailsClaimsNothingAndSkipsTheAdoptPhase() {
        start(
                Map.of("world", "uid-1"),
                () -> {
                    throw new UncheckedIOException(new IOException("world folder unreadable"));
                },
                true);

        assertThat(table.claimedWith).isNull();
        assertThat(table.adoptedWith).isNull();
        assertThat(reconciledAfterClaim.get()).isFalse();
        assertThat(log.warnings)
                .singleElement()
                .satisfies(line -> assertThat(line)
                        .contains("event=world_server_claim_failed")
                        .contains("reason=folder_scan"));
    }

    @Test
    void theClaimSeesTheLoadedWorldsAndTheFoldersWithTheirUids() {
        start(Map.of("world", "uid-1"), () -> Map.of("arena", Optional.of("uid-2"), "games", Optional.empty()), false);

        assertThat(table.claimedWith).containsExactlyInAnyOrder("world", "arena", "games");
        assertThat(table.uidsWith).containsOnly(Map.entry("world", "uid-1"), Map.entry("arena", "uid-2"));
        assertThat(reconciledAfterClaim.get()).isTrue();
    }

    @Test
    void aLoadedWorldIsAdoptedFromItsTemplateOnlyWhileAutoAdoptIsOn() {
        start(Map.of("arena", "uid-2"), Map::of, true);
        assertThat(table.adoptedWith).containsOnlyKeys("arena");
        assertThat(table.changes).isOne();

        table.adoptedWith = null;
        start(Map.of("arena", "uid-2"), Map::of, false);
        assertThat(table.adoptedWith).isNull();
    }

    @Test
    void copiesTheClaimCommittedReachTheCacheEvenWhenTheAdoptAfterItFails() {
        table.claims = new WorldServerClaimer.Claims(true, List.of("world"), List.of());
        table.adoptFailure = new PersistenceException("database gone");

        start(Map.of("world", "uid-1"), Map::of, true);

        assertThat(table.changes).isOne();
        assertThat(reconciledAfterClaim.get()).isFalse();
        assertThat(log.errors)
                .singleElement()
                .satisfies(line -> assertThat(line)
                        .contains("event=world_server_claim_failed")
                        .contains("reason=adopt"));
    }

    @Test
    void aWorldAdoptedFromTheTemplateOfAnotherWorldOfItsNameIsWarnedAbout() {
        table.adoptConflicts = List.of("arena");

        start(Map.of("arena", "uid-2"), Map::of, true);

        assertThat(log.warnings)
                .singleElement()
                .satisfies(line -> assertThat(line).contains("event=world_server_claim_conflict"));
    }

    @Test
    void aClaimByNameWhoseWriteFailedButLeftTheWorldHereIsLoggedAndReachesTheCache() {
        table.claimNamedFailure = new PersistenceException("connection lost at commit");
        table.kept = true;

        boolean claimed = adoption(Map.of(), Map::of, false).claim(WorldName.of("arena"));

        assertThat(claimed).isFalse();
        assertThat(table.changes).isOne();
        assertThat(log.errors)
                .singleElement()
                .satisfies(line -> assertThat(line).contains("event=world_server_claim_uncertain"));
    }

    @Test
    void aClaimByNameWhoseWriteFailedAndLeftNothingFails() {
        PersistenceException failure = new PersistenceException("database gone");
        table.claimNamedFailure = failure;

        assertThatThrownBy(() -> adoption(Map.of(), Map::of, false).claim(WorldName.of("arena")))
                .isSameAs(failure);
        assertThat(table.changes).isZero();
    }

    @Test
    void aClaimByNameWhoseWriteAndFollowUpReadBothFailReportsTheWriteFailure() {
        PersistenceException failure = new PersistenceException("database gone");
        PersistenceException unreadable = new PersistenceException("still gone");
        table.claimNamedFailure = failure;
        table.keepsFailure = unreadable;

        assertThatThrownBy(() -> adoption(Map.of(), Map::of, false).claim(WorldName.of("arena")))
                .isSameAs(failure)
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(unreadable));
        assertThat(table.changes).isZero();
    }

    @FunctionalInterface
    private interface Disk {
        Map<String, Optional<String>> scan();
    }

    /**
     * A world table whose start-up claim has run already unless told otherwise, and whose adopt copies every loaded
     * world it is given.
     */
    private static final class Table implements WorldServerAdoption.Store {

        WorldServerClaimer.Claims claims = new WorldServerClaimer.Claims(false, List.of(), List.of());

        List<String> adoptConflicts = List.of();

        @Nullable RuntimeException adoptFailure;

        @Nullable RuntimeException claimNamedFailure;

        boolean kept;

        @Nullable RuntimeException keepsFailure;

        @Nullable Collection<String> claimedWith;

        @Nullable Map<String, String> uidsWith;

        @Nullable Map<String, String> adoptedWith;

        int changes;

        @Override
        public WorldServerClaimer.Claims claimPresent(
                String server, Collection<String> present, Map<String, String> uids) {
            claimedWith = List.copyOf(present);
            uidsWith = Map.copyOf(uids);
            return claims;
        }

        @Override
        public WorldServerClaimer.Claims adoptLoaded(String server, Map<String, String> loadedUids) {
            if (adoptFailure != null) {
                throw adoptFailure;
            }
            adoptedWith = Map.copyOf(loadedUids);
            return new WorldServerClaimer.Claims(true, List.copyOf(loadedUids.keySet()), adoptConflicts);
        }

        @Override
        public boolean claimNamed(String server, String name) {
            if (claimNamedFailure != null) {
                throw claimNamedFailure;
            }
            return false;
        }

        @Override
        public boolean keeps(String server, String name) {
            if (keepsFailure != null) {
                throw keepsFailure;
            }
            return kept;
        }

        @Override
        public int rename(String from, String to) {
            return 0;
        }
    }

    private static final class Log implements Logger {

        final List<String> warnings = new ArrayList<>();

        final List<String> errors = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {
            errors.add(message);
        }

        @Override
        public void debug(String message, Object... args) {}
    }

    /** Every lane runs its task at once, on the calling thread. */
    private static final class Inline implements Scheduler {

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
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
