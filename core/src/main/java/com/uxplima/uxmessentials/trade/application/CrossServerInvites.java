package com.uxplima.uxmessentials.trade.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.port.TradeBus;
import com.uxplima.uxmessentials.trade.application.port.TradePresence;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The invite step of a cross-server trade, on both ends: the inviter's backend tracks each invite it sent until an
 * answer or the expiry settles it, and the target's backend holds each invite it showed until its player accepts,
 * denies, or lets it expire. Nothing opens before the target accepts, which is how a same-server request behaves, and
 * the target's window opens only once the inviter's backend confirms with {@code OPENED} that its own side is open,
 * so the target can never stake into a trade nobody is on the other end of.
 *
 * <p>An {@code INVITE} names its target only by name and reaches every peer, so every peer answers it: the one that
 * hosts the player with {@code DELIVERED} or {@code BUSY}, every other one with {@code NOT_FOUND}. The inviter's
 * backend reports "not found" early only once the peers that said so cover every server id in the live roster; an
 * empty roster (right after a restart) never concludes early. A roster that still lists a crashed peer, or a peer on
 * an older build or with cross-server trading off, never completes the cover, and the invite resolves at the expiry
 * instead, as not found when any peer said so. A failed invite (not found, busy, or never answered) gives the
 * cooldown stamp back, so only an invite that reached the target costs the inviter a wait.
 *
 * <p>The bus may deliver a frame twice (two transports at once) and out of order (each frame dispatched on its own
 * task). Every settled invite therefore leaves a tombstone for one invite lifetime: a repeat of an answer it already
 * settled on is swallowed, and an accept or delivery for an invite that ended without opening is answered with
 * {@code ABORT} whether or not its inviter is still here. The target side ignores a repeated {@code INVITE}.
 *
 * <p>Pure apart from its ports: the bus, this backend's presence, the live peer roster and the clock. The maps are
 * concurrent because bus dispatch, command threads and the expiry sweep reach them from different threads.
 */
@NullMarked
public final class CrossServerInvites {

    /** The id an {@code INVITE} carries for its not-yet-resolved target, and a {@code NOT_FOUND} for the absent one. */
    public static final UUID UNRESOLVED = new UUID(0L, 0L);

    /**
     * How much longer an answer may take. The inviter waits this much past the expiry for a delivered invite, since
     * the target's own expiry starts when the invite reaches it; the target waits this long after accepting for the
     * inviter's {@code OPENED} before it gives the trade up.
     */
    public static final Duration ANSWER_GRACE = Duration.ofSeconds(5);

    /** An invite this backend sent and has not seen settled. {@code target} is known once a peer delivered it. */
    record Outgoing(
            TradeId tradeId,
            PlayerRef inviter,
            String targetName,
            Instant sentAt,
            Instant cooldownStamp,
            @Nullable PlayerRef target,
            Set<String> notFoundFrom) {

        Outgoing {
            notFoundFrom = Set.copyOf(notFoundFrom);
        }

        boolean delivered() {
            return target != null;
        }

        Outgoing deliveredTo(PlayerRef found) {
            return new Outgoing(tradeId, inviter, targetName, sentAt, cooldownStamp, found, notFoundFrom);
        }

        Outgoing notFoundOn(String server) {
            Set<String> servers = new HashSet<>(notFoundFrom);
            servers.add(server);
            return new Outgoing(tradeId, inviter, targetName, sentAt, cooldownStamp, target, servers);
        }
    }

    /** How a settled invite ended, which decides how a late or repeated answer for it is met. */
    private enum Ending {
        /** Both sides went on to the trade: every later answer is a repeat and is swallowed. */
        OPENED,
        /** It ended without a trade: a late accept or delivery is withdrawn with {@code ABORT}. */
        CLOSED
    }

    private record Tombstone(Ending ending, Instant until) {}

