package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * Rank actions were kept on the origin backend for a player who was not there. The backend the player is on runs
 * them now instead of at their next join. The frame carries the player identity only; the kept rows are the source of
 * truth, and taking them deletes each row first, so a backend that runs them is the only one that does.
 *
 * @param originServer the backend that kept the actions
 * @param player the player whose kept actions to run
 */
public record RankActionsKept(String originServer, UUID player) implements NetworkMessage {

    public RankActionsKept {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(player, "player");
    }

    @Override
    public MessageType type() {
        return MessageType.RANK_ACTIONS_KEPT;
    }
}
