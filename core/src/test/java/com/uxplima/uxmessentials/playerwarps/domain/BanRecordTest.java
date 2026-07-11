package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class BanRecordTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final Instant BANNED_AT = Instant.ofEpochMilli(1_000L);

    @Test
    void aPermanentBanIsAlwaysActive() {
        BanRecord permanent = ban(Optional.empty());

        assertThat(permanent.isActiveAt(Instant.ofEpochMilli(0L))).isTrue();
        assertThat(permanent.isActiveAt(Instant.ofEpochMilli(Long.MAX_VALUE))).isTrue();
    }

    @Test
    void aTimedBanIsActiveStrictlyBeforeItsExpiry() {
        BanRecord timed = ban(Optional.of(Instant.ofEpochMilli(5_000L)));

        assertThat(timed.isActiveAt(Instant.ofEpochMilli(4_999L))).isTrue();
    }

    @Test
    void aTimedBanExpiresExactlyAtItsUntilInstant() {
        BanRecord timed = ban(Optional.of(Instant.ofEpochMilli(5_000L)));

        assertThat(timed.isActiveAt(Instant.ofEpochMilli(5_000L))).isFalse();
    }

    @Test
    void aTimedBanIsInactiveAfterItsExpiry() {
        BanRecord timed = ban(Optional.of(Instant.ofEpochMilli(5_000L)));

        assertThat(timed.isActiveAt(Instant.ofEpochMilli(5_001L))).isFalse();
    }

    private static BanRecord ban(Optional<Instant> until) {
        return new BanRecord(PLAYER, until, Optional.of("griefing"), Optional.of(UUID.randomUUID()), BANNED_AT);
    }
}
