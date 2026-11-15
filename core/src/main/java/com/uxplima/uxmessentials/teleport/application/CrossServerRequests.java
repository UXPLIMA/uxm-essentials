package com.uxplima.uxmessentials.teleport.application;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.RemoteTeleport.Step;
import com.uxplima.uxmessentials.teleport.application.port.RequestRegistry;
import com.uxplima.uxmessentials.teleport.application.port.RequestVisibility;
import com.uxplima.uxmessentials.teleport.application.port.TeleportNetwork;
import com.uxplima.uxmessentials.teleport.domain.CooldownStartPhase;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import com.uxplima.uxmessentials.teleport.domain.RequestId;
import com.uxplima.uxmessentials.teleport.domain.RequestState;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest;

/**
 * Teleport requests between players on two backends, on both ends.
 *
 * <p>The requester's backend runs the checks that concern the requester (jail, combat tag, self) and sends the
 * request; the target's backend judges it by everything that concerns the target, exactly as a local request is
 * judged: the target is online, not vanished from the requester (measured with the requester's see level, against
 * this backend's own vanish state), accepting requests and not blocking the requester. It then holds the request in
 * its registry under the id both backends know, so the target lists, accepts, denies and lets it expire with the
 * usual verbs, and each outcome goes back to the requester's backend. The requester reads exactly one line for how
 * the request was received (sent, or why not) and one for how it ended (accepted, denied, expired).
 *
 * <p>The backend the mover is on moves them once the request is accepted: the requester's for {@code /tpa}, the
 * target's for {@code /tpahere}. It checks the mover is still online, asks the hop gate (the jail, combat and
 * cooldown rules of a local request), stamps an {@code accept}-phase cooldown, runs the local warmup, and then sends
 * the mover through the shared hop, whose landing reads the anchor's live position on the anchor's backend.
 *
 * <p>The bus may deliver a frame twice and out of order. A repeated request is shown and answered once; every
 * request this backend sent leaves a tombstone once it ends, so a repeated answer is swallowed and a late delivery
 * of a request it already gave up on is withdrawn on the target's side. A request nobody answers within
 * {@link #ANSWER_GRACE} is given up as unreachable and withdrawn, and one still open a grace past its lifetime
 * expires here as well, so the requester is never left waiting.
 *
 * <p>Pure apart from its ports. The maps are concurrent because bus dispatch, command threads and timers all reach
 * them, and every ending is decided by one atomic removal, so it happens once.
 */
public final class CrossServerRequests implements RemoteRequests {

    /** How long a peer may take to answer, on top of a request's own lifetime. */
    public static final Duration ANSWER_GRACE = Duration.ofSeconds(5);

    /** A request this backend's player sent to a player on another backend, until it ends. */
    private record Outgoing(
            RequestId id,
            PlayerRef requester,
            PlayerRef target,
            String targetServer,
            RequestDirection direction,
            boolean delivered) {

        Outgoing asDelivered() {
            return new Outgoing(id, requester, target, targetServer, direction, true);
        }
    }

    /** How a sent request ended, which decides how a late answer for it is met. */
    private enum Ending {
        /** The target's backend settled it: any later answer is a repeat. */
        ANSWERED,
        /** This backend took it back: a late delivery is withdrawn on the target's side. */
        WITHDRAWN
    }

    private final TeleportNetwork network;
    private final NetworkPresence presence;
    private final PlayerLookup players;
    private final RequestRegistry requests;
    private final RequestTeleport requestTeleport;
    private final RequestVisibility visibility;
    private final PlayerHops hops;
    private final TeleportEngine engine;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final TeleportSettings settings;
    private final Scheduler scheduler;
    private final Consumer<PlayerRef> autoAccept;
    private final Logger log;

    private final Map<RequestId, Outgoing> outgoing = new ConcurrentHashMap<>();
    private final Map<RequestId, Ending> ended = new ConcurrentHashMap<>();
    private final Map<RequestId, String> incoming = new ConcurrentHashMap<>();
    private final Set<RequestId> received = ConcurrentHashMap.newKeySet();

