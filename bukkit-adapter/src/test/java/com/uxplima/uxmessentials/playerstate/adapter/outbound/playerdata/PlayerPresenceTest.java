package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * When a player's data file belongs to the server: from a pre-login that waited for any save of the file, through the
 * login's validation, until the connection closes.
 */
class PlayerPresenceTest {

    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @TempDir
    Path players;

    private final MovingClock clock = new MovingClock();
    private final PlayerPresence presence = new PlayerPresence(clock);

    @Test
    void aLoginWaitsForASaveOfThePlayersFileAndMarksNothingWhenItCannot() throws Exception {
        PlayerDataFiles files = new PlayerDataFiles(players, presence);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Thread saving = Thread.ofPlatform().daemon().start(() -> {
            ReentrantLock lock = PlayerDataFiles.lockOf(SAM);
            lock.lock();
            try {
                held.countDown();
                done.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        held.await();

        assertThat(files.admit(SAM, Duration.ZERO)).as("a save holds the file").isFalse();
        assertThat(presence.present(SAM)).isFalse();

        done.countDown();
        saving.join();

        assertThat(files.admit(SAM, Duration.ZERO)).isTrue();
        assertThat(presence.present(SAM)).isTrue();
    }

    @Test
    void aPreLoginThatIsNeverValidatedStopsCountingAfterAWhile() {
        presence.joining(SAM);

        clock.advance(PlayerPresence.JOINING_FOR.minusSeconds(1));
        assertThat(presence.present(SAM)).isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(presence.present(SAM))
                .as("the connection dropped before the server got that far")
                .isFalse();
    }

    @Test
    void aRefusedLoginStopsCountingAtOnce() {
        presence.joining(SAM);

        presence.refused(SAM);

        assertThat(presence.present(SAM)).isFalse();
    }

    @Test
    void aValidatedLoginCountsUntilEachOfItsConnectionsCloses() {
        presence.joining(SAM);
        presence.validated(SAM);
        clock.advance(PlayerPresence.JOINING_FOR.multipliedBy(2));
        presence.joining(SAM);
        presence.validated(SAM);

        presence.closed(SAM);
        assertThat(presence.present(SAM))
                .as("the second connection is still open")
                .isTrue();

        presence.closed(SAM);
        assertThat(presence.present(SAM)).isFalse();
    }

    @Test
    void aSecondLoginRefusedAtValidationDoesNotUncountThePlayerWhoIsOnline() {
        presence.joining(SAM);
        presence.validated(SAM);
        presence.joining(SAM);

        presence.turnedAway(SAM);
        presence.closed(SAM);

        assertThat(presence.present(SAM))
                .as("the refused connection closing leaves the open one counted")
                .isTrue();
        presence.closed(SAM);
        assertThat(presence.present(SAM)).isFalse();
    }

    @Test
    void aPlayerTheServerListsAsOnlineIsPresentEvenIfTheirLoginWasMissed() {
        Set<UUID> online = new HashSet<>();
        PlayerPresence listing = new PlayerPresence(clock, online::contains);
        assertThat(listing.present(SAM)).isFalse();

        online.add(SAM);

        assertThat(listing.present(SAM)).isTrue();
    }

    @Test
    void aPlayerAdoptedAgainAfterAReloadIsNotCountedTwice() {
        presence.adopt(SAM);
        presence.adopt(SAM);

        presence.closed(SAM);

        assertThat(presence.present(SAM)).isFalse();
    }

    /** A clock a test moves by hand. */
    private static final class MovingClock extends Clock {

        private Instant now = Instant.EPOCH;

        void advance(Duration by) {
            now = now.plus(by);
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
            return now;
        }
    }
}
