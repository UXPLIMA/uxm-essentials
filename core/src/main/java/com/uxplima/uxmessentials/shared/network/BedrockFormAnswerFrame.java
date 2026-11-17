package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * The proxy plugin handing a backend a Bedrock player's answer to the form that backend asked for in a
 * {@link BedrockFormRequestFrame}.
 *
 * <p>An answer can hold a secret, such as a PIN typed into an input. So it goes to the backend that asked and nowhere
 * else: over the player's own connection to that backend, sealed with {@code network.bus.secret}, never to the client
 * and never to another server. The backend reads it only when it arrived over that player's connection. Its
 * {@link #toString()} leaves the answer out.
 *
 * @param originServer {@link ProxyConnectionFrame#PROXY}, the sender
 * @param frameId identifies this frame, so the backend can match it to the connection that carried it
 * @param toServer the backend that asked, the one this frame is for
 * @param player the player who answered
 * @param requestId the id the request was sent under
 * @param answer the answer, as uxmLib's form codec writes it, in base64
 */
public record BedrockFormAnswerFrame(
        String originServer, UUID frameId, String toServer, UUID player, long requestId, String answer)
        implements NetworkMessage {

    public BedrockFormAnswerFrame {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(frameId, "frameId");
        Objects.requireNonNull(toServer, "toServer");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(answer, "answer");
    }

    @Override
    public MessageType type() {
        return MessageType.BEDROCK_FORM_ANSWER;
    }

    /** Everything but the answer, which may be a secret. */
    @Override
    public String toString() {
        return "BedrockFormAnswerFrame[originServer=" + originServer + ", frameId=" + frameId + ", toServer=" + toServer
                + ", player=" + player + ", requestId=" + requestId + "]";
    }
}
