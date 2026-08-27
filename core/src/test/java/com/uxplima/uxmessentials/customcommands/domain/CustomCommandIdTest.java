package com.uxplima.uxmessentials.customcommands.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CustomCommandIdTest {

    @Test
    void acceptsLowercaseWordsWithDigitsDashesAndUnderscores() {
        assertThat(CustomCommandId.of("odul").value()).isEqualTo("odul");
        assertThat(CustomCommandId.of("daily_reward-2").value()).isEqualTo("daily_reward-2");
    }

    @Test
    void trimsAndLowercasesWhatAnOperatorTyped() {
        assertThat(CustomCommandId.of("  Odul ").value()).isEqualTo("odul");
    }

    @Test
    void rejectsBlankSpacedAndSlashedIds() {
        assertThatThrownBy(() -> CustomCommandId.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CustomCommandId.of("two words")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CustomCommandId.of("/odul")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CustomCommandId.of("-leading")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void answersWhetherARawNameWouldBeAcceptedSoALoaderNeedNotCatch() {
        assertThat(CustomCommandId.valid("odul")).isTrue();
        assertThat(CustomCommandId.valid("two words")).isFalse();
        assertThat(CustomCommandId.valid(null)).isFalse();
    }
}
