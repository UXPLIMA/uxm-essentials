package com.uxplima.uxmessentials.messaging.adapter.outbound.api;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmMessagingActions;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.messaging.application.SendMail;
import com.uxplima.uxmessentials.messaging.application.SendMessage;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.messaging.domain.MessagingError;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * The published messaging actions, over the same use cases {@code /msg} and {@code /mail send} run.
 *
 * <p>Everything happens on a worker. Mail is a database write, and the delivery below it hands each line to the
 * message sink, which hops to the reader's own thread before it renders anything.
 *
 * <p>A message needs its sender online and takes the offline branch for a recipient who is not, which is the
 * branch a vanished recipient takes too: whether that becomes mail or a refusal is the operator's setting, not
 * this surface's, so a plugin cannot use the API to find out that somebody hidden is online.
 */
@NullMarked
public final class MessagingActions implements UxmMessagingActions {

    private final SendMessage message;
    private final SendMail mail;
    private final PlayerLookup players;
    private final Scheduler scheduler;
    private final String source;

    public MessagingActions(MessagingApiWrites writes, PlayerLookup players, Scheduler scheduler, String source) {
        Objects.requireNonNull(writes, "writes");
        this.message = writes.message();
        this.mail = writes.mail();
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public CompletableFuture<UxmOutcome> sendMessage(UUID senderId, UUID recipientId, String body) {
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(recipientId, "recipientId");
        MessageBody text = text(body);
        if (!players.isOnline(senderId)) {
            return CompletableFuture.completedFuture(
                    UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "the sender is not online to send it"));
        }
        PlayerRef from = ApiValues.subject(players, senderId);
        PlayerRef to = ApiValues.subject(players, recipientId);
        boolean online = players.isOnline(recipientId);
        return AsyncActions.perform(scheduler, () -> outcome(message.send(from, to, text, online)));
    }

    @Override
    public CompletableFuture<UxmOutcome> sendMail(UUID senderId, UUID recipientId, String body) {
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(recipientId, "recipientId");
        MessageBody text = text(body);
        PlayerRef from = ApiValues.subject(players, senderId);
        PlayerRef to = ApiValues.subject(players, recipientId);
        return AsyncActions.perform(scheduler, () -> outcome(mail.send(from, to, text)));
    }

    @Override
    public CompletableFuture<UxmOutcome> sendMail(UUID recipientId, String body) {
        Objects.requireNonNull(recipientId, "recipientId");
        MessageBody text = text(body);
        PlayerRef to = ApiValues.subject(players, recipientId);
        return AsyncActions.perform(scheduler, () -> {
            mail.sendFromSystem(source, to, text);
            return UxmOutcome.ok();
        });
    }

    private static MessageBody text(String body) {
        return MessageBody.of(Objects.requireNonNull(body, "body"));
    }

    private static UxmOutcome outcome(Result<Unit, MessagingError> result) {
        return result.isErr() ? UxmOutcome.failed(failure(result.errorOrThrow())) : UxmOutcome.ok();
    }

    /** Which published code a messaging refusal is. */
    private static UxmFailure failure(MessagingError error) {
        return switch (error) {
            case TARGET_OFFLINE, TARGET_HIDDEN ->
                UxmFailure.of(UxmFailure.PLAYER_OFFLINE, "the recipient is not online and mail is not the fallback");
            case SELF -> UxmFailure.of(UxmFailure.REFUSED, "a player cannot message themselves");
            case TARGET_TOGGLED_OFF -> UxmFailure.of(UxmFailure.REFUSED, "the recipient has private messages off");
            case SENDER_MUTED -> UxmFailure.of(UxmFailure.REFUSED, "the sender is muted");
            case NO_REPLY_TARGET -> UxmFailure.of(UxmFailure.NOT_FOUND, "there is nobody to reply to");
            case MAILBOX_EMPTY -> UxmFailure.of(UxmFailure.NOT_FOUND, "there is no mail there");
            default ->
                UxmFailure.of(
                        UxmFailure.REFUSED,
                        "messaging refused it: " + error.name().toLowerCase(java.util.Locale.ROOT));
        };
    }
}
