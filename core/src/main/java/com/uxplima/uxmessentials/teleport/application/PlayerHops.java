package com.uxplima.uxmessentials.teleport.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.hop.HopDeparture;
import com.uxplima.uxmessentials.shared.application.hop.HopDepartures;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * How a teleport to a player on another backend is handed to the shared cross-server hop: an accepted request as
 * {@link HopKind#PLAYER_REQUEST}, a staff teleport as {@link HopKind#PLAYER_STAFF}. {@code departures} is empty while
 * the teleport {@code cross-server} block is off, and then no player is ever sent to another backend.
 *
 * <p>The hop's target is the anchor player's id and name. The anchor's position is not known here and would be stale
 * by the time the mover arrives, so the target backend reads it at landing time; the departure carries a stand-in
 * position, which the origin's gate never reads (it judges the kind, the mover and the cooldown).
 */
public final class PlayerHops {

    private static final char SEPARATOR = ':';

    private final Optional<HopDepartures> departures;

    public PlayerHops(Optional<HopDepartures> departures) {
        this.departures = Objects.requireNonNull(departures, "departures");
    }

    /** The route that is never taken: teleports to players on other backends are off. */
    public static PlayerHops off() {
        return new PlayerHops(Optional.empty());
    }

    /** True while players may be sent to another backend. */
    public boolean enabled() {
        return departures.isPresent();
    }

    /**
     * Whether {@code mover} may hop to {@code anchor} on {@code server} under the local teleport rules of
     * {@code kind}; on false they have already been told why. Called before anything is spent on the hop.
     */
    public boolean admit(PlayerRef mover, HopKind kind, PlayerRef anchor, String server) {
        Objects.requireNonNull(mover, "mover");
        return departures.orElseThrow().admit(mover, departureOf(kind, anchor, server));
    }

    /** Send {@code mover} to {@code anchor} on {@code server}. Nothing is charged for a hop to a player. */
    public void send(PlayerRef mover, HopKind kind, PlayerRef anchor, String server) {
        Objects.requireNonNull(mover, "mover");
        departures.orElseThrow().send(mover, departureOf(kind, anchor, server), Optional.empty(), Optional.empty());
    }

    /** The hop's target reference for {@code anchor}: the id the landing resolves and the name its lines show. */
    static String targetOf(PlayerRef anchor) {
        return anchor.uuid().toString() + SEPARATOR + anchor.name();
    }

    /** The anchor a hop's target names, or empty for a reference this release did not write. */
    static Optional<PlayerRef> anchorOf(String target) {
        int split = target.indexOf(SEPARATOR);
        if (split <= 0 || split == target.length() - 1) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PlayerRef(UUID.fromString(target.substring(0, split)), target.substring(split + 1)));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    static HopDeparture departureOf(HopKind kind, PlayerRef anchor, String server) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(server, "server");
        if (kind != HopKind.PLAYER_REQUEST && kind != HopKind.PLAYER_STAFF) {
            throw new IllegalArgumentException("not a hop to a player: " + kind);
        }
        Position standIn = Position.of(new WorldRef(new UUID(0L, 0L), server), 0, 0, 0);
        return new HopDeparture(kind, targetOf(anchor), server, anchor.name(), standIn, Optional.empty());
    }
}
