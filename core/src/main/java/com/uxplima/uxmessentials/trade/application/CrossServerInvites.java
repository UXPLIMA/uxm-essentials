package com.uxplima.uxmessentials.trade.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.port.TradeBus;
import com.uxplima.uxmessentials.trade.application.port.TradePresence;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The invite step of a cross-server trade, on both ends: the inviter's backend tracks each invite it sent until an
 * answer or the expiry settles it, and the target's backend holds each invite it showed until its player accepts,
 * denies, or lets it expire. Nothing opens before the target accepts, which is how a same-server request behaves.
 *
 * <p>An {@code INVITE} names its target only by name and reaches every peer, so every peer answers it: the one that
 * hosts the player with {@code DELIVERED} or {@code BUSY}, every other one with {@code NOT_FOUND}. The inviter's
 * backend therefore reports "not found" only once every live peer has said so, and otherwise waits for the expiry.
 * A failed invite (not found, busy, or never answered) gives the cooldown stamp back, so only an invite that reached
 * the target costs the inviter a wait.
 *
 * <p>Pure apart from its ports: the bus, this backend's presence, the live peer count and the clock. The maps are
 * concurrent because bus dispatch, command threads and the expiry sweep reach them from different threads.
 */
@NullMarked
public final class CrossServerInvites {

    /** The id an {@code INVITE} carries for its not-yet-resolved target, and a {@code NOT_FOUND} for the absent one. */
    public static final UUID UNRESOLVED = new UUID(0L, 0L);

    /**
     * How much longer the inviter waits for an answer to a delivered invite. The target's own expiry starts when the
     * invite reaches it, a moment after it left here, and an accept sent at the last second still has to travel back.
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
            int notFound) {

        boolean delivered() {
            return target != null;
        }
    }

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

    /** How an invite this backend sent was settled, for the line its inviter reads. */
    public sealed interface Reply {

        /** The player who sent the invite. */
        PlayerRef inviter();
    }

    /** The target accepted and the inviter is free: open the inviter's window for {@code tradeId}. */
    public record Accepted(TradeId tradeId, PlayerRef inviter, PlayerRef target, String targetServer)
            implements Reply {}

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
    private final IntSupplier livePeers;
    private final TradeCooldown cooldown;
    private final Clock clock;
    private final Duration ttl;
    private final ConcurrentHashMap<TradeId, Outgoing> outgoing = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Incoming>> incomingByTarget =
            new ConcurrentHashMap<>();

    /**
     * @param livePeers how many peer backends are live right now, each of which answers every invite
     * @param ttl how long an invite stays acceptable, the same window a same-server request has
     */
    public CrossServerInvites(
            TradeBus bus,
            TradePresence presence,
            IntSupplier livePeers,
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
        outgoing.values()
                .removeIf(previous -> previous.inviter().equals(inviter)
                        && previous.targetName().equalsIgnoreCase(targetName));
        TradeId id = TradeId.newId();
        Instant stamp = cooldown.stamp(inviter.uuid());
        outgoing.put(id, new Outgoing(id, inviter, targetName, clock.instant(), stamp, null, 0));
        bus.send(new TradeSignal(
                id, TradeSignalType.INVITE, inviter, new PlayerRef(UNRESOLVED, targetName), bus.localServer()));
        return id;
    }

    /**
     * Read a peer's answer to an invite this backend may have sent. Returns how the invite settled when this answer
     * settles it; a {@code DELIVERED} and all but the last {@code NOT_FOUND} only move it along. An accept or delivery
     * for an invite that is no longer tracked, addressed to a player on this backend, is answered with {@code ABORT}
     * so the target's window or waiting invite does not outlive it.
     */
    public Optional<Reply> onReply(TradeSignal signal) {
        Objects.requireNonNull(signal, "signal");
        return switch (signal.type()) {
            case DELIVERED -> {
                delivered(signal);
                yield Optional.empty();
            }
            case ACCEPT -> accepted(signal);
            case DECLINE -> settle(signal.tradeId(), false).map(sent -> new Denied(sent.inviter(), signal.from()));
            case BUSY -> settle(signal.tradeId(), true).map(sent -> new Busy(sent.inviter(), signal.from()));
            case NOT_FOUND -> notFound(signal.tradeId());
            case INVITE, READY, COMMIT, ABORT -> Optional.empty();
        };
    }

    /** Settle every invite whose wait is over as of now: expired when it was delivered, otherwise unanswered. */
    public List<Reply> expire() {
        Instant now = clock.instant();
        List<Reply> settled = new ArrayList<>();
        for (Outgoing sent : List.copyOf(outgoing.values())) {
            Instant deadline = sent.sentAt().plus(sent.delivered() ? ttl.plus(ANSWER_GRACE) : ttl);
            if (now.isBefore(deadline) || !outgoing.remove(sent.tradeId(), sent)) {
                continue;
            }
            PlayerRef target = sent.target();
            if (target != null) {
                withdraw(sent.tradeId(), sent.inviter(), target);
                settled.add(new Expired(sent.inviter(), sent.targetName()));
            } else {
                cooldown.release(sent.inviter().uuid(), sent.cooldownStamp());
                settled.add(
                        sent.notFound() > 0
                                ? new NotFound(sent.inviter(), sent.targetName())
                                : new TimedOut(sent.inviter(), sent.targetName()));
            }
        }
        return settled;
    }

    private void delivered(TradeSignal signal) {
        Outgoing updated = outgoing.computeIfPresent(
                signal.tradeId(),
                (id, sent) -> new Outgoing(
                        id,
                        sent.inviter(),
                        sent.targetName(),
                        sent.sentAt(),
                        sent.cooldownStamp(),
                        signal.from(),
                        sent.notFound()));
        if (updated == null) {
            withdrawIfAddressedHere(signal);
        }
    }

