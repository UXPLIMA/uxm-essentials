package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.ranks.application.RankActions;
import com.uxplima.uxmessentials.ranks.application.port.PendingRankActions;
import com.uxplima.uxmessentials.ranks.application.port.RankActionRunner;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import org.junit.jupiter.api.Test;

/**
 * Rank actions kept on one server for a player who is on another run there at once: the kept batch is announced, and
 * the server the player is on takes and runs it. Every line still runs on one server only.
 */
class RankSyncTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");
    private static final List<String> LINES = List.of("console lp user {uuid} parent set citizen", "message welcome");

    private final SharedRows rows = new SharedRows();
    private final Frames frames = new Frames();
    private final Backend lobby = new Backend(false);
    private final Backend survival = new Backend(true);

    @Test
    void actionsKeptForAPlayerOnAnotherServerRunThereAtOnceAndOnlyThere() {
        lobby.actions.deliver(ALEX, LINES, true);
        for (NetworkMessage frame : frames.published) {
            survival.listener.onRemoteChange(frame);
        }

        assertThat(survival.ran).containsExactlyElementsOf(LINES);
        assertThat(lobby.ran).isEmpty();
        assertThat(rows.take(ALEX.uuid())).isEmpty();
    }

    @Test
    void aServerThePlayerIsNotOnLeavesTheKeptActionsForTheirNextJoin() {
        lobby.actions.deliver(ALEX, LINES, true);
        for (NetworkMessage frame : frames.published) {
            lobby.listener.onRemoteChange(frame);
        }

        assertThat(lobby.ran).isEmpty();
        assertThat(rows.take(ALEX.uuid())).containsExactlyElementsOf(LINES);
    }

    /** One backend: whether Alex is on it, what it ran, and its announcing store over the shared rows. */
    private final class Backend implements RankActionRunner {

        final boolean alexHere;
        final List<String> ran = new ArrayList<>();
        final RankActions actions;
        final RemoteSyncListener listener;

        Backend(boolean alexHere) {
            this.alexHere = alexHere;
            this.actions = new RankActions(
                    this, RankSync.pendingActions(rows, frames, new Quiet()), new InlineScheduler(), new Quiet());
            this.listener = RankSync.actionsListener(actions, new Roster(alexHere));
        }

        @Override
        public boolean run(PlayerRef who, List<String> actionLines) {
            if (!alexHere) {
                return false;
            }
            ran.addAll(actionLines);
            return true;
        }
    }

    /** The kept rows every backend shares, as the database holds them. */
    private static final class SharedRows implements PendingRankActions {

        private final Map<UUID, List<String>> kept = new HashMap<>();

        @Override
        public void keep(UUID player, List<String> lines) {
            kept.computeIfAbsent(player, id -> new ArrayList<>()).addAll(lines);
        }

        @Override
        public List<String> take(UUID player) {
            List<String> lines = kept.remove(player);
            return lines == null ? List.of() : List.copyOf(lines);
        }
    }

    /** Who is online on one backend. */
    private record Roster(boolean alexHere) implements PlayerLookup {

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return alexHere && name.equals(ALEX.name()) ? Optional.of(ALEX) : Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return uuid.equals(ALEX.uuid()) ? Optional.of(ALEX) : Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return alexHere && uuid.equals(ALEX.uuid());
        }
    }

    private static final class Frames implements BusPublisher {

        private final List<NetworkMessage> published = new ArrayList<>();

        @Override
        public void publish(NetworkMessage message) {
            published.add(message);
        }

        @Override
        public String serverId() {
            return "lobby";
        }
    }

    /** Runs the async lane and every hop at once. */
    private static final class InlineScheduler implements Scheduler {

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new UnsupportedOperationException("rank actions schedule nothing for later");
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
