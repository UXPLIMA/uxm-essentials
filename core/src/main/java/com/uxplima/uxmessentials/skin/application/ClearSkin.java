package com.uxplima.uxmessentials.skin.application;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.application.port.SkinView;
import com.uxplima.uxmessentials.skin.domain.event.SkinCleared;
import org.jspecify.annotations.NullMarked;

/**
 * Drops a player's own choice, so they go back to whatever the join order would have given them: their real
 * premium skin, their Bedrock skin, or an entry from the default pool.
 *
 * <p>This is deliberately not "remove the skin". A player who cleared on a cracked server keeps a face; they just
 * stop overriding the one the server would have picked. The re-derivation goes through {@link DressLogin} rather
 * than being written again here, so the order can never drift between a join and a clear.
 */
@NullMarked
public final class ClearSkin {

    private final SkinRepository repository;
    private final DressLogin dressLogin;
    private final SkinView view;
    private final DomainEventPublisher events;
    private final Clock clock;

    public ClearSkin(
            SkinRepository repository, DressLogin dressLogin, SkinView view, DomainEventPublisher events, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.dressLogin = Objects.requireNonNull(dressLogin, "dressLogin");
        this.view = Objects.requireNonNull(view, "view");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Drop {@code target}'s stored choice and dress them in whatever the join order resolves instead. */
    public Outcome clear(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        if (repository.find(target.uuid()).isEmpty()) {
            return Outcome.NOTHING_TO_CLEAR;
        }
        repository.delete(target.uuid());
        events.publish(new SkinCleared(target, clock.instant()));
        dressLogin
                .resolve(target.uuid(), target.name())
                .ifPresent(dressed -> view.apply(target, dressed.texture(), dressed.model()));
        return Outcome.CLEARED;
    }

    /** What became of a clear. */
    public enum Outcome {
        /** The choice is gone and the player wears whatever the join order gave them. */
        CLEARED,
        /** They had chosen nothing to begin with. */
        NOTHING_TO_CLEAR
    }
}
