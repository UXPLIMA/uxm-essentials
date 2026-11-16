package com.uxplima.uxmessentials.teleport.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.teleport.application.port.ArrivalGrace;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFee;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.testing.ClockCooldowns;
import com.uxplima.uxmessentials.testing.Fixtures;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.QuietLog;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every teleport starts its cooldown at the configured {@code cooldown-start-phase}. A teleport with no request step
 * starts it under {@code request} and {@code accept} once its checks pass and its warmup begins, and keeps it when the
 * warmup is cancelled or the hop misses; under {@code teleport} only a landing starts it. A player request starts it
 * for the sender when it opens, for the mover when it is accepted, or on landing.
 */
class CooldownStartPhaseTest {

    private static final Position THERE = Fixtures.at(40, 64, -12);
    private static final Position SAMS_SPOT = Fixtures.at(100, 70, 100);

    private final MutableClock clock = new MutableClock(NOW);
    private final ClockCooldowns cooldowns = new ClockCooldowns(clock);
    private final TeleportFakes.HeldWarmups warmups = new TeleportFakes.HeldWarmups();
    private final TeleportFakes.Hops hops = new TeleportFakes.Hops();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final RecordingEvents events = new RecordingEvents();
    private final CountingFee fee = new CountingFee("100");
    private boolean jailed;
    private boolean vetoed;

    private TeleportSettings settings(String phase, String... more) {
        List<String> pairs = new ArrayList<>(List.of("default-cooldown", "30", "cooldown-start-phase", phase));
        pairs.addAll(List.of(more));
        return new TeleportSettings(TeleportFakes.Config.of(pairs.toArray(String[]::new)));
    }

    private TeleportEngine engine(String phase, String... more) {
        return new TeleportEngine(
                cooldowns,
                warmups,
                hops,
                notifier.notifier(),
                events,
                settings(phase, more),
                who -> jailed,
                who -> false,
                fee,
                ArrivalGrace.NONE,
                proposal -> !vetoed,
                new QuietLog());
    }

    private static Destination there() {
        return Destination.at(THERE);
    }

    private static boolean onCooldown(TeleportEngine engine, TeleportKind kind) {
        return engine.gateHop(ALEX, there(), kind).isErr();
    }

    // A teleport the player starts: /home, /warp, a player warp, /spawn, /back, a world.

