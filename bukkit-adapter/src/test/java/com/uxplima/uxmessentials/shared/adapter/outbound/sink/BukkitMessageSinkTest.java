package com.uxplima.uxmessentials.shared.adapter.outbound.sink;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class BukkitMessageSinkTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ServerMock server;
    private RecordingScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        scheduler = new RecordingScheduler();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void systemActorFeedbackIsDeliveredToConsoleOnGlobalThread() {
        BukkitMessageSink sink = new BukkitMessageSink(scheduler, "<gray>[uxm]</gray>");

        sink.deliver(PlayerRef.system("CONSOLE"), "<prefix> <green>created</green>");

        assertThat(scheduler.globalTasks).hasSize(1);
        assertThat(scheduler.entityTasks).isEmpty();
        assertThat(PLAIN.serialize(server.getConsoleSender().nextComponentMessage()))
                .isEqualTo("[uxm] created");
    }

    private static final class RecordingScheduler implements Scheduler {
        private final List<Runnable> globalTasks = new ArrayList<>();
        private final List<Runnable> entityTasks = new ArrayList<>();

        @Override
        public void onGlobal(Runnable task) {
            globalTasks.add(task);
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            entityTasks.add(task);
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
    }
}
