package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.event.player.AsyncChatEvent;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * A chat line is raised on a thread of its own, which the server does not wait for, so a scenario has to wait for
 * every listener to have seen it however long that takes against the tick.
 */
@Tag("integration")
class OffTickEventScenarioTest {

    /** Well past the game seconds a scenario ticks through on its own once the server goes quiet. */
    private static final long HELD_TICKS = 15 * 20L;

    @Test
    void aChatLineAListenerHoldsWhileTheServerTicksOnIsStillSeenThrough() {
        try (RunningPlugin running = RunningPlugin.start()) {
            ScenarioPlayer speaker = running.join("Hana");
            Plugin watcher = MockBukkit.createMockPlugin("SlowChat");
            CountDownLatch ticked = new CountDownLatch(1);
            running.server.getGlobalRegionScheduler().runDelayed(watcher, task -> ticked.countDown(), HELD_TICKS);
            SlowListener slow = new SlowListener(ticked);
            running.server.getPluginManager().registerEvents(slow, watcher);

            AsyncChatEvent line = running.chat(speaker, "hello");

            assertThat(slow.seen).as("the slow listener saw the line").isTrue();
            assertThat(line.isCancelled()).isFalse();
            assertThat(running.problems()).isEmpty();
        }
    }

    /** A listener that holds the chat thread until the server has ticked a while, as one waiting on the tick does. */
    static final class SlowListener implements Listener {

        private final CountDownLatch ticked;
        final AtomicBoolean seen = new AtomicBoolean();

        SlowListener(CountDownLatch ticked) {
            this.ticked = ticked;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onChat(AsyncChatEvent event) throws InterruptedException {
            if (ticked.await(30, TimeUnit.SECONDS)) {
                seen.set(true);
            }
        }
    }
}
