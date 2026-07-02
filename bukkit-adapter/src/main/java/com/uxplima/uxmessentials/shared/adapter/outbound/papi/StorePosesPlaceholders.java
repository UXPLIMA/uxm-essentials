package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;

import com.uxplima.uxmessentials.poses.application.PoseSessions;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link PosesPlaceholders} read seam over the live {@link PoseSessions} registry — the same single source of
 * truth the {@code /sit} command and the seat listeners mutate, so the {@code poses_sitting} placeholder always
 * matches what the player is doing. The read is a cheap map lookup on the placeholder path.
 */
@NullMarked
public final class StorePosesPlaceholders implements PosesPlaceholders {

    private final PoseSessions sessions;

    public StorePosesPlaceholders(PoseSessions sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @Override
    public boolean sitting(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return sessions.current(who)
                .filter(session -> session.type() == PoseType.SIT)
                .isPresent();
    }
}
