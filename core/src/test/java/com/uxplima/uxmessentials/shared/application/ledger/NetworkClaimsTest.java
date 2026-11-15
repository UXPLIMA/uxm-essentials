package com.uxplima.uxmessentials.shared.application.ledger;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownKind;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MemoryClaimLedger;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import org.junit.jupiter.api.Test;

class NetworkClaimsTest {

    private static final CooldownKind WARP = CooldownKind.onTeleport("warp", 60);

    private final MutableClock clock = new MutableClock(NOW);
    private final MemoryClaimLedger ledger = new MemoryClaimLedger();
    private final QueuedScheduler scheduler = new QueuedScheduler();
    private final NetworkClaims claims = new NetworkClaims(ledger, scheduler, clock, new RecordingLogger());
    private final NetworkCooldowns cooldowns = new NetworkCooldowns(claims, new FakePermissions(), clock);

    private long in(Duration duration) {
        return clock.millis() + duration.toMillis();
    }

    @Test
    void aPlayerWhoseClaimsAreNotReadYetIsHeldBack() {
        claims.joined(ALEX.uuid());

        assertThat(claims.ready(ALEX.uuid())).isFalse();
        assertThat(claims.hasKit(ALEX.uuid(), "starter")).isTrue();
        assertThat(cooldowns.check(ALEX, WARP).asError()).contains(NetworkCooldowns.UNREAD_WAIT);

        scheduler.runAll();

        assertThat(claims.hasKit(ALEX.uuid(), "starter")).isFalse();
        assertThat(cooldowns.check(ALEX, WARP).isOk()).isTrue();
    }

    @Test
    void aCooldownStampedOnOneServerHoldsThePlayerOnTheNext() {
        claims.joined(ALEX.uuid());
        scheduler.runAll();
        cooldowns.stamp(ALEX, WARP);
        scheduler.runAll();

        NetworkClaims next = new NetworkClaims(ledger, scheduler, clock, new RecordingLogger());
        next.joined(ALEX.uuid());
        scheduler.runAll();

        assertThat(new NetworkCooldowns(next, new FakePermissions(), clock)
                        .check(ALEX, WARP)
                        .asError())
                .contains(Duration.ofSeconds(60));
    }

    @Test
    void theCarryOverKeepsTheStricterValue() {
        ledger.saveCooldown(ALEX.uuid(), "warp", in(Duration.ofMinutes(10)));
        claims.joined(ALEX.uuid());
        scheduler.runAll();
        AtomicBoolean carried = new AtomicBoolean();

        claims.carryOver(
                ALEX.uuid(),
                new LocalClaims(
                        Set.of("starter"),
                        Map.of(
                                "warp", in(Duration.ofMinutes(5)),
                                "tp", in(Duration.ofMinutes(20)),
                                "home", in(Duration.ofMinutes(-1)))),
                () -> carried.set(true));
        scheduler.runAll();

        assertThat(carried).isTrue();
        assertThat(ledger.hasKit(ALEX.uuid(), "starter")).isTrue();
        assertThat(ledger.cooldown(ALEX.uuid(), "warp")).isEqualTo(in(Duration.ofMinutes(10)));
        assertThat(ledger.cooldown(ALEX.uuid(), "tp")).isEqualTo(in(Duration.ofMinutes(20)));
        assertThat(ledger.cooldown(ALEX.uuid(), "home")).isZero();
        assertThat(claims.hasKit(ALEX.uuid(), "starter")).isTrue();
    }

    @Test
    void aCarryOverThatArrivesBeforeTheReadIsInPlaceBeforeThePlayerIsReady() {
        claims.joined(ALEX.uuid());
        claims.carryOver(ALEX.uuid(), new LocalClaims(Set.of("starter"), Map.of()), () -> {});

        assertThat(claims.ready(ALEX.uuid())).isFalse();
        scheduler.runAll();

        assertThat(claims.ready(ALEX.uuid())).isTrue();
        assertThat(claims.hasKit(ALEX.uuid(), "starter")).isTrue();
        assertThat(ledger.hasKit(ALEX.uuid(), "starter")).isTrue();
    }

