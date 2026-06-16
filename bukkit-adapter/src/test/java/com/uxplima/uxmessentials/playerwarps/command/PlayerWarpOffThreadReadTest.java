package com.uxplima.uxmessentials.playerwarps.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.playerwarps.adapter.PlayerWarpServices;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.command.PlayerWarpCommand;
import com.uxplima.uxmessentials.playerwarps.application.DelPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.ListPlayerWarps;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpNotifier;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpQuota;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarpVisibility;
import com.uxplima.uxmessentials.playerwarps.application.UsePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Pins that {@code /pwarp} resolves the player-warp store <em>off</em> the command (tick/region) thread. A
 * {@code /pwarp} subcommand that gates on the warp existing — here {@code rating} — must not read the database
 * on the thread Brigadier dispatched it on; the read belongs in a {@link Scheduler#async} task that bridges its
 * feedback back to the player's region thread, the same shape {@code /home} uses.
 *
 * <p>The scheduler here is a <em>deferring</em> double: {@code async} captures the task without running it, and
 * {@code onEntity} runs inline (the region bridge). So after dispatch the repository has seen zero reads —
 * proving the lookup did not run on the command thread — and only once the captured task is drained does the
 * read happen and the feedback land.
 */
class PlayerWarpOffThreadReadTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerWarpName HUB = PlayerWarpName.of("hub");

    private ServerMock server;
    private PlayerMock player;
    private CountingRepository repository;
    private DeferringScheduler scheduler;
    private RecordingSink sink;
    private PlayerWarpServices services;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        player.setOp(true); // the /pwarp node gates on a permission; op satisfies it without a permission wiring
        repository = new CountingRepository();
        scheduler = new DeferringScheduler();
        sink = new RecordingSink();
        services = services();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void ratingResolvesTheWarpOffTheCommandThread() {
        repository.store(new PlayerWarp(ref(), HUB, Position.of(WORLD, 1, 64, 1), true, Instant.ofEpochMilli(1_000)));
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand();

        execute(dispatcher, "pwarp rating hub Alice");

        // The command returned without touching the database — the read was handed to scheduler.async.
        assertThat(repository.reads).isZero();
        assertThat(scheduler.deferred).hasSize(1);

        scheduler.drain();

        // Draining the captured task is what performed the existence/visibility read and the rating feedback,
        // which the region bridge (onEntity, run inline here) delivered to the player.
        assertThat(repository.reads).isPositive();
        assertThat(player.nextMessage()).isNotNull();
    }

    private CommandDispatcher<CommandSourceStack> registerCommand() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new PlayerWarpCommand(services, new KeyMessages()).build());
        return dispatcher;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private PlayerRef ref() {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private PlayerWarpServices services() {
        PlayerWarpNotifier notifier = new PlayerWarpNotifier(new KeyMessages(), sink);
        Permissions permissions = new AllowAllPermissions();
        return new PlayerWarpServices(
                new SetPlayerWarp(
                        repository,
                        new PlayerWarpQuota(permissions, 3),
                        notifier,
                        event -> {},
                        java.time.Clock.systemUTC(),
                        List.of()),
                new DelPlayerWarp(repository, notifier, event -> {}),
                new UsePlayerWarp(repository, new NoTeleport(), notifier, position -> true, permissions),
                new ListPlayerWarps(repository, notifier),
                new SetPlayerWarpVisibility(repository, notifier),
                new NamingLookup(),
                repository,
                null,
                scheduler);
    }

    /** Counts repository reads and serves a single owner's warps from memory. */
    private static final class CountingRepository implements PlayerWarpRepository {
        private final Map<String, PlayerWarp> byName = new LinkedHashMap<>();
        private int reads;

        void store(PlayerWarp warp) {
            byName.put(warp.name().value(), warp);
        }

        @Override
        public Optional<PlayerWarp> find(PlayerRef owner, PlayerWarpName name) {
            reads++;
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<PlayerWarp> ownedBy(PlayerRef owner) {
            reads++;
            return List.copyOf(byName.values());
        }

        @Override
        public List<PlayerWarp> publicOf(PlayerRef owner) {
            reads++;
            return List.copyOf(byName.values());
        }

        @Override
        public int count(PlayerRef owner) {
            reads++;
            return byName.size();
        }

        @Override
        public boolean exists(PlayerRef owner, PlayerWarpName name) {
            reads++;
            return byName.containsKey(name.value());
        }

        @Override
        public void save(PlayerWarp warp) {
            store(warp);
        }

        @Override
        public void delete(PlayerRef owner, PlayerWarpName name) {
            byName.remove(name.value());
        }

        @Override
        public Optional<List<PlayerWarp>> peekOwned(PlayerRef owner) {
            return Optional.of(List.copyOf(byName.values()));
        }

        @Override
        public void rate(PlayerRef owner, PlayerWarpName name, UUID player, double rating) {}

        @Override
        public double averageRating(PlayerRef owner, PlayerWarpName name) {
            return 4.0;
        }
    }

    /** Captures async tasks without running them; runs the region bridge inline. */
    private static final class DeferringScheduler implements Scheduler {
        private final List<Runnable> deferred = new ArrayList<>();

        void drain() {
            List<Runnable> snapshot = List.copyOf(deferred);
            deferred.clear();
            snapshot.forEach(Runnable::run);
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

        @Override
        public void async(Runnable task) {
            deferred.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            deferred.add(task);
        }
    }

    private static final class NoTeleport implements PlayerWarpTeleporter {
        @Override
        public void teleportTo(PlayerRef who, PlayerWarp warp) {}
    }

    private final class NamingLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.of(ref());
        }

        @Override
        public Optional<PlayerRef> findByName(String name) {
            return Optional.of(ref());
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.of(ref());
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return true;
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class RecordingSink implements MessageSink {
        private final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }

    private static final class AllowAllPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }
}
