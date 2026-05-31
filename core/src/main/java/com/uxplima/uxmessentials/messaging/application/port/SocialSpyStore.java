package com.uxplima.uxmessentials.messaging.application.port;

import java.util.List;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the staff {@code /socialspy} toggle — which staff are currently observing other
 * players' private messages (audit-relevant). When a private message is delivered, every spying staff
 * member who is not a party to the conversation receives a spy line. The flag is per-holder session/PDC
 * state, dropped on relog by default; the store also enumerates the active spies so the send path can fan
 * out without scanning every online player.
 */
public interface SocialSpyStore {

    /** True when {@code who} currently has {@code /socialspy} on. */
    boolean isSpying(PlayerRef who);

    /** Flip {@code who}'s {@code /socialspy} state, returning the new "is spying" value. */
    boolean toggle(PlayerRef who);

    /** Every staff member currently spying, for the send-path fan-out. */
    List<PlayerRef> activeSpies();
}
