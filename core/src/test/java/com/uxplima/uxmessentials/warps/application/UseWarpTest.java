package com.uxplima.uxmessentials.warps.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.PasswordAttempts;
import com.uxplima.uxmessentials.shared.application.teleport.PaidTeleports;
import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.testing.ClockCooldowns;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.ManualWarmups;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.testing.SteppedScheduler;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * A use of a paid warp on this server: every check the use can fail runs before the money moves, so a refused use
 * leaves the wallet as it was, and a teleport that never lands after the charge gives the money back once. The
 * teleport rules are the real engine's.
 */
class UseWarpTest {

    private static final String LOBBY = "lobby";
    private static final WarpName SHOP = WarpName.of("shop");
    private static final BigDecimal START = new BigDecimal("100");

    private final MutableClock clock = new MutableClock(NOW);
    private final PasswordAttempts attempts = PasswordAttempts.defaults(clock);
    private final SteppedScheduler scheduler = new SteppedScheduler(clock);
    private final WarpFakes.Warps warps = new WarpFakes.Warps();
    private final WarpFakes.Purse purse = new WarpFakes.Purse();
    private final WarpFakes.Moves moves = new WarpFakes.Moves();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final RecordingLogger log = new RecordingLogger();
    private final ManualWarmups warmups = new ManualWarmups();
    private boolean hopMisses;
    private final FakePermissions permissions = new FakePermissions().grant(ALEX, WarpAccess.useNode(SHOP));
    private boolean tagged;
    private boolean unsafe;

    UseWarpTest() {
        warps.put(shop());
        purse.wallet = START;
        moves.engine = WarpFakes.engine(new ClockCooldowns(clock), notifier, () -> tagged);
    }

    private static Warp shop() {
        return Warp.create(SHOP, at(0, 64, 0), SAM, NOW, WarpCost.of(new BigDecimal("10"), "gold"), Optional.empty())
                .onServer(LOBBY);
    }

    private UseWarp useWarp() {
        return new UseWarp(
                warps,
                new WarpAccess(permissions, Optional.of(purse)),
                moves,
                notifier.notifier(),
                position -> !unsafe,
                permissions,
                scheduler,
                new WarpFakes.Online(),
                WarpHops.local(LOBBY),
                new PaidTeleports(scheduler, log),
                attempts,
                log);
    }

    /** The engine with a warmup the test ends itself, by letting it count down or by moving. */
    private TeleportEngine warmingEngine() {
        TeleportExecutor hops = new TeleportExecutor() {
            @Override
            public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {}

            @Override
            public void teleport(
                    PlayerRef who, Destination destination, TeleportKind kind, Runnable onLanded, Runnable onFailed) {
                (hopMisses ? onFailed : onLanded).run();
            }
        };
        return new TeleportEngine(
                new ClockCooldowns(clock),
                warmups,
                hops,
                notifier.notifier(),
                new RecordingEvents(),
                new TeleportSettings(new WarpFakes.Defaults()),
                who -> false,
                who -> tagged);
    }

    @Test
    void aPaidUseChargesOnceAndTeleports() {
        Result<Unit, WarpError> used = useWarp().use(ALEX, SHOP);

        assertThat(used.isOk()).isTrue();
        assertThat(purse.wallet).isEqualByComparingTo("90");
        assertThat(moves.made).containsExactly("Alex->shop");
        assertThat(purse.refunds).as("a teleport that landed is never refunded").isZero();
        assertThat(notifier.keys()).containsExactly("warp.teleporting");
    }

