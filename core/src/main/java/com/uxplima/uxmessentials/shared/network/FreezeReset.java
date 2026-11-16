package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;

/**
 * The origin backend started its moderation module and holds no freeze: a restart or a module reload dropped every
 * one it held. The proxy plugin forgets the freezes it kept for players frozen on that server before
 * {@code clearedAtMillis}, so a player frozen there is not kept off other servers by a freeze nobody holds any more.
 * Backends have nothing to do with it.
 *
 * @param originServer the backend that started
 * @param clearedAtMillis when it started, in epoch milliseconds
 */
public record FreezeReset(String originServer, long clearedAtMillis) implements NetworkMessage {

    public FreezeReset {
        Objects.requireNonNull(originServer, "originServer");
    }

    @Override
    public MessageType type() {
        return MessageType.FREEZE_RESET;
    }
}
