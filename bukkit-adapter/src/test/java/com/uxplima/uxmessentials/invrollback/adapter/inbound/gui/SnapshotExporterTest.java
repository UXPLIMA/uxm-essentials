package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link SnapshotExporter}: exporting a snapshot packs its items into shulker-box items in
 * the staff member's inventory (the box's block state carries the items), an inventory that overflows one box
 * produces as many boxes as it needs, and a snapshot with no items hands over nothing.
 */
class SnapshotExporterTest {

    private static final Instant WHEN = Instant.parse("2026-07-17T12:00:00Z");

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void packsTheSnapshotItemsIntoAShulkerAndGivesItToStaff() {
        PlayerMock staff = server.addPlayer("Staff");
        PlayerRef target = new PlayerRef(UUID.randomUUID(), "Victim");
        ItemStack[] contents = new ItemStack[41];
        contents[0] = new ItemStack(Material.DIAMOND, 5);
        contents[1] = new ItemStack(Material.GOLD_INGOT, 10);
        Snapshot snapshot = snapshot(target, contents);

        exporter().export(ref(staff), target, snapshot);

        List<ItemStack> boxes = shulkers(staff);
        assertThat(boxes).as("a shulker box was given to staff").isNotEmpty();
        ItemStack[] inside = shulkerContents(boxes.get(0));
        assertThat(inside[0]).isEqualTo(contents[0]);
        assertThat(inside[1]).isEqualTo(contents[1]);
    }

    @Test
    void producesMultipleShulkersWhenTheItemsOverflowOneBox() {
        PlayerMock staff = server.addPlayer("Staff");
        PlayerRef target = new PlayerRef(UUID.randomUUID(), "Victim");
        ItemStack[] contents = new ItemStack[41];
        for (int slot = 0; slot < 30; slot++) {
            contents[slot] = new ItemStack(Material.STONE, 1);
        }

        exporter().export(ref(staff), target, snapshot(target, contents));

        assertThat(shulkers(staff)).hasSize(2); // 30 stacks pack into 27 + 3
    }

    @Test
    void handsOverNothingWhenTheSnapshotHoldsNoItems() {
        PlayerMock staff = server.addPlayer("Staff");
        PlayerRef target = new PlayerRef(UUID.randomUUID(), "Victim");

        exporter().export(ref(staff), target, snapshot(target, new ItemStack[41]));

        assertThat(shulkers(staff)).isEmpty();
    }

    private static Snapshot snapshot(PlayerRef target, ItemStack[] contents) {
        return Snapshot.capture(
                target.uuid(), SnapshotCause.DEATH, WHEN, InventorySnapshotCodec.encode(contents, null));
    }

    private static SnapshotExporter exporter() {
        return new SnapshotExporter(inlineScheduler(), keyEcho(), noopSink());
    }

    private static List<ItemStack> shulkers(PlayerMock staff) {
        List<ItemStack> boxes = new ArrayList<>();
        for (ItemStack item : staff.getInventory().getContents()) {
            if (item != null && item.getType() == Material.SHULKER_BOX) {
                boxes.add(item);
            }
        }
        return boxes;
    }

    private static ItemStack[] shulkerContents(ItemStack box) {
        BlockStateMeta meta = (BlockStateMeta) box.getItemMeta();
        return ((ShulkerBox) meta.getBlockState()).getInventory().getContents();
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private static Messages keyEcho() {
        return (viewer, key, placeholders) -> key.key();
    }

    private static MessageSink noopSink() {
        return (viewer, renderedText) -> {};
    }

    private static Scheduler inlineScheduler() {
        return new Scheduler() {
            @Override
            public void onGlobal(Runnable task) {
                task.run();
            }

            @Override
            public void onRegion(Position position, Runnable task) {
                task.run();
            }

            @Override
            public void onEntity(PlayerRef player, Runnable task) {
                task.run();
            }

            @Override
            public void async(Runnable task) {
                task.run();
            }

            @Override
            public void asyncAfter(Duration delay, Runnable task) {
                task.run();
            }
        };
    }
}
