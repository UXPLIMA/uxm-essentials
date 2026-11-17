package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.networkpresence.RosterMember;
import com.uxplima.uxmessentials.shared.application.networkpresence.RosterStamp;
import com.uxplima.uxmessentials.shared.application.networkpresence.TrackNetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresenceBroadcast;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.PresenceTraits;
import com.uxplima.uxmessentials.shared.network.PresenceChanged;
import com.uxplima.uxmessentials.shared.network.PresenceSnapshot;
import com.uxplima.uxmessentials.shared.network.ServerPing;
import org.jspecify.annotations.NullMarked;

/**
 * The network-presence seam of the bus, the same two halves as {@link ModerationSync}.
 *
 * <ul>
 *   <li><b>Outbound</b>: {@link #publisher(BusPublisher)} binds the {@link NetworkPresenceBroadcast} port to the bus, so a
 *       local join or quit becomes a {@link PresenceChanged} and a roster announcement a {@link PresenceSnapshot},
 *       each stamped with this backend's origin.
 *   <li><b>Inbound</b>: {@link #listener(TrackNetworkPresence)} hands every remote presence frame to the tracker, and every
 *       {@link ServerPing} too, so the heartbeat that keeps a peer in the cluster roster keeps its players visible.
 * </ul>
 *
 * <p>With the bus disabled the publisher is the no-op one and the listener is never invoked, so presence knows the
 * local players only.
 */
@NullMarked
public final class NetworkPresenceSync {

    private NetworkPresenceSync() {}

    /** A {@link NetworkPresenceBroadcast} that publishes each announcement on the bus. */
    public static NetworkPresenceBroadcast publisher(BusPublisher bus) {
        Objects.requireNonNull(bus, "bus");
        return new Publishing(bus);
    }

    /** A listener that applies remote presence frames and heartbeats to {@code track}. */
    public static RemoteSyncListener listener(TrackNetworkPresence track) {
        Objects.requireNonNull(track, "track");
        return message -> {
            switch (message) {
                case PresenceChanged changed -> {
                    RosterStamp stamp = new RosterStamp(changed.session(), changed.sequence());
                    if (changed.online()) {
                        track.remoteJoined(
                                changed.originServer(),
                                stamp,
                                new PlayerRef(changed.player(), changed.playerName()),
                                changed.traits());
                    } else {
                        track.remoteLeft(
                                changed.originServer(), stamp, new PlayerRef(changed.player(), changed.playerName()));
                    }
                }
                case PresenceSnapshot snapshot -> {
                    List<RosterMember> players =
                            new ArrayList<>(snapshot.players().size());
                    for (PresenceSnapshot.Member member : snapshot.players()) {
                        players.add(new RosterMember(new PlayerRef(member.player(), member.name()), member.traits()));
                    }
                    track.remoteRoster(
                            snapshot.originServer(),
                            new RosterStamp(snapshot.session(), snapshot.sequence()),
                            snapshot.replyRequested(),
                            snapshot.part(),
                            snapshot.parts(),
                            players);
                }
                case ServerPing ping -> track.remoteAlive(ping.originServer());
                default -> {
                    // Every other frame belongs to another context.
                }
            }
        };
    }

    /** Stamps each announcement with this backend's origin and routes the matching frame to peers. */
    private record Publishing(BusPublisher bus) implements NetworkPresenceBroadcast {

        @Override
        public void joined(RosterStamp stamp, PlayerRef player, PresenceTraits traits) {
            bus.publish(new PresenceChanged(
                    bus.serverId(), stamp.session(), stamp.sequence(), player.uuid(), player.name(), true, traits));
        }

        @Override
        public void left(RosterStamp stamp, PlayerRef player) {
            bus.publish(new PresenceChanged(
                    bus.serverId(),
                    stamp.session(),
                    stamp.sequence(),
                    player.uuid(),
                    player.name(),
                    false,
                    PresenceTraits.PLAIN));
        }

        @Override
        public void roster(RosterStamp stamp, boolean replyRequested, List<RosterMember> players) {
            List<PresenceSnapshot.Member> members = new ArrayList<>(players.size());
            for (RosterMember member : players) {
                members.add(new PresenceSnapshot.Member(
                        member.player().uuid(), member.player().name(), member.traits()));
            }
            // Cut into parts so no relayed frame reaches the size at which a backend disconnects its carrier.
            for (PresenceSnapshot part : PresenceSnapshot.split(
                    bus.serverId(),
                    stamp.session(),
                    stamp.sequence(),
                    replyRequested,
                    members,
                    PresenceSnapshot.PART_BYTES)) {
                bus.publish(part);
            }
        }
    }
}
