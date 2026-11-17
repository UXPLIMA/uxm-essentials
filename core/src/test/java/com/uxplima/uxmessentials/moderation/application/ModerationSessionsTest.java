package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.JailSync;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.FakeStoredPermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.OnlineLookup;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.QuietLog;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import org.junit.jupiter.api.Test;

/**
 * A join or quit stores nothing on the server thread: last seen and the jail countdown are written on the storage
 * lane. The time a quit burns off an online-only jail is the time the player was connected, counted once per visit,
 * even when they leave or come back before the lane has caught up.
 */
class ModerationSessionsTest {

    private static final String SERVER = "lobby";
    private static final Optional<String> ADDRESS = Optional.of("198.51.100.9");

    private final MutableClock clock = new MutableClock(NOW);
    private final QueuedScheduler scheduler = new QueuedScheduler();
    private final JailNetworkFakes.Records records = new JailNetworkFakes.Records();
    private final JailNetworkFakes.Network network = new JailNetworkFakes.Network();
    private final JailNetworkFakes.LiveSide live = new JailNetworkFakes.LiveSide(SERVER, network);
    private final Notifier notifier = new Notifier((viewer, key, placeholders) -> key.key(), (viewer, text) -> {});
    private final QuietLog log = new QuietLog();
    private final JailTransfer transfer = new JailTransfer(
            records,
            new JailNetworkFakes.Locations(),
            live,
            live,
            network.seenFrom(SERVER),
            Optional.empty(),
            new JailHopAlerts(new JailNetworkFakes.StaffLines(), JailSync.NONE, clock),
            notifier,
            scheduler,
            log,
            clock);
    private final JailCountdown countdown =
            new JailCountdown(records, live, transfer, new SilentAudit(), new RecordingEvents(), clock);
    private final StorageLane lane = new StorageLane(
            scheduler, notifier, new FakePermissions(), new FakeStoredPermissions(), new OnlineLookup(), log);
    private final ModerationSessions sessions = new ModerationSessions(records, countdown, lane, clock);

    private void jailFor(Duration remaining) {
        records.saveJail(SAM, JailState.onlineTimed("cell", remaining, Issuer.of(ALEX), Optional.empty(), NOW));
    }

    private Duration remaining() {
        return ((JailState.Active) records.loadJail(SAM)).remaining().orElseThrow();
    }

    @Test
    void aQuitBeforeTheJoinsWorkLandsBurnsTheVisitOnce() {
        jailFor(Duration.ofMinutes(10));
        network.connect(SAM, SERVER);

        sessions.joined(SAM, ADDRESS);
        clock.advance(Duration.ofMinutes(3));
        sessions.left(SAM, ADDRESS);

        assertThat(records.seen).isEmpty();
        assertThat(remaining()).isEqualTo(Duration.ofMinutes(10));

        scheduler.runAll();

        assertThat(remaining()).isEqualTo(Duration.ofMinutes(7));
        assertThat(records.seen)
                .containsExactly("Sam 198.51.100.9 " + NOW, "Sam 198.51.100.9 " + NOW.plus(Duration.ofMinutes(3)));
    }

    @Test
    void twoQuickVisitsAreEachCountedOnceWhicheverWorkerRunsFirst() {
        jailFor(Duration.ofMinutes(10));
        network.connect(SAM, SERVER);
        scheduler.lastFirst();

        sessions.joined(SAM, ADDRESS);
        clock.advance(Duration.ofMinutes(2));
        sessions.left(SAM, ADDRESS);
        sessions.joined(SAM, ADDRESS);
        clock.advance(Duration.ofMinutes(3));
        sessions.left(SAM, ADDRESS);
        scheduler.runAll();

        assertThat(remaining()).isEqualTo(Duration.ofMinutes(5));
        assertThat(records.seen).hasSize(4);
    }

    @Test
    void aSentenceServedDuringTheVisitIsReleased() {
        jailFor(Duration.ofMinutes(1));
        network.connect(SAM, SERVER);

        sessions.joined(SAM, ADDRESS);
        clock.advance(Duration.ofMinutes(2));
        sessions.left(SAM, ADDRESS);
        scheduler.runAll();

        assertThat(records.loadJail(SAM)).isEqualTo(JailState.none());
        assertThat(live.released).containsExactly("Sam");
    }

    @Test
    void aQuitWithNoJoinSeenRecordsTheTimeAndTouchesNoSentence() {
        jailFor(Duration.ofMinutes(10));

        sessions.left(SAM, Optional.empty());
        scheduler.runAll();

        assertThat(remaining()).isEqualTo(Duration.ofMinutes(10));
        assertThat(records.seen).containsExactly("Sam - " + NOW);
    }
}
