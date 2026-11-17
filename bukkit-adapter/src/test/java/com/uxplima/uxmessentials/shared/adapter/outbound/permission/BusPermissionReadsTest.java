package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.moderation.application.ModerationGuard;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.StoredPermissions.Answer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A backend asks the backend a player is connected to whether they hold a node, and that backend reads it live on the
 * player's own thread. The question and the answer cross the wire as frames; a question nobody answers is no answer at
 * all, never a guess.
 */
class BusPermissionReadsTest {

    private static final String NODE = ModerationGuard.EXEMPT_NODE;
    private static final PlayerRef ADMIN = new PlayerRef(new UUID(0L, 9L), "Ada");

    private final Wire wire = new Wire();
    private final Backend survival = new Backend("survival", wire);
    private final Backend lobby = new Backend("lobby", wire);

    @Test
    void anExemptPlayerOnAnotherBackendIsReadAsHeldThere() {
        lobby.online.add(ADMIN.uuid());
        lobby.granted.add(ADMIN.uuid());

        CompletableFuture<Answer> answer = survival.reads.ask(ADMIN, NODE, "lobby");
        wire.deliver();
        assertThat(answer).as("read on the player's thread, not yet run").isNotDone();
        lobby.scheduler.runEntityTasks();
        wire.deliver();

        assertThat(answer).isCompletedWithValue(Answer.HELD);
    }

    @Test
    void aPlayerWithoutTheNodeOnAnotherBackendIsReadAsNotHeld() {
        lobby.online.add(ADMIN.uuid());

        CompletableFuture<Answer> answer = survival.reads.ask(ADMIN, NODE, "lobby");
        wire.deliver();
        lobby.scheduler.runEntityTasks();
        wire.deliver();

        assertThat(answer).isCompletedWithValue(Answer.NOT_HELD);
    }

    @Test
    void aPlayerWhoLeftThatBackendBeforeTheQuestionArrivedIsNoAnswer() {
        CompletableFuture<Answer> answer = survival.reads.ask(ADMIN, NODE, "lobby");
        wire.deliver();
        wire.deliver();

        assertThat(answer).isCompletedWithValue(Answer.NO_ANSWER);
    }

    @Test
    void aQuestionNobodyAnswersHasNoSourceOnceItsTimeIsUp() {
        lobby.online.add(ADMIN.uuid());
        lobby.granted.add(ADMIN.uuid());

        CompletableFuture<Answer> answer = survival.reads.ask(ADMIN, NODE, "lobby");
        wire.lose();
        assertThat(survival.scheduler.delays).containsExactly(BusPermissionReads.WAIT);
        assertThat(answer).isNotDone();
        survival.scheduler.runDelayed();

        assertThat(answer).isCompletedWithValue(Answer.NO_SOURCE);
    }

    @Test
    void aQuestionForAnotherBackendIsLeftAlone() {
        Backend hub = new Backend("hub", wire);
        hub.online.add(ADMIN.uuid());
        lobby.online.add(ADMIN.uuid());

        var unused = survival.reads.ask(ADMIN, NODE, "lobby");
        wire.deliver();

        assertThat(hub.scheduler.entityTasks).isEmpty();
        assertThat(lobby.scheduler.entityTasks).hasSize(1);
    }

    /** One backend: its bus end, who is online on it, what they hold, and its threads. */
    private static final class Backend {

        final Set<UUID> online = new HashSet<>();
        final Set<UUID> granted = new HashSet<>();
        final Threads scheduler = new Threads();
        final BusPermissionReads reads;

        Backend(String name, Wire wire) {
            BusPublisher publisher = new BusPublisher() {
                @Override
                public void publish(NetworkMessage message) {
                    wire.send(name, message);
                }

                @Override
                public String serverId() {
                    return name;
                }
            };
            Permissions live = new Permissions() {
                @Override
                public boolean has(PlayerRef who, String node) {
                    return node.equals(NODE) && granted.contains(who.uuid());
                }

                @Override
                public QuotaResult resolveQuota(
                        PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
                    throw new AssertionError("no quota is read");
                }
            };
            PlayerLookup lookup = new PlayerLookup() {
                @Override
                public Optional<PlayerRef> findOnlineByName(String playerName) {
                    return Optional.empty();
                }

                @Override
                public Optional<PlayerRef> findByUuid(UUID uuid) {
                    return online.contains(uuid) ? Optional.of(new PlayerRef(uuid, "Ada")) : Optional.empty();
                }

                @Override
                public boolean isOnline(UUID uuid) {
                    return online.contains(uuid);
                }
            };
            this.reads = new BusPermissionReads(publisher, live, lookup, scheduler, new Silent());
            wire.backends.add(this);
            wire.names.add(name);
        }
    }

    /** Frames sent by one backend reach every other backend, through the codec, when the test delivers them. */
    private static final class Wire {

        final List<Backend> backends = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        private final List<Sent> queued = new ArrayList<>();

        void send(String from, NetworkMessage message) {
            queued.add(new Sent(from, NetworkMessageCodec.encode(message)));
        }

        void deliver() {
            List<Sent> now = new ArrayList<>(queued);
            queued.clear();
            for (Sent sent : now) {
                for (int i = 0; i < backends.size(); i++) {
                    if (!names.get(i).equals(sent.from())) {
                        backends.get(i).reads.onRemoteChange(NetworkMessageCodec.decode(sent.frame()));
                    }
                }
            }
        }

        void lose() {
            queued.clear();
        }

        /** One frame on the wire, as the codec wrote it. */
        private static final class Sent {

            private final String from;
            private final byte[] frame;

            Sent(String from, byte[] frame) {
                this.from = from;
                this.frame = frame;
            }

            String from() {
                return from;
            }

            byte[] frame() {
                return frame;
            }
        }
    }

    /** Entity tasks and delayed tasks run when the test says. */
    private static final class Threads implements Scheduler {

        final List<Runnable> entityTasks = new ArrayList<>();
        final List<Duration> delays = new ArrayList<>();
        private final List<Runnable> delayed = new ArrayList<>();

        void runEntityTasks() {
            List<Runnable> now = new ArrayList<>(entityTasks);
            entityTasks.clear();
            now.forEach(Runnable::run);
        }

        void runDelayed() {
            List<Runnable> now = new ArrayList<>(delayed);
            delayed.clear();
            now.forEach(Runnable::run);
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            entityTasks.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            delays.add(delay);
            delayed.add(task);
        }

        @Override
        public void async(Runnable task) {
            throw new AssertionError("nothing runs on the async lane");
        }

        @Override
        public void onGlobal(Runnable task) {
            throw new AssertionError("nothing runs on the global region");
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new AssertionError("nothing runs on a region");
        }
    }

    private static final class Silent implements Logger {

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