    @ParameterizedTest
    @EnumSource(
            value = TeleportKind.class,
            names = {"HOME", "WARP", "SPAWN", "BACK"})
    void underTheRequestPhaseTheClockStartsWhenTheWarmupBegins(TeleportKind kind) {
        TeleportEngine engine = engine("request");

        assertThat(engine.launch(ALEX, there(), kind).isOk()).isTrue();

        assertThat(onCooldown(engine, kind))
                .as("on cooldown while still warming up")
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(
            value = TeleportKind.class,
            names = {"HOME", "WARP", "SPAWN", "BACK"})
    void underTheAcceptPhaseATeleportWithNoRequestStartsTheClockWhenTheWarmupBegins(TeleportKind kind) {
        TeleportEngine engine = engine("accept");

        assertThat(engine.launch(ALEX, there(), kind).isOk()).isTrue();

        assertThat(onCooldown(engine, kind))
                .as("on cooldown while still warming up")
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(
            value = TeleportKind.class,
            names = {"HOME", "WARP", "SPAWN", "BACK"})
    void underTheTeleportPhaseTheClockStartsOnlyOnLanding(TeleportKind kind) {
        TeleportEngine engine = engine("teleport");

        engine.launch(ALEX, there(), kind);
        assertThat(onCooldown(engine, kind)).as("free while warming up").isFalse();
        warmups.finish();

        assertThat(onCooldown(engine, kind)).isTrue();
    }

    @Test
    void underTheRequestPhaseTheClockStartsOnceSoTheLandingDoesNotExtendIt() {
        TeleportEngine engine = engine("request");
        engine.launch(ALEX, there(), TeleportKind.HOME);
        clock.advance(Duration.ofSeconds(20));
        warmups.finish();

        clock.advance(Duration.ofSeconds(10));

        assertThat(onCooldown(engine, TeleportKind.HOME)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"request", "accept"})
    void aWarmupCancelledAfterTheClockStartedKeepsTheCooldownAndStillReportsTheCancelForARefund(String phase) {
        TeleportEngine engine = engine(phase);
        List<TeleportOutcome> outcomes = new ArrayList<>();
        engine.launch(ALEX, there(), TeleportKind.WARP, outcomes::add);

        warmups.moveAway();

        assertThat(outcomes).containsExactly(TeleportOutcome.CANCELLED);
        assertThat(hops.landed).isEmpty();
        assertThat(onCooldown(engine, TeleportKind.WARP)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"request", "accept"})
    void aTeleportRefusedBeforeItsWarmupStartsNoCooldown(String phase) {
        TeleportEngine engine = engine(phase);

        jailed = true;
        assertThat(engine.launch(ALEX, there(), TeleportKind.HOME).asError()).contains(TeleportError.JAILED);
        jailed = false;
        vetoed = true;
        assertThat(engine.launch(ALEX, there(), TeleportKind.HOME).asError()).contains(TeleportError.VETOED);
        vetoed = false;

        assertThat(onCooldown(engine, TeleportKind.HOME)).isFalse();
    }

    // A random teleport, on its own clock.

    @ParameterizedTest
    @ValueSource(strings = {"request", "accept"})
    void underAnEarlyPhaseARandomTeleportStartsItsOwnClockWhenTheWarmupBeginsAndKeepsItWhenCancelled(String phase) {
        TeleportEngine engine = engine(phase, "rtp.cost", "40");
        assertThat(engine.gateRandom(ALEX).isOk()).isTrue();
        engine.launchRandom(ALEX, there());

        warmups.moveAway();

        assertThat(engine.gateRandom(ALEX).asError()).contains(TeleportError.ON_COOLDOWN);
        assertThat(fee.charges)
                .as("the money is taken only when the warmup ends")
                .isZero();
        assertThat(onCooldown(engine, TeleportKind.HOME))
                .as("the random clock is its own")
                .isFalse();
    }

    @Test
    void underTheTeleportPhaseARandomTeleportStartsItsClockOnlyOnLanding() {
        TeleportEngine engine = engine("teleport");
        engine.launchRandom(ALEX, there());
        assertThat(engine.gateRandom(ALEX).isOk()).isTrue();

        warmups.finish();

        assertThat(engine.gateRandom(ALEX).asError()).contains(TeleportError.ON_COOLDOWN);
    }

    // A hop from another server, landing here: the origin checked the same clock before it sent the player.

    @ParameterizedTest
    @ValueSource(strings = {"request", "accept"})
    void underAnEarlyPhaseAHopStartsTheClockTheOriginChecksAsTheMoveStartsEvenWhenItMisses(String phase) {
        TeleportEngine origin = engine(phase);
        TeleportEngine target = engine(phase);
        hops.misses = true;

        assertThat(target.arriveImmediately(ALEX, there(), TeleportKind.WARP, () -> {}, () -> {})
                        .isOk())
                .isTrue();

        assertThat(onCooldown(origin, TeleportKind.WARP)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"request", "accept"})
    void aHopRefusedWhereItArrivesStartsNoCooldown(String phase) {
        TeleportEngine target = engine(phase);
        jailed = true;

        assertThat(target.arriveImmediately(ALEX, there(), TeleportKind.HOME, () -> {}, () -> {})
                        .asError())
                .contains(TeleportError.JAILED);
        jailed = false;

        assertThat(onCooldown(engine(phase), TeleportKind.HOME)).isFalse();
    }

    @Test
    void underTheTeleportPhaseAHopStartsTheClockOnlyWhenItLands() {
        TeleportEngine origin = engine("teleport");
        TeleportEngine target = engine("teleport");
        hops.misses = true;
        target.arriveImmediately(ALEX, there(), TeleportKind.HOME, () -> {}, () -> {});
        assertThat(onCooldown(origin, TeleportKind.HOME))
                .as("a missed hop starts nothing")
                .isFalse();

        hops.misses = false;
        target.arriveImmediately(ALEX, there(), TeleportKind.HOME, () -> {}, () -> {});

        assertThat(onCooldown(origin, TeleportKind.HOME)).isTrue();
    }

    // A player request: /tpa and /tpahere.

    private final class Tpa {

        final TeleportFakes.Flags flags = new TeleportFakes.Flags();
        final TeleportFakes.Requests registry = new TeleportFakes.Requests();
        final TeleportEngine engine;
        final RequestTeleport request;
        final AcceptTeleport accept;

        Tpa(String phase) {
            engine = engine(phase);
            request = new RequestTeleport(
                    registry, flags, notifier.notifier(), events, settings(phase), who -> jailed, engine, clock);
            accept = new AcceptTeleport(registry, engine, notifier.notifier(), events, clock);
        }

        boolean onCooldown(PlayerRef who) {
            return engine.gateHop(who, there(), TeleportKind.HOME).isErr();
        }
    }

    @Test
    void underTheRequestPhaseASentRequestStartsTheSendersClockEvenWhenDenied() {
        Tpa tpa = new Tpa("request");

        assertThat(tpa.request.request(ALEX, SAM, RequestDirection.TO_REQUESTER).isOk())
                .isTrue();
        assertThat(tpa.onCooldown(ALEX)).isTrue();
        tpa.accept.deny(SAM);

        assertThat(tpa.onCooldown(ALEX)).isTrue();
        assertThat(tpa.onCooldown(SAM))
                .as("the player asked to move did nothing")
                .isFalse();
    }

    @Test
    void underTheRequestPhaseARequestTheTargetRefusesStartsNothing() {
        Tpa tpa = new Tpa("request");
        tpa.flags.refusing.add(SAM);

        assertThat(tpa.request.request(ALEX, SAM, RequestDirection.TO_TARGET).asError())
                .contains(TeleportError.TARGET_TOGGLED_OFF);

        assertThat(tpa.onCooldown(ALEX)).isFalse();
    }

    @Test
    void underTheRequestPhaseTheAcceptAndTheLandingDoNotRestartTheClock() {
        Tpa tpa = new Tpa("request");
        tpa.request.request(ALEX, SAM, RequestDirection.TO_TARGET);
        clock.advance(Duration.ofSeconds(20));
        tpa.accept.accept(SAM, SAMS_SPOT);
        warmups.finish();

        clock.advance(Duration.ofSeconds(10));

        assertThat(hops.landed).containsExactly("Alex:REQUEST");
        assertThat(tpa.onCooldown(ALEX)).isFalse();
    }

    @Test
    void underTheAcceptPhaseTheMoverIsStampedOnAcceptAndKeepsItWhenTheWarmupIsCancelled() {
        Tpa tpa = new Tpa("accept");
        tpa.request.request(ALEX, SAM, RequestDirection.TO_REQUESTER);
        assertThat(tpa.onCooldown(ALEX)).isFalse();

        tpa.accept.accept(SAM, SAMS_SPOT);
        warmups.moveAway();

        assertThat(tpa.onCooldown(SAM)).isTrue();
        assertThat(tpa.onCooldown(ALEX)).isFalse();
    }

    @Test
    void underTheTeleportPhaseAnAcceptedRequestStartsTheClockOnlyOnLanding() {
        Tpa tpa = new Tpa("teleport");
        tpa.request.request(ALEX, SAM, RequestDirection.TO_TARGET);
        tpa.accept.accept(SAM, SAMS_SPOT);
        assertThat(tpa.onCooldown(ALEX)).isFalse();

        warmups.finish();

        assertThat(tpa.onCooldown(ALEX)).isTrue();
    }

    /** One balance, counting each debit that took. */
    private static final class CountingFee implements TeleportFee {

        private BigDecimal balance;
        private int charges;

        CountingFee(String balance) {
            this.balance = new BigDecimal(balance);
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount) {
            return balance.compareTo(amount) >= 0;
        }

        @Override
        public CompletionStage<Result<Unit, TeleportError>> charge(PlayerRef who, BigDecimal amount) {
            balance = balance.subtract(amount);
            charges++;
            return CompletableFuture.completedFuture(Result.ok());
        }

        @Override
        public CompletionStage<Result<Unit, TeleportError>> refund(PlayerRef who, BigDecimal amount) {
            balance = balance.add(amount);
            return CompletableFuture.completedFuture(Result.ok());
        }

        @Override
        public String format(BigDecimal amount) {
            return amount.toPlainString();
        }
    }
}
