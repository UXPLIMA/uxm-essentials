package com.uxplima.uxmessentials.skin.application;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Deletes a stored skin outright, the staff counterpart to a player clearing their own.
 *
 * <p>The difference from {@link ClearSkin} is who is standing there: a clear re-dresses the player who asked for
 * it, while a drop is aimed at somebody who may well be offline, so it only removes the row and lets their next
 * join re-derive the skin. It is the answer to a stored texture that is wrong or unwanted.
 */
@NullMarked
public final class DropSkin {

    private final SkinRepository repository;

    public DropSkin(SkinRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** Delete {@code player}'s stored skin, reporting whether there was one. */
    public Outcome drop(UUID player) {
        Objects.requireNonNull(player, "player");
        if (repository.find(player).isEmpty()) {
            return Outcome.NOTHING_STORED;
        }
        repository.delete(player);
        return Outcome.DROPPED;
    }

    /** What became of a drop. */
    public enum Outcome {
        /** The row is gone and the next join re-derives the skin. */
        DROPPED,
        /** There was nothing stored for that player. */
        NOTHING_STORED
    }
}
