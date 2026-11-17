package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Async work that finishes late in the second a scenario settles for, and hands its answer on through more than one
 * hop, is still seen through: a reply to a player goes out on the tick after the hop that decided it, and the scenario
 * must not stop between the two.
 */
@Tag("integration")
class SettleScenarioTest {

    /** The tick of the settled second on which the async work is let finish: late, but inside the second. */
    private static final long LATE_TICK = 19;

    private ScenarioServer server;

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void anAnswerHandedOnTwiceByAsyncWorkThatFinishedLateIsStillDelivered() {
        server = MockBukkit.mock(new ScenarioServer());
        Plugin plugin = MockBukkit.createMockPlugin("LateAnswer");
        long start = server.getScheduler().getCurrentTick();
        CountDownLatch late = new CountDownLatch(1);
        BukkitTask clock = server.getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            if (server.getScheduler().getCurrentTick() - start >= LATE_TICK) {
                                late.countDown();
                            }
                        },
                        1L,
                        1L);
        AtomicBoolean delivered = new AtomicBoolean();

        server.getAsyncScheduler().runNow(plugin, task -> {
            awaitLate(late);
            // The decision hops onto the tick, and the reply it sends hops once more, as a message to a player does.
            server.getGlobalRegionScheduler()
                    .execute(
                            plugin, () -> server.getGlobalRegionScheduler().execute(plugin, () -> delivered.set(true)));
        });
        server.settle();

        clock.cancel();
        assertThat(delivered).isTrue();
    }

    private static void awaitLate(CountDownLatch late) {
        try {
            if (!late.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the settled second never reached its late tick");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the late tick", interrupted);
        }
    }
}
