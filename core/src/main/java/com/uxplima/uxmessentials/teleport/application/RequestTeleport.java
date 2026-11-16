package com.uxplima.uxmessentials.teleport.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.teleport.application.port.CombatGate;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.RequestRegistry;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFlags;
import com.uxplima.uxmessentials.teleport.domain.CooldownStartPhase;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import com.uxplima.uxmessentials.teleport.domain.RequestId;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportMessageKey;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest.Transition;

/**
 * The {@code /tpa} and {@code /tpahere} use case: open a teleport request after the toggle/block/self
 * gates pass, store it, publish {@code TeleportRequested}, and notify both parties. The request's TTL is
 * computed from {@link TeleportSettings#requestTtl()} against an injected {@link Clock}, so expiry is
 * deterministic and the domain never reads the wall clock itself. Under the {@code request} cooldown phase the
 * requester's cooldown starts once the request is open, so a request the target refuses stamps nothing.
 *
 * <p>Vanish-awareness (a request to a player the requester cannot see is silently refused) is applied
 * by the adapter's {@code canSee} check before reaching this use case, mirroring the presence-context
 * integration; this layer enforces the toggle and block gates the domain owns.
 */
public final class RequestTeleport {

    private final RequestRegistry requests;
    private final TeleportFlags flags;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final TeleportSettings settings;
    private final JailGate jail;
    private final CombatGate combat;
    private final TeleportEngine engine;
    private final Clock clock;

    public RequestTeleport(
            RequestRegistry requests,
            TeleportFlags flags,
            Notifier notifier,
            DomainEventPublisher events,
            TeleportSettings settings,
            JailGate jail,
            TeleportEngine engine,
            Clock clock) {
        this(requests, flags, notifier, events, settings, jail, CombatGate.NEVER, engine, clock);
    }

    public RequestTeleport(
            RequestRegistry requests,
            TeleportFlags flags,
            Notifier notifier,
            DomainEventPublisher events,
            TeleportSettings settings,
            JailGate jail,
            CombatGate combat,
            TeleportEngine engine,
            Clock clock) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.flags = Objects.requireNonNull(flags, "flags");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.jail = Objects.requireNonNull(jail, "jail");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.engine = Objects.requireNonNull(engine, "engine");
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
        engine.stampForPhase(requester, CooldownStartPhase.REQUEST);
        return Result.ok(opened.request());
    }

    private Optional<TeleportError> gate(PlayerRef requester, PlayerRef target) {
        Optional<TeleportError> refused = refuseRequester(requester, target);
        if (refused.isPresent()) {
            return refused;
        }
        Optional<TeleportError> declined = targetRefusal(requester, target);
        if (declined.isPresent()) {
            return reject(requester, declined.get());
        }
        return Optional.empty();
    }

    /**
     * The checks the requester's own backend runs before a request leaves it, local or not: the requester is not
     * jailed, not combat-tagged and not asking themselves. A refusal is told to the requester and returned.
     */
    public Optional<TeleportError> refuseRequester(PlayerRef requester, PlayerRef target) {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(target, "target");
        if (jail.isJailed(requester)) {
            return reject(requester, TeleportError.JAILED);
        }
        if (combat.isTagged(requester)) {
            // Without this, a player blocked from /home simply asks a friend for a /tpa and is pulled out
            // of the fight anyway, which would leave the whole gate decorative.
            return reject(requester, TeleportError.COMBAT_TAGGED);
        }
        if (requester.equals(target)) {
            return reject(requester, TeleportError.SELF_REQUEST);
        }
        return Optional.empty();
    }

    /**
     * Whether {@code target}, a player on this backend, refuses requests from {@code requester}: their toggle is
     * off, or they blocked the requester. Nobody is told here, since a requester on another backend hears it from
     * there.
     */
    public Optional<TeleportError> targetRefusal(PlayerRef requester, PlayerRef target) {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(target, "target");
        if (!flags.acceptsRequests(target)) {
            return Optional.of(TeleportError.TARGET_TOGGLED_OFF);
        }
        if (flags.hasBlocked(target, requester)) {
            return Optional.of(TeleportError.REQUESTER_BLOCKED);
        }
        return Optional.empty();
    }

    /**
     * Hold a request a player on another backend sent to {@code target} on this one, after its checks passed: store
     * it under the id both backends know, so the target resolves it like any other, and show it to the target. The
     * requester's own line comes from their backend.
     */
    public TeleportRequest receive(
            RequestId id, PlayerRef requester, PlayerRef target, RequestDirection direction, Duration ttl) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(ttl, "ttl");
        Transition opened = TeleportRequest.open(
                id, requester, target, direction, clock.instant().plus(ttl));
        requests.store(opened.request());
        events.publish(opened.event());
        notifier.send(target, receivedKey(direction), Map.of("player", requester.name()));
        return opened.request();
    }

    // Every gate rejection tells the requester why with the error's own catalog key, so a silent
    // /tpa (self, toggled-off, blocked, jailed) can't happen: the reason and its text never split.
    private Optional<TeleportError> reject(PlayerRef requester, TeleportError error) {
        notifier.send(requester, error.messageKey());
        return Optional.of(error);
    }

    private void announce(TeleportRequest request, RequestDirection direction) {
        Map<String, String> requesterPh = Map.of("player", request.target().name());
        notifier.send(request.requester(), TeleportMessageKey.TPA_SENT, requesterPh);
        notifier.send(
                request.target(),
                receivedKey(direction),
                Map.of("player", request.requester().name()));
    }

    private static TeleportMessageKey receivedKey(RequestDirection direction) {
        return direction == RequestDirection.TO_TARGET
                ? TeleportMessageKey.TPA_RECEIVED
                : TeleportMessageKey.TPA_HERE_RECEIVED;
    }
}
