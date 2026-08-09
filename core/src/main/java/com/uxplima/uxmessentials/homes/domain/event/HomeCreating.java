package com.uxplima.uxmessentials.homes.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * A home is about to be created. Every rule uxmEssentials enforces has already passed by the time this is asked, so
 * refusing it is another plugin's decision and nothing has been written yet.
 *
 * @param owner whose home it will be
 * @param slot the slot it will occupy
 * @param location where it will point
 */
public record HomeCreating(PlayerRef owner, HomeSlot slot, Position location) implements HomeProposal {

    public HomeCreating {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(location, "location");
    }
}
