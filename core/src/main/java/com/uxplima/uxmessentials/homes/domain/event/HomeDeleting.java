package com.uxplima.uxmessentials.homes.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A home is about to be deleted. The home still exists while this is asked.
 *
 * @param owner whose home it is
 * @param slot the slot it occupies
 */
public record HomeDeleting(PlayerRef owner, HomeSlot slot) implements HomeProposal {

    public HomeDeleting {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
    }
}
