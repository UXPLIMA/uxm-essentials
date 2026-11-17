package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;

/**
 * The live side of a kick staff issued on another backend against a player connected here. The staff member's
 * backend checked the exemption and wrote the history, the audit line and the broadcast; only the backend holding
 * the connection can close it, so this shows the kick screen, in the player's own language, and nothing more.
 *
 * <p>A player who already left or moved on is not kicked: the kick was for the connection they held when it was
 * issued, and the backend they reached since did not receive it.
 */
public final class ApplyRemoteKick {

    private final Sanctions sanctions;
    private final NetworkPresence network;
    private final Notifier notifier;

    public ApplyRemoteKick(Sanctions sanctions, NetworkPresence network, Notifier notifier) {
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.network = Objects.requireNonNull(network, "network");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** A peer kicked {@code target} with {@code reason}; they are kicked when they are connected here. */
    public void onKick(PlayerRef target, Optional<String> reason) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        String here = network.localServer();
        boolean connectedHere = network.find(target.uuid())
                .filter(online -> online.server().equals(here))
                .isPresent();
        if (!connectedHere) {
            return;
        }
        MessageKey key = ModerationMessageKey.KICK_KICKED;
        sanctions.kick(target, key, notifier.render(target, key, Map.of("reason", reason.orElse(""))));
    }
}
