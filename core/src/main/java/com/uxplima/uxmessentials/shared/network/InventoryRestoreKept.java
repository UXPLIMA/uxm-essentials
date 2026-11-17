package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * An inventory restore was kept on the origin backend for a player who was not there. The backend the player is on
 * puts it on them now instead of at their next join. The frame carries the player identity only; the kept rows are the
 * source of truth, and taking them removes each row first, so a backend that applies a restore is the only one that
 * does.
 *
 * @param originServer the backend that kept the restore
 * @param player the player whose kept restore to apply
 */
public record InventoryRestoreKept(String originServer, UUID player) implements NetworkMessage {

    public InventoryRestoreKept {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(player, "player");
    }

    @Override
    public MessageType type() {
        return MessageType.INVENTORY_RESTORE_KEPT;
    }
}
