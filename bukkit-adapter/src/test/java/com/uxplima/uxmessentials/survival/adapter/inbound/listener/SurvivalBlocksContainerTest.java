package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.survival.domain.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * The cascade half of the chest bug. A cascade that routes drops through the auto-drops pipeline clears each block
 * with {@code setType(AIR)}, and that clears the block entity <em>before</em> the removal that would have dropped its
 * contents, so a container caught in a vein or a fell lost whatever was inside it. A block carrying a block entity now
 * breaks naturally instead: the block entity survives into the removal, so it spills exactly as a hand-break does, and
 * its drops stay out of the pipeline rather than being taken over.
 *
 * <p>Reachable whenever an operator puts a block with a block entity in {@code veinminer.blocks} (the shipped list is
 * ores, which carry none), and always for anything a tree-fell touches.
 */
class SurvivalBlocksContainerTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aBlockThatHoldsSomethingBreaksNaturallyInsteadOfBeingClearedToAir() {
        block(0, 64, 0, Material.STONE); // the origin, which the vanilla event breaks itself
        block(0, 65, 0, Material.CHEST, new ItemStack(Material.CHEST));
        List<Location> routed = new ArrayList<>();

        int broken = SurvivalBlocks.breakGroup(
                world.getBlockAt(0, 64, 0),
                List.of(new BlockPos(0, 64, 0), new BlockPos(0, 65, 0)),
                new ItemStack(Material.DIAMOND_PICKAXE),
                (where, drops) -> routed.add(where));

        assertThat(broken).isEqualTo(1);
        assertThat(routed)
                .as("the chest's drops are vanilla's to spawn, contents and all, not ours to route")
                .isEmpty();
        assertThat(world.getBlockAt(0, 65, 0).getType()).isEqualTo(Material.AIR);
    }

    @Test
    void aPlainCascadeBlockIsStillRoutedThroughThePipeline() {
        block(0, 64, 0, Material.STONE);
        block(0, 65, 0, Material.COAL_ORE, new ItemStack(Material.COAL));
        List<ItemStack> routed = new ArrayList<>();

        SurvivalBlocks.breakGroup(
                world.getBlockAt(0, 64, 0),
                List.of(new BlockPos(0, 64, 0), new BlockPos(0, 65, 0)),
                new ItemStack(Material.DIAMOND_PICKAXE),
                (where, drops) -> routed.addAll(drops));

        // The guard is a block-entity test, not a blanket retreat: an ore still feeds the pipeline.
        assertThat(routed).extracting(ItemStack::getType).containsExactly(Material.COAL);
    }

    private void block(int x, int y, int z, Material type, ItemStack... drops) {
        BlockMock block = world.getBlockAt(x, y, z);
        block.setType(type);
        block.setDrops(List.of(drops));
    }
}
