package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.persistence.worlds.WorldServerClaimer;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The start-up claim of a named backend, over a fake world table and fake world folders. */
class WorldServerAdoptionTest {

    private static final String LOBBY = "lobby";

    private final Table table = new Table();
    private final Log log = new Log();
    private final AtomicReference<Boolean> reconciledAfterClaim = new AtomicReference<>();

    private void start(Map<String, String> loaded, Disk disk, boolean adoptLoaded) {
        new WorldServerAdoption(
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
                        () -> adoptLoaded)
                .claimThen(reconciledAfterClaim::set);
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

    @FunctionalInterface
    private interface Disk {
        Map<String, Optional<String>> scan();
    }

    /** A world table whose start-up claim has run already and whose adopt copies every loaded world it is given. */
    private static final class Table implements WorldServerAdoption.Store {

        @Nullable Collection<String> claimedWith;

        @Nullable Map<String, String> uidsWith;

        @Nullable Map<String, String> adoptedWith;

        int changes;

        @Override
        public WorldServerClaimer.Claims claimPresent(
                String server, Collection<String> present, Map<String, String> uids) {
            claimedWith = List.copyOf(present);
            uidsWith = Map.copyOf(uids);
            return new WorldServerClaimer.Claims(false, List.of(), List.of());
        }

        @Override
        public List<String> adoptLoaded(String server, Map<String, String> loadedUids) {
            adoptedWith = Map.copyOf(loadedUids);
            return List.copyOf(loadedUids.keySet());
        }

        @Override
        public boolean claimNamed(String server, String name) {
            return false;
        }

        @Override
        public int rename(String from, String to) {
            return 0;
        }
    }

    private static final class Log implements Logger {

        final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {}

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
