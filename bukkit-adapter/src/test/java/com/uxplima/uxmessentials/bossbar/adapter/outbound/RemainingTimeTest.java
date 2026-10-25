package com.uxplima.uxmessentials.bossbar.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/** A time left written the way the screenshot reads it: the largest unit first, three units at most. */
class RemainingTimeTest {

    private static final UUID READER = UUID.randomUUID();

    /** Turkish units, so the test reads the way the lobby does. */
    private final RemainingTime remaining = new RemainingTime(new Messages() {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            String unit =
                    switch (key.key()) {
                        case "bossbar.time.days" -> "g";
                        case "bossbar.time.hours" -> "s";
                        case "bossbar.time.minutes" -> "dk";
                        default -> "sn";
                    };
            return placeholders.get("n") + unit;
        }
    });

    @Test
    void daysHoursAndMinutes() {
        assertThat(remaining.apply(
                        READER, Duration.ofDays(5).plusHours(8).plusMinutes(52).plusSeconds(10)))
                .isEqualTo("5g 8s 52dk");
    }

    @Test
    void aZeroUnitInTheMiddleIsLeftOut() {
        assertThat(remaining.apply(READER, Duration.ofDays(7).plusHours(8).plusSeconds(40)))
                .isEqualTo("7g 8s");
        assertThat(remaining.apply(READER, Duration.ofDays(2).plusMinutes(5))).isEqualTo("2g 5dk");
    }

    @Test
    void underAnHourShowsMinutesAndSeconds() {
        assertThat(remaining.apply(READER, Duration.ofMinutes(4).plusSeconds(9)))
                .isEqualTo("4dk 9sn");
    }

    @Test
    void underAMinuteShowsSeconds() {
        assertThat(remaining.apply(READER, Duration.ofSeconds(42))).isEqualTo("42sn");
    }

    @Test
    void nothingLeftReadsZeroSeconds() {
        assertThat(remaining.apply(READER, Duration.ZERO)).isEqualTo("0sn");
        assertThat(remaining.apply(READER, Duration.ofMillis(400))).isEqualTo("0sn");
    }
}
