package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.DailyRewardLedger;
import com.uxplima.uxmessentials.testing.MutableClock;
import org.junit.jupiter.api.Test;

class ClaimDailyRewardTest {

    private static final Duration DAY = Duration.ofHours(24);

    private final MutableClock clock = new MutableClock(NOW);
    private final Stamps ledger = new Stamps();

    private ClaimDailyReward onABackend() {
        return new ClaimDailyReward(ledger, clock, DAY);
    }

    @Test
    void twoBackendsLoggingThePlayerInPayTheRewardOnce() {
        boolean first = onABackend().claim(ALEX.uuid(), OptionalLong.empty());
        boolean second = onABackend().claim(ALEX.uuid(), OptionalLong.empty());

        assertThat(first).isTrue();
        assertThat(second).isFalse();

        clock.advance(DAY);
        assertThat(onABackend().claim(ALEX.uuid(), OptionalLong.empty())).isTrue();
    }

    @Test
    void aRecentStampFromBeforeTheNetworkIsHonoured() {
        long anHourAgo = clock.millis() - Duration.ofHours(1).toMillis();

        assertThat(onABackend().claim(ALEX.uuid(), OptionalLong.of(anHourAgo))).isFalse();
    }

    @Test
    void anOlderLocalStampDoesNotUndoANewerSharedOne() {
        onABackend().claim(ALEX.uuid(), OptionalLong.empty());
        clock.advance(Duration.ofHours(2));
        long twoDaysAgo = clock.millis() - Duration.ofDays(2).toMillis();

        assertThat(onABackend().claim(ALEX.uuid(), OptionalLong.of(twoDaysAgo))).isFalse();
    }

    /** The database's compare-and-set, kept in memory. */
    private static final class Stamps implements DailyRewardLedger {

        private final Map<UUID, Long> claimedAt = new HashMap<>();

        @Override
        public boolean claimIfDue(UUID player, long now, long windowMillis) {
            Long last = claimedAt.get(player);
            if (last != null && last > now - windowMillis) {
                return false;
            }
            claimedAt.put(player, now);
            return true;
        }

        @Override
        public void keepLatest(UUID player, long stamp) {
            claimedAt.merge(player, stamp, Math::max);
        }
    }
}
