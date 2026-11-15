package com.uxplima.uxmessentials.messaging.application;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A {@code /helpop} request raised on one backend, for the staff on the others.
 *
 * @param id identifies the request
 * @param requester who asked for help
 * @param body the request
 */
public record RemoteHelpOp(UUID id, PlayerRef requester, MessageBody body) {

    public RemoteHelpOp {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(body, "body");
    }
}
