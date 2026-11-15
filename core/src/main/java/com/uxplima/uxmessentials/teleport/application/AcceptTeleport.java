package com.uxplima.uxmessentials.teleport.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.teleport.application.port.RequestRegistry;
import com.uxplima.uxmessentials.teleport.domain.CooldownStartPhase;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest.Transition;

/**
 * Resolves a pending {@code tpa} request ({@code /tpaccept}, {@code /tpdeny}, {@code /tpcancel}) and
 * the TTL expiry sweep. Accept drives the mover's warmup through {@link TeleportEngine} (anchored on the
 * acceptor's live position, supplied by the adapter); deny, cancel, and expire terminate the request
 * and, crucially, never burn the requester's cooldown under the {@code accept} or {@code teleport} start
 * phases: only an accepted request stamps for the {@code accept} phase.
 */
public final class AcceptTeleport {

    private final RequestRegistry requests;
    private final TeleportEngine engine;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final RemoteRequests remote;

    public AcceptTeleport(
            RequestRegistry requests,
            TeleportEngine engine,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock) {
        this(requests, engine, notifier, events, clock, RemoteRequests.NONE);
    }

    /** {@code remote} carries the outcome of a request sent from another backend back to it. */
    public AcceptTeleport(
            RequestRegistry requests,
            TeleportEngine engine,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock,
            RemoteRequests remote) {
        this.remote = Objects.requireNonNull(remote, "remote");
        this.requests = Objects.requireNonNull(requests, "requests");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Accept the oldest request aimed at {@code target}. {@code anchorPosition} is the live position the
     * mover lands at (the acceptor's location for {@code /tpa}, the requester's for {@code /tpahere}),
     * read by the adapter on the right region thread.
     */
    public Result<Unit, TeleportError> accept(PlayerRef target, Position anchorPosition) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(anchorPosition, "anchorPosition");
        Optional<TeleportRequest> pending = oldestPending(target);
        if (pending.isEmpty()) {
            return noPending(target);
        }
        TeleportRequest request = pending.get();
        Transition accepted = request.accept(clock.instant());
        finish(accepted);
        if (remote.isRemote(request.id())) {
            // The requester's backend tells them and, for /tpa, runs their warmup; the mover is not here.
            remote.accepted(accepted.request());
            return Result.ok();
        }
        beginMoverWarmup(accepted.request(), anchorPosition);
        notifier.send(request.requester(), TeleportMessageKey.TPA_ACCEPTED, Map.of("player", target.name()));
        return Result.ok();
    }

    /** Deny the oldest request aimed at {@code target}; the requester's cooldown is untouched. */
    public Result<Unit, TeleportError> deny(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        Optional<TeleportRequest> pending = oldestPending(target);
        if (pending.isEmpty()) {
            return noPending(target);
        }
        TeleportRequest request = pending.get();
        Transition denied = request.deny();
        finish(denied);
        notifier.send(
                target,
                TeleportMessageKey.TPA_DENIED,
                Map.of("player", request.requester().name()));
        if (remote.isRemote(request.id())) {
            remote.denied(denied.request());
        }
        return Result.ok();
    }

    /** Cancel {@code requester}'s own outgoing request. */
    public Result<Unit, TeleportError> cancel(PlayerRef requester) {
        Objects.requireNonNull(requester, "requester");
        Optional<TeleportRequest> outgoing = requests.outgoing(requester);
        if (outgoing.isEmpty()) {
            return remote.cancelOutgoing(requester) ? Result.ok() : noPending(requester);
        }
        TeleportRequest request = outgoing.get();
        finish(request.cancel());
        notifier.send(
                requester,
                TeleportMessageKey.TPA_CANCELLED,
                Map.of("player", request.target().name()));
        return Result.ok();
    }

    /** Expire a request whose TTL elapsed, notifying both parties. Driven by the adapter's sweep. */
    public void expireIfDue(TeleportRequest request) {
        Objects.requireNonNull(request, "request");
        Instant now = clock.instant();
        if (!request.hasExpired(now)
                || request.phase() != com.uxplima.uxmessentials.teleport.domain.RequestState.Phase.PENDING) {
            return;
        }
        Transition expired = request.expire(now);
        finish(expired);
        if (remote.isRemote(request.id())) {
            notifier.send(
                    request.target(),
                    TeleportMessageKey.TPA_EXPIRED,
                    Map.of("player", request.requester().name()));
            remote.expired(expired.request());
            return;
        }
        notifier.send(
                request.requester(),
                TeleportMessageKey.TPA_EXPIRED,
                Map.of("player", request.target().name()));
        notifier.send(
                request.target(),
                TeleportMessageKey.TPA_EXPIRED,
                Map.of("player", request.requester().name()));
    }

    private void beginMoverWarmup(TeleportRequest request, Position anchorPosition) {
        PlayerRef mover = request.mover();
        engine.stampForPhase(mover, CooldownStartPhase.ACCEPT);
        Destination destination = Destination.following(request.anchor(), anchorPosition);
        engine.beginGatedWarmup(mover, destination, TeleportKind.REQUEST);
    }

    // /tpaccept, /tpdeny and /tpcancel with nothing waiting used to do nothing at all; tell the
    // invoking player there is no pending request so the verb never fails silently.
    private Result<Unit, TeleportError> noPending(PlayerRef invoker) {
        notifier.send(invoker, TeleportError.NO_PENDING_REQUEST.messageKey());
        return Result.err(TeleportError.NO_PENDING_REQUEST);
    }

    private void finish(Transition transition) {
        requests.remove(transition.request().id());
        events.publish(transition.event());
    }

    /**
     * The oldest request aimed at {@code target} that is still within its TTL. The sweep runs only once a second, so
     * a request past its TTL can still be held here; it is expired now rather than accepted, which would throw.
     */
    private Optional<TeleportRequest> oldestPending(PlayerRef target) {
        for (TeleportRequest request : requests.pendingFor(target)) {
            if (!request.hasExpired(clock.instant())) {
                return Optional.of(request);
            }
            expireIfDue(request);
        }
        return Optional.empty();
    }
}
