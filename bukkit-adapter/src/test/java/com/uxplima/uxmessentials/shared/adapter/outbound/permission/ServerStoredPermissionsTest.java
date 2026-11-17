package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.OfflinePlayer;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

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
import org.mockbukkit.mockbukkit.entity.OfflinePlayerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A player who is not here is read failing closed. One on another backend is asked over the bus first, and its live
 * answer wins; every permission store is read when that backend does not answer, and with none there is no answer.
 * An offline one is held when any source grants the node: LuckPerms, the Vault permission provider, or the default
 * against the operator list here.
 */
class ServerStoredPermissionsTest {

    private static final String NODE = ModerationGuard.EXEMPT_NODE;
    private static final PlayerRef STRANGER = new PlayerRef(new UUID(9L, 9L), "Stranger");

    private ServerMock server;
    private PlayerRef olaf;
    private PlayerRef bert;
    private final Lane lane = new Lane();
    private final Presence presence = new Presence();
    private final List<String> warnings = new ArrayList<>();
    private final List<NetworkMessage> sent = new ArrayList<>();

    @BeforeEach
    void start() {
        server = MockBukkit.mock(new StrangerServer());
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
    void whatLuckPermsGrantsIsLoadedOnTheAsyncLane() {
        CompletableFuture<Answer> answer = withLuckPerms(store(Answer.HELD)).read(bert, NODE);

        assertThat(answer).isNotDone();
        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.HELD);
    }

    @Test
    void anOperatorStaysExemptWhenLuckPermsGrantsNothingOrHoldsNoDataForThem() {
        assertThat(withLuckPerms(store(Answer.NOT_HELD)).read(olaf, NODE)).isCompletedWithValue(Answer.HELD);
    }

