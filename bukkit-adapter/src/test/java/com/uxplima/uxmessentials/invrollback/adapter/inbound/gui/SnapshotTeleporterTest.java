package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link SnapshotTeleporter}: teleporting to a snapshot with a recorded location confirms
 * the destination it read from the payload; a snapshot with no recorded location refuses gracefully; and a
 * snapshot whose world is no longer loaded refuses with the world-unavailable line. The {@code teleportAsync} stub
 * throws under MockBukkit, so the fail-soft dispatch is exercised on the confirming path too.
 */
class SnapshotTeleporterTest {

    private static final Instant WHEN = Instant.parse("2026-07-17T12:00:00Z");

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void teleportsToTheStoredLocationAndConfirmsIt() {
        World world = server.addSimpleWorld("world");
        PlayerMock staff = server.addPlayer("Staff");
        PlayerRef target = new PlayerRef(UUID.randomUUID(), "Victim");
        Position location = new Position(new WorldRef(world.getUID(), "world"), 10.0, 65.0, -20.0, 0f, 0f);
        Snapshot snapshot = located(target, location);
        List<String> sent = new ArrayList<>();

        teleporter(sent).teleport(ref(staff), target, snapshot);

        assertThat(sent)
                .anyMatch(line -> line.contains("invrollback.teleported") && line.contains("world 10, 65, -20"));
    }

    @Test
    void refusesGracefullyWhenTheSnapshotHasNoLocation() {
        PlayerMock staff = server.addPlayer("Staff");
        PlayerRef target = new PlayerRef(UUID.randomUUID(), "Victim");
        // Old-shape payload: encoded with the two-arg overload, so it carries no location.
        Snapshot snapshot = Snapshot.capture(
                target.uuid(), SnapshotCause.DEATH, WHEN, InventorySnapshotCodec.encode(new ItemStack[41], null));
        List<String> sent = new ArrayList<>();

        teleporter(sent).teleport(ref(staff), target, snapshot);

        assertThat(sent).anyMatch(line -> line.contains("invrollback.no-location"));
    }

    @Test
    void refusesWhenTheRecordedWorldIsNoLongerLoaded() {
        PlayerMock staff = server.addPlayer("Staff");
        PlayerRef target = new PlayerRef(UUID.randomUUID(), "Victim");
        Position location = new Position(new WorldRef(UUID.randomUUID(), "ghost_world"), 5.0, 70.0, 5.0, 0f, 0f);
        Snapshot snapshot = located(target, location);
        List<String> sent = new ArrayList<>();

        teleporter(sent).teleport(ref(staff), target, snapshot);

        assertThat(sent).anyMatch(line -> line.contains("invrollback.world-unavailable"));
    }

    private static Snapshot located(PlayerRef target, Position location) {
        byte[] payload = InventorySnapshotCodec.encode(new ItemStack[41], null, location);
        return Snapshot.capture(target.uuid(), SnapshotCause.DEATH, WHEN, payload);
    }

    private static SnapshotTeleporter teleporter(List<String> sent) {
        return new SnapshotTeleporter(inlineScheduler(), substituting(), sink(sent), noopLog());
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** Echoes the key and the placeholder map so an assertion can see the location label that was passed. */
    private static Messages substituting() {
        return (viewer, key, placeholders) -> key.key() + " " + placeholders;
    }

    private static MessageSink sink(List<String> sent) {
        return (viewer, renderedText) -> sent.add(renderedText);
    }

    private static Logger noopLog() {
        return new Logger() {
            @Override
            public void info(String message, Object... args) {}

            @Override
            public void warn(String message, Object... args) {}

            @Override
            public void error(String message, Throwable cause) {}

            @Override
            public void debug(String message, Object... args) {}
        };
    }

    private static Scheduler inlineScheduler() {
        return new Scheduler() {
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
                task.run();
            }

            @Override
            public void asyncAfter(Duration delay, Runnable task) {
                task.run();
            }
        };
    }
}
