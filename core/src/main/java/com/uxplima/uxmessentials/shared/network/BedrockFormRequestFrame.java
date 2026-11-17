package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A backend asking the proxy plugin to show a Bedrock player a form, through Geyser on the proxy. The answer comes
 * back as a {@link BedrockFormAnswerFrame} to this backend alone.
 *
 * <p>It travels over the player's own connection, sealed with {@code network.bus.secret}, and only to the proxy: the
 * proxy plugin reads it, checks that the connection carrying it is that player's, and never relays it to another
 * backend. It never enters the bus between backends either, Redis included.
 *
 * @param originServer the backend that asks
 * @param player the Bedrock player the form is for
 * @param requestId the id the answer comes back under
 * @param form the form, as uxmLib's form codec writes it, in base64
 */
public record BedrockFormRequestFrame(String originServer, UUID player, long requestId, String form)
        implements NetworkMessage {

    public BedrockFormRequestFrame {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(form, "form");
    }

    @Override
    public MessageType type() {
        return MessageType.BEDROCK_FORM_REQUEST;
    }
}
