package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A {@link MutePolicy} that asks the database every time, for a check that already runs off the server thread (the
 * chat listener on Paper's async chat thread), so it sees a mute set on any server at once. It reads the live mute
 * state from the {@link ModerationRepository} and answers "is this player muted right now"
 * against the injected {@link Clock}, so a timed mute that has expired no longer gags, even if the expiry
 * sweep has not yet deleted the row.
 *
 * <p>The checks that answer on the server thread use {@link OnlineMutes} instead. This one lives in
 * {@code application} (pure) and imports only the messaging <em>port</em> and the moderation repository port.
 */
public final class RepositoryMutePolicy implements MutePolicy {

    private final ModerationRepository repository;
    private final Clock clock;

    public RepositoryMutePolicy(ModerationRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean isMuted(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return repository.loadMute(who).isActiveAt(clock.instant());
    }
}