    @Test
    void aPlayerLetGoIsAnsweredFromTheLedgerAgainOnTheirNextLogin() {
        long session = claims.joined(ALEX.uuid());
        scheduler.runAll();
        claims.markKit(ALEX.uuid(), "starter");
        scheduler.runAll();

        claims.drop(ALEX.uuid(), session);
        assertThat(claims.hasKit(ALEX.uuid(), "starter")).isFalse();

        claims.joined(ALEX.uuid());
        scheduler.runAll();
        assertThat(claims.hasKit(ALEX.uuid(), "starter")).isTrue();
    }

    @Test
    void theConsoleAndPlayersNotOnlineHerePassAndTheConsoleIsNeverHeldBack() {
        PlayerRef console = PlayerRef.system("CONSOLE");

        assertThat(cooldowns.check(console, WARP).isOk()).isTrue();
        assertThat(cooldowns.checkLabel(SAM, "spawn").isOk()).isTrue();

        cooldowns.stamp(console, WARP);
        scheduler.runAll();
        assertThat(cooldowns.check(console, WARP).isOk()).isTrue();
        assertThat(ledger.cooldown(PlayerRef.SYSTEM_UUID, "warp")).isZero();
    }

    @Test
    void aReadThatFailsIsTriedAgainAndKeepsThePendingCarryOver() {
        ledger.failNextLoads(2);
        claims.joined(ALEX.uuid());
        claims.carryOver(ALEX.uuid(), new LocalClaims(Set.of("starter"), Map.of()), () -> {});

        scheduler.runAll();

        assertThat(claims.ready(ALEX.uuid())).isTrue();
        assertThat(cooldowns.check(ALEX, WARP).isOk()).isTrue();
        assertThat(ledger.hasKit(ALEX.uuid(), "starter")).isTrue();
    }

    @Test
    void writesForOnePlayerReachTheLedgerInTheOrderTheyWereMade() {
        QueuedScheduler pool = new QueuedScheduler().lastFirst();
        NetworkClaims onPool = new NetworkClaims(ledger, pool, clock, new RecordingLogger());

        onPool.stampCooldown(ALEX.uuid(), "warp", 1_000L);
        onPool.stampCooldown(ALEX.uuid(), "warp", 2_000L);
        onPool.markKit(ALEX.uuid(), "starter");
        onPool.releaseKit(ALEX.uuid(), "starter");
        pool.runAll();

        assertThat(ledger.cooldown(ALEX.uuid(), "warp")).isEqualTo(2_000L);
        assertThat(ledger.hasKit(ALEX.uuid(), "starter")).isFalse();
    }

    @Test
    void aWriteMadeWhileStoppingReachesTheLedgerAtOnce() {
        claims.drain(Duration.ZERO);

        claims.releaseKit(ALEX.uuid(), "starter");
        claims.stampCooldown(ALEX.uuid(), "warp", 5_000L);

        assertThat(ledger.cooldown(ALEX.uuid(), "warp")).isEqualTo(5_000L);
    }

    @Test
    void aLateQuitOfAnOlderSessionKeepsTheNewSessionsClaims() {
        long older = claims.joined(ALEX.uuid());
        scheduler.runAll();
        claims.joined(ALEX.uuid());
        claims.markKit(ALEX.uuid(), "starter");

        claims.drop(ALEX.uuid(), older);

        assertThat(claims.hasKit(ALEX.uuid(), "starter")).isTrue();
    }

    @Test
    void aLaneThatRefusesWorkWhileStoppingStillWritesEveryStepInOrder() {
        NetworkClaims stopping = new NetworkClaims(ledger, new RefusingScheduler(), clock, new RecordingLogger());
        stopping.joined(ALEX.uuid());

        stopping.stampCooldown(ALEX.uuid(), "warp", 1_000L);
        stopping.stampCooldown(ALEX.uuid(), "warp", 2_000L);
        boolean won = stopping.acquireKit(ALEX, "starter").join();

        assertThat(won).isTrue();
        assertThat(ledger.cooldown(ALEX.uuid(), "warp")).isEqualTo(2_000L);
        assertThat(ledger.hasKit(ALEX.uuid(), "starter")).isTrue();
    }

    /** A plugin marked disabled: the async lane refuses every task, entity tasks run where they are asked. */
    private static final class RefusingScheduler implements Scheduler {

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
            throw new IllegalStateException("the plugin is disabled");
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {}
    }
}
