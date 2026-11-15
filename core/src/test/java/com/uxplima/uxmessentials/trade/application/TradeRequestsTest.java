package com.uxplima.uxmessentials.trade.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.MutableClock;
import org.junit.jupiter.api.Test;

/** A trade request lives for its time to live and is answered once; the requester then waits out a cooldown. */
class TradeRequestsTest {

    private static final Duration TTL = Duration.ofSeconds(60);
    private static final PlayerRef KAI = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000000c"), "Kai");

    private final MutableClock clock = new MutableClock(NOW);
    private final TradeRequests requests = new TradeRequests(clock, TTL);

    @Test
    void aRequestAnsweredOnTheLastSecondOpensAndCannotBeAnsweredTwice() {
        requests.submit(ALEX, SAM);
        clock.advance(TTL);

        assertThat(requests.resolve(SAM.uuid(), null).status()).isEqualTo(TradeRequests.Status.MATCHED);
        assertThat(requests.resolve(SAM.uuid(), null).status()).isEqualTo(TradeRequests.Status.NONE);
    }

    @Test
    void aRequestPastItsTimeToLiveIsReportedExpiredAndRemoved() {
        requests.submit(ALEX, SAM);
        clock.advance(TTL.plusMillis(1));

        assertThat(requests.hasPending(ALEX.uuid(), SAM.uuid())).isFalse();
        assertThat(requests.pendingRequesterNames(SAM.uuid())).isEmpty();
        assertThat(requests.resolve(SAM.uuid(), "alex").status()).isEqualTo(TradeRequests.Status.EXPIRED);
        assertThat(requests.resolve(SAM.uuid(), "alex").status()).isEqualTo(TradeRequests.Status.NONE);
    }

    @Test
    void withoutANameTheNewestRequestIsAnsweredAndTheOtherWaits() {
        requests.submit(ALEX, SAM);
        clock.advance(Duration.ofSeconds(5));
        requests.submit(KAI, SAM);

        TradeRequests.Match first = requests.resolve(SAM.uuid(), null);

        assertThat(Objects.requireNonNull(first.request()).requester()).isEqualTo(KAI);
        assertThat(requests.pendingRequesterNames(SAM.uuid())).containsExactly("Alex");
    }

    @Test
    void resendingRestartsTheTimeToLive() {
        requests.submit(ALEX, SAM);
        clock.advance(Duration.ofSeconds(50));
        requests.submit(ALEX, SAM);
        clock.advance(Duration.ofSeconds(50));

        assertThat(requests.resolve(SAM.uuid(), "Alex").status()).isEqualTo(TradeRequests.Status.MATCHED);
    }

    @Test
    void aPlayerWhoLeavesTakesTheirSentAndReceivedRequestsWithThem() {
        requests.submit(ALEX, SAM);
        requests.submit(SAM, KAI);

        requests.forget(SAM.uuid());

        assertThat(requests.resolve(SAM.uuid(), null).status()).isEqualTo(TradeRequests.Status.NONE);
        assertThat(requests.resolve(KAI.uuid(), null).status()).isEqualTo(TradeRequests.Status.NONE);
    }

    @Test
    void theCooldownCountsDownInWholeSecondsRoundedUp() {
        TradeCooldown cooldown = new TradeCooldown(clock, Duration.ofSeconds(5));
        cooldown.stamp(ALEX.uuid());

        clock.advance(Duration.ofMillis(4_001));
        assertThat(cooldown.remainingSeconds(ALEX.uuid())).isEqualTo(1);

        clock.advance(Duration.ofMillis(999));
        assertThat(cooldown.remainingSeconds(ALEX.uuid())).isZero();
        assertThat(cooldown.remainingSeconds(SAM.uuid())).isZero();
    }

    @Test
    void aRequestThatReachedNobodyGivesTheCooldownBackButALaterRequestKeepsItsOwn() {
        TradeCooldown cooldown = new TradeCooldown(clock, Duration.ofSeconds(5));
        Instant failed = cooldown.stamp(ALEX.uuid());

        cooldown.release(ALEX.uuid(), failed);
        assertThat(cooldown.remainingSeconds(ALEX.uuid())).isZero();

        Instant older = cooldown.stamp(ALEX.uuid());
        clock.advance(Duration.ofSeconds(1));
        cooldown.stamp(ALEX.uuid());
        cooldown.release(ALEX.uuid(), older);

        assertThat(cooldown.remainingSeconds(ALEX.uuid())).isEqualTo(5);
    }

    @Test
    void aZeroCooldownNeverHoldsAnyoneBack() {
        TradeCooldown cooldown = new TradeCooldown(clock, Duration.ZERO);
        cooldown.stamp(ALEX.uuid());

        assertThat(cooldown.remainingSeconds(ALEX.uuid())).isZero();
    }
}