    @ParameterizedTest
    @EnumSource(value = TeleportOutcome.class, names = "LANDED", mode = EnumSource.Mode.EXCLUDE)
    void aPaidUseWhoseTeleportNeverLandsIsRefundedOnceAndTheyAreToldOnce(TeleportOutcome ended) {
        moves.holds = true;

        useWarp().use(ALEX, SHOP);
        assertThat(purse.wallet).as("charged before the warmup").isEqualByComparingTo("90");
        moves.finish(ended);

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).containsExactly("warp.teleporting", "warp.cross-server.refunded");
    }

    @Test
    void aPaidUseThatLandsAfterItsWarmupKeepsTheCharge() {
        moves.holds = true;

        useWarp().use(ALEX, SHOP);
        moves.finish(TeleportOutcome.LANDED);

        assertThat(purse.wallet).isEqualByComparingTo("90");
        assertThat(purse.refunds).isZero();
    }

    @Test
    void aMoveDuringTheWarmupGivesTheChargeBack() {
        moves.engine = warmingEngine();
        useWarp().use(ALEX, SHOP);

        warmups.move(ALEX);

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys())
                .containsExactly("warp.teleporting", "teleport.warmup.cancelled", "warp.cross-server.refunded");
    }

    @Test
    void aNewerUseReplacingTheWarmupRefundsTheFirstAndKeepsTheSecond() {
        moves.engine = warmingEngine();
        useWarp().use(ALEX, SHOP);

        useWarp().use(ALEX, SHOP);
        warmups.finish(ALEX);

        assertThat(purse.wallet).isEqualByComparingTo("90");
        assertThat(purse.charges).isEqualTo(2);
        assertThat(purse.refunds).isOne();
    }

    @Test
    void aHopThatMissesAfterTheWarmupGivesTheChargeBack() {
        moves.engine = warmingEngine();
        hopMisses = true;
        useWarp().use(ALEX, SHOP);

        warmups.finish(ALEX);

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
    }

    @Test
    void aLandedTeleportIsNotRefundedByACancelThatArrivesLate() {
        moves.engine = warmingEngine();
        useWarp().use(ALEX, SHOP);
        var handle = warmups.handleOf(ALEX);
        warmups.finish(ALEX);

        handle.cancel();
        handle.supersede();

        assertThat(purse.wallet).isEqualByComparingTo("90");
        assertThat(purse.refunds).isZero();
    }

    @Test
    void aStaffSendThatNeverLandsRefundsThePlayerWhoPaid() {
        moves.holds = true;

        useWarp().useFor(SAM, ALEX, SHOP);
        moves.finish(TeleportOutcome.CANCELLED);

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(notifier.sent())
                .filteredOn(line -> line.key().equals("warp.cross-server.refunded"))
                .singleElement()
                .satisfies(line -> assertThat(line.viewer()).isEqualTo(ALEX));
    }

    @Test
    void aFreeUseThatNeverLandsMovesNoMoney() {
        warps.put(Warp.create(SHOP, at(0, 64, 0), SAM, NOW, WarpCost.free(), Optional.empty())
                .onServer(LOBBY));
        moves.holds = true;

        useWarp().use(ALEX, SHOP);
        moves.finish(TeleportOutcome.CANCELLED);

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isZero();
        assertThat(notifier.keys()).containsExactly("warp.teleporting");
    }

    @Test
    void aRefundTheEconomyRejectsIsLoggedWithThePlayerAndAmountAndNotAnnounced() {
        moves.holds = true;
        purse.refundFails = true;

        useWarp().use(ALEX, SHOP);
        moves.finish(TeleportOutcome.CANCELLED);

        assertThat(log.errors())
                .singleElement()
                .satisfies(line ->
                        assertThat(line).contains(ALEX.uuid().toString()).contains("10"));
        assertThat(notifier.keys()).containsExactly("warp.teleporting");
    }

    @Test
    void aLaunchThatThrowsAfterTheChargeGivesTheMoneyBackOnceAndStillFails() {
        moves.throwsOnTeleport = true;

        assertThatThrownBy(() -> useWarp().use(ALEX, SHOP)).isInstanceOf(IllegalStateException.class);

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(purse.refunds).isOne();
        assertThat(notifier.keys()).containsExactly("warp.teleporting", "warp.cross-server.refunded");
    }

    @Test
    void aVisitorCountTheAsyncLaneRefusesCostsTheCountNotTheUse() {
        scheduler.rejectAsync(true);
        moves.holds = true;

        Result<Unit, WarpError> used = useWarp().use(ALEX, SHOP);

        assertThat(used.isOk()).isTrue();
        assertThat(moves.held).hasSize(1);
        assertThat(warps.visitors(SHOP)).isZero();
        assertThat(log.warnings())
                .singleElement()
                .satisfies(line -> assertThat(line).contains("warp_visit_not_counted"));
    }

    @Test
    void aRefundIsNeverMadeOnTheThreadThatReportedTheOutcome() {
        moves.holds = true;
        useWarp().use(ALEX, SHOP);
        scheduler.rejectAsync(true);

        moves.finish(TeleportOutcome.CANCELLED);

        assertThat(purse.wallet).as("only the async lane gives money back").isEqualByComparingTo("90");
        assertThat(log.errors())
                .singleElement()
                .satisfies(line -> assertThat(line).contains(ALEX.uuid().toString()));
    }

    @Test
    void aWrongPasswordOnAPaidWarpTakesNoMoney() {
        warps.put(shop().withPassword(Optional.of("secret")));

        Result<Unit, WarpError> used = useWarp().use(ALEX, SHOP, "guess");

        assertThat(used.asError()).contains(WarpError.WRONG_PASSWORD);
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(moves.made).isEmpty();
    }

    @Test
    void wrongPasswordsUpToTheLimitLockTheWarpEvenForTheRightOneUntilTheLockoutEnds() {
        warps.put(shop().withPassword(Optional.of("secret")));
        UseWarp use = useWarp();
        for (int i = 0; i < PasswordAttempts.DEFAULT_MAX_ATTEMPTS; i++) {
            assertThat(use.use(ALEX, SHOP, "guess").asError()).contains(WarpError.WRONG_PASSWORD);
        }

        assertThat(use.use(ALEX, SHOP, "secret").asError()).contains(WarpError.RATE_LIMITED);
        assertThat(notifier.keys()).last().isEqualTo("warp.rate-limited");
        clock.advance(PasswordAttempts.DEFAULT_LOCKOUT);

        assertThat(use.use(ALEX, SHOP, "secret").isOk()).isTrue();
    }

    @Test
    void aRightPasswordStartsTheCountAgain() {
        warps.put(shop().withPassword(Optional.of("secret")));
        UseWarp use = useWarp();
        use.use(ALEX, SHOP, "guess");
        use.use(ALEX, SHOP, "guess");
        use.use(ALEX, SHOP, "secret");

        use.use(ALEX, SHOP, "guess");

        assertThat(use.use(ALEX, SHOP, "guess").asError()).contains(WarpError.WRONG_PASSWORD);
    }

    @Test
    void anUnsafeDestinationOnAPaidWarpTakesNoMoney() {
        unsafe = true;

        Result<Unit, WarpError> used = useWarp().use(ALEX, SHOP);

        assertThat(used.asError()).contains(WarpError.UNSAFE_LOCATION);
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(moves.made).isEmpty();
    }

    @Test
    void aUseTheTeleportRulesRefuseTakesNoMoney() {
        tagged = true;

        Result<Unit, WarpError> used = useWarp().use(ALEX, SHOP);

        assertThat(used.asError()).contains(WarpError.TELEPORT_REFUSED);
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(moves.made).isEmpty();
        assertThat(notifier.keys()).containsExactly("teleport.combat-tagged");
    }

    @Test
    void aStaffSendTheTeleportRulesRefuseTellsTheStaffMemberAndTakesNoMoney() {
        tagged = true;

        useWarp().useFor(SAM, ALEX, SHOP);

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(notifier.keys()).containsExactly("teleport.combat-tagged", "warp.sent-refused");
    }
}
