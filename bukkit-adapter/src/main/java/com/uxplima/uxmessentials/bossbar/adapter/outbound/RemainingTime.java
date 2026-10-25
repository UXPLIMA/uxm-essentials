package com.uxplima.uxmessentials.bossbar.adapter.outbound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

import com.uxplima.uxmessentials.bossbar.application.BossBarMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * A time left in one reader's words, for {@code {remaining}} on a bar: the largest unit that is not zero first, and
 * the next two after it, leaving out a unit that is zero. {@code 5d 8h 52m}, {@code 7d 8h}, {@code 4m 9s}. Each unit
 * is a catalogue line, because every language spells and shortens them its own way.
 */
@NullMarked
public final class RemainingTime implements BiFunction<UUID, Duration, String> {

    private static final int UNITS_SHOWN = 3;
    private static final List<MessageKey> UNITS = List.of(
            BossBarMessageKey.BOSSBAR_TIME_DAYS,
            BossBarMessageKey.BOSSBAR_TIME_HOURS,
            BossBarMessageKey.BOSSBAR_TIME_MINUTES,
            BossBarMessageKey.BOSSBAR_TIME_SECONDS);

    private final Messages messages;

    public RemainingTime(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public String apply(UUID reader, Duration left) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(left, "left");
        long seconds = Math.max(0L, left.toSeconds());
        long[] amounts = {seconds / 86_400L, seconds / 3_600L % 24L, seconds / 60L % 60L, seconds % 60L};
        int first = 0;
        while (first < amounts.length - 1 && amounts[first] == 0L) {
            first++;
        }
        PlayerRef viewer = new PlayerRef(reader, "");
        List<String> parts = new ArrayList<>();
        for (int unit = first; unit < Math.min(first + UNITS_SHOWN, amounts.length); unit++) {
            if (amounts[unit] != 0L || (parts.isEmpty() && unit == amounts.length - 1)) {
                parts.add(messages.resolve(viewer, UNITS.get(unit), Map.of("n", Long.toString(amounts[unit]))));
            }
        }
        return String.join(" ", parts);
    }
}
