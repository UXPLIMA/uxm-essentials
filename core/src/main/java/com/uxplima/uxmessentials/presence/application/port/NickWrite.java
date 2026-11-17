package com.uxplima.uxmessentials.presence.application.port;

import java.util.Objects;

import com.uxplima.uxmessentials.presence.domain.NickState;

/** What came of writing a nick: written, refused because another player holds it, or refused as out of date. */
public sealed interface NickWrite {

    /**
     * The nick is stored.
     *
     * @param state what the row holds now
     */
    record Written(NickState state) implements NickWrite {

        public Written {
            Objects.requireNonNull(state, "state");
        }
    }

    /** Another player holds the nick in some case, so it was not stored. */
    record Taken() implements NickWrite {}

    /** The row changed since it was read, so the write was not made; read it again before trying anew. */
    record Stale() implements NickWrite {}
}
