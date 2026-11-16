package com.uxplima.uxmessentials.worlds.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.hop.HopCharge;
import com.uxplima.uxmessentials.shared.application.hop.HopDeparture;
import com.uxplima.uxmessentials.shared.application.hop.HopDepartures;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.SpawnCodec;

/**
 * How {@link WorldTeleportService} hands a world on another backend to the shared cross-server hop. Both departures
 * are empty while the worlds {@code cross-server} block is off; a world of another backend is then not reachable from
 * here at all, so turning the block on is what changes behaviour.
 *
 * <p>A player's own {@code /worlds spawn} and a staff {@code /worlds tp} leave through different departures, because
 * each passes the teleport rules of its local counterpart: the spawn rules for one, the staff teleport rules for the
 * other.
 */
public final class WorldHops {

    private static final UUID UNKNOWN_UID = new UUID(0L, 0L);
    private static final double FALLBACK_Y = 64.0;

    private final Optional<HopDepartures> visits;
    private final Optional<HopDepartures> staff;
    private final String localServer;

    public WorldHops(Optional<HopDepartures> visits, Optional<HopDepartures> staff, String localServer) {
        this.visits = Objects.requireNonNull(visits, "visits");
        this.staff = Objects.requireNonNull(staff, "staff");
        this.localServer = Objects.requireNonNull(localServer, "localServer");
    }

    /** The route that is never taken: only this backend's worlds can be reached. */
    public static WorldHops local(String localServer) {
        return new WorldHops(Optional.empty(), Optional.empty(), localServer);
    }

    /** True when a world of another backend can be reached by sending the player there. */
    public boolean enabled() {
        return visits.isPresent() && staff.isPresent();
    }

    /** This backend's network name. */
    public String localServer() {
        return localServer;
    }

    /**
     * Whether {@code who} may hop to {@code world} under the local teleport rules; asked before any charge, so a
     * refused hop costs nothing. On false the player has already been told why.
     */
    public boolean admit(PlayerRef who, NetworkWorld world, boolean byStaff) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(world, "world");
        return departures(byStaff).admit(who, departureOf(world, byStaff));
    }

    /** Send {@code who} to {@code world}, carrying exactly what was {@code charged} so a miss refunds it. */
    public void send(PlayerRef who, NetworkWorld world, boolean byStaff, Optional<HopCharge> charged) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(charged, "charged");
        departures(byStaff).send(who, departureOf(world, byStaff), charged, Optional.empty());
    }

    private HopDepartures departures(boolean byStaff) {
        return (byStaff ? staff : visits).orElseThrow(() -> new IllegalStateException("world hops are off"));
    }

    /**
     * The hop to {@code world}. The origin cannot read another backend's live spawn, so the destination the gate
     * checks is the stored spawn when the world has one, and the target lands at the live spawn either way.
     */
    static HopDeparture departureOf(NetworkWorld world, boolean byStaff) {
        ManagedWorld managed = world.world();
        String name = managed.name().value();
        WorldRef ref = new WorldRef(managed.knownUid().orElse(UNKNOWN_UID), name);
        Position destination = managed.settings()
                .spawn()
                .flatMap(raw -> SpawnCodec.decode(raw, ref))
                .orElseGet(() -> Position.of(ref, 0, FALLBACK_Y, 0));
        return new HopDeparture(
                HopKind.WORLD,
                new WorldHopTarget(managed.name(), byStaff).encode(),
                world.knownServer().orElseThrow(),
                name,
                destination,
                Optional.empty());
    }
}
