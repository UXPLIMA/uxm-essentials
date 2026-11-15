package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A {@code /helpop} request raised on the origin backend, for the staff on every other backend. Each receiver shows
 * it to its own staff with the origin server in the line, so staff know where to go.
 *
 * @param originServer the backend the requester is on
 * @param requestId identifies the request, so a copy delivered twice is shown once
 * @param requesterUuid the requester's account
 * @param requesterName the requester's name
 * @param body the request as the origin backend escaped it
 */
public record HelpOpFrame(String originServer, UUID requestId, UUID requesterUuid, String requesterName, String body)
        implements NetworkMessage {

    public HelpOpFrame {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(requesterUuid, "requesterUuid");
        Objects.requireNonNull(requesterName, "requesterName");
        Objects.requireNonNull(body, "body");
    }

    @Override
    public MessageType type() {
        return MessageType.HELP_OP;
    }
}
