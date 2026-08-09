package com.uxplima.uxmessentials.warps.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.domain.WarpName;

/**
 * A server warp is about to be created.
 *
 * <p>Asked only for a genuinely new warp; re-anchoring an existing one to a new position is a move rather than a
 * creation and is not put to the gate.
 *
 * @param name what it would be called
 * @param owner who is creating it
 * @param location where it would point
 */
public record WarpCreating(WarpName name, PlayerRef owner, Position location) implements WarpProposal {

    public WarpCreating {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(location, "location");
    }
}
