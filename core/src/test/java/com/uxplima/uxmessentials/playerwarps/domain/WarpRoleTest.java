package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WarpRoleTest {

    @Test
    void parsesTheCanonicalUppercaseToken() {
        assertThat(WarpRole.parse("OWNER")).contains(WarpRole.OWNER);
        assertThat(WarpRole.parse("CO_OWNER")).contains(WarpRole.CO_OWNER);
        assertThat(WarpRole.parse("MANAGER")).contains(WarpRole.MANAGER);
    }

    @Test
    void parseIsCaseInsensitiveAndTrims() {
        assertThat(WarpRole.parse("  co_owner  ")).contains(WarpRole.CO_OWNER);
        assertThat(WarpRole.parse("Manager")).contains(WarpRole.MANAGER);
    }

    @Test
    void parseReturnsEmptyForNull() {
        assertThat(WarpRole.parse(null)).isEmpty();
    }

    @Test
    void parseReturnsEmptyForBlank() {
        assertThat(WarpRole.parse("   ")).isEmpty();
    }

    @Test
    void parseReturnsEmptyForAnUnknownToken() {
        assertThat(WarpRole.parse("ADMIN")).isEmpty();
    }
}
