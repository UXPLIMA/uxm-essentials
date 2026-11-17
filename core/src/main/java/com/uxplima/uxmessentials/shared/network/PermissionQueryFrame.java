package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * One step of a cross-server permission read, addressed to {@link #toServer()}: a backend asking the backend a player
 * is connected to whether they hold a node, or that backend's answer. Only the addressed backend acts on it.
 *
 * @param originServer the backend that sends this step
 * @param queryId the question the step belongs to, so an answer finds the question it answers
 * @param toServer the backend this step is for
 * @param player the player the question is about
 * @param node the permission node asked about
 * @param step {@link #ASK} for the question, else the answer: {@link #HELD}, {@link #NOT_HELD} or {@link #NO_ANSWER}
 *     (as text, so an older peer can drop a step it does not know instead of failing the frame)
 */
public record PermissionQueryFrame(
        String originServer, UUID queryId, String toServer, UUID player, String node, String step)
        implements NetworkMessage {

    /** The question. */
    public static final String ASK = "ASK";

    /** The player holds the node. */
    public static final String HELD = "HELD";

    /** The player does not hold the node. */
    public static final String NOT_HELD = "NOT_HELD";

    /** The player could not be read there: they left, or their thread did not run the read. */
    public static final String NO_ANSWER = "NO_ANSWER";

    public PermissionQueryFrame {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(queryId, "queryId");
        Objects.requireNonNull(toServer, "toServer");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(step, "step");
    }

    @Override
    public MessageType type() {
        return MessageType.PERMISSION_QUERY;
    }
}
