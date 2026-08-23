package com.uxplima.uxmessentials.scoreboard.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import org.junit.jupiter.api.Test;

class SidebarLineTest {

    @Test
    void carriesStableIdentityConditionAndIndependentRightText() {
        SidebarLine line = new SidebarLine(
                "balance",
                "<gray>Balance",
                DisplayCondition.always(),
                new SidebarNumberFormat.Fixed("<gold>{coins}"),
                true);

        assertThat(line.id()).isEqualTo("balance");
        assertThat(line.numberFormat()).isEqualTo(new SidebarNumberFormat.Fixed("<gold>{coins}"));
        assertThat(line.hideWhenEmpty()).isTrue();
    }

    @Test
    void rejectsAnInvalidStableId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SidebarLine(
                        "has spaces", "text", DisplayCondition.always(), SidebarNumberFormat.blank(), false));
    }
}
