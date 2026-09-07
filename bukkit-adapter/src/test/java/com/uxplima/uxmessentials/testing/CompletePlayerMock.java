package com.uxplima.uxmessentials.testing;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.InventoryView;

import io.papermc.paper.entity.TeleportFlag;

import org.jspecify.annotations.Nullable;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A {@link PlayerMock} that teleports asynchronously, that knows how much experience it holds, and that opens
 * an anvil.
 *
 * <p>MockBukkit declares all three operations and throws {@code UnimplementedOperationException} from them.
 * That exception extends {@code TestAbortedException}, so JUnit records a test that reaches one as skipped
 * rather than failed. The test then reports green with every assertion after the call unevaluated, which is
 * the failure CONTRACT.md section 15 exists to stop.
 *
 * <p>None of the three is simulated beyond what a caller can be held to. The async teleport completes with
 * the synchronous teleport MockBukkit does implement, so the location lands and the future is already done.
 * The experience total is the vanilla curve, so a level maps to the same number a server would report. The
 * anvil is a real anvil inventory the viewer has open, so a caller that opens a prompt and a caller that
 * closes one are both observable.
 */
public class CompletePlayerMock extends PlayerMock {

    public CompletePlayerMock(ServerMock server, String name) {
        super(server, name);
    }

    public CompletePlayerMock(ServerMock server, String name, UUID uuid) {
        super(server, name, uuid);
    }

    /** A player under {@code server}, named {@code name} and joined to it. */
    public static CompletePlayerMock addTo(ServerMock server, String name) {
        CompletePlayerMock player = new CompletePlayerMock(server, name);
        server.addPlayer(player);
        return player;
    }

    /**
     * Teleport, completing at once. A real server may finish this on a later tick; nothing under test here
     * reads the future, so completing it inline keeps the assertion on the same thread as the call.
     */
    @Override
    public CompletableFuture<Boolean> teleportAsync(Location location, TeleportCause cause, TeleportFlag... flags) {
        return CompletableFuture.completedFuture(teleport(location, cause));
    }

    /**
     * The total experience points this player holds, on the vanilla curve: every point up to the current
     * level, plus the fraction of the current level already filled.
     */
    @Override
    public int calculateTotalExperiencePoints() {
        return pointsUpToLevel(getLevel()) + Math.round(getExp() * getExperiencePointsNeededForNextLevel());
    }

    /** The points between this level and the next, on the vanilla curve. */
    @Override
    public int getExperiencePointsNeededForNextLevel() {
        int level = getLevel();
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        if (level >= 15) {
            return 37 + (level - 15) * 5;
        }
        return 7 + level * 2;
    }

    /**
     * Open a real anvil inventory for this player and return the view, the way a server does when the player
     * has room for one. A server returns null when it cannot open one, and the callers under test already
     * handle that; what they could not do before was carry on past a successful open.
     */
    @Override
    public InventoryView openAnvil(@Nullable Location location, boolean force) {
        return openInventory(getServer().createInventory(null, InventoryType.ANVIL));
    }

    /** The sum of the vanilla level costs below {@code level}. */
    private static int pointsUpToLevel(int level) {
        if (level >= 32) {
            return (int) (4.5 * level * level - 162.5 * level + 2220.0);
        }
        if (level >= 17) {
            return (int) (2.5 * level * level - 40.5 * level + 360.0);
        }
        return level * level + 6 * level;
    }
}
