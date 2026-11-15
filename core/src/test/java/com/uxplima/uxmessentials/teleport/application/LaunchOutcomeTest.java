package com.uxplima.uxmessentials.teleport.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.application.port.ArrivalGrace;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFee;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.testing.ClockCooldowns;
import com.uxplima.uxmessentials.testing.Fixtures;
import com.uxplima.uxmessentials.testing.ManualWarmups;
import com.uxplima.uxmessentials.testing.QuietLog;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * How a local teleport ended, as the engine reports it to whoever started it: one outcome per launch, a landing only
 * once the player stands at the destination, and every way a teleport can end without moving them named apart.
 */
class LaunchOutcomeTest {

    private static final Position HOME = Fixtures.at(300, 70, -40);
    private static final Position MARKET = Fixtures.at(-90, 64, 12);

    private final ManualWarmups warmups = new ManualWarmups();
    private final Hops hops = new Hops();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private boolean jailed;
    private boolean tagged;
    private boolean vetoed;
    private final TeleportEngine engine = new TeleportEngine(
            new ClockCooldowns(CLOCK),
            warmups,
            hops,
            notifier.notifier(),
            new RecordingEvents(),
            new TeleportSettings(new WithCooldown()),
            who -> jailed,
            who -> tagged,
            TeleportFee.FREE,
            ArrivalGrace.NONE,
            proposal -> !vetoed,
            new QuietLog());

    private List<TeleportOutcome> launch(Position to) {
        List<TeleportOutcome> outcomes = new ArrayList<>();
        engine.launch(ALEX, Destination.at(to), TeleportKind.HOME, outcomes::add);
        return outcomes;
    }

    @Test
    void aTeleportThatArrivesReportsALandingOnceAndStartsTheCooldown() {
        List<TeleportOutcome> outcomes = launch(HOME);
        assertThat(outcomes).as("nothing is known while the warmup runs").isEmpty();

        warmups.finish(ALEX);

        assertThat(outcomes).containsExactly(TeleportOutcome.LANDED);
        assertThat(hops.landed).containsExactly(HOME);
        assertThat(engine.launch(ALEX, Destination.at(HOME), TeleportKind.HOME).asError())
                .contains(TeleportError.ON_COOLDOWN);
    }

    @Test
    void aMoveDuringTheWarmupReportsACancelAndMovesNobody() {
        List<TeleportOutcome> outcomes = launch(HOME);

        warmups.move(ALEX);

        assertThat(outcomes).containsExactly(TeleportOutcome.CANCELLED);
        assertThat(hops.landed).isEmpty();
        assertThat(notifier.keys()).containsExactly("teleport.warmup.cancelled");
    }

    @Test
    void aNewerTeleportReportsTheOneItReplacedAsSupersededAndLandsItself() {
        List<TeleportOutcome> first = launch(HOME);
        List<TeleportOutcome> second = launch(MARKET);

        assertThat(first).containsExactly(TeleportOutcome.SUPERSEDED);
        assertThat(second).isEmpty();

        warmups.finish(ALEX);

        assertThat(first).containsExactly(TeleportOutcome.SUPERSEDED);
        assertThat(second).containsExactly(TeleportOutcome.LANDED);
        assertThat(hops.landed).containsExactly(MARKET);
        assertThat(notifier.keys()).containsExactly("teleport.warmup.replaced");
    }

    @Test
    void aHopThatMissesAfterTheWarmupReportsAMissAndStartsNoCooldown() {
        hops.misses = true;
        List<TeleportOutcome> outcomes = launch(HOME);

        warmups.finish(ALEX);

        assertThat(outcomes).containsExactly(TeleportOutcome.MISSED);
        assertThat(engine.gateHop(ALEX, Destination.at(HOME), TeleportKind.HOME).isOk())
                .isTrue();
    }

    @Test
    void aTeleportTheRulesRefuseReportsARefusalWithoutAWarmup() {
        jailed = true;
        assertThat(launch(HOME)).containsExactly(TeleportOutcome.REFUSED);

        jailed = false;
        tagged = true;
        assertThat(launch(HOME)).containsExactly(TeleportOutcome.REFUSED);

        assertThat(warmups.waiting(ALEX)).isFalse();
    }

    @Test
    void aTeleportStillOnCooldownReportsARefusal() {
        launch(HOME);
        warmups.finish(ALEX);

        assertThat(launch(MARKET)).containsExactly(TeleportOutcome.REFUSED);
        assertThat(warmups.waiting(ALEX)).isFalse();
    }

    @Test
    void aTeleportAnotherPluginVetoesReportsARefusal() {
        vetoed = true;

        assertThat(launch(HOME)).containsExactly(TeleportOutcome.REFUSED);
        assertThat(warmups.waiting(ALEX)).isFalse();
    }

    @Test
    void anExecutorThatReportsBothALandingAndAMissIsHeardOnceAsALanding() {
        hops.reportsBoth = true;
        List<TeleportOutcome> outcomes = launch(HOME);

        warmups.finish(ALEX);

        assertThat(outcomes).containsExactly(TeleportOutcome.LANDED);
    }

    @Test
    void aCancelArrivingAfterTheLandingChangesNothing() {
        List<TeleportOutcome> outcomes = launch(HOME);
        var handle = warmups.handleOf(ALEX);
        warmups.finish(ALEX);

        handle.cancel();
        handle.supersede();

        assertThat(outcomes).containsExactly(TeleportOutcome.LANDED);
    }

    @Test
    void aLaunchWithNobodyListeningStillTeleports() {
        assertThat(engine.launch(ALEX, Destination.at(HOME), TeleportKind.HOME).isOk())
                .isTrue();
        warmups.finish(ALEX);

        assertThat(hops.landed).containsExactly(HOME);
    }

    /** Lands every hop unless told to miss; can also be told to break its promise and report both. */
    private static final class Hops implements TeleportExecutor {

        final List<Position> landed = new ArrayList<>();
        boolean misses;
        boolean reportsBoth;

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            landed.add(destination.position());
        }

        @Override
        public void teleport(
                PlayerRef who, Destination destination, TeleportKind kind, Runnable onLanded, Runnable onFailed) {
            if (misses) {
                onFailed.run();
                return;
            }
            teleport(who, destination, kind);
            onLanded.run();
            if (reportsBoth) {
                onFailed.run();
            }
        }
    }

    /** Teleport settings with a cooldown, so a landing can be seen starting one. */
    private static final class WithCooldown implements ConfigStore {

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
            return path.equals("default-cooldown") ? 60 : fallback;
        }
    }
}
