package com.uxplima.uxmessentials.messaging.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The target's backend telling the network what it did with a {@link RemoteMessage}. The parties and the body ride
 * along so a backend that did not send the message can still show it to its social spies.
 *
 * @param id the message answered
 * @param outcome what happened
 * @param sender who sent the message
 * @param target who it was for
 * @param body the message
 * @param afkReason present when the target was away, holding the reason they gave (possibly blank)
 */
public record RemoteAnswer(
        UUID id,
        RemoteOutcome outcome,
        PlayerRef sender,
        PlayerRef target,
        MessageBody body,
        Optional<String> afkReason) {

    public RemoteAnswer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(afkReason, "afkReason");
    }

    /** The answer to {@code message} with {@code outcome} and nobody away. */
    static RemoteAnswer to(RemoteMessage message, RemoteOutcome outcome) {
        return new RemoteAnswer(
                message.id(), outcome, message.sender(), message.target(), message.body(), Optional.empty());
    }
}
