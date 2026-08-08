package com.uxplima.uxmessentials.shared.adapter.inbound.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.event.player.PlayerJoinEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Proves the join handler feeds the name index, which is what lets a later command resolve that account by name
 * on an offline-mode server, where Paper's own name cache is never consulted.
 */
class PlayerNameRecordingListenerTest {

    private ServerMock server;
    private RecordingIndex index;
    private PlayerNameRecordingListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        index = new RecordingIndex();
        listener = new PlayerNameRecordingListener(index);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aJoinRecordsTheNameAgainstTheAccount() {
        PlayerMock player = server.addPlayer("Cofteey");

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(index.byName("cofteey")).contains(new PlayerRef(player.getUniqueId(), "Cofteey"));
    }

    /** The index port, reduced to the one behaviour this listener is responsible for feeding. */
    private static final class RecordingIndex implements PlayerNameIndex {

        private final ConcurrentHashMap<String, PlayerRef> recorded = new ConcurrentHashMap<>();

        @Override
        public Optional<PlayerRef> byName(String name) {
            return Optional.ofNullable(recorded.get(name.toLowerCase(java.util.Locale.ROOT)));
        }

        @Override
        public void record(UUID uuid, String name) {
            recorded.put(name.toLowerCase(java.util.Locale.ROOT), new PlayerRef(uuid, name));
        }
    }
}
