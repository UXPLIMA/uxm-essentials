package com.uxplima.uxmessentials.presence.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A player's {@code /nick} as every backend shares it: the nick, empty when they have none, and when it was set, so of
 * two backends writing at once the newer setting stays.
 *
 * @param nick the display name, or empty for none
 * @param changedAtMillis when it was set, in epoch milliseconds; {@code 0} for a player who never set one
 */
public record NickState(String nick, long changedAtMillis) {

    /** A player who never set a nick. */
    public static final NickState NONE = new NickState("", 0L);

    public NickState {
        Objects.requireNonNull(nick, "nick");
    }

    /** The nick, when the player has one. */
    public Optional<String> value() {
        return nick.isEmpty() ? Optional.empty() : Optional.of(nick);
    }
}
