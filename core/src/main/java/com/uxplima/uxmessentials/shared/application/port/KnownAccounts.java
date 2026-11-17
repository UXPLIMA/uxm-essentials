package com.uxplima.uxmessentials.shared.application.port;

import java.util.UUID;

/**
 * Whether an account has joined a server of the database before: the plugin's name index, which every backend writes
 * as players join. A port because it is a storage boundary. Reads the database: call it off the tick.
 */
public interface KnownAccounts {

    /** Whether {@code player} joined any server sharing this database before. */
    boolean known(UUID player);
}
