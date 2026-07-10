package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class WarpAccessTest {

    @Test
    void parsesTheCanonicalUppercaseToken() {
        assertThat(WarpAccess.parse("PUBLIC")).contains(WarpAccess.PUBLIC);
    }

    @Test
    void parseIsCaseInsensitiveAndTrims() {
        assertThat(WarpAccess.parse("  password  ")).contains(WarpAccess.PASSWORD);
        assertThat(WarpAccess.parse("WhiteList")).contains(WarpAccess.WHITELIST);
    }

    @Test
    void parseReturnsEmptyForNull() {
        assertThat(WarpAccess.parse(null)).isEmpty();
    }

    @Test
    void parseReturnsEmptyForBlank() {
        assertThat(WarpAccess.parse("   ")).isEmpty();
    }

    @Test
    void parseReturnsEmptyForUnknownToken() {
        assertThat(WarpAccess.parse("open-to-all")).isEmpty();
    }

    @Test
    void everyConstantRoundTripsThroughItsName() {
        for (WarpAccess access : WarpAccess.values()) {
            assertThat(WarpAccess.parse(access.name())).contains(access);
        }
    }

    @Test
    void hasExactlyTheExpectedConstants() {
        assertThat(WarpAccess.values())
                .containsExactly(WarpAccess.PUBLIC, WarpAccess.PASSWORD, WarpAccess.WHITELIST, WarpAccess.PRIVATE);
    }

    @Test
    void parseNeverThrows() {
        Optional<WarpAccess> result = WarpAccess.parse("!!!");
        assertThat(result).isEmpty();
    }
}
