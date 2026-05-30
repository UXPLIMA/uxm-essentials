package com.uxplima.uxmessentials.homes.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A home was removed — {@code /delhome} or an admin delete of another player's home. The freed slot
 * lets the owner set a new home again under their limit.
 *
 * @param owner the player whose home was removed
 * @param name the name of the removed home
 */
public record HomeDeleted(PlayerRef owner, HomeName name) implements HomeEvent {

    public HomeDeleted {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
    }
}
