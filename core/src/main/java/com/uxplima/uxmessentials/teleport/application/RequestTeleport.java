package com.uxplima.uxmessentials.teleport.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.RequestRegistry;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFlags;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest.Transition;

/**
 * The {@code /tpa} and {@code /tpahere} use case: open a teleport request after the toggle/block/self
 * gates pass, store it, publish {@code TeleportRequested}, and notify both parties. The request's TTL is
 * computed from {@link TeleportSettings#requestTtl()} against an injected {@link Clock}, so expiry is
 * deterministic and the domain never reads the wall clock itself.
 *
 * <p>Vanish-awareness — a request to a player the requester cannot see is silently refused — is applied
 * by the adapter's {@code canSee} check before reaching this use case, mirroring the presence-context
 * integration; this layer enforces the toggle and block gates the domain owns.
 */
public final class RequestTeleport {

    private final RequestRegistry requests;
    private final TeleportFlags flags;
    private final PlayerNotifier notifier;
    private final DomainEventPublisher events;
    private final TeleportSettings settings;
    private final JailGate jail;
    private final Clock clock;

    public RequestTeleport(
            RequestRegistry requests,
            TeleportFlags flags,
            PlayerNotifier notifier,
            DomainEventPublisher events,
            TeleportSettings settings,
            JailGate jail,
            Clock clock) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.flags = Objects.requireNonNull(flags, "flags");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.jail = Objects.requireNonNull(jail, "jail");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Issue a request in {@code direction} from {@code requester} to {@code target}. */
    public Result<TeleportRequest, TeleportError> request(
            PlayerRef requester, PlayerRef target, RequestDirection direction) {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(direction, "direction");
        Optional<TeleportError> rejected = gate(requester, target);
        if (rejected.isPresent()) {
            return Result.err(rejected.get());
        }
        Instant expiresAt = clock.instant().plus(settings.requestTtl());
        Transition opened = TeleportRequest.open(requester, target, direction, expiresAt);
        requests.store(opened.request());
        events.publish(opened.event());
        announce(opened.request(), direction);
        return Result.ok(opened.request());
    }

    private Optional<TeleportError> gate(PlayerRef requester, PlayerRef target) {
        if (jail.isJailed(requester)) {
            notifier.send(requester, TeleportMessageKey.JAILED);
            return Optional.of(TeleportError.JAILED);
        }
        if (requester.equals(target)) {
            return Optional.of(TeleportError.SELF_REQUEST);
        }
        if (!flags.acceptsRequests(target)) {
            return Optional.of(TeleportError.TARGET_TOGGLED_OFF);
        }
        if (flags.hasBlocked(target, requester)) {
            return Optional.of(TeleportError.REQUESTER_BLOCKED);
        }
        return Optional.empty();
    }

    private void announce(TeleportRequest request, RequestDirection direction) {
        Map<String, String> requesterPh = Map.of("player", request.target().name());
        notifier.send(request.requester(), TeleportMessageKey.TPA_SENT, requesterPh);
        TeleportMessageKey key = direction == RequestDirection.TO_TARGET
                ? TeleportMessageKey.TPA_RECEIVED
                : TeleportMessageKey.TPA_HERE_RECEIVED;
        notifier.send(
                request.target(), key, Map.of("player", request.requester().name()));
    }
}
