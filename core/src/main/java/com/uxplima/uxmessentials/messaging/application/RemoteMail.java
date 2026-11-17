package com.uxplima.uxmessentials.messaging.application;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Mail left for a player who may be online on another backend, so that backend can tell them it arrived.
 *
 * @param id identifies the notice, so a copy delivered twice is shown once
 * @param recipient who the mail is for
 * @param senderName the name the mail was sent under
 */
public record RemoteMail(UUID id, PlayerRef recipient, String senderName) {

    public RemoteMail {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(senderName, "senderName");
    }
}