    /**
     * An invite shown to a player on this backend, waiting for their accept or deny.
     *
     * @param tradeId the trade the invite opens
     * @param inviter the player who sent it, on another backend
     * @param target the local player it was shown to
     * @param inviterServer the inviter's backend, which the trade's escrow signals go to
     * @param receivedAt when it arrived here, for the expiry
     */
    public record Incoming(
            TradeId tradeId, PlayerRef inviter, PlayerRef target, String inviterServer, Instant receivedAt) {

        public Incoming {
            Objects.requireNonNull(tradeId, "tradeId");
            Objects.requireNonNull(inviter, "inviter");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(inviterServer, "inviterServer");
            Objects.requireNonNull(receivedAt, "receivedAt");
        }
    }

    /** An accepted invite whose target waits for the inviter's {@code OPENED}. */
    private record Awaiting(Incoming invite, Instant acceptedAt) {}

    /** How an invite this backend sent was settled, for the line its inviter reads. */
    public sealed interface Reply {

        /** The player who sent the invite. */
        PlayerRef inviter();
    }

    /**
     * The target accepted and the inviter is free: open the inviter's window for {@code tradeId}, then confirm with
     * {@link #opened}.
     */
    public record Accepted(TradeId tradeId, PlayerRef inviter, PlayerRef target, String targetServer)
            implements Reply {}

    /** The target accepted, but the inviter was in another trade by then; the target was told the trade is off. */
    public record AcceptedWhileBusy(PlayerRef inviter, PlayerRef target) implements Reply {}

    /** The target denied the invite. */
    public record Denied(PlayerRef inviter, PlayerRef target) implements Reply {}

    /** The target is already in a trade. */
    public record Busy(PlayerRef inviter, PlayerRef target) implements Reply {}

    /** No backend has a player by that name online. */
    public record NotFound(PlayerRef inviter, String targetName) implements Reply {}

    /** The target saw the invite and let it expire. */
    public record Expired(PlayerRef inviter, String targetName) implements Reply {}

    /** No backend answered within the expiry. */
    public record TimedOut(PlayerRef inviter, String targetName) implements Reply {}

    /** How an accept or deny matched against the invites shown to a player. */
    public enum Status {
        /** A live invite was found, removed and answered. */
        MATCHED,
        /** An invite was found but had expired; it was removed and nothing was answered. */
        EXPIRED,
        /** No invite from the named (or any) player is waiting. */
        NONE,
        /** The player is already in a trade, so the invite was left waiting. */
        TRADING
    }

    /** The result of {@link #accept} or {@link #deny}: the status and, when found, the invite. */
    public record Answer(Status status, @Nullable Incoming invite) {

        public Answer {
            Objects.requireNonNull(status, "status");
        }
    }

    private final TradeBus bus;
    private final TradePresence presence;
    private final Supplier<Set<String>> livePeers;
    private final TradeCooldown cooldown;
    private final Clock clock;
    private final Duration ttl;
    private final ConcurrentHashMap<TradeId, Outgoing> outgoing = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TradeId, Tombstone> tombstones = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Incoming>> incomingByTarget =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TradeId, Awaiting> awaitingOpen = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TradeId, Instant> seenInvites = new ConcurrentHashMap<>();

