package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class WorldPropertyAccessTest {

    @Test
    void ofIntegerDecodesNonNegative() {
        WorldProperty<Integer> limit = WorldProperty.ofInteger("player-limit", 0);

        assertThat(limit.decode("10")).contains(10);
        assertThat(limit.defaultValue()).isZero();
    }

    @Test
    void ofIntegerRejectsNegativeAndNonNumeric() {
        WorldProperty<Integer> limit = WorldProperty.ofInteger("player-limit", 0);

        assertThat(limit.decode("-1")).isEmpty();
        assertThat(limit.decode("x")).isEmpty();
    }

    @Test
    void ofIntegerEncodeRoundTrips() {
        WorldProperty<Integer> limit = WorldProperty.ofInteger("player-limit", 0);

        assertThat(limit.encode(50)).isEqualTo("50");
        assertThat(limit.decode(limit.encode(50))).contains(50);
    }

    @Test
    void ofDecimalDecodesNonNegative() {
        WorldProperty<BigDecimal> fee = WorldProperty.ofDecimal("entry-fee");

        assertThat(fee.decode("100.5")).contains(new BigDecimal("100.5"));
        assertThat(fee.defaultValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void ofDecimalRejectsNegativeAndNonNumeric() {
        WorldProperty<BigDecimal> fee = WorldProperty.ofDecimal("entry-fee");

        assertThat(fee.decode("-5")).isEmpty();
        assertThat(fee.decode("abc")).isEmpty();
    }

    @Test
    void ofDecimalEncodeRoundTrips() {
        WorldProperty<BigDecimal> fee = WorldProperty.ofDecimal("entry-fee");

        assertThat(fee.encode(new BigDecimal("1000"))).isEqualTo("1000");
        assertThat(fee.decode(fee.encode(new BigDecimal("500.25")))).contains(new BigDecimal("500.25"));
    }

    @Test
    void catalogExposesAccessProperties() {
        assertThat(WorldProperties.byKey("access-restricted")).isPresent();
        assertThat(WorldProperties.byKey("player-limit")).isPresent();
        assertThat(WorldProperties.byKey("entry-fee")).isPresent();
    }

    @Test
    void allContainsTheThreeAccessProperties() {
        assertThat(WorldProperties.ALL)
                .contains(WorldProperties.ACCESS_RESTRICTED, WorldProperties.PLAYER_LIMIT, WorldProperties.ENTRY_FEE);
    }
}
