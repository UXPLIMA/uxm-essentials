package com.uxplima.uxmessentials.homes.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * An existing home is about to be moved. The home still points at its old place while this is asked; the location
 * carried here is the new one being proposed, which is what a plugin protecting an area needs to judge.
 *
 * @param owner whose home it is
 * @param slot the slot it occupies, which does not change
 * @param location where it would point afterwards
 */
public record HomeRelocating(PlayerRef owner, HomeSlot slot, Position location) implements HomeProposal {

    public HomeRelocating {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(location, "location");
    }
}
