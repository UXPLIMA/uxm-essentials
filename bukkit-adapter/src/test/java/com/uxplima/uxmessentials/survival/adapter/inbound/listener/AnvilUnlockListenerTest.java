package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.view.AnvilView;

import com.uxplima.uxmessentials.survival.application.SurvivalConfig.AnvilUnlocker;
import org.junit.jupiter.api.Test;

/**
 * Coverage of anvil-unlocker: preparing an anvil raises the maximum repair cost past the vanilla "Too Expensive!"
 * ceiling, and the level price is zeroed only when {@code remove-cost-limit} is set. The anvil view is mocked because
 * MockBukkit ships no anvil view, and it is the seam the listener actually mutates.
 */
class AnvilUnlockListenerTest {

    @Test
    void removesTheLevelLimitButLeavesTheCostAlone() {
        AnvilView view = mock(AnvilView.class);
        AnvilUnlockListener listener = new AnvilUnlockListener(new AnvilUnlocker(true, true, false));

        listener.onPrepareAnvil(new PrepareAnvilEvent(view, null));

        verify(view).setMaximumRepairCost(Integer.MAX_VALUE);
        verify(view, never()).setRepairCost(anyInt());
    }

    @Test
    void alsoZeroesTheCostWhenRemoveCostLimitIsSet() {
        AnvilView view = mock(AnvilView.class);
        AnvilUnlockListener listener = new AnvilUnlockListener(new AnvilUnlocker(true, true, true));

        listener.onPrepareAnvil(new PrepareAnvilEvent(view, null));

        verify(view).setMaximumRepairCost(Integer.MAX_VALUE);
        verify(view).setRepairCost(0);
    }

    @Test
    void leavesTheLevelCeilingWhenRemoveLevelLimitIsOff() {
        AnvilView view = mock(AnvilView.class);
        AnvilUnlockListener listener = new AnvilUnlockListener(new AnvilUnlocker(true, false, false));

        listener.onPrepareAnvil(new PrepareAnvilEvent(view, null));

        verify(view, never()).setMaximumRepairCost(anyInt());
    }
}
