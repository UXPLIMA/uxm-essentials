package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Refuses a live action on a player who is connected to another backend. A kick or a freeze acts on the connected
 * player through this backend, so on a player elsewhere it would report success while nothing happened. Durable
 * sanctions (ban, mute, warn) are stored and enforced on every backend and do not ask this.
 */
final class LiveTargetCheck {

    private final NetworkPresence presence;
    private final Notifier notifier;

    LiveTargetCheck(NetworkPresence presence, Notifier notifier) {
        this.presence = Objects.requireNonNull(presence, "presence");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Whether {@code target} is on another backend; if so {@code actor} has been told which one. */
    boolean refusedElsewhere(PlayerRef actor, PlayerRef target) {
        Optional<String> server = elsewhere(target);
        if (server.isEmpty()) {
            return false;
        }
        notifier.send(
                actor, ModerationMessageKey.TARGET_ELSEWHERE, Map.of("player", target.name(), "server", server.get()));
        return true;
    }

    /** The backend {@code target} is connected to, when that is not this one. */
    Optional<String> elsewhere(PlayerRef target) {
        String here = presence.localServer();
        return presence.find(target.uuid()).map(OnlinePlayer::server).filter(server -> !server.equals(here));
    }
}
