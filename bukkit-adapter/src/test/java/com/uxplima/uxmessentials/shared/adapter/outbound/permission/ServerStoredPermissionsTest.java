package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.moderation.application.ModerationGuard;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.StoredPermissions.Answer;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.PermissionQueryFrame;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A player who is not here is read the way the server they would play on applies the node. What LuckPerms stored wins,
 * loaded off the tick, and an unset node falls back to the default against the operator list. Without LuckPerms a
 * player on another backend is asked about over the bus, an offline one through the Vault permission provider, and
 * with neither the operator list answers. Nothing here is ever a guess.
 */
class ServerStoredPermissionsTest {

    private static final String NODE = ModerationGuard.EXEMPT_NODE;

    private ServerMock server;
    private PlayerRef olaf;
    private PlayerRef bert;
    private final Lane lane = new Lane();
    private final Presence presence = new Presence();
    private final List<String> warnings = new ArrayList<>();
    private final List<NetworkMessage> sent = new ArrayList<>();

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
    void whatLuckPermsSetsIsLoadedOnTheAsyncLaneAndWins() {
        CompletableFuture<Answer> answer =
                withLuckPerms(store(Optional.of(false))).read(olaf, NODE);

        assertThat(answer).isNotDone();
        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.NOT_HELD);
    }

    @Test
    void aNodeLuckPermsLeavesUnsetFallsBackToItsDefaultForAnOperator() {
        CompletableFuture<Answer> answer =
                withLuckPerms(store(Optional.empty())).read(olaf, NODE);

        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.HELD);
    }

    @Test
    void aStoreThatFailsGivesNoAnswerAndIsLogged() {
        NodeStore failing =
                (player, node) -> () -> CompletableFuture.failedFuture(new IllegalStateException("db down"));
        CompletableFuture<Answer> answer = withLuckPerms(failing).read(olaf, NODE);

        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.NO_ANSWER);
        assertThat(warnings).containsExactly("event=stored_permission_unread player={} node={}");
    }

    @Test
    void withLuckPermsAPlayerOnAnotherBackendIsReadFromItsStorage() {
        presence.at = Optional.of(new OnlinePlayer(olaf, "lobby"));
        CompletableFuture<Answer> answer =
                withLuckPerms(store(Optional.of(true))).read(olaf, NODE);

        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.HELD);
        assertThat(sent).isEmpty();
    }

    @Test
    void withNoPermissionPluginAnOfflinePlayerIsReadFromTheOperatorList() {
        ServerStoredPermissions reader = reader(null, null, Optional.of(bus()));

        assertThat(reader.read(olaf, NODE)).isCompletedWithValue(Answer.HELD);
        assertThat(reader.read(bert, NODE)).isCompletedWithValue(Answer.NOT_HELD);
    }

    @Test
    void anOfflinePlayerIsAskedAboutThroughTheVaultProviderOffTheTick() {
        CompletableFuture<Answer> answer =
                reader(null, store(Optional.of(true)), Optional.empty()).read(bert, NODE);

        assertThat(answer).isNotDone();
        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.HELD);
    }

    @Test
    void aVaultProviderThatThrowsGivesNoAnswer() {
        NodeStore throwing = (player, node) -> () -> {
            throw new IllegalStateException("the provider cannot read offline players");
        };
        CompletableFuture<Answer> answer =
                reader(null, throwing, Optional.empty()).read(bert, NODE);

        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.NO_ANSWER);
    }

    @Test
    void withoutLuckPermsAPlayerOnAnotherBackendIsAskedAboutOverTheBus() {
        presence.at = Optional.of(new OnlinePlayer(olaf, "lobby"));

        CompletableFuture<Answer> answer =
                reader(null, store(Optional.of(false)), Optional.of(bus())).read(olaf, NODE);

        assertThat(answer).isNotDone();
        assertThat(sent).singleElement().isInstanceOfSatisfying(PermissionQueryFrame.class, frame -> {
            assertThat(frame.toServer()).isEqualTo("lobby");
            assertThat(frame.player()).isEqualTo(olaf.uuid());
            assertThat(frame.step()).isEqualTo(PermissionQueryFrame.ASK);
        });
    }

    @Test
    void withoutLuckPermsOrABusAPlayerOnAnotherBackendHasNoSource() {
        presence.at = Optional.of(new OnlinePlayer(olaf, "lobby"));

        assertThat(reader(null, store(Optional.of(false)), Optional.empty()).read(olaf, NODE))
                .isCompletedWithValue(Answer.NO_SOURCE);
    }

    private ServerStoredPermissions withLuckPerms(NodeStore luckPerms) {
        return reader(luckPerms, null, Optional.of(bus()));
    }

    private ServerStoredPermissions reader(
            @Nullable NodeStore luckPerms, @Nullable NodeStore vault, Optional<BusPermissionReads> bus) {
        return new ServerStoredPermissions(server, lane, presence, new Warnings(warnings), luckPerms, () -> vault, bus);
    }

    private BusPermissionReads bus() {
        BusPublisher publisher = new BusPublisher() {
            @Override
            public void publish(NetworkMessage message) {
                sent.add(message);
            }

            @Override
            public String serverId() {
                return "survival";
            }
        };
        Permissions none = new Permissions() {
            @Override
            public boolean has(PlayerRef who, String node) {
                throw new AssertionError("nobody is read live here");
            }

            @Override
            public QuotaResult resolveQuota(
                    PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
                throw new AssertionError("nobody is read live here");
            }
        };
        PlayerLookup nobody = new PlayerLookup() {
            @Override
            public Optional<PlayerRef> findOnlineByName(String name) {
                return Optional.empty();
            }

            @Override
            public Optional<PlayerRef> findByUuid(UUID uuid) {
                return Optional.empty();
            }

            @Override
            public boolean isOnline(UUID uuid) {
                return false;
            }
        };
        return new BusPermissionReads(publisher, none, nobody, lane, new Warnings(warnings));
    }

    private static NodeStore store(Optional<Boolean> set) {
        return (player, node) -> () -> CompletableFuture.completedFuture(set);
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
            // A bus question's time limit; these tests read the question, not its expiry.
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
