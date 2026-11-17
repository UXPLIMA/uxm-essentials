package com.uxplima.uxmessentials.presence.application.port;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.presence.domain.NickState;

/**
 * Where the players' nicks live, shared by every backend of one database. A nick is held by one player at most in any
 * case, which the store enforces, so two backends claiming one nick at once cannot both have it. Every call reads or
 * writes the database: call it off the tick.
 */
public interface NickRecords {

    /** The nick {@code player} set, {@link NickState#NONE} when they never set one. */
    NickState read(UUID player);

    /**
     * Store {@code nick} for {@code player}, or no nick when it is empty, over the row at {@code version} ({@code 0}
     * when there is none): {@link NickWrite.Taken} when another player holds the nick in any case, and
     * {@link NickWrite.Stale} when the row is no longer at that version.
     */
    NickWrite write(UUID player, long version, String nick);

    /** The accounts whose nick is {@code nick} in any case. */
    List<UUID> holders(String nick);
}
