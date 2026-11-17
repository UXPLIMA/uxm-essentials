package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.moderation.application.ModerationGuard;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.StoredPermissions.Answer;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A player who is not here is read the way this server would apply the node if they joined: what LuckPerms stored
 * wins, loaded off the tick, and an unset node falls back to the default against the operator list. Without a store
 * that can answer, a player on another backend or a server with another permission plugin gets no answer at all.
 */
class ServerStoredPermissionsTest {

    private static final String NODE = ModerationGuard.EXEMPT_NODE;

    private ServerMock server;
    private PlayerRef olaf;
    private PlayerRef bert;
    private final Lane lane = new Lane();
    private final Presence presence = new Presence();
    private final List<String> warnings = new ArrayList<>();

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        olaf = seenBefore("Olaf", true);
        bert = seenBefore("Bert", false);
    }

    /** A player who played here once and left, an operator or not. */
    private PlayerRef seenBefore(String name, boolean operator) {
        PlayerMock player = server.addPlayer(name);
        player.setOp(operator);
        player.disconnect();
        return new PlayerRef(player.getUniqueId(), name);
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void whatTheStoreSetsIsLoadedOnTheAsyncLaneAndWins() {
        CompletableFuture<Answer> answer =
                reader(store(Optional.of(false)), false).read(olaf, NODE);

        assertThat(answer).isNotDone();
        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.NOT_HELD);
    }

    @Test
    void aNodeTheStoreLeavesUnsetFallsBackToItsDefaultForAnOperator() {
        CompletableFuture<Answer> answer =
                reader(store(Optional.empty()), false).read(olaf, NODE);

        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.HELD);
    }

    @Test
    void aStoreThatFailsGivesNoAnswerAndIsLogged() {
        CompletableFuture<Answer> answer = reader(
                        (player, node) -> CompletableFuture.failedFuture(new IllegalStateException("db down")), false)
                .read(olaf, NODE);

        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.NO_ANSWER);
        assertThat(warnings).containsExactly("event=stored_permission_unread player={} node={}");
    }

    @Test
    void withoutAStoreAnOfflinePlayerIsReadFromTheOperatorList() {
        ServerStoredPermissions reader = reader(null, false);

        assertThat(reader.read(olaf, NODE)).isCompletedWithValue(Answer.HELD);
        assertThat(reader.read(bert, NODE)).isCompletedWithValue(Answer.NOT_HELD);
    }

    @Test
    void withoutAStoreAPlayerOnAnotherBackendHasNoSource() {
        presence.at = Optional.of(new OnlinePlayer(olaf, "lobby"));

        assertThat(reader(null, false).read(olaf, NODE)).isCompletedWithValue(Answer.NO_SOURCE);
    }

    @Test
    void withoutAStoreAnotherPermissionPluginsGrantsCannotBeReadSoThereIsNoSource() {
        assertThat(reader(null, true).read(olaf, NODE)).isCompletedWithValue(Answer.NO_SOURCE);
    }

    @Test
    void withAStoreAPlayerOnAnotherBackendIsReadFromIt() {
        presence.at = Optional.of(new OnlinePlayer(olaf, "lobby"));
        CompletableFuture<Answer> answer =
                reader(store(Optional.of(true)), false).read(olaf, NODE);

        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.HELD);
    }

    private ServerStoredPermissions reader(@Nullable NodeStore store, boolean otherPlugin) {
        return new ServerStoredPermissions(server, lane, presence, new Warnings(warnings), store, () -> otherPlugin);
    }

    private static NodeStore store(Optional<Boolean> set) {
        return (player, node) -> CompletableFuture.completedFuture(set);
    }

    /** The async lane, run when the test says. Nothing else is asked of it. */
    private static final class Lane implements Scheduler {

        private final List<Runnable> queued = new ArrayList<>();

        void runAll() {
            List<Runnable> now = new ArrayList<>(queued);
            queued.clear();
            now.forEach(Runnable::run);
        }

        @Override
        public void async(Runnable task) {
            queued.add(task);
        }

        @Override
        public void onGlobal(Runnable task) {
            throw new AssertionError("only the async lane is used");
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new AssertionError("only the async lane is used");
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            throw new AssertionError("only the async lane is used");
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new AssertionError("only the async lane is used");
        }
    }

    /** This backend is "survival"; a player is wherever the test puts them. */
    private static final class Presence implements NetworkPresence {

        Optional<OnlinePlayer> at = Optional.empty();

        @Override
        public String localServer() {
            return "survival";
        }

        @Override
        public Optional<OnlinePlayer> find(UUID player) {
            return at.filter(online -> online.player().uuid().equals(player));
        }

        @Override
        public Optional<OnlinePlayer> findByName(String name) {
            return at.filter(online -> online.player().name().equalsIgnoreCase(name));
        }

        @Override
        public List<OnlinePlayer> onServer(String name) {
            return at.filter(online -> online.server().equals(name)).stream().toList();
        }

        @Override
        public List<OnlinePlayer> online() {
            return at.stream().toList();
        }
    }

    private record Warnings(List<String> lines) implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            lines.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
