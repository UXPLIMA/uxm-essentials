package com.uxplima.uxmessentials.teleport.application.port;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

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
     * A saved position as stored. The world is named the way the file names it, since it is looked up on a server
     * thread: by the uid the server writes beside the position, or, in data from before that, by the dimension key.
     *
     * @param worldUid the uid of the world the player was saved in, or null when the data has none
     * @param dimension the dimension key, such as {@code minecraft:overworld}, used only when there is no uid
     */
    record SavedSpot(
            @Nullable UUID worldUid, @Nullable String dimension, double x, double y, double z, float yaw, float pitch) {

        public SavedSpot {
            if (worldUid == null && dimension == null) {
                throw new IllegalArgumentException("a saved position names its world by uid or by dimension");
            }
        }

        /** A position in the world with {@code worldUid}. */
        public static SavedSpot inWorld(UUID worldUid, double x, double y, double z, float yaw, float pitch) {
            return new SavedSpot(Objects.requireNonNull(worldUid, "worldUid"), null, x, y, z, yaw, pitch);
        }

        /** A position in the dimension named {@code dimension}, for data that carries no world uid. */
        public static SavedSpot inDimension(String dimension, double x, double y, double z, float yaw, float pitch) {
            return new SavedSpot(null, Objects.requireNonNull(dimension, "dimension"), x, y, z, yaw, pitch);
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
