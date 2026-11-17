package com.uxplima.uxmessentials.shared.adapter.outbound.notice;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * The {@code notices} block of {@code config.conf}: how many lines are kept for one player who is away, how long a
 * line waits before it is dropped, and how many are shown at once when the player is back. A value below one is
 * reported and replaced by its default.
 *
 * @param keptPerPlayer the most lines kept for one player; the oldest go first
 * @param keepFor how long a line is kept for a player who does not come back
 * @param shownAtOnce how many kept lines a returning player is shown, the rest counted in one line
 */
public record KeptNoticeSettings(int keptPerPlayer, Duration keepFor, int shownAtOnce) {

    static final int DEFAULT_KEPT_PER_PLAYER = 50;
    static final int DEFAULT_KEEP_DAYS = 30;
    static final int DEFAULT_SHOWN_AT_ONCE = 10;

    public KeptNoticeSettings {
        Objects.requireNonNull(keepFor, "keepFor");
    }

    /** The settings in {@code config}, each out-of-range value reported to {@code log} and replaced. */
    public static KeptNoticeSettings from(ConfigStore config, Logger log) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(log, "log");
        int kept = atLeastOne(config, "notices.kept-per-player", DEFAULT_KEPT_PER_PLAYER, log);
        int days = atLeastOne(config, "notices.keep-days", DEFAULT_KEEP_DAYS, log);
        int shown = atLeastOne(config, "notices.shown-at-once", DEFAULT_SHOWN_AT_ONCE, log);
        return new KeptNoticeSettings(kept, Duration.ofDays(days), shown);
    }

    private static int atLeastOne(ConfigStore config, String path, int fallback, Logger log) {
        int value = config.getInt(path, fallback);
        if (value < 1) {
            log.warn("event=config_value_out_of_range path={} value={} used={}", path, value, fallback);
            return fallback;
        }
        return value;
    }
}
