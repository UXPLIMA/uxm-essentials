package com.uxplima.uxmessentials.homes.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A home's visibility was flipped between public (any player may visit) and private (only the owner and
 * invited players may). The slot is the home's identity and is unchanged; only who may reach it moved.
 *
 * @param owner the player whose home changed visibility
 * @param slot the slot whose visibility changed
 */
public record HomeVisibilityChanged(PlayerRef owner, HomeSlot slot) implements HomeEvent {

    public HomeVisibilityChanged {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
    }
}
