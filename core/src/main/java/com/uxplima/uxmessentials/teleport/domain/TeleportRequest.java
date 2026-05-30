package com.uxplima.uxmessentials.teleport.domain;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.domain.event.TeleportEvent;
import com.uxplima.uxmessentials.teleport.domain.event.TeleportRequestAccepted;
import com.uxplima.uxmessentials.teleport.domain.event.TeleportRequestCancelled;
import com.uxplima.uxmessentials.teleport.domain.event.TeleportRequestDenied;
import com.uxplima.uxmessentials.teleport.domain.event.TeleportRequestExpired;
import com.uxplima.uxmessentials.teleport.domain.event.TeleportRequested;

/**
 * The {@code tpa} handshake aggregate: a request from one player to another, its direction, its TTL,
 * and its accept/deny lifecycle state. The aggregate is the sole owner of which transitions are legal
 * and which domain event each transition raises, so a use case never hand-rolls a state change.
 *
 * <p>Transitions are immutable: {@link #accept(Instant)}, {@link #deny()}, {@link #cancel()}, and
 * {@link #expire(Instant)} each return a {@link Transition} carrying the new aggregate state and the
 * single {@link TeleportEvent} the change produced. The TTL is checked against an injected
 * {@link Instant} (the clock is a port concern) so the domain stays deterministic and testable.
 */
public final class TeleportRequest {

    private final RequestId id;
    private final PlayerRef requester;
    private final PlayerRef target;
    private final RequestDirection direction;
    private final Instant expiresAt;
    private final RequestState state;

    private TeleportRequest(
            RequestId id,
            PlayerRef requester,
            PlayerRef target,
            RequestDirection direction,
            Instant expiresAt,
            RequestState state) {
        this.id = id;
        this.requester = requester;
        this.target = target;
        this.direction = direction;
        this.expiresAt = expiresAt;
        this.state = state;
    }

    /**
     * Open a new pending request. Rejects a self-request at construction so the aggregate can never
     * exist in that invalid state.
     */
    public static Transition open(
            PlayerRef requester, PlayerRef target, RequestDirection direction, Instant expiresAt) {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (requester.equals(target)) {
            throw new IllegalArgumentException("a teleport request cannot target the requester");
        }
        RequestId newId = RequestId.random();
        TeleportRequest request =
                new TeleportRequest(newId, requester, target, direction, expiresAt, RequestState.pending());
        return new Transition(request, new TeleportRequested(newId, requester, target, direction, expiresAt));
    }

    /** The target accepts; the request moves to {@code ACCEPTED} and the mover's warmup may begin. */
    public Transition accept(Instant now) {
        requirePendingAndFresh(now);
        return transition(state.accept(), new TeleportRequestAccepted(id, requester, target));
    }

    /** The target denies; the request terminates without burning the requester's cooldown by phase. */
    public Transition deny() {
        requirePending();
        return transition(state.deny(), new TeleportRequestDenied(id, requester, target));
    }

    /** The requester withdraws their own outstanding request. */
    public Transition cancel() {
        requirePending();
        return transition(state.cancel(), new TeleportRequestCancelled(id, requester, target));
    }

    /** The TTL elapsed; the request terminates and both parties are notified. */
    public Transition expire(Instant now) {
        requirePending();
        if (!hasExpired(now)) {
            throw new IllegalStateException("request has not reached its TTL yet");
        }
        return transition(state.expire(), new TeleportRequestExpired(id, requester, target));
    }

    /** True once {@code now} is at or past the request's TTL. */
    public boolean hasExpired(Instant now) {
        return !Objects.requireNonNull(now, "now").isBefore(expiresAt);
    }

    public RequestId id() {
        return id;
    }

    public PlayerRef requester() {
        return requester;
    }

    public PlayerRef target() {
        return target;
    }

    public RequestDirection direction() {
        return direction;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public RequestState.Phase phase() {
        return state.phase();
    }

    /** The player who actually teleports when this request is accepted. */
    public PlayerRef mover() {
        return direction.mover(requester, target);
    }

    /** The player the mover lands at on accept. */
    public PlayerRef anchor() {
        return direction.anchor(requester, target);
    }

    private void requirePending() {
        if (!state.isPending()) {
            throw new IllegalStateException("request is not pending, was " + state.phase());
        }
    }

    private void requirePendingAndFresh(Instant now) {
        requirePending();
        if (hasExpired(now)) {
            throw new IllegalStateException("request has already expired");
        }
    }

    private Transition transition(RequestState next, TeleportEvent event) {
        TeleportRequest moved = new TeleportRequest(id, requester, target, direction, expiresAt, next);
        return new Transition(moved, event);
    }

    /**
     * The result of a state change: the request in its new state plus the event the change raised.
     *
     * @param request the aggregate after the transition
     * @param event the domain event the transition produced
     */
    public record Transition(TeleportRequest request, TeleportEvent event) {

        public Transition {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(event, "event");
        }
    }
}
