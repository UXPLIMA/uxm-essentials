package com.uxplima.uxmessentials.teleport.application.port;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.teleport.domain.PendingRelocation;

/**
 * The relocations {@code /tpofflinehere} leaves for players who are not online, one per player and backend, in the
 * database every backend shares. Every method touches the database, so it runs on the async lane.
 */
public interface PendingRelocations {

    /** Store {@code relocation}, replacing any the player already has on its backend. */
    void save(PendingRelocation relocation);

    /** The players with a relocation waiting on {@code server}. */
    Set<UUID> playersWaitingOn(String server);

    /** The relocation waiting for {@code player} on {@code server}, if any. */
    Optional<PendingRelocation> find(UUID player, String server);

    /**
     * Remove {@code relocation} if it is still the one stored. True only for the caller whose delete removed it, so of
     * two quick joins only one applies it, and a relocation replaced meanwhile is left for the next join.
     */
    boolean claim(PendingRelocation relocation);

    /** Put back a claimed relocation that could not be applied, unless a newer one has been stored since. */
    void restore(PendingRelocation relocation);
}
