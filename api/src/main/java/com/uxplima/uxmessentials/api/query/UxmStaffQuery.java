package com.uxplima.uxmessentials.api.query;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who is on duty.
 *
 * <p>Staff mode is the toggle that swaps a staff member's own inventory for the moderation loadout. It is not the
 * same question as "does this player have a staff permission": somebody with every node in the plugin is off duty
 * until they turn it on, and a chat or logging plugin that wants to mark a message as staff wants the toggle, not
 * the node.
 *
 * <p>Everything answers straight away, since the state is held in memory for the players who are online. A player
 * who is offline is not in staff mode: the toggle does not survive a quit, by design, so nobody comes back holding
 * a gadget hotbar.
 */
public interface UxmStaffQuery {

    /** Whether this player is in staff mode right now. */
    boolean isInStaffMode(UUID playerId);

    /**
     * The named mode they are in, or empty when they are off duty. An operator can configure more than one mode
     * (a light one for a helper, a full one for an admin), and the name is the one from the config.
     */
    Optional<String> modeOf(UUID playerId);

    /** Every player in staff mode right now. Usually small, and often empty. */
    Set<UUID> inStaffMode();
}
