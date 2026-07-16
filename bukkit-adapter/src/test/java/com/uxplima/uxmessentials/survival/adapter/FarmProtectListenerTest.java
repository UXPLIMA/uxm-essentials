package com.uxplima.uxmessentials.survival.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import com.uxplima.uxmessentials.survival.adapter.inbound.listener.FarmProtectListener;
import com.uxplima.uxmessentials.survival.adapter.outbound.PdcSurvivalToggles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of farmland protection: a {@code PHYSICAL} interaction on farmland (a trample) is cancelled
 * while the player's toggle is on and allowed once they turn it off, and a trample on any other block is left alone.
 */
class FarmProtectListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private PdcSurvivalToggles toggles;
    private FarmProtectListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Steve");
        player.teleport(new Location(world, 0.5, 65, 0.5));
        toggles = new PdcSurvivalToggles();
        listener = new FarmProtectListener(toggles);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void cancelsTheTrampleWhileTheToggleIsOn() {
        PlayerInteractEvent event = trampleOf(farmlandAt(0, 64, 0));

        listener.onTrample(event);

        // Default-on: a protected player's step does not trample the farmland (the physical action is denied).
        assertThat(event.useInteractedBlock()).isEqualTo(Event.Result.DENY);
    }

    @Test
    void allowsTheTrampleOnceTheToggleIsOff() {
        toggles.toggleFarmProtect(player, true); // flip the default-on toggle to off
        PlayerInteractEvent event = trampleOf(farmlandAt(0, 64, 0));

        listener.onTrample(event);

        assertThat(event.useInteractedBlock()).isNotEqualTo(Event.Result.DENY);
    }

    @Test
    void leavesANonFarmlandTrampleAlone() {
        Block plate = world.getBlockAt(0, 64, 0);
        plate.setType(Material.STONE_PRESSURE_PLATE);
        PlayerInteractEvent event = trampleOf(plate);

        listener.onTrample(event);

        assertThat(event.useInteractedBlock()).isNotEqualTo(Event.Result.DENY);
    }

    private Block farmlandAt(int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.FARMLAND);
        return block;
    }

    private PlayerInteractEvent trampleOf(Block block) {
        return new PlayerInteractEvent(player, Action.PHYSICAL, null, block, BlockFace.UP, EquipmentSlot.HAND);
    }
}
