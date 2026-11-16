package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/** Lets a test in another package hold a player's file lock, as a save in progress would. */
public final class PlayerDataFilesAccess {

    private PlayerDataFilesAccess() {}

    /** The lock every read and write of {@code player}'s file holds. */
    public static ReentrantLock lockOf(UUID player) {
        return PlayerDataFiles.lockOf(player);
    }
}
