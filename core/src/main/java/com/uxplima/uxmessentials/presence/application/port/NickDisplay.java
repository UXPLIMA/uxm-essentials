package com.uxplima.uxmessentials.presence.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Shows a player's nick as their display name while they are on this backend, or their account name when they have
 * none. A player not on this backend is left alone. Any thread: the display is changed on the player's own.
 */
public interface NickDisplay {

    /** Show {@code nick} as the display name of {@code player}, or their account name when it is empty. */
    void show(UUID player, Optional<String> nick);
}
