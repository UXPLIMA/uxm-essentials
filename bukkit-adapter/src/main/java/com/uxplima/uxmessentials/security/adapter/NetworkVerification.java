package com.uxplima.uxmessentials.security.adapter;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.RemoteSyncListener;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.StaffActions;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.StaffActionFrame.Action;
import org.jspecify.annotations.NullMarked;

/**
 * The live half of {@code /security force} and {@code /security reset}, for a target on any backend. The durable half
 * (device trust revoked, factors cleared) is in the shared database and already reaches every backend; what is left
 * acts on the session the player is standing in, which only the backend holding them can do. A target here is acted
 * on here, a target on another backend is acted on there, and a target on no server meets the durable half at their
 * next join.
 *
 * <p>The backend holding the player applies its own revoked-access policy, since that is the backend whose session
 * the reset interrupts.
 */
@NullMarked
public final class NetworkVerification {

    private final VerificationController local;
    private final PlayerLookup here;
    private final NetworkPresence presence;
    private final StaffActions actions;

    /**
     * @param here the players connected to this backend, asked first: right after a switch the network view can
     *     still place a player who is here on the backend they left
     */
    public NetworkVerification(
            VerificationController local, PlayerLookup here, NetworkPresence presence, StaffActions actions) {
        this.local = Objects.requireNonNull(local, "local");
        this.here = Objects.requireNonNull(here, "here");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    /** Send {@code target} back through verification now, wherever they are connected. */
    public void forceReverify(PlayerRef target, String actorName) {
        Optional<String> elsewhere = elsewhere(target);
        if (elsewhere.isPresent()) {
            actions.send(Action.SECURITY_FORCE, elsewhere.get(), target, actorName, Map.of());
        } else {
            local.forceReverify(target);
        }
    }

    /** Apply the revoked-access policy to {@code target}, whose factor staff just cleared, wherever they are. */
    public void onAccessRevoked(PlayerRef target, String actorName) {
        Optional<String> elsewhere = elsewhere(target);
        if (elsewhere.isPresent()) {
            actions.send(Action.SECURITY_REVOKED, elsewhere.get(), target, actorName, Map.of());
        } else {
            local.onAccessRevoked(target);
        }
    }

    /** The listeners that carry out, on this backend, what staff on another asked of a player connected here. */
    public RemoteSyncListener[] listeners() {
        String server = actions.serverId();
        return new RemoteSyncListener[] {
            StaffActions.listener(
                    server,
                    Action.SECURITY_FORCE,
                    frame -> onlyHere(frame.target()).ifPresent(local::forceReverify)),
            StaffActions.listener(
                    server,
                    Action.SECURITY_REVOKED,
                    frame -> onlyHere(frame.target()).ifPresent(local::onAccessRevoked))
        };
    }

    private Optional<String> elsewhere(PlayerRef target) {
        if (here.isOnline(target.uuid())) {
            return Optional.empty();
        }
        String local = presence.localServer();
        return presence.find(target.uuid()).map(OnlinePlayer::server).filter(server -> !server.equals(local));
    }

    /** The player {@code uuid} names when they are connected to this backend. */
    private Optional<PlayerRef> onlyHere(UUID uuid) {
        return here.isOnline(uuid) ? here.findByUuid(uuid) : Optional.empty();
    }
}
