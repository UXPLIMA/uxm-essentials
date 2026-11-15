package com.uxplima.uxmessentials.warps.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.Test;

/** Fixing the server a warp lives on by hand, one warp or all of them. */
class SetWarpServerTest {

    private static final WarpName MARKET = WarpName.of("market");
    private static final WarpName PLOTS = WarpName.of("plots");

    private final WarpFakes.Warps warps = new WarpFakes.Warps();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final Audit audit = new Audit();
    private int announcements;
    private final SetWarpServer setServer = new SetWarpServer(
            warps, notifier.notifier(), new Live(Set.of("lobby", "survival")), () -> announcements++, audit);

    SetWarpServerTest() {
        warps.put(Warp.create(MARKET, at(0, 64, 0), SAM, NOW).onServer("lobby"));
        warps.put(Warp.create(PLOTS, at(9, 64, 9), SAM, NOW).onServer("lobby"));
    }

    private Optional<String> serverOf(WarpName name) {
        return warps.find(name).flatMap(Warp::knownServer);
    }

    @Test
    void oneWarpMovesToTheNamedServer() {
        assertThat(setServer.set(ALEX, MARKET, "survival").isOk()).isTrue();

        assertThat(serverOf(MARKET)).contains("survival");
        assertThat(serverOf(PLOTS)).contains("lobby");
        assertThat(announcements).isOne();
        assertThat(notifier.keys()).containsExactly("warp.server-set");
        assertThat(audit.lines)
                .containsExactly("event=warp_server_rename actor=" + ALEX.uuid()
                        + " warp=market from=lobby to=survival moved=1");
    }

    @Test
    void everyWarpMovesAndPeersAreToldOnce() {
        assertThat(setServer.setAll(ALEX, "survival").asValue()).contains(2);

        assertThat(serverOf(MARKET)).contains("survival");
        assertThat(serverOf(PLOTS)).contains("survival");
        assertThat(announcements).isOne();
        assertThat(audit.lines)
                .containsExactly("event=warp_server_rename actor=" + ALEX.uuid() + " warp=* to=survival moved=2");
    }

    @Test
    void aNameNoBackendCouldHaveIsRefusedAndNothingIsWritten() {
        assertThat(setServer.set(ALEX, MARKET, "lobby two").asError()).contains(WarpError.INVALID_SERVER);
        assertThat(setServer.setAll(ALEX, "x".repeat(65)).asError()).contains(WarpError.INVALID_SERVER);

        assertThat(serverOf(MARKET)).contains("lobby");
        assertThat(announcements).isZero();
        assertThat(notifier.keys()).containsExactly("warp.server-invalid", "warp.server-invalid");
        assertThat(audit.lines).isEmpty();
    }

    @Test
    void aServerThatIsNotRunningIsSetWithAWarning() {
        setServer.set(ALEX, MARKET, "creative");

        assertThat(serverOf(MARKET)).contains("creative");
        assertThat(notifier.keys()).containsExactly("warp.server-not-running", "warp.server-set");
    }

    @Test
    void unscopedClearsTheServerSoTheWarpIsUsedWhereverItIsAsked() {
        setServer.set(ALEX, MARKET, Warp.UNSCOPED);

        assertThat(serverOf(MARKET)).isEmpty();
        assertThat(warps.find(MARKET).orElseThrow().livesElsewhere("survival")).isFalse();
        assertThat(notifier.keys()).containsExactly("warp.server-set");
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
