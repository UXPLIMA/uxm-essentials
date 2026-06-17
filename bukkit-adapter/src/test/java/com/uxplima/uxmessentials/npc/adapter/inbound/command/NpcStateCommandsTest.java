package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NpcStateCommandsTest {

    @Test
    void parsesAPlainNumberAsMilliseconds() {
        assertThat(NpcStateCommands.parseFriendlyMillis("500")).isEqualTo(500L);
        assertThat(NpcStateCommands.parseFriendlyMillis("0")).isEqualTo(0L);
    }

    @Test
    void parsesSuffixedDurations() {
        assertThat(NpcStateCommands.parseFriendlyMillis("250ms")).isEqualTo(250L);
        assertThat(NpcStateCommands.parseFriendlyMillis("30s")).isEqualTo(30_000L);
        assertThat(NpcStateCommands.parseFriendlyMillis("5min")).isEqualTo(300_000L);
        assertThat(NpcStateCommands.parseFriendlyMillis("2h")).isEqualTo(7_200_000L);
        assertThat(NpcStateCommands.parseFriendlyMillis("1MIN")).isEqualTo(60_000L);
    }

    @Test
    void rejectsGarbageUnknownUnitsAndNegatives() {
        assertThat(NpcStateCommands.parseFriendlyMillis("soon")).isNull();
        assertThat(NpcStateCommands.parseFriendlyMillis("10x")).isNull();
        assertThat(NpcStateCommands.parseFriendlyMillis("-5s")).isNull();
        assertThat(NpcStateCommands.parseFriendlyMillis("")).isNull();
    }

    @Test
    void resolvesVisibilitySentinels() {
        assertThat(NpcStateCommands.sentinelBlocks("not_visible")).isEqualTo(0.0);
        assertThat(NpcStateCommands.sentinelBlocks("ALWAYS_VISIBLE")).isEqualTo(100_000.0);
        assertThat(NpcStateCommands.sentinelBlocks("48")).isNull();
        assertThat(NpcStateCommands.sentinelBlocks("default")).isNull();
    }
}
