package com.uxplima.uxmessentials.homes.application;

import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.port.HomeServers;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/** {@code /home setserver}: every home recorded on one server moved to another, for a renamed server. */
class SetHomeServerTest {

    private final Servers servers = new Servers("server-1", "server-1", "survival");
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final Audit audit = new Audit();
    private int announcements;

    private SetHomeServer use(String... live) {
        return new SetHomeServer(servers, notifier.notifier(), new Live(Set.of(live)), () -> announcements++, audit);
    }

    @Test
    void everyHomeOnTheOldNameMovesToTheNewOneAndThePeersAreToldOnce() {
        Result<Integer, HomeError> moved = use("lobby").rename(SAM, "server-1", "lobby");

        assertThat(moved.asValue()).contains(2);
        assertThat(servers.rows).containsExactly("lobby", "lobby", "survival");
        assertThat(announcements).isOne();
        assertThat(audit.lines)
                .containsExactly("event=home_server_rename actor=" + SAM.uuid() + " from=server-1 to=lobby moved=2");
        assertThat(notifier.sent()).singleElement().satisfies(line -> {
            assertThat(line.key()).isEqualTo("home.server-set");
            assertThat(line.placeholders()).containsEntry("count", "2");
        });
    }

    @Test
    void aNameThatCannotBeAServersIsRefusedAndNothingIsWritten() {
        Result<Integer, HomeError> moved = use("lobby").rename(SAM, "server-1", "lob by");

        assertThat(moved.asError()).contains(HomeError.INVALID_SERVER);
        assertThat(servers.rows).containsExactly("server-1", "server-1", "survival");
        assertThat(announcements).isZero();
        assertThat(notifier.keys()).containsExactly("home.server-invalid");
    }

    @Test
    void aServerThatIsNotRunningIsSetWithAWarning() {
        use().rename(SAM, "server-1", "lobby");

        assertThat(servers.rows).containsExactly("lobby", "lobby", "survival");
        assertThat(notifier.keys()).containsExactly("home.server-not-running", "home.server-set");
    }

    @Test
    void unscopedClearsTheServerSoTheClaimTakesTheHomesAgainWithoutAWarning() {
        use().rename(SAM, "server-1", Home.UNSCOPED);

        assertThat(servers.rows).containsExactly(Home.UNSCOPED, Home.UNSCOPED, "survival");
        assertThat(notifier.keys()).containsExactly("home.server-set");
    }

    @Test
    void homesOfNoKnownServerAreLeftToTheClaimAndNotMovedByName() {
        servers.rows.set(0, Home.UNSCOPED);

        Result<Integer, HomeError> moved = use("lobby").rename(SAM, Home.UNSCOPED, "lobby");

        assertThat(moved.asError()).contains(HomeError.INVALID_SERVER);
        assertThat(servers.rows).containsExactly(Home.UNSCOPED, "server-1", "survival");
        assertThat(notifier.keys()).containsExactly("home.server-from-unscoped");
        assertThat(audit.lines).isEmpty();
    }

    /** The info lines the use case logs, with their placeholders filled in. */
    private static final class Audit implements Logger {

        final List<String> lines = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            String line = message;
            for (Object arg : args) {
                line = line.replaceFirst("\\{\\}", String.valueOf(arg));
            }
            lines.add(line);
        }

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** The server column of every home of the network, one entry per home. */
    private static final class Servers implements HomeServers {

        final List<String> rows = new ArrayList<>();

        Servers(String... servers) {
            rows.addAll(List.of(servers));
        }

        @Override
        public int rename(String from, String to) {
            int changed = 0;
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).equals(from)) {
                    rows.set(i, to);
                    changed++;
                }
            }
            return changed;
        }
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
