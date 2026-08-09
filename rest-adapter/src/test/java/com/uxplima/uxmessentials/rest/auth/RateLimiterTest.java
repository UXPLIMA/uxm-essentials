package com.uxplima.uxmessentials.rest.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void requestsAreAllowedUpToTheLimitAndRefusedAfterIt() {
        RateLimiter limiter = new RateLimiter(3, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        assertThat(limiter.allow("panel")).isTrue();
        assertThat(limiter.allow("panel")).isTrue();
        assertThat(limiter.allow("panel")).isTrue();
        assertThat(limiter.allow("panel")).isFalse();
    }

    @Test
    void theAllowanceComesBackWithTheNextMinute() {
        MovingClock clock = new MovingClock(Instant.EPOCH);
        RateLimiter limiter = new RateLimiter(1, clock);
        assertThat(limiter.allow("panel")).isTrue();
        assertThat(limiter.allow("panel")).isFalse();

        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.allow("panel")).isTrue();
    }

    @Test
    void oneTokenSpendingItsAllowanceDoesNotSpendAnothers() {
        RateLimiter limiter = new RateLimiter(1, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        limiter.allow("panel");

        assertThat(limiter.allow("panel")).isFalse();
        assertThat(limiter.allow("bot")).isTrue();
    }

    @Test
    void theResetIsHowLongIsLeftOfThisMinute() {
        RateLimiter limiter = new RateLimiter(1, Clock.fixed(Instant.EPOCH.plusSeconds(20), ZoneOffset.UTC));

        assertThat(limiter.secondsUntilReset()).isEqualTo(40);
    }

    @Test
    void aLimitOfNothingWouldRefuseEverythingSoItIsRefusedInstead() {
        assertThatThrownBy(() -> new RateLimiter(0, Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
    }

    /** A clock a test can push forward, so a minute rolling over does not take a minute. */
    private static final class MovingClock extends Clock {

        private Instant now;

        private MovingClock(Instant start) {
            this.now = start;
        }

        private void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
