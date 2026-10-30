package com.uxplima.uxmessentials.shared.adapter.outbound.board;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmlib.board.BoardCalendar;
import com.uxplima.uxmlib.board.BoardPeriod;
import com.uxplima.uxmlib.board.BoardSettings;

/**
 * Reads the {@code leaderboard} block of the root config into the shared {@link BoardSettings}, the same keys every
 * UXM plugin with boards writes. A key left out takes the default; a key written wrong throws, naming the key.
 */
public final class BoardConfig {

    private static final String AT = "leaderboard.";

    private BoardConfig() {}

    /** The block as written, or the defaults for every key left out. */
    public static BoardSettings read(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        BoardSettings defaults = BoardSettings.defaults();
        return new BoardSettings(
                config.getInt(AT + "size", defaults.size()),
                Duration.ofSeconds(config.getLong(
                        AT + "refresh-seconds", defaults.refresh().toSeconds())),
                periods(config.getStringList(AT + "periods", List.of())),
                new BoardCalendar(
                        zone(config.getString(AT + "time-zone", "")),
                        weekStart(config.getString(AT + "week-starts", "monday"))),
                config.getInt(AT + "keep-windows", defaults.keepWindows()),
                config.getString(AT + "empty-head", BoardSettings.STEVE));
    }

    private static Set<BoardPeriod> periods(List<String> written) {
        if (written.isEmpty()) {
            return EnumSet.allOf(BoardPeriod.class);
        }
        Set<BoardPeriod> periods = EnumSet.noneOf(BoardPeriod.class);
        for (String word : written) {
            periods.add(BoardPeriod.byWord(word.toLowerCase(Locale.ROOT).strip())
                    .orElseThrow(() -> new IllegalArgumentException(AT + "periods names no period: " + word
                            + ". It takes alltime, monthly, weekly and daily")));
        }
        return periods;
    }

    private static ZoneId zone(String written) {
        if (written.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(written.strip());
        } catch (DateTimeException unknown) {
            throw new IllegalArgumentException(
                    AT + "time-zone is not a time zone: " + written + ". Write one such as Europe/Istanbul", unknown);
        }
    }

    private static DayOfWeek weekStart(String written) {
        try {
            return DayOfWeek.valueOf(written.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(
                    AT + "week-starts is not a day: " + written + ". Write one such as monday", unknown);
        }
    }
}
