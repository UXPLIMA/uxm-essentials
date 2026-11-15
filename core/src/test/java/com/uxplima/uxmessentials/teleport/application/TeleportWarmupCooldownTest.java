package com.uxplima.uxmessentials.teleport.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.teleport.application.port.ArrivalGrace;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFee;
import com.uxplima.uxmessentials.teleport.domain.CooldownStartPhase;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.testing.ClockCooldowns;
import com.uxplima.uxmessentials.testing.Fixtures;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.QuietLog;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * The cooldown starts only once a teleport lands and runs out on the clock; a warmup cancelled by moving, a vetoed
 * teleport or a missed hop costs neither the cooldown nor the random-teleport fee.
 */
class TeleportWarmupCooldownTest {

    private static final Position HOME = Fixtures.at(10, 64, 10);

    private final MutableClock clock = new MutableClock(NOW);
    private final TeleportFakes.HeldWarmups warmups = new TeleportFakes.HeldWarmups();
    private final TeleportFakes.Hops hops = new TeleportFakes.Hops();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final CountingFee fee = new CountingFee("100");
    private boolean jailed;
    private boolean tagged;
    private boolean vetoed;

    private TeleportEngine engine(String... config) {
        Logger log = new QuietLog();
        return new TeleportEngine(
                new ClockCooldowns(clock),
                warmups,
                hops,
                notifier.notifier(),
                new RecordingEvents(),
                new TeleportSettings(TeleportFakes.Config.of(config)),
                who -> jailed,
                who -> tagged,
                fee,
                ArrivalGrace.NONE,
                proposal -> !vetoed,
                log);
    }

    private TeleportEngine engine() {
        return engine("default-cooldown", "30");
    }

    private static Destination home() {
        return Destination.at(HOME);
    }

    @Test
    void theCooldownStartsWhenTheTeleportLandsAndRunsOutOnTheClock() {
        TeleportEngine engine = engine();

        assertThat(engine.launch(ALEX, home(), TeleportKind.HOME).isOk()).isTrue();
        warmups.finish();

        assertThat(hops.landed).containsExactly("Alex:HOME");
        assertThat(engine.launch(ALEX, home(), TeleportKind.HOME).asError()).contains(TeleportError.ON_COOLDOWN);
        clock.advance(Duration.ofSeconds(29));
        assertThat(engine.launch(ALEX, home(), TeleportKind.HOME).asError()).contains(TeleportError.ON_COOLDOWN);
        clock.advance(Duration.ofSeconds(1));
        assertThat(engine.launch(ALEX, home(), TeleportKind.HOME).isOk()).isTrue();
    }

    @Test
    void theCooldownNoticeSaysHowLongIsLeft() {
        TeleportEngine engine = engine();
        engine.launch(ALEX, home(), TeleportKind.HOME);
        warmups.finish();
        clock.advance(Duration.ofSeconds(18));

        engine.launch(ALEX, home(), TeleportKind.HOME);

        assertThat(notifier.sent())
                .last()
                .isEqualTo(new RecordingNotifier.Sent(
                        ALEX, "teleport.cooldown.active", java.util.Map.of("seconds", "12")));
    }

    @Test
    void aWarmupCancelledByMovingNeitherMovesNorStartsTheCooldown() {
        TeleportEngine engine = engine();
        engine.launch(ALEX, home(), TeleportKind.HOME);

        warmups.moveAway();

        assertThat(hops.landed).isEmpty();
        assertThat(notifier.keys()).containsExactly("teleport.warmup.cancelled");
        assertThat(engine.launch(ALEX, home(), TeleportKind.HOME).isOk()).isTrue();
    }

    @Test
    void aHopThatMissesStartsNoCooldown() {
        TeleportEngine engine = engine();
        hops.misses = true;
        engine.launch(ALEX, home(), TeleportKind.HOME);

        warmups.finish();

        assertThat(engine.launch(ALEX, home(), TeleportKind.HOME).isOk()).isTrue();
    }

    @Test
    void aVetoedTeleportNeverStartsAWarmup() {
        vetoed = true;

        assertThat(engine().launch(ALEX, home(), TeleportKind.HOME).asError()).contains(TeleportError.VETOED);

        assertThat(warmups.pending()).isZero();
        assertThat(hops.landed).isEmpty();
    }

    @Test
    void aJailedOrTaggedPlayerIsRefusedBeforeTheWarmup() {
        TeleportEngine engine = engine();
        jailed = true;
        assertThat(engine.launch(ALEX, home(), TeleportKind.WARP).asError()).contains(TeleportError.JAILED);
        jailed = false;
        tagged = true;
        assertThat(engine.launch(ALEX, home(), TeleportKind.WARP).asError()).contains(TeleportError.COMBAT_TAGGED);

        assertThat(warmups.pending()).isZero();
    }

