package com.uxplima.uxmessentials.messaging.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the per-player {@code /msgtoggle} switch. Whether a player accepts incoming private
 * messages. When a player has toggled DMs off, a {@code /msg} or {@code /reply} to them resolves
 * {@link com.uxplima.uxmessentials.messaging.domain.MessagingError#TARGET_TOGGLED_OFF} before delivery, but
 * <em>mail still delivers</em> (the toggle gates only the real-time channel, per docs/permissions.md). The
 * flag is kept in the database every backend shares, held in memory for the players online here.
 */
public interface MessageToggleStore {

    /** True when {@code who} currently accepts incoming private messages (the default for a fresh player). */
    boolean acceptsMessages(PlayerRef who);

    /**
     * Flip {@code who}'s {@code /msgtoggle} state, returning the new "accepts messages" value; unchanged when the
     * setting is not {@link #ready}.
     */
    boolean toggle(PlayerRef who);

    /**
     * Whether {@code who}'s setting was read on this server, so it can be changed. A change asked for before then is
     * refused rather than built on a setting this server does not know.
     */
    default boolean ready(PlayerRef who) {
        return true;
    }
}