    private Optional<Reply> accepted(TradeSignal signal) {
        Outgoing sent = outgoing.remove(signal.tradeId());
        if (sent == null) {
            withdrawIfAddressedHere(signal);
            return Optional.empty();
        }
        PlayerRef inviter = sent.inviter();
        if (!presence.isOnline(inviter.uuid()) || presence.isTrading(inviter.uuid())) {
            withdraw(signal.tradeId(), inviter, signal.from());
            return Optional.empty();
        }
        return Optional.of(new Accepted(signal.tradeId(), inviter, signal.from(), signal.originServer()));
    }

    private Optional<Outgoing> settle(TradeId id, boolean giveCooldownBack) {
        Outgoing sent = outgoing.remove(id);
        if (sent == null) {
            return Optional.empty();
        }
        if (giveCooldownBack) {
            cooldown.release(sent.inviter().uuid(), sent.cooldownStamp());
        }
        return Optional.of(sent);
    }

    private Optional<Reply> notFound(TradeId id) {
        Outgoing counted = outgoing.computeIfPresent(
                id,
                (key, sent) -> sent.delivered()
                        ? sent
                        : new Outgoing(
                                key,
                                sent.inviter(),
                                sent.targetName(),
                                sent.sentAt(),
                                sent.cooldownStamp(),
                                null,
                                sent.notFound() + 1));
        if (counted == null || counted.delivered() || counted.notFound() < Math.max(1, livePeers.getAsInt())) {
            return Optional.empty();
        }
        if (!outgoing.remove(id, counted)) {
            return Optional.empty();
        }
        cooldown.release(counted.inviter().uuid(), counted.cooldownStamp());
        return Optional.of(new NotFound(counted.inviter(), counted.targetName()));
    }

    private void withdrawIfAddressedHere(TradeSignal signal) {
        // Every peer hears every answer; only the inviter's own backend may withdraw on its behalf.
        if (presence.isOnline(signal.to().uuid())) {
            withdraw(signal.tradeId(), signal.to(), signal.from());
        }
    }

    private void withdraw(TradeId id, PlayerRef inviter, PlayerRef target) {
        bus.send(new TradeSignal(id, TradeSignalType.ABORT, inviter, target, bus.localServer()));
    }

    // Target side.

    /**
     * Answer an {@code INVITE} from another backend: {@code NOT_FOUND} when nobody here has the name, {@code BUSY} when
     * that player is already trading, otherwise hold the invite for them and answer {@code DELIVERED}. Returns the
     * held invite, which the caller shows to its player.
     */
    public Optional<Incoming> onInvite(TradeSignal invite) {
        Objects.requireNonNull(invite, "invite");
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
     * {@code target} accepts the invite from {@code inviterName}, or the newest one when no name is given. A match is
     * answered with {@code ACCEPT}; a player already in a trade keeps the invite for later, as a local request does.
     */
    public Answer accept(UUID target, @Nullable String inviterName) {
        Objects.requireNonNull(target, "target");
        if (presence.isTrading(target)) {
            return new Answer(Status.TRADING, null);
        }
        return resolve(target, inviterName, TradeSignalType.ACCEPT);
    }

    /** {@code target} denies the invite from {@code inviterName}, or the newest one; a match is answered with DECLINE. */
    public Answer deny(UUID target, @Nullable String inviterName) {
        Objects.requireNonNull(target, "target");
        return resolve(target, inviterName, TradeSignalType.DECLINE);
    }

    /** The inviter withdrew: drop the invite with that trade id if it is still waiting for its target here. */
    public void onAbort(TradeSignal abort) {
        Objects.requireNonNull(abort, "abort");
        ConcurrentHashMap<UUID, Incoming> waiting =
                incomingByTarget.get(abort.to().uuid());
        if (waiting != null) {
            waiting.values().removeIf(held -> held.tradeId().equals(abort.tradeId()));
        }
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
     * Drop everything a departing player sent or was sent. A delivered invite they sent is withdrawn, so its target
     * cannot accept into a trade nobody will join.
     */
    public void forget(UUID player) {
        Objects.requireNonNull(player, "player");
        incomingByTarget.remove(player);
        for (Outgoing sent : List.copyOf(outgoing.values())) {
            PlayerRef target = sent.target();
            if (sent.inviter().uuid().equals(player) && outgoing.remove(sent.tradeId(), sent) && target != null) {
                withdraw(sent.tradeId(), sent.inviter(), target);
            }
        }
    }

    private Answer resolve(UUID target, @Nullable String inviterName, TradeSignalType answer) {
        ConcurrentHashMap<UUID, Incoming> waiting = incomingByTarget.get(target);
        if (waiting == null) {
            return new Answer(Status.NONE, null);
        }
        Incoming chosen = choose(waiting, inviterName);
        if (chosen == null || !waiting.remove(chosen.inviter().uuid(), chosen)) {
            return new Answer(Status.NONE, null);
        }
        if (waiting.isEmpty()) {
            incomingByTarget.remove(target, waiting);
        }
        if (expired(chosen)) {
            return new Answer(Status.EXPIRED, chosen);
        }
        bus.send(new TradeSignal(chosen.tradeId(), answer, chosen.target(), chosen.inviter(), bus.localServer()));
        return new Answer(Status.MATCHED, chosen);
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

    private void answer(TradeSignal invite, TradeSignalType type, PlayerRef from) {
        bus.send(new TradeSignal(invite.tradeId(), type, from, invite.from(), bus.localServer()));
    }

    private boolean expired(Incoming held) {
        return Duration.between(held.receivedAt(), clock.instant()).compareTo(ttl) > 0;
    }
}
