package com.uxplima.uxmessentials.shared.network;

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
 * @param originServer the backend on which the party fired
 * @param threshold the vote count at which the party fired, for broadcast and logging context
 * @param reach which of a peer's online players the party rewards
 * @param voters the voters of the party window a peer may reward, filled only when {@code reach} is
 *     {@link Reach#VOTERS}
 */
public record VotePartyFired(String originServer, int threshold, Reach reach, Set<UUID> voters)
        implements NetworkMessage {

    public VotePartyFired {
        Objects.requireNonNull(originServer, "originServer");
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

    @Override
    public MessageType type() {
        return MessageType.VOTE_PARTY_FIRED;
    }

    /**
     * Which players a peer rewards. Each carries a stable wire byte, never the ordinal, like
     * {@link MessageType}.
     */
    public enum Reach {
        /** Peers reward nobody: a frame from a release that predates network rewards, or a voter list too long to send. */
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
