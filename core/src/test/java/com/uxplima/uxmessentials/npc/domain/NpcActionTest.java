package com.uxplima.uxmessentials.npc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NpcActionTest {

    @Test
    void carriesItsTriggerTypeAndValue() {
        NpcAction action = new NpcAction(ClickTrigger.RIGHT_CLICK, NpcActionType.MESSAGE, "<green>hi");

        assertThat(action.trigger()).isEqualTo(ClickTrigger.RIGHT_CLICK);
        assertThat(action.type()).isEqualTo(NpcActionType.MESSAGE);
        assertThat(action.value()).isEqualTo("<green>hi");
    }
}
