package com.uxplima.uxmessentials.teleport.adapter.outbound.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.query.UxmTeleportQuery;
import com.uxplima.uxmessentials.api.view.UxmBackCause;
import com.uxplima.uxmessentials.api.view.UxmBackPoint;
import com.uxplima.uxmessentials.api.view.UxmTeleportRequest;
import com.uxplima.uxmessentials.api.view.UxmTeleportRequestDirection;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.BackLocationStore;
import com.uxplima.uxmessentials.teleport.application.port.RequestRegistry;
import com.uxplima.uxmessentials.teleport.domain.BackCause;
import com.uxplima.uxmessentials.teleport.domain.BackLocation;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest;
import org.jspecify.annotations.NullMarked;

/**
 * The published teleport query, over the registry {@code /tpaccept} resolves against and the return points
 * {@code /back} reads.
 *
 * <p>Both are in-flight state held in memory and dropped as soon as they are used, so nothing here waits and
 * nothing here reports a request that has already been answered or has lapsed.
 *
 * <p>Reading a return point does not consume it. The plugin clears a capture once the player has gone back to it,
 * and a question about where they would go must not do the same, or the next {@code /back} would find nothing.
 */
@NullMarked
public final class TeleportQueries implements UxmTeleportQuery {

    private final RequestRegistry requests;
    private final BackLocationStore backLocations;
    private final PlayerLookup players;

    public TeleportQueries(RequestRegistry requests, BackLocationStore backLocations, PlayerLookup players) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.backLocations = Objects.requireNonNull(backLocations, "backLocations");
        this.players = Objects.requireNonNull(players, "players");
    }

    @Override
    public List<UxmTeleportRequest> pendingFor(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return requests.pendingFor(subject(playerId)).stream()
                .map(TeleportQueries::view)
                .toList();
    }

    @Override
    public Optional<UxmTeleportRequest> outgoingFrom(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return requests.outgoing(subject(playerId)).map(TeleportQueries::view);
    }

    @Override
    public Optional<UxmBackPoint> backPoint(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return backLocations.current(subject(playerId)).map(TeleportQueries::view);
    }

    private PlayerRef subject(UUID playerId) {
        return ApiValues.subject(players, playerId);
    }

    private static UxmTeleportRequest view(TeleportRequest request) {
        return new UxmTeleportRequest(
                request.requester().uuid(),
                request.requester().name(),
                request.target().uuid(),
                request.target().name(),
                direction(request.direction()),
                request.expiresAt());
    }

    private static UxmBackPoint view(BackLocation location) {
        return new UxmBackPoint(
                ApiValues.location(location.position()), cause(location.cause()), location.capturedAt());
    }

    private static UxmTeleportRequestDirection direction(RequestDirection direction) {
        return switch (direction) {
            case TO_TARGET -> UxmTeleportRequestDirection.TO_TARGET;
            case TO_REQUESTER -> UxmTeleportRequestDirection.TO_REQUESTER;
        };
    }

    private static UxmBackCause cause(BackCause cause) {
        return switch (cause) {
            case TELEPORT -> UxmBackCause.TELEPORT;
            case DEATH -> UxmBackCause.DEATH;
        };
    }
}
