package com.uxplima.uxmessentials.shared.adapter.outbound.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.plugin.Plugin;

import org.junit.jupiter.api.Test;

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
}
