package com.uxplima.uxmessentials.presence.application;

import java.util.Objects;

import com.uxplima.uxmessentials.presence.domain.PresenceMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /nick off}: clear a player's display name and restore their account name, on every backend. Confirms with
 * {@link PresenceMessageKey#NICK_CLEARED}. Clearing is idempotent: clearing a player who has no nick still restores the
 * account name and confirms, so the command never has to branch on whether a nick was set.
 */
public final class ClearNick {

    private final PlayerNicks nicks;
    private final Notifier notifier;

    public ClearNick(PlayerNicks nicks, Notifier notifier) {
        this.nicks = Objects.requireNonNull(nicks, "nicks");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Clear {@code who}'s nick and confirm to {@code actor}. */
    public void clear(PlayerRef actor, PlayerRef who) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(who, "who");
        nicks.set(who.uuid(), "");
        notifier.send(actor, PresenceMessageKey.NICK_CLEARED);
    }

    /** Clear {@code who}'s own nick and confirm to them. */
    public void clear(PlayerRef who) {
        clear(who, who);
    }
}
