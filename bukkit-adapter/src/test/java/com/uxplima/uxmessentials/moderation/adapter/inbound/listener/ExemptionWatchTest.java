package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.moderation.application.ModerationGuard;
import com.uxplima.uxmessentials.moderation.application.port.ExemptionSightings;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Each backend records whether a player holds the exemption when they join and leave it and when their permissions
 * change, read live from the player and written off their thread; a change that changes nothing writes nothing.
 */
class ExemptionWatchTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");

    private ServerMock server;
    private final List<String> written = new ArrayList<>();
    private final List<Runnable> lane = new ArrayList<>();
    private ExemptionWatch watch;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        ExemptionSightings sightings = new ExemptionSightings() {
            @Override
            public void record(UUID player, String at, boolean held, Instant when) {
                written.add(at + " " + held);
            }

            @Override
            public Sighting sighting(UUID player) {
                throw new AssertionError("nothing is read here");
            }
        };
        watch = new ExemptionWatch(
                sightings, "survival", new Lane(lane), new Quiet(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void aJoinAndALeaveRecordWhatThePlayerHoldsOffTheirThread() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(MockBukkit.createMockPlugin(), ModerationGuard.EXEMPT_NODE, true);

        watch.onJoin(new PlayerJoinEvent(staff, Component.empty()));
        assertThat(written).as("nothing is written on the player's thread").isEmpty();
        runLane();
        watch.onQuit(new PlayerQuitEvent(staff, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        runLane();

        assertThat(written).containsExactly("survival true", "survival true");
    }

    @Test
    void aPermissionChangeIsRecordedOnlyWhenItChangesTheAnswer() {
        PlayerMock staff = server.addPlayer("Staff");
        watch.onJoin(new PlayerJoinEvent(staff, Component.empty()));

        watch.changed(staff);
        staff.addAttachment(MockBukkit.createMockPlugin(), ModerationGuard.EXEMPT_NODE, true);
        watch.changed(staff);
        runLane();

        assertThat(written).containsExactly("survival false", "survival true");
    }

    private void runLane() {
        List<Runnable> now = new ArrayList<>(lane);
        lane.clear();
        now.forEach(Runnable::run);
    }

    /** The async lane, run when the test says. */
    private record Lane(List<Runnable> tasks) implements Scheduler {

        @Override
        public void async(Runnable task) {
            tasks.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new AssertionError("only the async lane is used");
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
