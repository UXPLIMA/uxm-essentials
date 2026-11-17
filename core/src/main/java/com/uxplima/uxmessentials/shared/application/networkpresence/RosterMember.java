package com.uxplima.uxmessentials.shared.application.networkpresence;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.PresenceTraits;

/**
 * One player of a backend's roster, with the traits that backend announced for them.
 *
 * @param player the player
 * @param traits how deep they are hidden and whether they are staff
 */
public record RosterMember(PlayerRef player, PresenceTraits traits) {

    public RosterMember {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(traits, "traits");
    }
}
