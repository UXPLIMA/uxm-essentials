package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.persistence.vote.CachedVoteRepository;
import com.uxplima.uxmessentials.shared.application.RecentIds;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.network.VoteCounterChanged;
import com.uxplima.uxmessentials.shared.network.VotePartyFired;
import com.uxplima.uxmessentials.vote.application.RewardNetworkParty;
import com.uxplima.uxmessentials.vote.application.port.ForwardingVoteRepository;
import com.uxplima.uxmessentials.vote.application.port.VoteBroadcaster;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.VoteMessageKey;
import com.uxplima.uxmessentials.vote.domain.event.VotePartyTriggered;
import org.jspecify.annotations.NullMarked;

/**
 * The vote context's cross-server sync seam, the same shape as {@link HomeSync} and {@link WarpSync}, applied
 * to the server-wide vote-party counter. The party counter is one global figure backed by the shared database,
 * so (like warps) the whole cached value is the invalidation unit:
 *
 * <ul>
 *   <li><b>Outbound (counter)</b>: {@link #repository(CachedVoteRepository, BusPublisher)} wraps the cached
 *       repository so every counter mutation ({@code incrementAndGetPartyCount}, {@code setPartyCount},
 *       {@code claimPartyFire}) publishes a {@link VoteCounterChanged} frame after the durable write commits
 *       peers learn the running total advanced and drop their cached copy.
 *   <li><b>Outbound (party fire)</b>: {@link #partyPublisher(BusPublisher, Clock, boolean)} returns a domain-event consumer
 *       that, on a local {@link VotePartyTriggered}, publishes a {@link VotePartyFired} frame so peers run
 *       their own party celebration once, in lockstep, rather than each racing to detect the crossing.
 *   <li><b>Inbound</b>: {@link #listener(Runnable, VoteBroadcaster, Set, RewardNetworkParty, Instant)}
 *       returns a {@link RemoteSyncListener} that, on a remote {@link VoteCounterChanged}, drops the cached
 *       counter, and on a remote {@link VotePartyFired}, drops the cached counter, re-broadcasts the party
 *       announcement on the configured channels and rewards the players online here whom the frame makes
 *       eligible. The origin paid only its own players, and a player is online on one backend at a time, so
 *       nobody is paid twice. With {@code network.transport = both} each frame arrives once per transport, so a
 *       party id already celebrated here is dropped, and so is a both-carrier frame for a party that fired before
 *       this listener started: the proxy copy can lag the Redis one by hours, past a restart that emptied the
 *       memory of celebrated ids.
 * </ul>
 *
 * <p>The decorator wraps the <em>same</em> cache the vote commands read, so the loop closes: a write here emits
 * a frame, the peer's listener drops its cached counter there. The bus client drops a frame that originated on
 * this backend, so a backend never re-broadcasts its own party.
 */
@NullMarked
public final class VoteSync {

    /**
     * The proxy relays a frame to a backend as a plugin message the server refuses above 32767 bytes, which
     * disconnects the player carrying it. At 16 bytes a voter this keeps the frame near half of that.
     */
    static final int MAX_VOTERS_ON_WIRE = 1000;

    private VoteSync() {}

