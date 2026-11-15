package com.uxplima.uxmessentials.shared.application.hop.port;

import com.uxplima.uxmessentials.shared.application.hop.HopDeparture;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The checks a local teleport passes before it starts (jail, combat tag, cooldown), asked by the origin before it
 * charges for a hop and connects the player. It is a port because the teleport context owns those rules and the
 * shared hop must not depend on it; tests bind a fake.
 */
public interface HopGate {

    /** A gate that refuses nobody. */
    HopGate OPEN = (who, departure) -> true;

    /** True when {@code who} may start {@code departure}; on false the player has already been told why. */
    boolean admits(PlayerRef who, HopDeparture departure);
}
