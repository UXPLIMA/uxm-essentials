package com.uxplima.uxmessentials.homes.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeCost;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.application.hop.HopCharge;
import com.uxplima.uxmessentials.shared.application.hop.HopDeparture;
import com.uxplima.uxmessentials.shared.application.hop.HopDepartures;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * How the home use cases hand a home on another backend to the shared cross-server hop. {@code departures} is empty
 * while the homes {@code cross-server} block is off; every home is then used on this backend whatever server it was
 * set on, as it always was, so turning the block on is what changes behaviour.
 *
 * <p>A hop's target names the home by its owner and slot ({@code <owner-uuid>:<slot>}), so the target backend reads
 * the home again on arrival rather than trusting a position the origin read.
 */
public final class HomeHops {

    private final Optional<HopDepartures> departures;
    private final PlayerLookup players;
    private final String localServer;
    private final boolean recordsServer;

    /**
     * {@code recordsServer} is false off a network or under the shipped default server name: a name every standalone
     * backend shares would send each home to a server that does not exist once the backend joins a network under its
     * own name, so such a backend writes no server and its rows stay for the claim.
     */
    public HomeHops(
            Optional<HopDepartures> departures, PlayerLookup players, String localServer, boolean recordsServer) {
        this.departures = Objects.requireNonNull(departures, "departures");
        this.players = Objects.requireNonNull(players, "players");
        this.localServer = Objects.requireNonNull(localServer, "localServer");
        this.recordsServer = recordsServer;
    }

    /** The route that is never taken: every home is used on this backend. */
    public static HomeHops local(PlayerLookup players, String localServer, boolean recordsServer) {
        return new HomeHops(Optional.empty(), players, localServer, recordsServer);
    }

    /** This backend's network name, which a home of unknown server is used on. */
    public String localServer() {
        return localServer;
    }

    /** The server a home set or moved here records, or empty when this backend records none. */
    public Optional<String> recordedServer() {
        return recordsServer ? Optional.of(localServer) : Optional.empty();
    }

    /** True when {@code home} is used by sending the player to the backend it was set on. */
    public boolean routes(Home home) {
        Objects.requireNonNull(home, "home");
        return departures.isPresent() && home.livesElsewhere(localServer);
    }

    /**
     * Off-tick: send {@code who} to the remote {@code home}. A player who left before this ran is neither charged nor
     * sent, since the connect could not reach them and the charge would wait out the TTL. Then the teleport rules, so
     * a refused hop costs nothing, then {@code charge}, which answers exactly what it took, then the hop that carries
     * that amount so every miss refunds it. On a refusal the player has already been told why.
     */
    public Result<Unit, HomeError> travel(
            PlayerRef who, Home home, Supplier<Result<Optional<HomeCost>, HomeError>> charge) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(home, "home");
        Objects.requireNonNull(charge, "charge");
        HopDepartures hop = departures.orElseThrow();
        if (!players.isOnline(who.uuid())) {
            return Result.err(HomeError.TELEPORT_REFUSED);
        }
        HopDeparture departure = departureOf(home);
        if (!hop.admit(who, departure)) {
            return Result.err(HomeError.TELEPORT_REFUSED);
        }
        Result<Optional<HomeCost>, HomeError> charged = charge.get();
        if (charged.isErr()) {
            return Result.err(charged.errorOrThrow());
        }
        Optional<HopCharge> carried =
                charged.orElseThrow().map(cost -> new HopCharge(cost.amount(), cost.currencyId()));
        hop.send(who, departure, carried, Optional.empty());
        return Result.ok();
    }

    /**
     * The hop to {@code home}: owner and slot as the reference, and no cooldown override, so the origin's gate checks
     * the home cooldown key the landing stamps.
     */
    static HopDeparture departureOf(Home home) {
        return new HopDeparture(
                HopKind.HOME,
                targetOf(home.owner().uuid(), home.slot()),
                home.knownServer().orElseThrow(),
                labelOf(home),
                home.location(),
                Optional.empty());
    }

    /** The name a player knows {@code home} by: its label, or its slot number when it has none. */
    static String labelOf(Home home) {
        return home.label()
                .map(HomeLabel::value)
                .orElseGet(() -> Integer.toString(home.slot().displayNumber()));
    }

    static String targetOf(UUID owner, HomeSlot slot) {
        return owner + ":" + slot.index();
    }
}