    @Test
    void aVerbWithItsOwnCooldownDoesNotBlockTheOthers() {
        TeleportEngine engine = engine("default-cooldown", "30", "cooldowns.warp", "120");
        engine.launch(ALEX, home(), TeleportKind.WARP);
        warmups.finish();

        assertThat(engine.launch(ALEX, home(), TeleportKind.WARP).asError()).contains(TeleportError.ON_COOLDOWN);
        assertThat(engine.launch(ALEX, home(), TeleportKind.HOME).isOk()).isTrue();
        clock.advance(Duration.ofSeconds(119));
        assertThat(engine.launch(ALEX, home(), TeleportKind.WARP).asError()).contains(TeleportError.ON_COOLDOWN);
        clock.advance(Duration.ofSeconds(1));
        assertThat(engine.launch(ALEX, home(), TeleportKind.WARP).isOk()).isTrue();
    }

    @Test
    void aDestinationCooldownOverrideRunsOnItsOwnClock() {
        TeleportEngine engine = engine();
        Destination shop = Destination.at(HOME, Optional.empty(), Optional.of(Duration.ofSeconds(5)));
        engine.launch(ALEX, shop, TeleportKind.WARP);
        warmups.finish();

        assertThat(engine.launch(ALEX, shop, TeleportKind.WARP).asError()).contains(TeleportError.ON_COOLDOWN);
        clock.advance(Duration.ofSeconds(5));
        assertThat(engine.launch(ALEX, shop, TeleportKind.WARP).isOk()).isTrue();
    }

    @Test
    void underTheAcceptPhaseAnAcceptedRequestStartsTheCooldownBeforeTheWarmup() {
        TeleportEngine engine = engine("default-cooldown", "30", "cooldown-start-phase", "accept");

        engine.stampForPhase(ALEX, CooldownStartPhase.ACCEPT);

        assertThat(engine.gateHop(ALEX, home(), TeleportKind.HOME).asError()).contains(TeleportError.ON_COOLDOWN);
    }

    @Test
    void underTheTeleportPhaseAnAcceptDoesNotStartTheCooldown() {
        TeleportEngine engine = engine();

        engine.stampForPhase(ALEX, CooldownStartPhase.ACCEPT);

        assertThat(engine.gateHop(ALEX, home(), TeleportKind.HOME).isOk()).isTrue();
    }

    @Test
    void aRandomTeleportCancelledByMovingCostsNothingAndStartsNoCooldown() {
        TeleportEngine engine = engine("default-cooldown", "30", "rtp.cost", "40");
        assertThat(engine.gateRandom(ALEX).isOk()).isTrue();
        engine.launchRandom(ALEX, Destination.at(HOME));

        warmups.moveAway();

        assertThat(fee.charges).isZero();
        assertThat(fee.balance).isEqualByComparingTo("100");
        assertThat(engine.gateRandom(ALEX).isOk()).isTrue();
    }

    @Test
    void aRandomTeleportIsChargedOnceAndStartsItsOwnCooldownOnLanding() {
        TeleportEngine engine = engine("default-cooldown", "30", "rtp.cost", "40");
        engine.gateRandom(ALEX);
        engine.launchRandom(ALEX, Destination.at(HOME));

        warmups.finish();

        assertThat(fee.charges).isEqualTo(1);
        assertThat(fee.balance).isEqualByComparingTo("60");
        assertThat(engine.gateRandom(ALEX).asError()).contains(TeleportError.ON_COOLDOWN);
        assertThat(engine.launch(ALEX, home(), TeleportKind.HOME).isOk())
                .as("the random-teleport cooldown is its own tier")
                .isTrue();
    }

    @Test
    void aPlayerWhoCannotAffordARandomTeleportIsRefusedBeforeTheSearchAndNotCharged() {
        TeleportEngine engine = engine("rtp.cost", "400");

        assertThat(engine.gateRandom(ALEX).asError()).contains(TeleportError.RTP_CANT_AFFORD);

        assertThat(fee.charges).isZero();
        assertThat(notifier.keys()).containsExactly("teleport.rtp.cant-afford");
    }

    @Test
    void aFreeRandomTeleportNeverTouchesTheFee() {
        TeleportEngine engine = engine();
        engine.gateRandom(ALEX);
        engine.launchRandom(ALEX, Destination.at(HOME));

        warmups.finish();

        assertThat(fee.charges).isZero();
        assertThat(hops.landed).containsExactly("Alex:RANDOM");
    }

    /** One balance, debited only when it covers the amount, counting each debit that took. */
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
            if (!canAfford(who, amount)) {
                return CompletableFuture.completedFuture(Result.err(TeleportError.RTP_CANT_AFFORD));
            }
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
