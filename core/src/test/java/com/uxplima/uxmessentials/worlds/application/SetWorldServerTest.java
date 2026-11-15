package com.uxplima.uxmessentials.worlds.application;

import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import org.junit.jupiter.api.Test;

/** An operator moves every world of one server to another, as after renaming the server. */
class SetWorldServerTest {

    private final List<String> renames = new ArrayList<>();
    private final RecordingNotifier notifier = new RecordingNotifier();

    private SetWorldServer setServer(Set<String> live) {
        return new SetWorldServer(
                (from, to) -> {
                    renames.add(from + "->" + to);
                    return 2;
                },
                notifier.notifier(),
                new Live(live),
                new InlineScheduler(),
                new RecordingLogger());
    }

    @Test
    void theWorldsOfTheOldNameMoveToTheNewOneAndTheOperatorIsToldHowMany() {
        setServer(Set.of("lobby")).rename(SAM, "server-1", "lobby");

        assertThat(renames).containsExactly("server-1->lobby");
        assertThat(notifier.sent()).singleElement().satisfies(line -> {
            assertThat(line.key()).isEqualTo("world.server-set");
            assertThat(line.placeholders()).containsEntry("count", "2");
        });
    }

    @Test
    void theUnclaimedWorldsAreNotMovedByName() {
        setServer(Set.of("lobby")).rename(SAM, NetworkWorld.UNSCOPED, "lobby");

        assertThat(renames).isEmpty();
        assertThat(notifier.keys()).containsExactly("world.server-from-unscoped");
    }

    @Test
    void aNameNoRowCanHoldIsRefused() {
        setServer(Set.of("lobby")).rename(SAM, "server-1", "has space");

        assertThat(renames).isEmpty();
        assertThat(notifier.keys()).containsExactly("world.server-invalid");
    }

    @Test
    void aServerNotRunningIsWarnedAboutAndTheWorldsMoveAnyway() {
        setServer(Set.of()).rename(SAM, "server-1", "survival");

        assertThat(renames).containsExactly("server-1->survival");
        assertThat(notifier.keys()).containsExactly("world.server-not-running", "world.server-set");
    }

    /** A network whose running backends are the given names. */
    private record Live(Set<String> servers) implements NetworkPresence {

        @Override
        public String localServer() {
            return "lobby";
        }

        @Override
        public Optional<OnlinePlayer> find(UUID player) {
            return Optional.empty();
        }

        @Override
        public Optional<OnlinePlayer> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public List<OnlinePlayer> onServer(String server) {
            return List.of();
        }

        @Override
        public List<OnlinePlayer> online() {
            return List.of();
        }

        @Override
        public boolean isLive(String server) {
            return servers.contains(server);
        }
    }
}
