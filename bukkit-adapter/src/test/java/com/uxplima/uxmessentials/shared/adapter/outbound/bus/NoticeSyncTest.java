package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PendingPlayerNotices;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerNotice;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.PlayerNoticesKept;
import org.junit.jupiter.api.Test;

/**
 * A line kept for a player is announced once it is stored, and the server that player is on delivers their kept lines
 * when it hears of it; a server they are not on does nothing.
 */
class NoticeSyncTest {

    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");

    private final List<NetworkMessage> sent = new ArrayList<>();
    private final BusPublisher bus = new BusPublisher() {
        @Override
        public void publish(NetworkMessage message) {
            sent.add(message);
        }

        @Override
        public String serverId() {
            return "lobby";
        }
    };

    @Test
    void aKeptLineIsStoredThenAnnouncedForItsRecipient() {
        List<String> order = new ArrayList<>();
        PendingPlayerNotices store = new PendingPlayerNotices() {
            @Override
            public void keep(PlayerNotice notice) {
                order.add("stored");
            }

            @Override
            public List<PlayerNotice> take(UUID recipient) {
                return List.of();
            }

            @Override
            public int purgeOlderThan(Instant cutoff) {
                return 0;
            }
        };
        BusPublisher ordered = new BusPublisher() {
            @Override
            public void publish(NetworkMessage message) {
                order.add("announced");
                bus.publish(message);
            }

            @Override
            public String serverId() {
                return bus.serverId();
            }
        };

        NoticeSync.pending(store, ordered, new Quiet())
                .keep(new PlayerNotice(
                        SAM.uuid(), new MessageLine(() -> "economy.pay-received", Map.of()), Instant.EPOCH));

        assertThat(order).containsExactly("stored", "announced");
        assertThat(sent).containsExactly(new PlayerNoticesKept("lobby", SAM.uuid()));
    }

    @Test
    void theServerTheRecipientIsOnDeliversTheirKeptLines() {
        List<PlayerRef> delivered = new ArrayList<>();
        Roster roster = new Roster();
        roster.here.add(SAM.uuid());

        NoticeSync.listener(delivered::add, roster, new InlineLane())
                .onRemoteChange(new PlayerNoticesKept("lobby", SAM.uuid()));

        assertThat(delivered).containsExactly(SAM);
    }

    @Test
    void aServerTheRecipientIsNotOnDeliversNothing() {
        List<PlayerRef> delivered = new ArrayList<>();

        NoticeSync.listener(delivered::add, new Roster(), new InlineLane())
                .onRemoteChange(new PlayerNoticesKept("lobby", SAM.uuid()));

        assertThat(delivered).isEmpty();
    }

    private static final class Roster implements PlayerLookup {

        private final Set<UUID> here = new HashSet<>();

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return here.contains(SAM.uuid()) && name.equals(SAM.name()) ? Optional.of(SAM) : Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return uuid.equals(SAM.uuid()) ? Optional.of(SAM) : Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return here.contains(uuid);
        }
    }

    /** Runs the async lane and every hop at once. */
    private static final class InlineLane implements Scheduler {

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new UnsupportedOperationException("kept lines are delivered at once");
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }
    }

    private static final class Quiet implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
