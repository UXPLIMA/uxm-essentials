package com.uxplima.uxmessentials.ranks.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.ranks.application.port.RankActionRunner;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.ManualScheduler;
import com.uxplima.uxmessentials.testing.QuietLog;
import org.junit.jupiter.api.Test;

/**
 * A rank's actions reach the player wherever they are: at once on the server they are on, otherwise kept in the
 * shared store and run once on their next join, on any server.
 */
class RankActionsTest {

    private static final List<String> LINES = List.of("console lp user {uuid} parent set citizen", "message welcome");

    private final ManualScheduler scheduler = new ManualScheduler();
    private final QuietLog log = new QuietLog();
    private final InMemoryPendingRankActions store = new InMemoryPendingRankActions();
    private final Server lobby = new Server();
    private final Server survival = new Server();

    @Test
    void linesForAPlayerOnAnotherServerAreKeptAndRunOnceOnTheirNextJoinThere() {
        survival.here.add(ALEX.uuid());

        lobby.actions.deliver(ALEX, LINES, true);
        scheduler.runAsync();
        assertThat(lobby.ran).isEmpty();

        survival.actions.joined(ALEX);
        scheduler.runAsync();
        lobby.actions.joined(ALEX);
        scheduler.runAsync();

        assertThat(survival.ran).containsExactlyElementsOf(LINES);
        assertThat(lobby.ran).isEmpty();
        assertThat(store.keptFor(ALEX.uuid())).isEmpty();
    }

    @Test
    void aPlayerWhoLeavesBeforeTheirKeptLinesRunHasThemKeptAgain() {
        lobby.actions.deliver(ALEX, LINES, false);
        scheduler.runAsync();

        survival.here.add(ALEX.uuid());
        survival.actions.joined(ALEX);
        scheduler.leave(ALEX);
        scheduler.runAsync();

        assertThat(survival.ran).isEmpty();
        assertThat(store.keptFor(ALEX.uuid())).containsExactlyElementsOf(LINES);
    }

    @Test
    void aStoreThatIsDownIsReportedAndNeverReachesTheCaller() {
        store.fail();

        lobby.actions.deliver(ALEX, LINES, true);
        scheduler.runAsync();
        lobby.actions.joined(ALEX);
        scheduler.runAsync();

        assertThat(log.errors()).hasSize(2);
    }

    /** One server of the network: the players on it, and every line it ran. */
    private final class Server implements RankActionRunner {

        final Set<UUID> here = new HashSet<>();
        final List<String> ran = new ArrayList<>();
        final RankActions actions = new RankActions(this, store, scheduler, log);

        @Override
        public boolean run(PlayerRef who, List<String> actionLines) {
            if (!here.contains(who.uuid())) {
                return false;
            }
            ran.addAll(actionLines);
            return true;
        }
    }
}
