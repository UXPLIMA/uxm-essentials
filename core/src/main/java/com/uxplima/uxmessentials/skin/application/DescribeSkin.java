package com.uxplima.uxmessentials.skin.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import com.uxplima.uxmessentials.skin.domain.SkinSource;
import org.jspecify.annotations.NullMarked;

/**
 * Reads back what a player is wearing, for {@code /skin info}: which skin, from which source, on which model, and
 * when it was set. Staff answering "why does this player look like that" have one place to ask.
 */
@NullMarked
public final class DescribeSkin {

    private final SkinRepository repository;

    public DescribeSkin(SkinRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** What {@code player} chose, or empty when they chose nothing and wear whatever the join order gave them. */
    public Optional<Description> describe(UUID player) {
        Objects.requireNonNull(player, "player");
        return repository.find(player).map(DescribeSkin::describe);
    }

    private static Description describe(PlayerSkin skin) {
        return new Description(skin.source(), skin.model(), skin.appliedAt());
    }

    /**
     * One player's stored skin, as a reader wants it.
     *
     * @param source where the skin came from
     * @param model the player model it was cut for
     * @param appliedAt when it was set
     */
    public record Description(SkinSource source, SkinModel model, Instant appliedAt) {

        public Description {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(appliedAt, "appliedAt");
        }
    }
}
