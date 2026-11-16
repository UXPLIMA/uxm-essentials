package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.JailDirectory;
import com.uxplima.uxmessentials.moderation.application.port.JailSync;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.QuietLog;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.testing.SteppedScheduler;
import org.junit.jupiter.api.Test;

/**
 * {@code /togglejail} releases a jailed player and jails a free one. Without a jail name it uses the configured
 * default jail, and with none set it refuses and lists the jails {@code /jail} accepts, the ones made with
 * {@code /setjail} included.
 */
class ToggleJailTest {

    private static final String SERVER = "lobby";

    private final MutableClock clock = new MutableClock(NOW);
    private final SteppedScheduler scheduler = new SteppedScheduler(clock);
    private final JailNetworkFakes.Records records = new JailNetworkFakes.Records();
    private final JailNetworkFakes.Locations setJails = new JailNetworkFakes.Locations();
    private final JailNetworkFakes.Network network = new JailNetworkFakes.Network();
    private final JailNetworkFakes.LiveSide live = new JailNetworkFakes.LiveSide(SERVER, network);
    private final RecordingNotifier notifier = new RecordingNotifier();

    /**
     * The directory {@code /jail} validates against: the {@code /setjail} jails, with no config jail. The quick
     * config-only read used for tab completion knows none of them, as on a server that only uses {@code /setjail}.
     */
    private final JailDirectory directory = new JailDirectory() {
        @Override
        public boolean exists(String jail) {
            return setJails.exists(jail);
        }

        @Override
        public boolean isWallClock(String jail) {
            return false;
        }

        @Override
        public List<String> names() {
            return setJails.names();
        }

        @Override
        public List<String> peekNames() {
            return List.of();
        }
    };

    ToggleJailTest() {
        network.live.add(SERVER);
        network.connect(ALEX, SERVER);
        scheduler.online(ALEX);
    }

    @Test
    void withoutAJailNameAFreePlayerGoesToTheDefaultJailMadeWithSetjail() {
        setJail("cell");
        setJail("annex");

        toggleJail(Optional.of("cell")).toggle(SAM, ALEX, "", Optional.empty());

        assertThat(records.loadJail(ALEX))
                .isInstanceOfSatisfying(
                        JailState.Active.class,
                        active -> assertThat(active.jail()).isEqualTo("cell"));
    }

    @Test
    void withoutAJailNameOrADefaultTheCommandListsTheJailsAndJailsNobody() {
        setJail("cell");
        setJail("annex");

        toggleJail(Optional.empty()).toggle(SAM, ALEX, "", Optional.empty());

        assertThat(records.loadJail(ALEX)).isEqualTo(JailState.none());
        assertThat(notifier.sent())
                .containsExactly(new RecordingNotifier.Sent(
                        SAM, ModerationMessageKey.TOGGLEJAIL_NAME_NEEDED.key(), Map.of("jails", "annex, cell")));
    }

    @Test
    void withNoJailAtAllTheCommandSaysThereIsNone() {
        toggleJail(Optional.empty()).toggle(SAM, ALEX, "", Optional.empty());

        assertThat(records.loadJail(ALEX)).isEqualTo(JailState.none());
        assertThat(notifier.keys()).containsExactly(ModerationMessageKey.JAILS_EMPTY.key());
    }

    @Test
    void aNamedJailWinsOverTheDefault() {
        setJail("cell");
        setJail("annex");

        toggleJail(Optional.of("cell")).toggle(SAM, ALEX, "annex", Optional.empty());

        assertThat(records.loadJail(ALEX))
                .isInstanceOfSatisfying(
                        JailState.Active.class,
                        active -> assertThat(active.jail()).isEqualTo("annex"));
    }

    @Test
    void aJailedPlayerIsReleasedWithoutNamingAJail() {
        setJail("cell");
        ToggleJail toggle = toggleJail(Optional.empty());
        toggle.toggle(SAM, ALEX, "cell", Optional.empty());

        toggle.toggle(SAM, ALEX, "", Optional.empty());

        assertThat(records.loadJail(ALEX)).isEqualTo(JailState.none());
        assertThat(notifier.keys()).doesNotContain(ModerationMessageKey.TOGGLEJAIL_NAME_NEEDED.key());
    }

    private void setJail(String name) {
        setJails.save(StoredJail.of(name, at(7, 64, 7), SERVER));
    }

    private ToggleJail toggleJail(Optional<String> defaultJail) {
        SilentAudit audit = new SilentAudit();
        RecordingEvents events = new RecordingEvents();
        JailTransfer transfer = new JailTransfer(
                records,
                setJails,
                live,
                live,
                network.seenFrom(SERVER),
                Optional.empty(),
                new JailHopAlerts(new JailNetworkFakes.StaffLines(), JailSync.NONE, clock),
                notifier.notifier(),
                scheduler,
                new QuietLog(),
                clock);
        Jail jail = new Jail(
                records,
                directory,
                transfer,
                JailSync.NONE,
                new ModerationGuard(new FakePermissions()),
                notifier.notifier(),
                audit,
                events,
                clock);
        Unjail unjail = new Unjail(records, live, JailSync.NONE, notifier.notifier(), audit, events, clock);
        return new ToggleJail(records, directory, jail, unjail, notifier.notifier(), defaultJail);
    }
}
