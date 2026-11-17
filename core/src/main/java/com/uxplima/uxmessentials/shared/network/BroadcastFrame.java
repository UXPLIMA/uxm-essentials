package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * An operator's {@code /broadcast} sent on the origin backend, for the players of every other backend. Each backend
 * frames the text in each viewer's own language and leaves out the players who turned broadcasts off, as it does for
 * one sent there.
 *
 * @param originServer the backend the broadcast was sent on
 * @param broadcastId identifies the broadcast, so a backend that receives it twice shows it once
 * @param body the operator's MiniMessage text
 */
public record BroadcastFrame(String originServer, UUID broadcastId, String body) implements NetworkMessage {

    public BroadcastFrame {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(broadcastId, "broadcastId");
        Objects.requireNonNull(body, "body");
    }

    @Override
    public MessageType type() {
        return MessageType.BROADCAST;
    }
}
