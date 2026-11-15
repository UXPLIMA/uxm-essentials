package com.uxplima.uxmessentials.shared.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One part of the roster of the origin backend: every player connected to it, sent with each heartbeat and when the
 * origin starts. It is what repairs a peer's presence view after a lost {@link PresenceChanged} or a peer restart:
 * the next roster replaces whatever the peer held for the origin. A roster carries the same {@code session} and
 * {@code sequence} as the join and quit frames, so an older roster that arrives after a newer join cannot undo it.
 *
 * <p>A frame relayed through the proxy reaches the other backends as a serverbound custom payload, and one larger
 * than {@link BusChannel#MAX_FRAME_BYTES} disconnects the player carrying it. A roster is therefore sent as
 * {@code parts} frames of the same session and sequence, built by {@link #split}, and a peer applies it only once
 * every part has arrived.
 *
 * <p>A backend that has just started sends one with {@code replyRequested} set, and every peer answers with its own
 * roster, so the new backend knows the whole network after one round trip instead of after a heartbeat.
 *
 * @param originServer the backend whose players these are
 * @param session identifies one run of the origin backend; a new value means the origin restarted
 * @param sequence the position of this roster among the presence frames of {@code session}
 * @param replyRequested whether each peer should answer with a roster of its own players
 * @param part the index of this part, from zero
 * @param parts how many parts the roster was split into
 * @param players the players this part names
 */
public record PresenceSnapshot(
        String originServer,
        long session,
        long sequence,
        boolean replyRequested,
        int part,
        int parts,
        List<Member> players)
        implements NetworkMessage {

    /**
     * The size each part is cut to: room below {@link BusChannel#MAX_FRAME_BYTES} for the header, so no part can
     * reach the limit whatever the origin's name.
     */
    public static final int PART_BYTES = 30_000;

    /** What a part costs before its first member: version, tag, the numbers and the member count. */
    private static final int FIXED_BYTES = 1 + 1 + Long.BYTES + Long.BYTES + 1 + Integer.BYTES * 3;

    /** What a member costs besides its name: the two longs of its account id. */
    private static final int MEMBER_BYTES = Long.BYTES * 2;

    public PresenceSnapshot {
        Objects.requireNonNull(originServer, "originServer");
        players = List.copyOf(Objects.requireNonNull(players, "players"));
        if (parts < 1 || part < 0 || part >= parts) {
            throw new IllegalArgumentException("part " + part + " of " + parts + " is not a roster part");
        }
    }

    /**
     * Cut a roster into the fewest parts that each encode to at most {@code maxBytes}. An empty roster is one empty
     * part, so a backend with nobody on it still tells its peers so.
     */
    public static List<PresenceSnapshot> split(
            String originServer,
            long session,
            long sequence,
            boolean replyRequested,
            List<Member> players,
            int maxBytes) {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(players, "players");
        int header = FIXED_BYTES + NetworkMessageCodec.utfBytes(originServer);
        List<List<Member>> chunks = new ArrayList<>();
        List<Member> chunk = new ArrayList<>();
        int size = header;
        for (Member member : players) {
            int cost = MEMBER_BYTES + NetworkMessageCodec.utfBytes(member.name());
            if (!chunk.isEmpty() && size + cost > maxBytes) {
                chunks.add(chunk);
                chunk = new ArrayList<>();
                size = header;
            }
            chunk.add(member);
            size += cost;
        }
        chunks.add(chunk);
        List<PresenceSnapshot> parts = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            parts.add(new PresenceSnapshot(
                    originServer, session, sequence, replyRequested, i, chunks.size(), chunks.get(i)));
        }
        return List.copyOf(parts);
    }

    @Override
    public MessageType type() {
        return MessageType.PRESENCE_SNAPSHOT;
    }

    /**
     * One connected player in a roster.
     *
     * @param player the player's account id
     * @param name the name the player is connected under
     */
    public record Member(UUID player, String name) {

        public Member {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(name, "name");
        }
    }
}
