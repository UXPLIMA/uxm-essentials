package com.uxplima.uxmessentials.vanish.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * A player went hidden, or came back into view. Raised by the one authority every entry point runs through
 * ({@code /vanish}, {@code /vanish <player>}, the presence panel, staff mode), so a consumer hears the same fact
 * whichever door was used.
 *
 * <p>The level is the tier they are hidden at, resolved from their own permissions as they vanish. On a reveal it
 * is the level they were hidden at until a moment ago, which is what a consumer that mirrors the state needs to
 * undo it, rather than a level of zero that would mean nothing.
 *
 * <p>A quit is not a reveal and does not raise this. A player who logs out while hidden logs back in hidden, and a
 * server hop that read as an unvanish would flicker every consumer's own view.
 *
 * @param player who was hidden or revealed
 * @param vanished true when they are now hidden, false when they are visible again
 * @param level the tier they are (or were) hidden at
 */
public record VanishToggled(PlayerRef player, boolean vanished, VanishLevel level) implements VanishEvent {

    public VanishToggled {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(level, "level");
    }
}
