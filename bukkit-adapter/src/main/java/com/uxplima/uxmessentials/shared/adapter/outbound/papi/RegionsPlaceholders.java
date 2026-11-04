package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code regions_*} placeholders: which guard region a player is standing
 * in, who holds it, their role there, whether an action is allowed, and how many regions their world carries.
 * Wired during guard wiring; with the guard module disabled the seam is absent and every key degrades to the
 * dash.
 */
public interface RegionsPlaceholders {

    /** Whether a region provider is reachable at all; false makes every other read empty by definition. */
    boolean available();

    /** The region covering {@code who}, highest priority first when several overlap. */
    Optional<Standing> standingIn(PlayerRef who);

    /** How many regions cover {@code who} at once. */
    int coveringCount(PlayerRef who);

    /** How many regions are defined in the world {@code who} stands in. */
    int worldCount(PlayerRef who);

    /** The role {@code who} holds in the region they stand in. */
    default Optional<String> role(PlayerRef who) {
        return Optional.empty();
    }

    /** Whether {@code who} may do {@code action} (dots may be written as underscores) where they stand. */
    default Optional<Boolean> allowed(PlayerRef who, String action) {
        return Optional.empty();
    }

    /**
     * The region a player stands in.
     *
     * @param id the region's id
     * @param priority its priority, which is what decides an overlap
     * @param owners the identifiers of everyone who owns it
     * @param members the identifiers of everyone who may build in it
     */
    record Standing(String id, int priority, List<String> owners, List<String> members) {

        public Standing {
            owners = List.copyOf(owners);
            members = List.copyOf(members);
        }
    }
}
