package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.survival.adapter.outbound.PdcSurvivalToggles;
import com.uxplima.uxmessentials.survival.domain.AutoToolSelector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** MockBukkit coverage of auto-tool: starting to break a block swaps the held slot to the block's best tool. */
class AutoToolListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private PdcSurvivalToggles toggles;
    private AutoToolListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Steve");
        player.teleport(new Location(world, 0.5, 65, 0.5));
        player.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.survival.autotool", true);
        player.getInventory().setItem(0, new ItemStack(Material.WOODEN_PICKAXE));
        player.getInventory().setItem(4, new ItemStack(Material.DIAMOND_PICKAXE));
        player.getInventory().setHeldItemSlot(0);
        toggles = new PdcSurvivalToggles();
        listener = new AutoToolListener(new AutoToolSelector(), toggles);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void swapsToTheStrongestPickaxeForStone() {
        listener.onDamage(damageOf(Material.STONE));

        assertThat(player.getInventory().getHeldItemSlot()).isEqualTo(4);
    }

    @Test
    void leavesTheHeldSlotWhenTheToggleIsOff() {
        toggles.toggleAutoTool(player, true); // flip the default-on toggle to off

        listener.onDamage(damageOf(Material.STONE));

        assertThat(player.getInventory().getHeldItemSlot()).isEqualTo(0);
    }

    @Test
    void leavesTheHeldSlotForABlockThatNeedsNoTool() {
        listener.onDamage(damageOf(Material.OAK_SAPLING));

        assertThat(player.getInventory().getHeldItemSlot()).isEqualTo(0);
    }

    private BlockDamageEvent damageOf(Material type) {
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(type);
        return new BlockDamageEvent(
                player, block, BlockFace.UP, player.getInventory().getItemInMainHand(), false);
    }
}
