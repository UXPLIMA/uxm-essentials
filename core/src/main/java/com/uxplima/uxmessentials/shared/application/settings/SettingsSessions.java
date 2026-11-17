package com.uxplima.uxmessentials.shared.application.settings;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;

/**
 * When the shared per-player settings are read into memory and let go of, from the moments of a player's connection.
 * They are read during the login, off the tick, and let go of when the player quits. A duplicate login quits the old
 * session after the new one was read, so the join reads them again when they are no longer held, on the async lane;
 * the old connection closing after the new session is online lets go of nothing.
 */
public final class SettingsSessions {

    private final HeldSettings settings;
    private final Scheduler scheduler;

    public SettingsSessions(HeldSettings settings, Scheduler scheduler) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** A login was let in. Reads storage: call it off the tick, as the pre-login runs. */
    public void loggingIn(UUID player) {
        settings.load(Objects.requireNonNull(player, "player"));
    }

    /** The player joined: read their settings again when the login read did not leave them held. */
    public void joined(UUID player) {
        Objects.requireNonNull(player, "player");
        if (!settings.holds(player)) {
            scheduler.async(() -> settings.load(player));
        }
    }

    /** The player quit. */
    public void quit(UUID player) {
        settings.forget(Objects.requireNonNull(player, "player"));
    }

    /**
     * A connection closed, after its quit when the player had joined. A login that never joined is let go of here; a
     * player online again by now is a newer session of the same account and keeps their settings.
     */
    public void closed(UUID player, boolean onlineNow) {
        if (!onlineNow) {
            settings.forget(Objects.requireNonNull(player, "player"));
        }
    }

    /** Read the settings of a player who was already online when the module started, off the tick. */
    public void adopt(UUID player) {
        Objects.requireNonNull(player, "player");
        scheduler.async(() -> settings.load(player));
    }
}
