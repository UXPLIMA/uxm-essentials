package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;

import com.uxplima.uxmessentials.poses.application.PoseSessions;
import com.uxplima.uxmessentials.poses.application.port.PlayerSitPreferences;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link PosesPlaceholders} read seam over the live poses state: the {@link PoseSessions} registry for
 * {@code poses_sitting} (the same single source of truth the {@code /sit} command and the seat listeners mutate)
 * and the {@link PlayerSitPreferences} store for {@code poses_toggle} (the {@code /poses toggle} opt-out). Both are
 * cheap lookups on the placeholder path.
 */
@NullMarked
public final class StorePosesPlaceholders implements PosesPlaceholders {

    private final PoseSessions sessions;
    private final PlayerSitPreferences preferences;

    public StorePosesPlaceholders(PoseSessions sessions, PlayerSitPreferences preferences) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    @Override
    public boolean sitting(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return sessions.current(who)
                .filter(session -> session.type() == PoseType.SIT)
                .isPresent();
    }

    @Override
    public boolean allowsSitting(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return preferences.allowsSitting(who);
    }
}
