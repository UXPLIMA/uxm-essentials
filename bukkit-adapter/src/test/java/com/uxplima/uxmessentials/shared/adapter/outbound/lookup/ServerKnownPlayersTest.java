package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;

/**
 * Only the ids are taken on the global region; the names, read from each player's data file, are read off it, only
 * for the players the shared list lacks, and one unreadable file does not keep the others off the list.
 */
class ServerKnownPlayersTest {

    private static final UUID ALEX = new UUID(0L, 1L);
    private static final UUID SAM = new UUID(0L, 2L);
    private static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6");
    private static final UUID BROKEN = new UUID(0L, 4L);

    private final GlobalRegion global = new GlobalRegion();
    private final List<UUID> readOnGlobal = new ArrayList<>();
    private final List<UUID> read = new ArrayList<>();
    private final List<PlayerName> added = new ArrayList<>();

    @Test
    void theNamesTheListLacksAreReadOffTheGlobalRegionAndAdded() {
        ServerKnownPlayers known = new ServerKnownPlayers(
                global,
                () -> {
                    assertThat(global.running).isTrue();
                    return List.of(ALEX, SAM, BEDROCK, BROKEN);
                },
                id -> {
                    if (global.running) {
                        readOnGlobal.add(id);
                    }
                    read.add(id);
                    if (id.equals(BROKEN)) {
                        throw new IllegalStateException("corrupt player data");
                    }
                    String name = id.equals(BEDROCK) ? ".BedrockGamer123" : "Sam";
                    return Optional.of(new PlayerName(id, name, 0L));
                },
                () -> Set.of(ALEX),
                rows -> {
                    added.addAll(rows);
                    return rows.size();
                },
                new QuietLog());

        assertThat(known.recordAll()).isEqualTo(2);

        assertThat(global.calls).isEqualTo(1);
        assertThat(readOnGlobal).isEmpty();
        assertThat(read).containsExactly(SAM, BEDROCK, BROKEN);
        assertThat(added).extracting(PlayerName::name).containsExactly("Sam", ".BedrockGamer123");
    }

    /** The global region, run in place while saying so. */
    private static final class GlobalRegion implements Scheduler {

        boolean running;
        int calls;

        @Override
        public void onGlobal(Runnable task) {
            calls++;
            running = true;
            try {
                task.run();
            } finally {
                running = false;
            }
        }

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void onEntity(PlayerRef player, Runnable task) {}

        @Override
        public void async(Runnable task) {}

        @Override
        public void asyncAfter(Duration delay, Runnable task) {}
    }

    private static final class QuietLog implements Logger {

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
