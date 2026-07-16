package com.uxplima.uxmessentials.survival.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.survival.adapter.inbound.listener.FarmAssistListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of farm-assist: right-clicking a mature crop harvests it and, when the player carries a matching
 * seed, spends one to replant the crop at age zero; with no matching seed the crop is harvested but not replanted.
 */
class FarmAssistListenerTest {

    private static final String PERMISSION = "uxmessentials.survival.farmassist";

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private FarmAssistListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Steve");
        world = server.addSimpleWorld("world");
        player.teleport(new Location(world, 0.5, 65, 0.5));
        player.addAttachment(MockBukkit.createMockPlugin(), PERMISSION, true);
        listener = new FarmAssistListener(Map.of(Material.CARROTS, Material.CARROT));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void harvestsAndReplantsSpendingOneSeedWhenAMatchingSeedIsPresent() {
        Block crop = matureCarrotsAt(0, 64, 0);
        player.getInventory().addItem(new ItemStack(Material.CARROT, 3));

        listener.onHarvest(rightClick(crop));

        // The crop is replanted at age zero and exactly one carrot seed was spent to do it.
        assertThat(crop.getType()).isEqualTo(Material.CARROTS);
        assertThat(((Ageable) crop.getBlockData()).getAge()).isZero();
        assertThat(seedCount()).isEqualTo(2);
    }

    @Test
    void harvestsWithoutReplantingWhenNoMatchingSeedIsPresent() {
        Block crop = matureCarrotsAt(0, 64, 0); // player carries no carrots

        listener.onHarvest(rightClick(crop));

        // Harvested but not replanted: the crop bed is left empty rather than regrowing for free.
        assertThat(crop.getType()).isEqualTo(Material.AIR);
    }

    private Block matureCarrotsAt(int x, int y, int z) {
        Block crop = world.getBlockAt(x, y, z);
        crop.setType(Material.CARROTS);
        Ageable age = (Ageable) crop.getBlockData();
        age.setAge(age.getMaximumAge());
        crop.setBlockData(age);
        return crop;
    }

    private int seedCount() {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.CARROT) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private PlayerInteractEvent rightClick(Block crop) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, hand, crop, BlockFace.UP, EquipmentSlot.HAND);
    }
}
