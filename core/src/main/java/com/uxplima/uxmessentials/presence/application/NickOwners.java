package com.uxplima.uxmessentials.presence.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.presence.application.port.NickRecords;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The accounts that carry a nick, on any server of the database, online or not, for {@code /realname}. Reads the
 * database: call it off the tick.
 */
public final class NickOwners {

    private final NickRecords records;
    private final PlayerLookup players;

    public NickOwners(NickRecords records, PlayerLookup players) {
        this.records = Objects.requireNonNull(records, "records");
        this.players = Objects.requireNonNull(players, "players");
    }

    /** The accounts whose nick is {@code nick} in any case, by the names they last joined with. */
    public List<PlayerRef> of(String nick) {
        Objects.requireNonNull(nick, "nick");
        List<PlayerRef> owners = new ArrayList<>();
        for (UUID holder : records.holders(nick)) {
            players.findByUuid(holder).ifPresent(owners::add);
        }
        return List.copyOf(owners);
    }
}
