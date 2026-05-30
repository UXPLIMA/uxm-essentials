package com.uxplima.uxmessentials.homes.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * A home was created — {@code /sethome} under a name the owner did not already hold. A {@code /sethome}
 * onto an existing name re-anchors that home and raises nothing here; that path is a move, not a
 * creation.
 *
 * @param owner the player who now owns the home
 * @param name the name the home was created under
 * @param location where the new home points
 */
public record HomeCreated(PlayerRef owner, HomeName name, Position location) implements HomeEvent {

    public HomeCreated {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
    }
}
