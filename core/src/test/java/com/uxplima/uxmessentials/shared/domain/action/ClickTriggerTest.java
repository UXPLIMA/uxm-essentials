package com.uxplima.uxmessentials.shared.domain.action;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClickTriggerTest {

    @Test
    void leftClickMatchesOnlyAttacks() {
        assertThat(ClickTrigger.LEFT_CLICK.matches(true)).isTrue();
        assertThat(ClickTrigger.LEFT_CLICK.matches(false)).isFalse();
    }

    @Test
    void rightClickMatchesOnlyInteracts() {
        assertThat(ClickTrigger.RIGHT_CLICK.matches(false)).isTrue();
        assertThat(ClickTrigger.RIGHT_CLICK.matches(true)).isFalse();
    }

    @Test
    void anyMatchesBoth() {
        assertThat(ClickTrigger.ANY.matches(true)).isTrue();
        assertThat(ClickTrigger.ANY.matches(false)).isTrue();
    }
}