    @Test
    void aNonOperatorLuckPermsGrantsNothingIsNotHeld() {
        CompletableFuture<Answer> answer = withLuckPerms(store(Answer.NOT_HELD)).read(bert, NODE);

        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.NOT_HELD);
    }

    @Test
    void aStoreThatFailsGivesNoAnswerAndIsLogged() {
        NodeStore failing =
                (player, node) -> () -> CompletableFuture.failedFuture(new IllegalStateException("db down"));
        CompletableFuture<Answer> answer = withLuckPerms(failing).read(bert, NODE);

        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.NO_ANSWER);
        assertThat(warnings).containsExactly("event=stored_permission_unread player={} node={}");
    }

    @Test
    void aGrantFromAnySourceIsHeldEvenWhenAnotherFails() {
        NodeStore failing =
                (player, node) -> () -> CompletableFuture.failedFuture(new IllegalStateException("db down"));
        CompletableFuture<Answer> answer =
                reader(failing, store(Answer.HELD), Optional.empty()).read(bert, NODE);

        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.HELD);
    }

    @Test
    void aParentPermissionThatGrantsTheNodeByDefaultCountsForAnOperator() {
        server.getPluginManager().removePermission(NODE);
        server.getPluginManager().addPermission(new Permission(NODE, PermissionDefault.FALSE));
        server.getPluginManager().addPermission(new Permission("staff.all", PermissionDefault.OP, Map.of(NODE, true)));

        assertThat(reader(null, null, Optional.empty()).read(olaf, NODE)).isCompletedWithValue(Answer.HELD);
        assertThat(reader(null, null, Optional.empty()).read(bert, NODE)).isCompletedWithValue(Answer.NOT_HELD);
    }

    @Test
    void withNoPermissionPluginAnOfflinePlayerIsReadFromTheOperatorList() {
        ServerStoredPermissions reader = reader(null, null, Optional.of(bus()));

        assertThat(reader.read(olaf, NODE)).isCompletedWithValue(Answer.HELD);
        assertThat(reader.read(bert, NODE)).isCompletedWithValue(Answer.NOT_HELD);
    }

    @Test
    void aProviderVaultRegisteredUnderItsOwnNameIsAskedAndItsGrantHolds() {
        register(new FakeProvider("PermissionsEx", "Bert"));

        CompletableFuture<Answer> answer = ServerStoredPermissions.of(
                        server, lane, presence, new Warnings(warnings), Optional.empty())
                .read(bert, NODE);
        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.HELD);
    }

    @Test
    void vaultsOwnStandInIsNotReadAndTheOperatorListAnswers() {
        register(new FakeProvider(VaultNodeStore.SUPER_PERMS, "Bert"));

        assertThat(ServerStoredPermissions.of(server, lane, presence, new Warnings(warnings), Optional.empty())
                        .read(bert, NODE))
                .isCompletedWithValue(Answer.NOT_HELD);
    }

    @Test
    void aProviderThatSaysNoDoesNotOutweighTheOperatorDefault() {
        register(new FakeProvider("PermissionsEx"));

        assertThat(ServerStoredPermissions.of(server, lane, presence, new Warnings(warnings), Optional.empty())
                        .read(olaf, NODE))
                .isCompletedWithValue(Answer.HELD);
    }

    @Test
    void aPlayerWhoNeverJoinedHereCannotBeAskedAboutByName() {
        register(new FakeProvider("PermissionsEx"));

        CompletableFuture<Answer> answer = ServerStoredPermissions.of(
                        server, lane, presence, new Warnings(warnings), Optional.empty())
                .read(STRANGER, NODE);
        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.NO_SOURCE);
    }

    @Test
    void aPlayerOnAnotherBackendIsAskedOverTheBusFirstAndItsLiveAnswerWinsOverLuckPerms() {
        presence.at = Optional.of(new OnlinePlayer(olaf, "lobby"));
        BusPermissionReads reads = bus();

        CompletableFuture<Answer> answer =
                reader(store(Answer.HELD), null, Optional.of(reads)).read(olaf, NODE);
        PermissionQueryFrame asked = (PermissionQueryFrame) sent.getFirst();
        reads.onRemoteChange(new PermissionQueryFrame(
                "lobby", asked.queryId(), "survival", olaf.uuid(), NODE, PermissionQueryFrame.NOT_HELD));

        assertThat(asked.toServer()).isEqualTo("lobby");
        assertThat(asked.step()).isEqualTo(PermissionQueryFrame.ASK);
        assertThat(answer).isCompletedWithValue(Answer.NOT_HELD);
    }

    @Test
    void whenThatBackendCannotAnswerLuckPermsIsRead() {
        presence.at = Optional.of(new OnlinePlayer(bert, "lobby"));
        BusPermissionReads reads = bus();

        CompletableFuture<Answer> answer =
                reader(store(Answer.HELD), null, Optional.of(reads)).read(bert, NODE);
        PermissionQueryFrame asked = (PermissionQueryFrame) sent.getFirst();
        reads.onRemoteChange(new PermissionQueryFrame(
                "lobby", asked.queryId(), "survival", bert.uuid(), NODE, PermissionQueryFrame.NO_ANSWER));
        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.HELD);
    }

    @Test
    void anAnswerFromAnotherBackendOrAboutAnotherPlayerIsNotTaken() {
        presence.at = Optional.of(new OnlinePlayer(bert, "lobby"));
        BusPermissionReads reads = bus();

        CompletableFuture<Answer> answer =
                reader(null, null, Optional.of(reads)).read(bert, NODE);
        PermissionQueryFrame asked = (PermissionQueryFrame) sent.getFirst();
        reads.onRemoteChange(new PermissionQueryFrame(
                "hub", asked.queryId(), "survival", bert.uuid(), NODE, PermissionQueryFrame.NOT_HELD));
        reads.onRemoteChange(new PermissionQueryFrame(
                "lobby", asked.queryId(), "survival", olaf.uuid(), NODE, PermissionQueryFrame.NOT_HELD));

        assertThat(answer).isNotDone();
    }

    @Test
    void withoutAPermissionStoreOrABusAPlayerOnAnotherBackendHasNoSourceEvenAsAnOperatorHere() {
        presence.at = Optional.of(new OnlinePlayer(olaf, "lobby"));

        assertThat(reader(null, null, Optional.empty()).read(olaf, NODE)).isCompletedWithValue(Answer.NO_SOURCE);
    }

    @Test
    void whenThatBackendCannotAnswerTheVaultProviderIsReadAndItsGrantHolds() {
        register(new FakeProvider("PermissionsEx", "Bert"));
        presence.at = Optional.of(new OnlinePlayer(bert, "lobby"));
        BusPermissionReads reads = bus();

        CompletableFuture<Answer> answer = ServerStoredPermissions.of(
                        server, lane, presence, new Warnings(warnings), Optional.of(reads))
                .read(bert, NODE);
        PermissionQueryFrame asked = (PermissionQueryFrame) sent.getFirst();
        reads.onRemoteChange(new PermissionQueryFrame(
                "lobby", asked.queryId(), "survival", bert.uuid(), NODE, PermissionQueryFrame.NO_ANSWER));
        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.HELD);
    }

    @Test
    void whenThatBackendCannotAnswerAndNoStoreGrantsTheNodeItIsNotHeld() {
        register(new FakeProvider("PermissionsEx"));
        presence.at = Optional.of(new OnlinePlayer(bert, "lobby"));
        BusPermissionReads reads = bus();

        CompletableFuture<Answer> answer = ServerStoredPermissions.of(
                        server, lane, presence, new Warnings(warnings), Optional.of(reads))
                .read(bert, NODE);
        PermissionQueryFrame asked = (PermissionQueryFrame) sent.getFirst();
        reads.onRemoteChange(new PermissionQueryFrame(
                "lobby", asked.queryId(), "survival", bert.uuid(), NODE, PermissionQueryFrame.NO_ANSWER));
        lane.runAll();

        assertThat(answer).isCompletedWithValue(Answer.NOT_HELD);
    }

    /** A server that, like a real one, knows no name for a player who never joined it. */
    // ServerMock's own raw ban list signature, which this subclass inherits and does not touch.
    @SuppressWarnings("unchecked")
    private static final class StrangerServer extends ServerMock {

        @Override
        public OfflinePlayer getOfflinePlayer(UUID id) {
            return id.equals(STRANGER.uuid()) ? new OfflinePlayerMock(id, null) : super.getOfflinePlayer(id);
        }
    }

    private void register(FakeProvider provider) {
        Plugin vault = MockBukkit.createMockPlugin("Vault");
        server.getServicesManager()
                .register(net.milkbowl.vault.permission.Permission.class, provider, vault, ServicePriority.Normal);
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

    private static NodeStore store(Answer answer) {
        return (player, node) -> () -> CompletableFuture.completedFuture(answer);
    }

    /** A permission plugin as Vault hands it out: it grants the node to the players named, by name, and to no one else. */
    // Vault's abstract methods are the old by-name ones, deprecated but still what a provider implements.
    @SuppressWarnings("deprecation")
    private static final class FakeProvider extends net.milkbowl.vault.permission.Permission {

        private final String name;
        private final Set<String> granted;

        FakeProvider(String name, String... granted) {
            this.name = name;
            this.granted = Set.of(granted);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean hasSuperPermsCompat() {
            return false;
        }

        @Override
        public boolean playerHas(String world, String player, String permission) {
            return permission.equals(NODE) && granted.contains(player);
        }

        @Override
        public boolean playerAdd(String world, String player, String permission) {
            return false;
        }

        @Override
        public boolean playerRemove(String world, String player, String permission) {
            return false;
        }

        @Override
        public boolean groupHas(String world, String group, String permission) {
            return false;
        }

        @Override
        public boolean groupAdd(String world, String group, String permission) {
            return false;
        }

        @Override
        public boolean groupRemove(String world, String group, String permission) {
            return false;
        }

        @Override
        public boolean playerInGroup(String world, String player, String group) {
            return false;
        }

        @Override
        public boolean playerAddGroup(String world, String player, String group) {
            return false;
        }

        @Override
        public boolean playerRemoveGroup(String world, String player, String group) {
            return false;
        }

        @Override
        public String[] getPlayerGroups(String world, String player) {
            return new String[0];
        }

        @Override
        public String getPrimaryGroup(String world, String player) {
            return "default";
        }

        @Override
        public String[] getGroups() {
            return new String[0];
        }

        @Override
        public boolean hasGroupSupport() {
            return false;
        }
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
