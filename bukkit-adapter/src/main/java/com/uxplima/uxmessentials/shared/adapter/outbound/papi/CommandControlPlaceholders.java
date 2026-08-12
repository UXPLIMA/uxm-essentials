package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code commandcontrol_allowed_<command>} family: whether this player
 * would be allowed to run that command where they are standing. It answers from the same rule set and the same
 * player facts the gate consults, so a menu that hides a button and the gate that would refuse the click agree
 * rather than guess alongside each other.
 *
 * <p>The resolution is a pure walk over the world's rule set plus a permission check, so it is cheap enough for a
 * menu requirement. Wired during commandcontrol wiring; with the module disabled the seam is absent, the family
 * degrades to the dash, and nothing is being blocked anyway.
 */
public interface CommandControlPlaceholders {

    /** Whether {@code who} may run {@code command} (written without the leading slash) in their current world. */
    boolean allowed(PlayerRef who, String command);
}
