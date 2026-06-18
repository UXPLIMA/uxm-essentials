package com.uxplima.uxmessentials.shared.domain.action;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClickActionTest {

    @Test
    void carriesItsTriggerTypeAndValue() {
        ClickAction action = new ClickAction(ClickTrigger.RIGHT_CLICK, ClickActionType.MESSAGE, "<green>hi");

        assertThat(action.trigger()).isEqualTo(ClickTrigger.RIGHT_CLICK);
        assertThat(action.type()).isEqualTo(ClickActionType.MESSAGE);
        assertThat(action.value()).isEqualTo("<green>hi");
    }
}
