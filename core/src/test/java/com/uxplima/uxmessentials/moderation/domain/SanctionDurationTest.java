package com.uxplima.uxmessentials.moderation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * The sanction duration grammar: single units, concatenations, the permanent forms, and the malformed
 * rejections that drive {@code BAD_DURATION}. Pure parsing, so the whole grammar is covered in {@code :core}.
 */
class SanctionDurationTest {

    @Test
    void parsesSingleUnits() {
        assertThat(SanctionDuration.parse("30s").duration()).contains(Duration.ofSeconds(30));
        assertThat(SanctionDuration.parse("15m").duration()).contains(Duration.ofMinutes(15));
        assertThat(SanctionDuration.parse("2h").duration()).contains(Duration.ofHours(2));
        assertThat(SanctionDuration.parse("7d").duration()).contains(Duration.ofDays(7));
        assertThat(SanctionDuration.parse("4w").duration()).contains(Duration.ofDays(28));
    }

    @Test
    void parsesConcatenatedUnits() {
        assertThat(SanctionDuration.parse("1h30m").duration())
                .contains(Duration.ofHours(1).plusMinutes(30));
        assertThat(SanctionDuration.parse("1d12h").duration()).contains(Duration.ofHours(36));
    }

    @Test
    void blankOrPermanentParsesToNoDurationButIsWellFormed() {
        assertThat(SanctionDuration.parse("").isPermanent()).isTrue();
        assertThat(SanctionDuration.parse("permanent").isPermanent()).isTrue();
        assertThat(SanctionDuration.parse("perm").isPermanent()).isTrue();
        assertThat(SanctionDuration.parse("permanent").malformed()).isFalse();
    }

    @Test
    void garbageAndZeroAreMalformed() {
        assertThat(SanctionDuration.parse("abc").malformed()).isTrue();
        assertThat(SanctionDuration.parse("10x").malformed()).isTrue();
        assertThat(SanctionDuration.parse("1h junk").malformed()).isTrue();
        assertThat(SanctionDuration.parse("0s").malformed()).isTrue();
    }

    @Test
    void formatRendersACompactLabel() {
        assertThat(SanctionDuration.format(Duration.ofHours(1).plusMinutes(30))).isEqualTo("1h30m");
        assertThat(SanctionDuration.format(Duration.ofDays(7))).isEqualTo("1w");
        assertThat(SanctionDuration.format(Duration.ofSeconds(45))).isEqualTo("45s");
        assertThat(SanctionDuration.format(Duration.ZERO)).isEqualTo("0s");
    }
}
