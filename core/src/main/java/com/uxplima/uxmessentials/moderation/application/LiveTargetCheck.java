package com.uxplima.uxmessentials.moderation.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Which backend a player connected elsewhere is on. A kick acts on the connection, which only the backend holding it
 * can close, so a kick on a player elsewhere is carried there rather than run here, and {@code /seen} names that
 * backend. Durable sanctions (ban, mute, warn) are stored and enforced on every backend and do not ask this.
 */
final class LiveTargetCheck {

    private final NetworkPresence presence;

    LiveTargetCheck(NetworkPresence presence) {
        this.presence = Objects.requireNonNull(presence, "presence");
    }

    /** The backend {@code target} is connected to, when that is not this one. */
    Optional<String> elsewhere(PlayerRef target) {
        String here = presence.localServer();
        return presence.find(target.uuid()).map(OnlinePlayer::server).filter(server -> !server.equals(here));
    }
}
