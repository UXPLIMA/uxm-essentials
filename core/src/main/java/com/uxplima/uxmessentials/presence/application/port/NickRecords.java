package com.uxplima.uxmessentials.presence.application.port;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.presence.domain.NickState;

/**
 * Where the players' nicks live, shared by every backend of one database. Every call reads or writes the database:
 * call it off the tick.
 */
public interface NickRecords {

    /** The nick {@code player} set, {@link NickState#NONE} when they never set one. */
    NickState read(UUID player);

    /** Store {@code state} for {@code player}, unless the row holds a newer one. */
    void write(UUID player, NickState state);

    /** The accounts whose nick is {@code nick} in any case. */
    List<UUID> holders(String nick);
}
