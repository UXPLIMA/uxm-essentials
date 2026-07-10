package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WarpStatusTest {

    @Test
    void parsesTheCanonicalUppercaseToken() {
        assertThat(WarpStatus.parse("ACTIVE")).contains(WarpStatus.ACTIVE);
    }

    @Test
    void parseIsCaseInsensitiveAndTrims() {
        assertThat(WarpStatus.parse("  suspended  ")).contains(WarpStatus.SUSPENDED);
        assertThat(WarpStatus.parse("Archived")).contains(WarpStatus.ARCHIVED);
    }

    @Test
    void parseReturnsEmptyForNull() {
        assertThat(WarpStatus.parse(null)).isEmpty();
    }

    @Test
    void parseReturnsEmptyForBlank() {
        assertThat(WarpStatus.parse("   ")).isEmpty();
    }

    @Test
    void parseReturnsEmptyForUnknownToken() {
        assertThat(WarpStatus.parse("frozen")).isEmpty();
    }

    @Test
    void everyConstantRoundTripsThroughItsName() {
        for (WarpStatus status : WarpStatus.values()) {
            assertThat(WarpStatus.parse(status.name())).contains(status);
        }
    }

    @Test
    void hasExactlyTheExpectedConstants() {
        assertThat(WarpStatus.values()).containsExactly(WarpStatus.ACTIVE, WarpStatus.SUSPENDED, WarpStatus.ARCHIVED);
    }
}
