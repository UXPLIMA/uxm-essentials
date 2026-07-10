package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SponsorshipTest {

    private static final Instant EXPIRY = Instant.parse("2026-07-10T12:00:00Z");

    @Test
    void keepsTheExpiryAndSlot() {
        Sponsorship sponsorship = new Sponsorship(EXPIRY, 2);
        assertThat(sponsorship.activeUntil()).isEqualTo(EXPIRY);
        assertThat(sponsorship.slot()).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("NullAway") // verifies the compact constructor rejects a literal null expiry
    void rejectsNullExpiry() {
        assertThatNullPointerException().isThrownBy(() -> new Sponsorship(null, 0));
    }

    @Test
    void rejectsNegativeSlot() {
        assertThatThrownBy(() -> new Sponsorship(EXPIRY, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isActiveBeforeTheExpiry() {
        assertThat(new Sponsorship(EXPIRY, 0).isActiveAt(EXPIRY.minusSeconds(1)))
                .isTrue();
    }

    @Test
    void lapsesExactlyAtTheExpiry() {
        assertThat(new Sponsorship(EXPIRY, 0).isActiveAt(EXPIRY)).isFalse();
    }

    @Test
    void isNotActiveAfterTheExpiry() {
        assertThat(new Sponsorship(EXPIRY, 0).isActiveAt(EXPIRY.plusSeconds(1))).isFalse();
    }

    @Test
    @SuppressWarnings("NullAway") // verifies isActiveAt rejects a literal null argument
    void isActiveAtRejectsNull() {
        Sponsorship sponsorship = new Sponsorship(EXPIRY, 0);
        assertThatNullPointerException().isThrownBy(() -> sponsorship.isActiveAt(null));
    }
}
