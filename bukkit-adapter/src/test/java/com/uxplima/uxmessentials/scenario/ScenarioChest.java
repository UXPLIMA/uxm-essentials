package com.uxplima.uxmessentials.scenario;

import org.bukkit.inventory.InventoryHolder;

import org.jspecify.annotations.Nullable;
import org.mockbukkit.mockbukkit.inventory.ChestInventoryMock;

/**
 * MockBukkit's chest with Paper's holder read that skips the block snapshot, which every window the plugin opens is
 * asked for and the stock one leaves unimplemented. A chest the plugin builds has no block behind it, so both reads
 * give the same holder.
 */
final class ScenarioChest extends ChestInventoryMock {

    ScenarioChest(@Nullable InventoryHolder holder, int size) {
        super(holder, size);
    }

    @Override
    public @Nullable InventoryHolder getHolder(boolean useSnapshot) {
        return getHolder();
    }
}
