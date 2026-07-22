package com.uxplima.uxmessentials.invrollback.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * MockBukkit coverage of {@link InventorySnapshotCodec}: a payload round-trips its items, its ender chest, and now
 * its capture location; an old (pre-location) blob still decodes cleanly to an absent location, so existing
 * snapshots keep listing/previewing/restoring; and the item-free {@link InventorySnapshotCodec#summarize} reads the
 * location and buckets the occupied slots by store.
 */
class InventorySnapshotCodecTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void roundTripsLocationAndContents() {
        ItemStack[] contents = new ItemStack[41];
        contents[0] = new ItemStack(Material.DIAMOND, 3);
        ItemStack[] ender = new ItemStack[27];
        ender[1] = new ItemStack(Material.EMERALD, 2);
        WorldRef world = new WorldRef(UUID.randomUUID(), "world_the_end");
        Position location = new Position(world, 100.5, 64.0, -200.25, 42.0f, -10.0f);

        InventorySnapshotCodec.Decoded decoded =
                InventorySnapshotCodec.decode(InventorySnapshotCodec.encode(contents, ender, location));

        assertThat(decoded.location()).isPresent();
        Position back = decoded.location().orElseThrow();
        assertThat(back).isEqualTo(location);
        assertThat(back.world().name()).isEqualTo("world_the_end");
        assertThat(decoded.contents()[0]).isEqualTo(contents[0]);
        assertThat(decoded.enderChest()[1]).isEqualTo(ender[1]);
    }

    @Test
    void twoArgEncodeRecordsNoLocation() {
        ItemStack[] contents = new ItemStack[41];
        contents[5] = new ItemStack(Material.STONE, 64);

        InventorySnapshotCodec.Decoded decoded =
                InventorySnapshotCodec.decode(InventorySnapshotCodec.encode(contents, null));

        assertThat(decoded.location()).isEmpty();
        assertThat(decoded.contents()[5]).isEqualTo(contents[5]);
    }

    @Test
    void oldLocationlessBlobDecodesToAbsentLocation() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT, 7);
        byte[] legacy = legacyPayload(item);

        InventorySnapshotCodec.Decoded decoded = InventorySnapshotCodec.decode(legacy);

        assertThat(decoded.location()).isEmpty();
        assertThat(decoded.contents()[0]).isEqualTo(item);
        assertThat(decoded.enderChest()).isEmpty();
    }

    @Test
    void summarizeReadsLocationAndBucketsSlots() {
        ItemStack[] contents = new ItemStack[41];
        contents[0] = new ItemStack(Material.DIAMOND, 1); // main
        contents[36] = new ItemStack(Material.DIAMOND_BOOTS, 1); // armor
        contents[40] = new ItemStack(Material.SHIELD, 1); // offhand
        ItemStack[] ender = new ItemStack[27];
        ender[0] = new ItemStack(Material.EMERALD, 1);
        ender[5] = new ItemStack(Material.EMERALD, 1);
        Position location = new Position(new WorldRef(UUID.randomUUID(), "world"), 1.0, 2.0, 3.0, 0f, 0f);

        InventorySnapshotCodec.Summary summary =
                InventorySnapshotCodec.summarize(InventorySnapshotCodec.encode(contents, ender, location));

        assertThat(summary.location()).isPresent();
        assertThat(summary.items()).isEqualTo(1);
        assertThat(summary.armor()).isEqualTo(1);
        assertThat(summary.offhand()).isEqualTo(1);
        assertThat(summary.ender()).isEqualTo(2);
        assertThat(summary.carriedItems()).isEqualTo(2); // main + offhand
    }

    @Test
    void summarizeOfALegacyBlobHasNoLocation() {
        InventorySnapshotCodec.Summary summary =
                InventorySnapshotCodec.summarize(legacyPayload(new ItemStack(Material.STONE, 1)));

        assertThat(summary.location()).isEmpty();
        assertThat(summary.items()).isEqualTo(1);
    }

    /**
     * Build a payload in the pre-location wire shape: two sections (main + empty ender), each a slot count then
     * occupied {@code (slot, len, bytes)} entries closed by a {@code -1} sentinel, with no leading format marker.
     * This is exactly what the old codec wrote, so decoding it proves backward compatibility.
     */
    private static byte[] legacyPayload(ItemStack slotZero) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes)) {
            byte[] item = slotZero.serializeAsBytes();
            out.writeInt(41); // main section slot count
            out.writeInt(0); // slot index 0 is occupied
            out.writeInt(item.length);
            out.write(item);
            out.writeInt(-1); // end of main section
            out.writeInt(0); // ender section slot count
            out.writeInt(-1); // end of ender section
            return bytes.toByteArray();
        } catch (IOException io) {
            throw new UncheckedIOException("could not build a legacy payload", io);
        }
    }
}
