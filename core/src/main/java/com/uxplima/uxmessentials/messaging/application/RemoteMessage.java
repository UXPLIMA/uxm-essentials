package com.uxplima.uxmessentials.messaging.application;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A private message on its way to a player on another backend.
 *
 * @param id identifies the message; the answer names it
 * @param sender who sent it
 * @param senderSeeLevel the vanish see level the sender holds on their backend
 * @param target who it is for
 * @param targetServer the backend the target was online on when it was sent
 * @param body the message
 * @param reply whether it is a {@code /reply}, which the target's reply-routing switch also gates
 */
public record RemoteMessage(
        UUID id,
        PlayerRef sender,
        int senderSeeLevel,
        PlayerRef target,
        String targetServer,
        MessageBody body,
        boolean reply) {

    public RemoteMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetServer, "targetServer");
        Objects.requireNonNull(body, "body");
    }
}
