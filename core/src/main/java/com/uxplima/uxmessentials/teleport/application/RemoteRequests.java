package com.uxplima.uxmessentials.teleport.application;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.domain.RequestId;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest;

/**
 * The cross-server side of the requests {@link AcceptTeleport} resolves. A request a player on another backend sent
 * sits in this backend's registry like a local one, so the target lists, accepts, denies and lets it expire with the
 * usual verbs; this is how each of those outcomes reaches the requester's backend instead of being acted on here.
 * It also lets {@code /tpcancel} withdraw a request this backend's player sent to another backend.
 */
public interface RemoteRequests {

    /** No request crosses servers. */
    RemoteRequests NONE = new RemoteRequests() {
        @Override
        public boolean isRemote(RequestId id) {
            return false;
        }

        @Override
        public void accepted(TeleportRequest request) {
            // Nothing is remote here.
        }

        @Override
        public void denied(TeleportRequest request) {
            // Nothing is remote here.
        }

        @Override
        public void expired(TeleportRequest request) {
            // Nothing is remote here.
        }

        @Override
        public boolean cancelOutgoing(PlayerRef requester) {
            return false;
        }
    };

    /** True when the request with {@code id} was sent from another backend, whatever has happened to it since. */
    boolean isRemote(RequestId id);

    /** The target accepted the remote {@code request}; it is already out of the registry. */
    void accepted(TeleportRequest request);

    /** The target denied the remote {@code request}; it is already out of the registry. */
    void denied(TeleportRequest request);

    /** The remote {@code request} expired here; it is already out of the registry. */
    void expired(TeleportRequest request);

    /** Withdraw a request {@code requester} sent to another backend; false when they have none open. */
    boolean cancelOutgoing(PlayerRef requester);
}
