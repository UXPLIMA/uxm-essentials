package com.uxplima.uxmessentials.shared.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * One row of the plugin's own name index: the account, the name it last joined under, and when.
 *
 * <p>The name is carried in its original case for rendering; matching is always done on the lower-cased
 * form, which the repository stores alongside it.
 *
 * @param uuid the account identifier
 * @param name the display name as the player last joined with it
 * @param lastSeen epoch milliseconds of that join
 */
public record PlayerName(UUID uuid, String name, long lastSeen) {

    public PlayerName {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
    }

    /** This player as a {@link PlayerRef}, the identity value the resolution ports hand out. */
    public PlayerRef ref() {
        return new PlayerRef(uuid, name);
    }
}
