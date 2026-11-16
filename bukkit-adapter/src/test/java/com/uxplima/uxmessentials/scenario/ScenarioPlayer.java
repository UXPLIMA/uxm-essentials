package com.uxplima.uxmessentials.scenario;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.InventoryView;

import io.papermc.paper.entity.TeleportFlag;

import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.InventoryMock;
import org.mockbukkit.mockbukkit.inventory.SimpleInventoryViewMock;

/**
 * MockBukkit's player with the parts the plugin reads on join or on a teleport, which the stock one leaves out. The id
 * is the one an offline-mode server gives the name, so the same name joining again is the same account.
 */
final class ScenarioPlayer extends PlayerMock {

    private final InventoryView ownWindow;

    ScenarioPlayer(ServerMock server, String name) {
        super(server, name, UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
        ownWindow = new SimpleInventoryViewMock(
                this, new InventoryMock(this, InventoryType.CRAFTING), getInventory(), InventoryType.CRAFTING);
    }

    /**
     * With no other window open a player's own inventory is in front of them, crafting grid on top, as on a server.
     * The stock player leaves the top empty once a window closes or another plugin refuses one.
     */
    @Override
    public InventoryView getOpenInventory() {
        InventoryView open = super.getOpenInventory();
        return open.getTopInventory() == null ? ownWindow : open;
    }

    @Override
    public CompletableFuture<Boolean> teleportAsync(
            Location location, PlayerTeleportEvent.TeleportCause cause, TeleportFlag... flags) {
        return CompletableFuture.completedFuture(teleport(location, cause, flags));
    }

    @Override
    public String getClientBrandName() {
        return "vanilla";
    }
}
