package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Sending a private message, or leaving mail.
 *
 * <p>A message sent through here is the message {@code /msg} sends, gates and all: a muted sender is refused, a
 * recipient who has turned messages off is refused, a recipient who ignores the sender is quietly dropped, staff
 * on socialspy see it, and both sides can reply to it. Mail is the {@code /mail send} path: it is stored in the
 * database and waits for the recipient however long it has to.
 *
 * <p>Anything over 256 characters, or blank, is a malformed call and throws. That is the same bound the mail
 * column holds, so a body that is accepted here is a body that survives a restart.
 *
 * <pre>{@code
 * actions.messaging().ifPresent(messaging ->
 *     messaging.sendMail(winnerId, "Your prize is waiting at spawn."));
 * }</pre>
 */
public interface UxmMessagingActions {

    /**
     * Send a private message from one player to another, applying every gate {@code /msg} applies.
     *
     * <p>The sender has to be online, because the echo, the reply target and the socialspy line are all theirs.
     * The recipient does not: when they are offline and the server turns offline messages into mail, this leaves
     * mail; when it does not, this fails with {@code player-offline}.
     */
    CompletableFuture<UxmOutcome> sendMessage(UUID senderId, UUID recipientId, String body);

    /** Leave mail from one player to another. The sender being muted refuses it, as it does in game. */
    CompletableFuture<UxmOutcome> sendMail(UUID senderId, UUID recipientId, String body);

    /**
     * Leave mail from your plugin rather than from a player.
     *
     * <p>It arrives under your plugin's name, the way a server notice does, and nothing refuses it: there is no
     * mute to apply to a plugin and no way for a player to ignore one.
     */
    CompletableFuture<UxmOutcome> sendMail(UUID recipientId, String body);
}
