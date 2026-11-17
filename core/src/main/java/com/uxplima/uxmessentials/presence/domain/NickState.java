package com.uxplima.uxmessentials.presence.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A player's {@code /nick} as every backend shares it: the nick, empty when they have none, and the version of the row
 * that holds it. The database counts the version up on every write, so a write made over an older version than the
 * row's is refused rather than taken, whatever the clocks of the backends say.
 *
 * @param nick the display name, or empty for none
 * @param version the version of the stored row; {@code 0} for a player who never set a nick
 */
public record NickState(String nick, long version) {

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
