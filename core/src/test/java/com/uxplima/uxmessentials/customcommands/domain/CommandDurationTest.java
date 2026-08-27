package com.uxplima.uxmessentials.customcommands.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class CommandDurationTest {

    @Test
    void parsesSingleAndCompoundUnits() {
        assertThat(CommandDuration.parse("30s")).contains(Duration.ofSeconds(30));
        assertThat(CommandDuration.parse("1h30m")).contains(Duration.ofMinutes(90));
        assertThat(CommandDuration.parse("2d")).contains(Duration.ofDays(2));
        assertThat(CommandDuration.parse("1w")).contains(Duration.ofDays(7));
    }

    @Test
    void parsesZeroAndBlankAsNoWait() {
        assertThat(CommandDuration.parse("0s")).contains(Duration.ZERO);
        assertThat(CommandDuration.parse("")).contains(Duration.ZERO);
        assertThat(CommandDuration.parse("0")).contains(Duration.ZERO);
        assertThat(CommandDuration.parse(null)).contains(Duration.ZERO);
    }

    @Test
    void rejectsGarbage() {
        assertThat(CommandDuration.parse("soon")).isEmpty();
        assertThat(CommandDuration.parse("30x")).isEmpty();
        assertThat(CommandDuration.parse("30s later")).isEmpty();
    }

    @Test
    void formatsBackIntoTheGrammarItReads() {
        assertThat(CommandDuration.format(Duration.ofMinutes(90))).isEqualTo("1h30m");
        assertThat(CommandDuration.format(Duration.ZERO)).isEqualTo("0s");
        assertThat(CommandDuration.parse(CommandDuration.format(Duration.ofSeconds(3725))))
                .contains(Duration.ofSeconds(3725));
    }
}
