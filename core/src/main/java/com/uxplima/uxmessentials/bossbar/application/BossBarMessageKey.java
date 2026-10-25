package com.uxplima.uxmessentials.bossbar.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The boss bar context's own words. The bars themselves are operator content under
 * {@code modules/bossbar/config.conf}, so these are only the command replies and the units of a time left.
 */
public enum BossBarMessageKey implements MessageKey {
    BOSSBAR_SHOWN("bossbar.shown"),
    BOSSBAR_HIDDEN("bossbar.hidden"),
    BOSSBAR_SHOWN_OTHER("bossbar.shown-other"),
    BOSSBAR_HIDDEN_OTHER("bossbar.hidden-other"),
    BOSSBAR_UNKNOWN_BAR("bossbar.unknown-bar"),
    BOSSBAR_BAD_DURATION("bossbar.bad-duration"),
    BOSSBAR_ANNOUNCED("bossbar.announced"),
    BOSSBAR_SENT("bossbar.sent"),
    // The units of {remaining} on a timed bar, one number each.
    BOSSBAR_TIME_DAYS("bossbar.time.days"),
    BOSSBAR_TIME_HOURS("bossbar.time.hours"),
    BOSSBAR_TIME_MINUTES("bossbar.time.minutes"),
    BOSSBAR_TIME_SECONDS("bossbar.time.seconds");

    private final String key;

    BossBarMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
