package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.moderation.application.port.FreezeSync;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.shared.application.networkpresence.NetworkRoster;
import com.uxplima.uxmessentials.shared.application.networkpresence.RosterStamp;
import com.uxplima.uxmessentials.shared.application.networkpresence.TrackNetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresenceBroadcast;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import com.uxplima.uxmessentials.shared.domain.NameCase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.PresenceTraits;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.shared.network.FreezeChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * A freeze holds on every backend of the network: a frozen player who switches servers arrives frozen, an unfreeze on
 * any backend lifts it everywhere, and a freeze and its release arriving out of order or twice leave the last word.
 */
class CrossServerFreezeTest {

    private static final PlayerRef STAFF = new PlayerRef(UUID.randomUUID(), "Staff");
    private static final PlayerRef STEVE = new PlayerRef(UUID.randomUUID(), "Steve");

    private static final PlayerNameIndex NO_NAMES = new PlayerNameIndex() {
        @Override
        public List<PlayerRef> byName(String name) {
            return List.of();
        }

        @Override
        public void record(UUID uuid, String name) {}

        @Override
        public void learn(UUID uuid, String name) {}
    };

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-16T12:00:00Z"));
    private final List<byte[]> wire = new ArrayList<>();
    private final Backend survival = new Backend("survival-1");
    private final Backend lobby = new Backend("lobby-1");
    private final List<Backend> backends = List.of(survival, lobby);

    /** Steve is connected to {@code server} as every backend sees it. */
    private void steveOn(Backend server, long sequence) {
        for (Backend backend : backends) {
            if (backend == server) {
                backend.track.joined(STEVE);
            } else {
                backend.track.remoteJoined(server.name, new RosterStamp(1L, sequence), STEVE, PresenceTraits.PLAIN);
            }
        }
    }

    /** Hand every frame on the wire to every backend but its origin, in the order given. */
    private void deliver(List<byte[]> frames) {
        for (byte[] frame : frames) {
            FreezeChanged change = (FreezeChanged) NetworkMessageCodec.decode(frame);
            for (Backend backend : backends) {
                if (!backend.name.equals(change.originServer())) {
                    backend.freezes.changedElsewhere(
                            change.frameId(),
                            new PlayerRef(change.target(), change.targetName()),
                            change.frozen(),
                            Instant.ofEpochMilli(change.changedAtMillis()));
                }
            }
        }
    }

    @Test
    void aPlayerFrozenOnOneServerArrivesFrozenOnAnother() {
        steveOn(survival, 1L);

        assertThat(survival.freeze.freeze(STAFF, STEVE).isOk()).isTrue();
        deliver(wire);
        steveOn(lobby, 2L);

        assertThat(lobby.frozen.contains(STEVE.uuid())).isTrue();
        assertThat(survival.frozen.contains(STEVE.uuid())).isTrue();
    }

    @Test
    void anUnfreezeOnAnyServerLiftsItEverywhereAndTellsThePlayerWhereTheyAre() {
        steveOn(survival, 1L);
        survival.freeze.freeze(STAFF, STEVE);
        deliver(List.copyOf(wire));
        wire.clear();
        clock.advance(Duration.ofSeconds(5));

        lobby.freeze.unfreeze(STAFF, STEVE);
        deliver(wire);

        assertThat(survival.frozen).isEmpty();
        assertThat(lobby.frozen).isEmpty();
        assertThat(survival.toldSteve())
                .containsExactly("moderation.freeze.notify-target", "moderation.unfreeze.notify-target");
    }

    @Test
    void aReleaseThatOvertakesItsFreezeIsNotUndoneByIt() {
        steveOn(survival, 1L);
        survival.freeze.freeze(STAFF, STEVE);
        byte[] freeze = wire.removeFirst();
        clock.advance(Duration.ofSeconds(5));
        survival.freeze.unfreeze(STAFF, STEVE);
        byte[] release = wire.removeFirst();

        deliver(List.of(release, freeze, release));

        assertThat(lobby.frozen).isEmpty();
    }

    @Test
    void aFreezeDeliveredTwiceTellsThePlayerOnce() {
        steveOn(lobby, 1L);
        survival.freezes.changeHere(STEVE, true);
        byte[] freeze = wire.removeFirst();

        deliver(List.of(freeze, freeze));

        assertThat(lobby.frozen).contains(STEVE.uuid());
        assertThat(lobby.toldSteve()).containsExactly("moderation.freeze.notify-target");
    }

    /** One backend: its presence view, its frozen set and the freeze use cases over them. */
    private final class Backend {

        final String name;
        final NetworkRoster roster;
        final TrackNetworkPresence track;
        final RecordingNotifier notifier = new RecordingNotifier();
        final Set<UUID> frozen = new HashSet<>();
        final NetworkFreeze freezes;
        final Freeze freeze;

        Backend(String name) {
            this.name = name;
            this.roster = new NetworkRoster(name, clock, Duration.ofSeconds(90), () -> NameCase.INSENSITIVE);
            this.track = new TrackNetworkPresence(roster, NetworkPresenceBroadcast.NONE, NO_NAMES, clock);
            FreezeSync bus = new FreezeSync() {
                @Override
                public void freezeChanged(PlayerRef target, boolean isFrozen, Instant at) {
                    wire.add(NetworkMessageCodec.encode(frame(target, isFrozen, at)));
                }

                @Override
                public void cleared(Instant at) {
                    // Only the proxy reads it.
                }
            };
            this.freezes = new NetworkFreeze(new FrozenSet(frozen), bus, roster, notifier.notifier(), clock);
            this.freeze = new Freeze(
                    freezes,
                    new ModerationGuard(new FakePermissions()),
                    notifier.notifier(),
                    new SilentAudit(),
                    roster);
        }

        /** The lines Steve read on this backend. */
        List<String> toldSteve() {
            return notifier.sent().stream()
                    .filter(sent -> sent.viewer().equals(STEVE))
                    .map(RecordingNotifier.Sent::key)
                    .toList();
        }

        private NetworkMessage frame(PlayerRef target, boolean isFrozen, Instant at) {
            return new FreezeChanged(
                    name, UUID.randomUUID(), target.uuid(), target.name(), isFrozen, at.toEpochMilli());
        }
    }

    /** The frozen set of one backend; it holds a player whether or not they are connected there, as the adapter does. */
    private record FrozenSet(Set<UUID> frozen) implements Sanctions {

        @Override
        public void freeze(PlayerRef target) {
            frozen.add(target.uuid());
        }

        @Override
        public void unfreeze(PlayerRef target) {
            frozen.remove(target.uuid());
        }

        @Override
        public boolean isFrozen(PlayerRef target) {
            return frozen.contains(target.uuid());
        }

        @Override
        public void kick(PlayerRef target, MessageKey reasonKey, String reasonText) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void kickAddress(String ip, Predicate<PlayerRef> kickable, Function<PlayerRef, String> reasonFor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<PlayerRef> onlinePlayers() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void sendToJail(PlayerRef target, String jail) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void releaseFromJail(PlayerRef target) {
            throw new UnsupportedOperationException();
        }
    }
}
