package com.uxplima.uxmessentials.shared.network;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A vote party reached its threshold and fired on the origin backend, so peers should run their local
 * party celebration (broadcast, rewards for their own players) without re-counting. The shared database holds
 * the authoritative accumulated vote total and the fired stamp; this frame is the notification that lets every
 * backend celebrate the same party once, in lockstep, rather than each backend racing to detect the threshold
 * from its own cached counter. The {@code threshold} travels with the frame for the broadcast context only.
 *
 * <p>{@code reach} says which players a peer may reward. The voter list has to travel with the frame because the
 * origin clears the shared participant set as soon as the party fires, before a peer could read it.
 *
 * <p>{@code partyId} names the party, so a backend that receives the frame over two transports celebrates and pays
 * it once. That memory does not survive a restart, and with two transports the proxy copy can wait for a player to
 * carry it long after the Redis copy arrived. So the frame also says when the party fired and whether it went out on
 * both carriers: a receiver drops such a frame when it fired before the receiver started listening, since the Redis
 * copy reached the receiver's previous run (or found it down, hours before this late copy). A frame sent on one
 * carrier only is never dropped this way, so a party that only arrives late over the proxy is still run.
 *
 * @param originServer the backend on which the party fired
 * @param partyId identifies this party, or {@link #NO_ID} on a frame from a peer that sends none
 * @param threshold the vote count at which the party fired, for broadcast and logging context
 * @param reach which of a peer's online players the party rewards
 * @param voters the voters of the party window a peer may reward, filled only when {@code reach} is
 *     {@link Reach#VOTERS}
 * @param firedAt when the party fired on the origin, or {@link #UNKNOWN_TIME} on a frame from a peer that sends none
 * @param bothCarriers whether the origin sent the frame on both the proxy and Redis
 */
public record VotePartyFired(
        String originServer,
        UUID partyId,
        int threshold,
        Reach reach,
        Set<UUID> voters,
        Instant firedAt,
        boolean bothCarriers)
        implements NetworkMessage {

    /** The fire time a frame decodes with when its sender wrote none. */
    public static final Instant UNKNOWN_TIME = Instant.EPOCH;

    /**
     * The id a frame decodes with when its sender wrote none. A random id is never all zeros, so this cannot
     * collide with a real party.
     */
    public static final UUID NO_ID = new UUID(0L, 0L);

    /** A party with no fire time, sent on one carrier, as a peer that predates the fire time sends it. */
    public VotePartyFired(String originServer, UUID partyId, int threshold, Reach reach, Set<UUID> voters) {
        this(originServer, partyId, threshold, reach, voters, UNKNOWN_TIME, false);
    }

    public VotePartyFired {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(firedAt, "firedAt");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(reach, "reach");
        Objects.requireNonNull(voters, "voters");
        if (originServer.isBlank()) {
            throw new IllegalArgumentException("originServer must not be blank");
        }
        if (threshold < 0) {
            throw new IllegalArgumentException("threshold must not be negative: " + threshold);
        }
        if (reach != Reach.VOTERS && !voters.isEmpty()) {
            throw new IllegalArgumentException("voters travel only with reach VOTERS, not " + reach);
        }
        voters = Set.copyOf(voters);
    }

    /**
     * Whether this is a late copy of a party a receiver that started listening at {@code listeningSince} has no
     * reason to run: it went out on both carriers and fired before then.
     */
    public boolean firedBefore(Instant listeningSince) {
        Objects.requireNonNull(listeningSince, "listeningSince");
        return bothCarriers && !UNKNOWN_TIME.equals(firedAt) && firedAt.isBefore(listeningSince);
    }

    /** Whether the frame names its party, so a second copy of it can be told apart from a new party. */
    public boolean identified() {
        return !NO_ID.equals(partyId);
    }

    @Override
    public MessageType type() {
        return MessageType.VOTE_PARTY_FIRED;
    }

    /**
     * Which players a peer rewards. Each carries a stable wire byte, never the ordinal, like
     * {@link MessageType}.
     */
    public enum Reach {
        /**
         * Peers reward nobody. A frame without the reach byte decodes as this, and so does a voters-only party
         * whose voter list was too long to send.
         */
        NONE(0),
        /** Every player online on the peer. */
        EVERYONE(1),
        /** Only the listed voters who are online on the peer. */
        VOTERS(2);

        private final byte wireTag;

        Reach(int wireTag) {
            this.wireTag = (byte) wireTag;
        }

        /** The stable on-wire byte for this reach. */
        public byte wireTag() {
            return wireTag;
        }

        /**
         * The reach carrying {@code wireTag}.
         *
         * @throws IllegalArgumentException if no reach uses that byte
         */
        public static Reach fromWireTag(byte wireTag) {
            for (Reach reach : values()) {
                if (reach.wireTag == wireTag) {
                    return reach;
                }
            }
            throw new IllegalArgumentException("unknown vote party reach: " + wireTag);
        }
    }
}
