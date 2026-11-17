package com.uxplima.uxmessentials.scenario;

import org.bukkit.inventory.InventoryHolder;

import org.jspecify.annotations.Nullable;
import org.mockbukkit.mockbukkit.inventory.AnvilInventoryMock;

/**
 * MockBukkit's anvil with Paper's holder read that skips the block snapshot, as {@link ScenarioChest} has it: every
 * window the plugin opens is asked for it, and a text prompt's anvil has no block behind it either.
 */
final class ScenarioAnvil extends AnvilInventoryMock {

    ScenarioAnvil(@Nullable InventoryHolder holder) {
        super(holder);
    }

    @Override
    public @Nullable InventoryHolder getHolder(boolean useSnapshot) {
        return getHolder();
    }
}
