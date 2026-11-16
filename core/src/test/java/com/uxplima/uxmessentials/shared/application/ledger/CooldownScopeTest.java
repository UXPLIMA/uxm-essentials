package com.uxplima.uxmessentials.shared.application.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A stamp name the stores can hold, which two different names never share. */
class CooldownScopeTest {

    /** The width of the {@code scope} column of {@code player_cooldowns}. */
    private static final int STORED_WIDTH = 128;

    @Test
    void aNameTheStoresCanHoldIsKeptAsItIs() {
        assertThat(CooldownScope.distinct("tp.warp.my_shop")).isEqualTo("tp.warp.my_shop");
    }

    @Test
    void namesThatFoldToTheSameTextStayApart() {
        assertThat(CooldownScope.distinct("tp.warp.шахта"))
                .isNotEqualTo(CooldownScope.distinct("tp.warp.лавка"))
                .isNotEqualTo(CooldownScope.normalize("tp.warp.лавка"));
        assertThat(CooldownScope.distinct("tp.warp.my shop"))
                .isNotEqualTo(CooldownScope.distinct("tp.warp.my!shop"))
                .isNotEqualTo("tp.warp.my_shop");
    }

    @Test
    void aFoldedNameIsTheSameEveryTimeAndOnlyHoldsWhatTheStoresAccept() {
        String once = CooldownScope.distinct("tp.warp.шахта");

        assertThat(CooldownScope.distinct("tp.warp.шахта")).isEqualTo(once);
        assertThat(CooldownScope.normalize(once)).isEqualTo(once);
    }

    @Test
    void theLongestWarpClockFitsTheStoredColumn() {
        String longest = "tp.warp." + "ш".repeat(64);

        assertThat(CooldownScope.distinct(longest)).hasSizeLessThanOrEqualTo(STORED_WIDTH);
    }
}
