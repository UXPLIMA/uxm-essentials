package com.uxplima.uxmessentials.communication.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * How the connection lines treat a player moving between the servers of a network, and what {@code {count}} counts.
 *
 * @param hideSwitches whether a move shows no join or quit line and no MOTD on either server
 * @param window how long after a player left another server their arrival here counts as a move, and how long a quit
 *     line waits to learn whether the player arrived on another server
 * @param networkCount whether {@code {count}} counts the players of the whole network rather than of this server
 */
public record ServerSwitchPolicy(boolean hideSwitches, Duration window, boolean networkCount) {

    /** Moves are hidden, a quit line waits three seconds, and {@code {count}} counts this server. */
    public static final ServerSwitchPolicy DEFAULT = new ServerSwitchPolicy(true, Duration.ofSeconds(3), false);

    public ServerSwitchPolicy {
        Objects.requireNonNull(window, "window");
        if (window.isNegative()) {
            throw new IllegalArgumentException("window must not be negative: " + window);
        }
    }
}