    /** A {@link VoteRepository} that broadcasts a {@link VoteCounterChanged} after every counter mutation. */
    public static VoteRepository repository(CachedVoteRepository delegate, BusPublisher bus) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        return new Broadcasting(delegate, bus);
    }

    /**
     * A listener that drops the cached counter on a remote {@link VoteCounterChanged}, and on a remote
     * {@link VotePartyFired} drops the counter, re-broadcasts {@link VoteMessageKey#VOTEPARTY_REACHED} on
     * {@code channels} and hands the frame's eligible players to {@code rewards}, which pays the ones online
     * here. {@code dropCachedCounter} is the cache's invalidation ({@link CachedVoteRepository#invalidate}),
     * passed as a {@code Runnable} so this seam is testable without the persistence graph. {@code listeningSince}
     * is when this listener starts taking frames, before which any both-carrier party is a late copy; a backend on
     * one carrier passes {@link Instant#MIN}, since it never receives the other copy and so drops nothing.
     */
    public static RemoteSyncListener listener(
            Runnable dropCachedCounter,
            VoteBroadcaster broadcaster,
            Set<BroadcastChannel> channels,
            RewardNetworkParty rewards,
            Instant listeningSince) {
        Objects.requireNonNull(dropCachedCounter, "dropCachedCounter");
        Objects.requireNonNull(broadcaster, "broadcaster");
        Objects.requireNonNull(channels, "channels");
        Objects.requireNonNull(rewards, "rewards");
        Objects.requireNonNull(listeningSince, "listeningSince");
        Set<BroadcastChannel> safeChannels = Set.copyOf(channels);
        RecentIds celebrated = new RecentIds();
        return message -> {
            if (message instanceof VoteCounterChanged) {
                dropCachedCounter.run();
            } else if (message instanceof VotePartyFired fired
                    && !fired.firedBefore(listeningSince)
                    && firstCopy(fired, celebrated)) {
                dropCachedCounter.run();
                broadcaster.broadcast(
                        VoteMessageKey.VOTEPARTY_REACHED,
                        Map.of("threshold", Integer.toString(fired.threshold())),
                        safeChannels);
                switch (fired.reach()) {
                    case EVERYONE -> rewards.rewardEveryone();
                    case VOTERS -> rewards.rewardVoters(fired.voters());
                    case NONE -> {
                        // The frame names nobody for this server to reward.
                    }
                }
            }
        };
    }

    /**
     * Whether {@code fired} is a party not yet celebrated here. A frame without an id cannot be told apart from a new
     * party, so it is always run rather than risk dropping a real party.
     */
    private static boolean firstCopy(VotePartyFired fired, RecentIds celebrated) {
        return !fired.identified() || celebrated.firstTime(fired.partyId());
    }

    /**
     * A domain-event consumer that publishes a {@link VotePartyFired} when a {@link VotePartyTriggered} fires
     * locally, so peers celebrate the same party. The frame carries the time from {@code clock} and whether
     * {@code bothCarriers} are in use, which is what lets a receiver tell a late copy from a late party. Any other
     * event is ignored.
     */
    public static Consumer<DomainEvent> partyPublisher(BusPublisher bus, Clock clock, boolean bothCarriers) {
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(clock, "clock");
        return event -> {
            if (event instanceof VotePartyTriggered triggered) {
                bus.publish(fired(bus.serverId(), triggered, clock.instant(), bothCarriers));
            }
        };
    }

    private static VotePartyFired fired(
            String origin, VotePartyTriggered triggered, Instant firedAt, boolean bothCarriers) {
        UUID party = UUID.randomUUID();
        int threshold = triggered.threshold();
        VotePartyFired.Reach reach;
        Set<UUID> voters = Set.of();
        if (!triggered.onlyVoters()) {
            reach = VotePartyFired.Reach.EVERYONE;
        } else if (triggered.absentVoters().size() > MAX_VOTERS_ON_WIRE) {
            reach = VotePartyFired.Reach.NONE;
        } else {
            reach = VotePartyFired.Reach.VOTERS;
            voters = triggered.absentVoters();
        }
        return new VotePartyFired(origin, party, threshold, reach, voters, firedAt, bothCarriers);
    }

    /** Forwards every call to the cached delegate, then announces each counter mutation on the bus. */
    private static final class Broadcasting extends ForwardingVoteRepository {

        private final BusPublisher bus;

        Broadcasting(VoteRepository delegate, BusPublisher bus) {
            super(delegate);
            this.bus = bus;
        }

        @Override
        public void setPartyCount(int count) {
            delegate.setPartyCount(count);
            announce();
        }

        @Override
        public int incrementAndGetPartyCount() {
            int updated = delegate.incrementAndGetPartyCount();
            announce();
            return updated;
        }

        @Override
        public boolean claimPartyFire(int threshold) {
            boolean won = delegate.claimPartyFire(threshold);
            // The reset moves the durable counter regardless of who won the claim, and invalidation is
            // idempotent, so announce unconditionally rather than gating on the win.
            announce();
            return won;
        }

        private void announce() {
            bus.publish(new VoteCounterChanged(bus.serverId()));
        }
    }
}
