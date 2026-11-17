package com.uxplima.uxmessentials.moderation.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.adapter.SanctionRows;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** An account the name list already holds existed by then, even with no moderation row; one with no trace is new. */
class KnownAccountTracesTest {

    private static final PlayerRef OLD_STEVE = new PlayerRef(UUID.randomUUID(), "STEVE");
    private static final PlayerRef NEW_STEVE = new PlayerRef(UUID.randomUUID(), "steve");

    private final Names names = new Names();
    private ServerMock server;

    @BeforeEach
    void mock() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void unmock() {
        MockBukkit.unmock();
    }

    @Test
    void aStoredNameIsATraceAndNoTraceAtAllIsNone() {
        names.rows.add(new PlayerName(OLD_STEVE.uuid(), OLD_STEVE.name(), 1_000L));
        KnownAccountTraces traces = new KnownAccountTraces(new SanctionRows(), names, server);

        assertThat(traces.earliest(OLD_STEVE)).contains(Instant.ofEpochMilli(1_000L));
        assertThat(traces.earliest(NEW_STEVE)).isEmpty();
    }

    /** The name list, every row under a name in any case. */
    private static final class Names implements PlayerNameRepository {

        final List<PlayerName> rows = new ArrayList<>();

        @Override
        public List<PlayerName> loadRecent(int limit) {
            return List.of();
        }

        @Override
        public List<PlayerName> underName(String name, int limit) {
            return rows.stream()
                    .filter(row -> row.name().equalsIgnoreCase(name))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Set<String> sharedNames() {
            return Set.of();
        }

        @Override
        public void upsert(PlayerName record) {
            rows.add(record);
        }

        @Override
        public int count() {
            return rows.size();
        }
    }
}