    /**
     * @param autoAccept accepts for {@code target} on their own thread when their {@code /tpauto} is on, called once a
     *     remote request to them is held here; the same step a local request takes
     */
    public CrossServerRequests(
            TeleportNetwork network,
            NetworkPresence presence,
            PlayerLookup players,
            RequestRegistry requests,
            RequestTeleport requestTeleport,
            RequestVisibility visibility,
            PlayerHops hops,
            TeleportEngine engine,
            Notifier notifier,
            DomainEventPublisher events,
            TeleportSettings settings,
            Scheduler scheduler,
            Consumer<PlayerRef> autoAccept,
            Logger log) {
        this.network = Objects.requireNonNull(network, "network");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.players = Objects.requireNonNull(players, "players");
        this.requests = Objects.requireNonNull(requests, "requests");
        this.requestTeleport = Objects.requireNonNull(requestTeleport, "requestTeleport");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.hops = Objects.requireNonNull(hops, "hops");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.autoAccept = Objects.requireNonNull(autoAccept, "autoAccept");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** True while requests may reach players on other backends. */
    public boolean enabled() {
        return hops.enabled();
    }

    /**
     * Send {@code requester}'s request to the player named {@code targetName}, who is not on this backend. The
     * requester reads the target-offline line when no live backend has such a player, or when requests to other
     * backends are off.
     */
    public void request(PlayerRef requester, String targetName, RequestDirection direction) {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(direction, "direction");
        Optional<OnlinePlayer> found = presence.findByName(targetName)
                .filter(where -> !where.server().equals(network.localServer()))
                .filter(where -> presence.isLive(where.server()));
        if (found.isEmpty() || !enabled()) {
            notifier.send(requester, TeleportError.TARGET_OFFLINE.messageKey(), Map.of("player", targetName));
            return;
        }
        PlayerRef target = found.get().player();
        String server = found.get().server();
        if (requestTeleport.refuseRequester(requester, target).isPresent()) {
            return;
        }
        if (settings.singleRequestDisplace()) {
            // The target's backend replaces the earlier request without a word, as a local one does, so it ends
            // here the same way rather than lapsing later with a line about it.
            outgoing.values().stream()
                    .filter(earlier -> earlier.requester().equals(requester)
                            && earlier.target().equals(target))
                    .forEach(earlier -> {
                        if (outgoing.remove(earlier.id(), earlier)) {
                            end(earlier.id(), Ending.WITHDRAWN);
                        }
                    });
        }
        RequestId id = RequestId.random();
        Duration ttl = settings.requestTtl();
        outgoing.put(id, new Outgoing(id, requester, target, server, direction, false));
        network.send(new RemoteTeleport.Request(
                network.localServer(), id, requester, target, server, direction, visibility.seeLevel(requester), ttl));
        later(ANSWER_GRACE, () -> giveUpUndelivered(id));
        later(ttl.plus(ANSWER_GRACE), () -> lapse(id));
    }

    /** Act on a message a peer sent; one that is not for this backend is dropped. */
    public void onMessage(RemoteTeleport message) {
        Objects.requireNonNull(message, "message");
        switch (message) {
            case RemoteTeleport.Request request -> onRequest(request);
            case RemoteTeleport.Answer answer -> onAnswer(answer);
            case RemoteTeleport.StaffMove staff -> {
                // A staff teleport is handled by CrossServerStaffTeleport.
            }
        }
    }

    @Override
    public boolean isRemote(RequestId id) {
        return incoming.containsKey(id);
    }

    @Override
    public void accepted(TeleportRequest request) {
        String requesterServer = incoming.remove(request.id());
        if (requesterServer == null) {
            return;
        }
        answer(requesterServer, request.id(), Step.ACCEPTED, request.target());
        if (request.direction() == RequestDirection.TO_REQUESTER) {
            moveAccepted(request.target(), request.requester(), requesterServer);
        }
    }

    @Override
    public void denied(TeleportRequest request) {
        String requesterServer = incoming.remove(request.id());
        if (requesterServer != null) {
            answer(requesterServer, request.id(), Step.DENIED, request.target());
        }
    }

    @Override
    public void expired(TeleportRequest request) {
        String requesterServer = incoming.remove(request.id());
        if (requesterServer != null) {
            answer(requesterServer, request.id(), Step.EXPIRED, request.target());
        }
    }

    @Override
    public boolean cancelOutgoing(PlayerRef requester) {
        Objects.requireNonNull(requester, "requester");
        Optional<Outgoing> open = outgoing.values().stream()
                .filter(sent -> sent.requester().equals(requester))
                .findFirst();
        if (open.isEmpty() || !outgoing.remove(open.get().id(), open.get())) {
            return false;
        }
        Outgoing sent = open.get();
        end(sent.id(), Ending.WITHDRAWN);
        notifier.send(
                requester,
                TeleportMessageKey.TPA_CANCELLED,
                Map.of("player", sent.target().name()));
        answer(sent.targetServer(), sent.id(), Step.WITHDRAWN, requester);
        return true;
    }

    // The target's side.

    private void onRequest(RemoteTeleport.Request request) {
        if (!request.targetServer().equals(network.localServer()) || !received.add(request.id())) {
            return;
        }
        later(request.ttl().plus(ANSWER_GRACE), () -> received.remove(request.id()));
        PlayerRef target = request.target();
        if (!players.isOnline(target.uuid())) {
            answer(request.fromServer(), request.id(), Step.OFFLINE, target);
            return;
        }
        Runnable gone = () -> answer(request.fromServer(), request.id(), Step.OFFLINE, target);
        try {
            // The toggle lives on the player and the line goes to them, so the request is judged on their thread.
            scheduler.onEntity(target, () -> receive(request), gone);
        } catch (RuntimeException unscheduled) {
            gone.run();
        }
    }

    private void receive(RemoteTeleport.Request request) {
        PlayerRef requester = request.requester();
        PlayerRef target = request.target();
        if (visibility.hides(target, requester, request.seeLevel())) {
            // Indistinguishable from a player who is not online, as a vanished player is to a local requester.
            answer(request.fromServer(), request.id(), Step.OFFLINE, target);
            return;
        }
        Optional<TeleportError> declined = requestTeleport.targetRefusal(requester, target);
        if (declined.isPresent()) {
            Step step = declined.get() == TeleportError.TARGET_TOGGLED_OFF ? Step.TOGGLED_OFF : Step.BLOCKED;
            answer(request.fromServer(), request.id(), step, target);
            return;
        }
        incoming.put(request.id(), request.fromServer());
        requestTeleport.receive(request.id(), requester, target, request.direction(), request.ttl());
        answer(request.fromServer(), request.id(), Step.DELIVERED, target);
        // A request another one displaced from the registry never reaches an outcome here, so its entry is dropped.
        later(request.ttl().plus(ANSWER_GRACE), () -> incoming.remove(request.id()));
        autoAccept.accept(target);
    }

    /** The requester's backend took the request back: it leaves the target's list if it is still waiting there. */
    private void withdraw(RequestId id) {
        if (incoming.remove(id) == null) {
            return;
        }
        requests.byId(id)
                .filter(request -> request.phase() == RequestState.Phase.PENDING)
                .ifPresent(request -> {
                    requests.remove(id);
                    events.publish(request.cancel().event());
                });
    }

    // The requester's side.

    private void onAnswer(RemoteTeleport.Answer answer) {
        if (!answer.toServer().equals(network.localServer())) {
            return;
        }
        RequestId id = new RequestId(answer.id());
        switch (answer.step()) {
            case WITHDRAWN -> withdraw(id);
            case DELIVERED -> delivered(id, answer);
            case OFFLINE -> conclude(id, sent -> tell(sent, TeleportMessageKey.TPA_TARGET_OFFLINE));
            case TOGGLED_OFF -> conclude(id, sent -> tell(sent, TeleportMessageKey.TPA_TOGGLED_OFF));
            case BLOCKED -> conclude(id, sent -> tell(sent, TeleportMessageKey.TPA_BLOCKED));
            case DENIED -> conclude(id, sent -> tell(sent, TeleportMessageKey.TPA_DENIED_REMOTE));
            case EXPIRED -> conclude(id, sent -> tell(sent, TeleportMessageKey.TPA_EXPIRED));
            case ACCEPTED ->
                conclude(id, sent -> {
                    tell(sent, TeleportMessageKey.TPA_ACCEPTED);
                    if (sent.direction() == RequestDirection.TO_TARGET) {
                        moveAccepted(sent.requester(), sent.target(), sent.targetServer());
                    }
                });
            case MOVED, SENT, REFUSED, SUBJECT_OFFLINE, DESTINATION_OFFLINE -> {
                // A staff teleport's answer, handled by CrossServerStaffTeleport.
            }
        }
    }

    private void delivered(RequestId id, RemoteTeleport.Answer answer) {
        Outgoing sent = outgoing.get(id);
        if (sent == null) {
            if (ended.get(id) == Ending.WITHDRAWN) {
                // Shown to the target after this backend gave up on it: take it off their list again.
                answer(answer.fromServer(), id, Step.WITHDRAWN, answer.about());
            }
            return;
        }
        if (!sent.delivered() && outgoing.replace(id, sent, sent.asDelivered())) {
            tell(sent, TeleportMessageKey.TPA_SENT);
        }
    }

    /** End the request with an answer from the target's backend; only the first answer to arrive acts. */
    private void conclude(RequestId id, Consumer<Outgoing> outcome) {
        Outgoing sent = outgoing.remove(id);
        if (sent == null) {
            return;
        }
        end(id, Ending.ANSWERED);
        outcome.accept(sent);
    }

    private void giveUpUndelivered(RequestId id) {
        Outgoing sent = outgoing.get(id);
        if (sent == null || sent.delivered() || !outgoing.remove(id, sent)) {
            return;
        }
        end(id, Ending.WITHDRAWN);
        tell(sent, TeleportMessageKey.TPA_TARGET_OFFLINE);
        answer(sent.targetServer(), id, Step.WITHDRAWN, sent.requester());
    }

    private void lapse(RequestId id) {
        Outgoing sent = outgoing.remove(id);
        if (sent == null) {
            return;
        }
        end(id, Ending.WITHDRAWN);
        tell(sent, TeleportMessageKey.TPA_EXPIRED);
        answer(sent.targetServer(), id, Step.WITHDRAWN, sent.requester());
    }

    // The mover's side.

    /**
     * Move {@code mover}, a player on this backend, to {@code anchor} on {@code server} for an accepted request. The
     * gate runs before the warmup, so a refused hop does not make the player stand still first, and the send checks
     * once more that the player is still here, since a hop for a player who left would only time out.
     */
    private void moveAccepted(PlayerRef mover, PlayerRef anchor, String server) {
        if (!enabled()) {
            return;
        }
        offTick(() -> {
            if (!players.isOnline(mover.uuid()) || !hops.admit(mover, HopKind.PLAYER_REQUEST, anchor, server)) {
                return;
            }
            engine.stampForPhase(mover, CooldownStartPhase.ACCEPT);
            scheduler.onEntity(
                    mover,
                    () -> engine.beginHopWarmup(
                            mover,
                            TeleportKind.REQUEST,
                            () -> offTick(() -> {
                                if (players.isOnline(mover.uuid())) {
                                    hops.send(mover, HopKind.PLAYER_REQUEST, anchor, server);
                                }
                            })));
        });
    }

    private void tell(Outgoing sent, TeleportMessageKey key) {
        notifier.send(sent.requester(), key, Map.of("player", sent.target().name()));
    }

    private void answer(String toServer, RequestId id, Step step, PlayerRef about) {
        network.send(new RemoteTeleport.Answer(network.localServer(), id.value(), step, toServer, about, ""));
    }

    /** Remember how a sent request ended for as long as an answer about it may still arrive. */
    private void end(RequestId id, Ending ending) {
        ended.put(id, ending);
        later(settings.requestTtl().plus(ANSWER_GRACE).plus(ANSWER_GRACE), () -> ended.remove(id, ending));
    }

    private void later(Duration delay, Runnable task) {
        try {
            scheduler.asyncAfter(delay, task);
        } catch (RuntimeException refused) {
            // A stopping backend drops its timers; only bookkeeping and a lapse line are lost with them.
            log.warn("event=cross_server_tpa_timer_refused reason={}", refused.toString());
        }
    }

    private void offTick(Runnable work) {
        try {
            scheduler.async(work);
        } catch (RuntimeException refused) {
            // Nothing has been spent on the hop yet, so a stopping backend may drop it.
            log.warn("event=cross_server_tpa_async_refused reason={}", refused.toString());
        }
    }
}
