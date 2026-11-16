package com.uxplima.uxmessentials.teleport.application.port;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Where an offline player stands on this server: the position the server itself saved with the rest of their data
 * when they last left it. Reading touches the disk, so it runs on the async lane and never on a tick thread.
 */
public interface LogoutPositions {

    /** What a server whose saved data cannot be reached answers: every read fails. */
    LogoutPositions UNAVAILABLE = player -> {
        throw new Unreadable("the saved player data of this server cannot be read");
    };

    /**
     * The saved position of {@code player}, or empty when this server keeps none for them.
     *
     * @throws Unreadable when the saved data exists but cannot be read, such as a file cut short while it is written
     */
    Optional<SavedSpot> read(UUID player);

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

    /** The saved data exists, or should, but could not be read; the cause is logged where it happened. */
    final class Unreadable extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public Unreadable(String message) {
            super(message);
        }
    }
}
