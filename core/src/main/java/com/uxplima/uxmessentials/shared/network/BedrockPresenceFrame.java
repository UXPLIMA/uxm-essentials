package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * The proxy plugin telling a backend that a player who just connected to it is a Bedrock player, as Geyser on the
 * proxy knows them. A backend with neither Floodgate nor Geyser of its own learns its Bedrock players only this way.
 *
 * <p>It is sent to that backend alone, over the player's own connection, sealed with {@code network.bus.secret}; the
 * backend reads it only when it arrived over that player's connection, so neither a client nor a frame relayed from
 * another server can claim one. The client fields are Geyser's own names, read by name on the backend.
 *
 * @param originServer {@link ProxyConnectionFrame#PROXY}, the sender
 * @param frameId identifies this frame, so the backend can match it to the connection that carried it
 * @param player the Bedrock player
 * @param server the backend they connected to, the one this frame is for
 * @param xuid their Xbox user id, or empty when Geyser does not know it
 * @param platform the device their client runs on, as Geyser names it
 * @param inputMode how they play, as Geyser names it
 * @param uiProfile the screen layout they chose, as Geyser names it
 * @param version their client's game version
 * @param language their client's language code
 */
public record BedrockPresenceFrame(
        String originServer,
        UUID frameId,
        UUID player,
        String server,
        String xuid,
        String platform,
        String inputMode,
        String uiProfile,
        String version,
        String language)
        implements NetworkMessage {

    public BedrockPresenceFrame {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(frameId, "frameId");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(xuid, "xuid");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(inputMode, "inputMode");
        Objects.requireNonNull(uiProfile, "uiProfile");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(language, "language");
    }

    @Override
    public MessageType type() {
        return MessageType.BEDROCK_PRESENCE;
    }
}
