package com.uxplima.uxmessentials.shared.adapter.outbound.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.plugin.Plugin;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Guards the teardown behaviour of the region-dispatch methods. Paper's region schedulers throw
 * {@code IllegalPluginAccessException} when handed work on a disabled plugin, which used to surface as a
 * {@code module_teardown_failed} error every time a display module cleared its state on server stop. The
 * dispatch methods now short-circuit while the plugin is disabling, so a stop-time cleanup is a silent no-op
 * rather than a rejected task.
 */
class FoliaSchedulerTest {

    @Test
    void onGlobalIsANoOpWhileThePluginIsDisabled() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(false);
        FoliaScheduler scheduler = new FoliaScheduler(plugin);
        AtomicBoolean ran = new AtomicBoolean(false);

        // Without the guard this would reach Bukkit.getGlobalRegionScheduler() and blow up; the guard returns first.
        scheduler.onGlobal(() -> ran.set(true));

        assertThat(ran).isFalse();
    }

    @Test
    void entityDispatchIsANoOpWhileThePluginIsDisabled() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(false);
        FoliaScheduler scheduler = new FoliaScheduler(plugin);
        AtomicBoolean ran = new AtomicBoolean(false);
        AtomicBoolean retired = new AtomicBoolean(false);

        scheduler.onEntity(
                new com.uxplima.uxmessentials.shared.domain.PlayerRef(java.util.UUID.randomUUID(), "tester"),
                () -> ran.set(true),
                () -> retired.set(true));

        assertThat(ran).isFalse();
        assertThat(retired).isFalse();
    }

    /**
     * Paper refuses a task on an entity it already retired, answers {@code false} and runs neither the task nor the
     * retired callback. The answer was ignored, so a caller waiting on one of the two waited forever: the HUD loop
     * kept such a player marked as waiting and never drew them again.
     */
    @Test
    void anEntityAlreadyRetiredRunsTheRetiredCallback() {
        ServerMock server = MockBukkit.mock();
        try {
            Plugin plugin = MockBukkit.createMockPlugin();
            EntityScheduler retiredScheduler = mock(EntityScheduler.class);
            when(retiredScheduler.execute(any(), any(), any(), anyLong())).thenReturn(false);
            PlayerMock gone = new PlayerMock(server, "Gone") {
                @Override
                public EntityScheduler getScheduler() {
                    return retiredScheduler;
                }
            };
            server.addPlayer(gone);
            AtomicBoolean retired = new AtomicBoolean(false);

            new FoliaScheduler(plugin)
                    .onEntity(new PlayerRef(gone.getUniqueId(), "Gone"), () -> {}, () -> retired.set(true));

            assertThat(retired).isTrue();
        } finally {
            MockBukkit.unmock();
        }
    }

    /**
     * Paper marks a plugin disabled before it calls {@code onDisable}, so a self-rescheduling loop that fires in
     * between asks for its next run on a disabled plugin. The wallet writer's flush loop did that on every stop
     * and logged an {@code IllegalPluginAccessException}. A delayed task can never run after the plugin stops, so
     * declining it is the whole answer; the writer's own stop drains what was pending.
     */
    @Test
    void delayedWorkIsDeclinedWhileThePluginIsDisabled() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(false);
        FoliaScheduler scheduler = new FoliaScheduler(plugin);
        AtomicBoolean ran = new AtomicBoolean(false);

        scheduler.asyncAfter(Duration.ofSeconds(1), () -> ran.set(true));
        scheduler.laterGlobal(Duration.ofSeconds(1), () -> ran.set(true));
        scheduler
                .repeatGlobal(() -> ran.set(true), Duration.ZERO, Duration.ofSeconds(1))
                .close();

        assertThat(ran).isFalse();
    }
}