    /**
     * @param livePeers the server ids of the peer backends live right now, each of which answers every invite
     * @param ttl how long an invite stays acceptable, the same window a same-server request has
     */
    public CrossServerInvites(
            TradeBus bus,
            TradePresence presence,
            Supplier<Set<String>> livePeers,
            TradeCooldown cooldown,
            Clock clock,
            Duration ttl) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.livePeers = Objects.requireNonNull(livePeers, "livePeers");
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("invite ttl must be positive: " + ttl);
        }
    }

    /** The delays after a send at which {@link #expire} can settle it: the expiry, and the expiry plus the grace. */
    public List<Duration> deadlines() {
        return List.of(ttl, ttl.plus(ANSWER_GRACE));
    }

    // Inviter side.

    /**
     * Send {@code inviter}'s invite to whoever is online as {@code targetName} on another backend, replacing an earlier
     * invite of theirs to the same name, and start their cooldown.
     */
    public TradeId invite(PlayerRef inviter, String targetName) {
        Objects.requireNonNull(inviter, "inviter");
        Objects.requireNonNull(targetName, "targetName");
        for (Outgoing previous : List.copyOf(outgoing.values())) {
            if (previous.inviter().equals(inviter)
                    && previous.targetName().equalsIgnoreCase(targetName)
                    && outgoing.remove(previous.tradeId()) != null) {
                bury(previous.tradeId(), Ending.CLOSED);
            }
        }
        TradeId id = TradeId.newId();
        Instant stamp = cooldown.stamp(inviter.uuid());
        outgoing.put(id, new Outgoing(id, inviter, targetName, clock.instant(), stamp, null, Set.of()));
        bus.send(new TradeSignal(
                id, TradeSignalType.INVITE, inviter, new PlayerRef(UNRESOLVED, targetName), bus.localServer()));
        return id;
    }

    /**
     * Read a peer's answer to an invite this backend may have sent. Returns how the invite settled when this answer
     * settles it; a {@code DELIVERED} and a {@code NOT_FOUND} that does not complete the cover only move it along. An
     * answer for an invite this backend never sent is someone else's and is ignored.
     */
    public Optional<Reply> onReply(TradeSignal signal) {
        Objects.requireNonNull(signal, "signal");
        TradeSignalType type = signal.type();
        if (type != TradeSignalType.DELIVERED
                && type != TradeSignalType.ACCEPT
                && type != TradeSignalType.DECLINE
                && type != TradeSignalType.BUSY
                && type != TradeSignalType.NOT_FOUND) {
            return Optional.empty();
        }
        Tombstone settled = tombstone(signal.tradeId());
        if (settled != null) {
            if (settled.ending() == Ending.CLOSED
                    && (type == TradeSignalType.DELIVERED || type == TradeSignalType.ACCEPT)) {
                withdraw(signal.tradeId(), signal.to(), signal.from());
            }
            return Optional.empty();
        }
        return switch (type) {
            case DELIVERED -> {
                outgoing.computeIfPresent(
                        signal.tradeId(), (id, sent) -> sent.delivered() ? sent : sent.deliveredTo(signal.from()));
                yield Optional.empty();
            }
            case ACCEPT -> accepted(signal);
            case DECLINE -> settle(signal.tradeId(), false).map(sent -> new Denied(sent.inviter(), signal.from()));
            case BUSY -> settle(signal.tradeId(), true).map(sent -> new Busy(sent.inviter(), signal.from()));
            default -> notFound(signal.tradeId(), signal.originServer());
        };
    }

    /** The inviter's window for an accepted invite is open: tell the target's backend to open its own. */
    public void opened(Accepted accepted) {
        Objects.requireNonNull(accepted, "accepted");
        bus.send(new TradeSignal(
                accepted.tradeId(), TradeSignalType.OPENED, accepted.inviter(), accepted.target(), bus.localServer()));
    }

    /**
     * Settle every invite whose wait is over as of now: expired when it was delivered, otherwise unanswered or not
     * found. Also drops tombstones and remembered invites older than one invite lifetime.
     */
    public List<Reply> expire() {
        Instant now = clock.instant();
        tombstones.values().removeIf(stone -> !now.isBefore(stone.until()));
        seenInvites.values().removeIf(seen -> !now.isBefore(seen.plus(ttl).plus(ANSWER_GRACE)));
        List<Reply> settled = new ArrayList<>();
        for (TradeId id : List.copyOf(outgoing.keySet())) {
            AtomicReference<@Nullable Outgoing> due = new AtomicReference<>();
            // The deadline is read inside the update, so a delivery that lands meanwhile moves it instead of losing it.
            outgoing.computeIfPresent(id, (key, sent) -> {
                Instant deadline = sent.sentAt().plus(sent.delivered() ? ttl.plus(ANSWER_GRACE) : ttl);
                if (now.isBefore(deadline)) {
                    return sent;
                }
                due.set(sent);
                return null;
            });
            Outgoing sent = due.get();
            if (sent == null) {
                continue;
            }
            bury(id, Ending.CLOSED);
            PlayerRef target = sent.target();
            if (target != null) {
                withdraw(id, sent.inviter(), target);
                settled.add(new Expired(sent.inviter(), sent.targetName()));
            } else {
                cooldown.release(sent.inviter().uuid(), sent.cooldownStamp());
                settled.add(
                        sent.notFoundFrom().isEmpty()
                                ? new TimedOut(sent.inviter(), sent.targetName())
                                : new NotFound(sent.inviter(), sent.targetName()));
            }
        }
        return settled;
    }

    private Optional<Reply> accepted(TradeSignal signal) {
        Outgoing sent = outgoing.remove(signal.tradeId());
        if (sent == null) {
            return Optional.empty();
        }
        PlayerRef inviter = sent.inviter();
        if (!presence.isOnline(inviter.uuid())) {
            bury(signal.tradeId(), Ending.CLOSED);
            withdraw(signal.tradeId(), inviter, signal.from());
            return Optional.empty();
        }
        if (presence.isTrading(inviter.uuid())) {
            bury(signal.tradeId(), Ending.CLOSED);
            withdraw(signal.tradeId(), inviter, signal.from());
            return Optional.of(new AcceptedWhileBusy(inviter, signal.from()));
        }
        bury(signal.tradeId(), Ending.OPENED);
        return Optional.of(new Accepted(signal.tradeId(), inviter, signal.from(), signal.originServer()));
    }

    private Optional<Outgoing> settle(TradeId id, boolean giveCooldownBack) {
        Outgoing sent = outgoing.remove(id);
        if (sent == null) {
            return Optional.empty();
        }
        bury(id, Ending.CLOSED);
        if (giveCooldownBack) {
            cooldown.release(sent.inviter().uuid(), sent.cooldownStamp());
        }
        return Optional.of(sent);
    }

    private Optional<Reply> notFound(TradeId id, String server) {
        AtomicReference<@Nullable Outgoing> concluded = new AtomicReference<>();
        outgoing.computeIfPresent(id, (key, sent) -> {
            if (sent.delivered()) {
                return sent;
            }
            // Counted by server, so a frame the bus delivered twice is one answer, not two.
            Outgoing counted = sent.notFoundOn(server);
            Set<String> live = livePeers.get();
            if (!live.isEmpty() && counted.notFoundFrom().containsAll(live)) {
                concluded.set(counted);
                return null;
            }
            return counted;
        });
        Outgoing done = concluded.get();
        if (done == null) {
            return Optional.empty();
        }
        bury(id, Ending.CLOSED);
        cooldown.release(done.inviter().uuid(), done.cooldownStamp());
        return Optional.of(new NotFound(done.inviter(), done.targetName()));
    }

    private void bury(TradeId id, Ending ending) {
        tombstones.put(id, new Tombstone(ending, clock.instant().plus(ttl).plus(ANSWER_GRACE)));
    }

    private @Nullable Tombstone tombstone(TradeId id) {
        Tombstone stone = tombstones.get(id);
        return stone != null && clock.instant().isBefore(stone.until()) ? stone : null;
    }

    private void withdraw(TradeId id, PlayerRef inviter, PlayerRef target) {
        bus.send(new TradeSignal(id, TradeSignalType.ABORT, inviter, target, bus.localServer()));
    }

    // Target side.

    /**
     * Answer an {@code INVITE} from another backend: {@code NOT_FOUND} when nobody here has the name, {@code BUSY} when
     * that player is already trading, otherwise hold the invite for them and answer {@code DELIVERED}. Returns the
     * held invite, which the caller shows to its player. A repeat of an invite already answered is ignored.
     */
    public Optional<Incoming> onInvite(TradeSignal invite) {
        Objects.requireNonNull(invite, "invite");
        if (seenInvites.putIfAbsent(invite.tradeId(), clock.instant()) != null) {
            return Optional.empty();
        }
        String name = invite.to().name();
        Optional<PlayerRef> found = presence.onlineByName(name);
        if (found.isEmpty()) {
            answer(invite, TradeSignalType.NOT_FOUND, new PlayerRef(UNRESOLVED, name));
            return Optional.empty();
        }
        PlayerRef target = found.get();
        if (presence.isTrading(target.uuid())) {
            answer(invite, TradeSignalType.BUSY, target);
            return Optional.empty();
        }
        Incoming held = new Incoming(invite.tradeId(), invite.from(), target, invite.originServer(), clock.instant());
        incomingByTarget
                .computeIfAbsent(target.uuid(), key -> new ConcurrentHashMap<>())
                .put(held.inviter().uuid(), held);
        answer(invite, TradeSignalType.DELIVERED, target);
        return Optional.of(held);
    }

    /**
     * {@code target} accepts the invite from {@code inviterName}, or the newest one when no name is given. On a match
     * {@code claim} reserves the target's side before {@code ACCEPT} goes out, so nothing else can take the player in
     * between; a failed claim, like a player already in a trade, leaves the invite waiting, as a local request does.
     * The target's window waits for the inviter's {@code OPENED} ({@link #onOpened}).
     */
    public Answer accept(UUID target, @Nullable String inviterName, Predicate<Incoming> claim) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(claim, "claim");
        if (presence.isTrading(target)) {
            return new Answer(Status.TRADING, null);
        }
        Answer found = take(target, inviterName);
        Incoming chosen = found.invite();
        if (found.status() != Status.MATCHED || chosen == null) {
            return found;
        }
        if (!claim.test(chosen)) {
            incomingByTarget
                    .computeIfAbsent(target, key -> new ConcurrentHashMap<>())
                    .putIfAbsent(chosen.inviter().uuid(), chosen);
            return new Answer(Status.TRADING, null);
        }
        awaitingOpen.put(chosen.tradeId(), new Awaiting(chosen, clock.instant()));
        send(chosen, TradeSignalType.ACCEPT);
        return found;
    }

    /** {@code target} denies the invite from {@code inviterName}, or the newest one; a match is answered with DECLINE. */
    public Answer deny(UUID target, @Nullable String inviterName) {
        Objects.requireNonNull(target, "target");
        Answer found = take(target, inviterName);
        Incoming chosen = found.invite();
        if (found.status() == Status.MATCHED && chosen != null) {
            send(chosen, TradeSignalType.DECLINE);
        }
        return found;
    }

    /**
     * The inviter's side of an accepted invite is open. Returns the invite the target may now open its window for, or
     * empty when this backend is not waiting for it (a repeat, or a trade already given up).
     */
    public Optional<Incoming> onOpened(TradeSignal opened) {
        Objects.requireNonNull(opened, "opened");
        Awaiting waiting = awaitingOpen.remove(opened.tradeId());
        return waiting == null ? Optional.empty() : Optional.of(waiting.invite());
    }

    /**
     * Give up every accepted invite whose {@code OPENED} has not come within the grace: the inviter's side never
     * opened, or its answer is stuck. Each is withdrawn with {@code ABORT}; the caller releases the target.
     */
    public List<Incoming> expireAccepts() {
        Instant now = clock.instant();
        List<Incoming> abandoned = new ArrayList<>();
        for (TradeId id : List.copyOf(awaitingOpen.keySet())) {
            AtomicReference<@Nullable Awaiting> due = new AtomicReference<>();
            awaitingOpen.computeIfPresent(id, (key, waiting) -> {
                if (now.isBefore(waiting.acceptedAt().plus(ANSWER_GRACE))) {
                    return waiting;
                }
                due.set(waiting);
                return null;
            });
            Awaiting waiting = due.get();
            if (waiting != null) {
                send(waiting.invite(), TradeSignalType.ABORT);
                abandoned.add(waiting.invite());
            }
        }
        return abandoned;
    }

    /**
     * The peer withdrew the trade {@code abort} names. On the target side this drops its waiting invite, and returns
     * the accepted invite that was still waiting for {@code OPENED}, which the caller releases. On the inviter side an
     * invite not yet settled ends here, so an accept that arrives after it is withdrawn rather than opened.
     */
    public Optional<Incoming> onAbort(TradeSignal abort) {
        Objects.requireNonNull(abort, "abort");
        TradeId id = abort.tradeId();
        incomingByTarget.computeIfPresent(abort.to().uuid(), (target, waiting) -> {
            waiting.values().removeIf(held -> held.tradeId().equals(id));
            return waiting.isEmpty() ? null : waiting;
        });
        if (outgoing.remove(id) != null) {
            bury(id, Ending.CLOSED);
        }
        Awaiting accepted = awaitingOpen.remove(id);
        return accepted == null ? Optional.empty() : Optional.of(accepted.invite());
    }

    /** The names of everyone with a live invite waiting for {@code target}, the accept and deny suggestions. */
    public List<String> pendingInviterNames(UUID target) {
        Objects.requireNonNull(target, "target");
        ConcurrentHashMap<UUID, Incoming> waiting = incomingByTarget.get(target);
        if (waiting == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Incoming held : waiting.values()) {
            if (!expired(held)) {
                names.add(held.inviter().name());
            }
        }
        return names;
    }

    /**
     * Drop everything a departing player sent or was sent. Every invite they sent ends here, and one already
     * delivered is withdrawn, so its target cannot accept into a trade nobody will join; one not yet delivered is
     * withdrawn when its delivery arrives. An accepted invite of theirs still waiting for {@code OPENED} is dropped;
     * the caller ends their side of that trade.
     */
    public void forget(UUID player) {
        Objects.requireNonNull(player, "player");
        incomingByTarget.remove(player);
        awaitingOpen
                .values()
                .removeIf(waiting -> waiting.invite().target().uuid().equals(player));
        for (Outgoing sent : List.copyOf(outgoing.values())) {
            if (!sent.inviter().uuid().equals(player) || outgoing.remove(sent.tradeId()) == null) {
                continue;
            }
            bury(sent.tradeId(), Ending.CLOSED);
            PlayerRef target = sent.target();
            if (target != null) {
                withdraw(sent.tradeId(), sent.inviter(), target);
            }
        }
    }

    /** Find and remove the invite {@code target} answers; nothing is sent here. */
    private Answer take(UUID target, @Nullable String inviterName) {
        ConcurrentHashMap<UUID, Incoming> waiting = incomingByTarget.get(target);
        if (waiting == null) {
            return new Answer(Status.NONE, null);
        }
        Incoming chosen = choose(waiting, inviterName);
        if (chosen == null || !waiting.remove(chosen.inviter().uuid(), chosen)) {
            return new Answer(Status.NONE, null);
        }
        incomingByTarget.computeIfPresent(target, (key, map) -> map.isEmpty() ? null : map);
        return new Answer(expired(chosen) ? Status.EXPIRED : Status.MATCHED, chosen);
    }

    private static @Nullable Incoming choose(Map<UUID, Incoming> waiting, @Nullable String inviterName) {
        Incoming newest = null;
        for (Incoming held : waiting.values()) {
            if (inviterName != null) {
                if (held.inviter().name().equalsIgnoreCase(inviterName)) {
                    return held;
                }
            } else if (newest == null || held.receivedAt().isAfter(newest.receivedAt())) {
                newest = held;
            }
        }
        return newest;
    }

    private void send(Incoming held, TradeSignalType type) {
        bus.send(new TradeSignal(held.tradeId(), type, held.target(), held.inviter(), bus.localServer()));
    }

    private void answer(TradeSignal invite, TradeSignalType type, PlayerRef from) {
        bus.send(new TradeSignal(invite.tradeId(), type, from, invite.from(), bus.localServer()));
    }

    private boolean expired(Incoming held) {
        return Duration.between(held.receivedAt(), clock.instant()).compareTo(ttl) > 0;
    }
}
