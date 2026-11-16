package com.uxplima.uxmessentials.teleport.application.port;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Where an offline player stands on this server: the position the server itself saved with the rest of their data
 * when they last left it. Both methods touch the disk, so they run on the async lane and never on a tick thread.
 */
public interface LogoutPositions {

    /** The saved position of {@code player}, or empty when this server keeps no data for them. */
    Optional<SavedSpot> read(UUID player);

    /**
     * Replace the saved position of {@code player} with {@code at}, so they log in there next time. Only for a player
     * who is not online on this server; false when this server keeps no data for them.
     */
    boolean write(UUID player, Position at);

    /**
     * A saved position as stored: the world by its uid, since the world's name is looked up on a server thread.
     *
     * @param worldUid the uid of the world the player was saved in
     */
    record SavedSpot(UUID worldUid, double x, double y, double z, float yaw, float pitch) {

        public SavedSpot {
            Objects.requireNonNull(worldUid, "worldUid");
        }
    }
}
