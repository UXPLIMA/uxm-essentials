package com.uxplima.uxmessentials.trade.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Pins the per-player request cooldown: a fresh player may send at once, a stamp starts the window (reported rounded up
 * to whole seconds), the window lapses as the clock advances, and a zero window never blocks.
 */
class TradeCooldownTest {

    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void aFreshPlayerHasNoRemainingCooldown() {
        TradeCooldown cooldown = new TradeCooldown(new MutableClock(Instant.EPOCH), Duration.ofSeconds(5));

        assertThat(cooldown.remainingSeconds(PLAYER)).isZero();
    }

    @Test
    void aStampBlocksUntilTheWindowElapses() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        TradeCooldown cooldown = new TradeCooldown(clock, Duration.ofSeconds(5));

        cooldown.stamp(PLAYER);
        assertThat(cooldown.remainingSeconds(PLAYER)).isEqualTo(5);

        clock.advance(Duration.ofMillis(2500));
        // 2.5s elapsed of a 5s window leaves 2.5s, rounded up to 3.
        assertThat(cooldown.remainingSeconds(PLAYER)).isEqualTo(3);

        clock.advance(Duration.ofSeconds(3));
        assertThat(cooldown.remainingSeconds(PLAYER)).isZero();
    }

    @Test
    void aZeroWindowNeverBlocks() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        TradeCooldown cooldown = new TradeCooldown(clock, Duration.ZERO);

        cooldown.stamp(PLAYER);

        assertThat(cooldown.remainingSeconds(PLAYER)).isZero();
    }

    /** A hand-advanced {@link Clock} so a test can step time deterministically across the cooldown window. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration delta) {
            now = now.plus(delta);
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
