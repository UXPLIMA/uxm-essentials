package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Saves of an offline player's container into a compressed player data file laid out as the server writes it: every
 * other tag survives byte for byte, the old file is kept, an edit never writes over another, a failed save leaves the
 * file alone, and a player who is joining or online is never written.
 */
class PlayerDataFilesTest {

    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final int VERSION = 4440;

    @TempDir
    Path players;

    private final PlayerPresence presence = new PlayerPresence(Clock.systemUTC());

    private PlayerDataFiles files() {
        return new PlayerDataFiles(players, presence);
    }

    @Test
    void aSaveRewritesOnlyTheSlotsItChangesAndKeepsEveryOtherTagAsItWas() throws IOException {
        save(player(List.of(slotted(0, DIAMONDS), slotted(5, STONE)), helmetOnly(), List.of(slotted(2, APPLE))));
        PlayerDataFiles files = files();
        StoredSlots seen = found(files, ContainerLayout.INVENTORY);

        SaveResult result = files.write(
                SAM,
                ContainerLayout.INVENTORY,
                seen,
                List.of(new SlotWrite(0, null), new SlotWrite(5, payload(GOLD)), new SlotWrite(38, payload(CHEST))));

        assertThat(result).isEqualTo(SaveResult.of(SaveOutcome.SAVED));
        assertThat(stored())
                .as("only slot 0, slot 5 and the chestplate differ from the file before the save")
                .isEqualTo(raw(player(
                        List.of(slotted(5, GOLD)),
                        compound("head", HELMET, "chest", CHEST),
                        List.of(slotted(2, APPLE)))));
    }

    @Test
    void theFileBeforeTheSaveIsKeptAsDatOldAndNoTemporaryFileIsLeft() throws IOException {
        byte[] before = save(player(List.of(slotted(0, DIAMONDS)), helmetOnly(), List.of(slotted(1, STONE))));
        PlayerDataFiles files = files();

        files.write(
                SAM,
                ContainerLayout.ENDER,
                found(files, ContainerLayout.ENDER),
                List.of(new SlotWrite(1, null), new SlotWrite(4, payload(APPLE))));

        assertThat(players.resolve(SAM + ".dat_old")).hasBinaryContent(before);
        assertThat(fileNames()).containsExactlyInAnyOrder(SAM + ".dat", SAM + ".dat_old");
        assertThat(stored())
                .as("the ender chest lost its stone and gained an apple; nothing else changed")
                .isEqualTo(raw(player(List.of(slotted(0, DIAMONDS)), helmetOnly(), List.of(slotted(4, APPLE)))));
    }

    @Test
    void twoEditsFromTheSameOpenBothLandWhenTheyChangeDifferentSlots() throws IOException {
        save(player(List.of(slotted(0, DIAMONDS)), helmetOnly(), List.of()));
        PlayerDataFiles files = files();
        StoredSlots first = found(files, ContainerLayout.INVENTORY);
        StoredSlots second = found(files, ContainerLayout.INVENTORY);

        files.write(SAM, ContainerLayout.INVENTORY, first, List.of(new SlotWrite(0, null)));
        SaveResult later =
                files.write(SAM, ContainerLayout.INVENTORY, second, List.of(new SlotWrite(7, payload(GOLD))));

        assertThat(later).isEqualTo(SaveResult.of(SaveOutcome.SAVED));
        assertThat(stored())
                .as("the second save adds its slot without putting back the diamonds the first one took")
                .isEqualTo(raw(player(List.of(slotted(7, GOLD)), helmetOnly(), List.of())));
    }

    @Test
    void anEditOfASlotThatChangedSinceItsWindowOpenedWritesNothingUnlessThePartIsAccepted() throws IOException {
        save(player(List.of(slotted(0, DIAMONDS)), helmetOnly(), List.of()));
        PlayerDataFiles files = files();
        StoredSlots first = found(files, ContainerLayout.INVENTORY);
        StoredSlots second = found(files, ContainerLayout.INVENTORY);
        files.write(SAM, ContainerLayout.INVENTORY, first, List.of(new SlotWrite(0, null)));
        byte[] afterFirst = stored();
        List<SlotWrite> moveDiamonds = List.of(new SlotWrite(0, null), new SlotWrite(9, payload(DIAMONDS)));

        SaveResult refused = files.write(SAM, ContainerLayout.INVENTORY, second, moveDiamonds, conflicts -> false);

        assertThat(refused)
                .as("moving the diamonds the first edit already took would hand them out twice")
                .isEqualTo(new SaveResult(SaveOutcome.CHANGED, Set.of(0)));
        assertThat(stored()).isEqualTo(afterFirst);

        SaveResult partly = files.write(
                SAM, ContainerLayout.INVENTORY, second, moveDiamonds, conflicts -> conflicts.equals(Set.of(0)));

        assertThat(partly).isEqualTo(new SaveResult(SaveOutcome.SAVED, Set.of(0)));
        assertThat(stored())
                .as("the accepted part is written and the conflicting slot is left as the first save left it")
                .isEqualTo(raw(player(List.of(slotted(9, DIAMONDS)), helmetOnly(), List.of())));
    }

    @Test
    void aFileWrittenByAnotherVersionSinceTheReadIsNotWritten() throws IOException {
        save(player(List.of(slotted(0, DIAMONDS)), helmetOnly(), List.of()));
        PlayerDataFiles files = files();
        StoredSlots seen = found(files, ContainerLayout.INVENTORY);
        byte[] upgraded =
                save(compound("DataVersion", intTag(VERSION + 1), "Inventory", list(COMPOUND, slotted(0, DIAMONDS))));

        SaveResult result = files.write(
                SAM, ContainerLayout.INVENTORY, seen, List.of(new SlotWrite(3, payload(GOLD))), any -> true);

        assertThat(result).isEqualTo(new SaveResult(SaveOutcome.CHANGED, Set.of(3)));
        assertThat(players.resolve(SAM + ".dat")).hasBinaryContent(upgraded);
    }

    @Test
    void savesRunningAtOnceForDifferentSlotsAllLand() throws Exception {
        save(player(List.of(), helmetOnly(), List.of()));
        PlayerDataFiles files = files();
        StoredSlots seen = found(files, ContainerLayout.INVENTORY);
        int saves = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<SaveResult>> tasks = new ArrayList<>();
        for (int slot = 0; slot < saves; slot++) {
            SlotWrite write = new SlotWrite(slot, payload(STONE));
            tasks.add(() -> {
                start.await();
                return files.write(SAM, ContainerLayout.INVENTORY, seen, List.of(write));
            });
        }

        List<SaveResult> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(saves)) {
            List<Future<SaveResult>> running = new ArrayList<>();
            for (Callable<SaveResult> task : tasks) {
                running.add(pool.submit(task));
            }
            start.countDown();
            for (Future<SaveResult> result : running) {
                results.add(result.get());
            }
        }

        assertThat(results).containsOnly(SaveResult.of(SaveOutcome.SAVED));
        assertThat(found(files(), ContainerLayout.INVENTORY).filled())
                .as("every save's slot is in the file")
                .containsOnlyKeys(0, 1, 2, 3, 4, 5, 6, 7, 39);
    }

    @Test
    void aSaveThatFailsLeavesTheFileAsItWasAndRemovesItsTemporaryFile() throws IOException {
        byte[] before = save(player(List.of(slotted(0, DIAMONDS)), helmetOnly(), List.of()));
        Path blocked = Files.createDirectories(players.resolve(SAM + ".dat_old").resolve("in-the-way"));
        PlayerDataFiles files = files();
        StoredSlots seen = found(files, ContainerLayout.INVENTORY);

        assertThatThrownBy(() -> files.write(SAM, ContainerLayout.INVENTORY, seen, List.of(new SlotWrite(0, null))))
                .isInstanceOf(IOException.class);

        assertThat(players.resolve(SAM + ".dat")).hasBinaryContent(before);
        assertThat(fileNames()).containsExactlyInAnyOrder(SAM + ".dat", SAM + ".dat_old");
        assertThat(blocked).isDirectory();
    }

    @Test
    void aPlayerWhoIsJoiningOrConnectedIsNeitherReadNorWrittenUntilTheirConnectionCloses() throws Exception {
        byte[] before = save(player(List.of(slotted(0, DIAMONDS)), helmetOnly(), List.of()));
        PlayerDataFiles files = files();
        StoredSlots seen = found(files, ContainerLayout.INVENTORY);
        List<SlotWrite> takeDiamonds = List.of(new SlotWrite(0, null));

        assertThat(files.admit(SAM, Duration.ZERO)).isTrue();

        assertThat(files.read(SAM, ContainerLayout.INVENTORY))
                .isEqualTo(new PlayerDataFiles.Refused(SaveOutcome.PLAYER_PRESENT));
        assertThat(files.write(SAM, ContainerLayout.INVENTORY, seen, takeDiamonds))
                .isEqualTo(SaveResult.of(SaveOutcome.PLAYER_PRESENT));
        presence.validated(SAM);
        assertThat(files.write(SAM, ContainerLayout.INVENTORY, seen, takeDiamonds))
                .isEqualTo(SaveResult.of(SaveOutcome.PLAYER_PRESENT));
        assertThat(players.resolve(SAM + ".dat")).hasBinaryContent(before);

        presence.closed(SAM);
        assertThat(files.write(SAM, ContainerLayout.INVENTORY, seen, takeDiamonds))
                .isEqualTo(SaveResult.of(SaveOutcome.SAVED));
    }

    @Test
    void aSlotIsReadAsTheServerReadsItAndEntriesItSkipsAreKept() throws IOException {
        Tag notANumber = compound("Slot", string("two"), "id", string("minecraft:gold_ingot"), "count", intTag(7));
        Tag wide = compound("Slot", intTag(263), "id", string("minecraft:stone"), "count", intTag(64));
        Tag unslotted = APPLE;
        save(player(
                List.of(slotted(2, DIAMONDS), notANumber, slotted(2, STONE), unslotted, wide),
                helmetOnly(),
                List.of()));
        PlayerDataFiles files = files();

        StoredSlots seen = found(files, ContainerLayout.INVENTORY);

        assertThat(seen.filled())
                .as("a missing slot is 0, an int is narrowed to an unsigned byte, the later of two entries wins")
                .containsOnlyKeys(0, 2, 7, 39)
                .containsEntry(0, payload(APPLE))
                .containsEntry(2, payload(slotted(2, STONE)))
                .containsEntry(7, payload(wide));

        files.write(SAM, ContainerLayout.INVENTORY, seen, List.of(new SlotWrite(2, payload(GOLD))));

        assertThat(stored())
                .as("the slot's earlier duplicate goes, its last entry is replaced in place, the rest is kept")
                .isEqualTo(
                        raw(player(List.of(notANumber, slotted(2, GOLD), unslotted, wide), helmetOnly(), List.of())));
    }

    @Test
    void equipmentKeysThisDoesNotShowAreKeptAndAnEmptyEquipmentTagIsLeftOut() throws IOException {
        save(player(List.of(), compound("head", HELMET, "body", CHEST), List.of()));
        PlayerDataFiles files = files();
        List<SlotWrite> takeHelmet = List.of(new SlotWrite(39, null));

        files.write(SAM, ContainerLayout.INVENTORY, found(files, ContainerLayout.INVENTORY), takeHelmet);

        assertThat(stored()).isEqualTo(raw(player(List.of(), compound("body", CHEST), List.of())));

        save(player(List.of(), helmetOnly(), List.of()));
        files.write(SAM, ContainerLayout.INVENTORY, found(files, ContainerLayout.INVENTORY), takeHelmet);

        assertThat(stored())
                .as("the server leaves the key out when nothing is worn")
                .isEqualTo(raw(player(List.of(), null, List.of())));
    }

    @Test
    void aBrokenFileIsUnreadableRatherThanReadAsLessThanItHolds() throws IOException {
        byte[] whole = raw(player(List.of(slotted(0, DIAMONDS)), helmetOnly(), List.of()));
        Tag tooDeep = list(INT);
        for (int i = 0; i < 600; i++) {
            tooDeep = list(LIST, tooDeep);
        }
        List<byte[]> broken = List.of(
                Arrays.copyOf(whole, whole.length / 2),
                raw(compound("blob", new Simple(BYTE_ARRAY, out -> out.writeInt(-1)))),
                raw(compound("deep", tooDeep)),
                raw(compound("odd", new Simple((byte) 13, out -> out.writeInt(0)))));
        PlayerDataFiles files = files();

        for (byte[] file : broken) {
            saveRaw(file);
            assertThatThrownBy(() -> files.read(SAM, ContainerLayout.INVENTORY)).isInstanceOf(IOException.class);
        }
    }

    // ---- the player data file, written the way the server writes it -------------------------------------------

    private static final Tag DIAMONDS = compound("id", string("minecraft:diamond"), "count", intTag(3));
    private static final Tag STONE = compound("id", string("minecraft:stone"), "count", intTag(64));
    private static final Tag GOLD = compound("id", string("minecraft:gold_ingot"), "count", intTag(7));
    private static final Tag APPLE = compound("id", string("minecraft:apple"), "count", intTag(1));
    private static final Tag HELMET = compound(
            "id", string("minecraft:iron_helmet"),
            "count", intTag(1),
            "components", compound("minecraft:damage", intTag(12)));
    private static final Tag CHEST = compound("id", string("minecraft:iron_chestplate"), "count", intTag(1));

    private static Tag helmetOnly() {
        return compound("head", HELMET);
    }

    /** A player's data with every tag type around the three containers, as the server lays it out. */
    private static Tag player(List<Tag> inventory, @Nullable Tag equipment, List<Tag> ender) {
        List<Object> entries = new ArrayList<>(List.of(
                "DataVersion", intTag(VERSION),
                "Pos", list(DOUBLE, doubleTag(12.5), doubleTag(64), doubleTag(-80.25)),
                "Rotation", list(FLOAT, floatTag(90f), floatTag(-15f)),
                "OnGround", byteTag(1),
                "Air", shortTag(300),
                "UUID", intArray(1, 2, 3, 4),
                "WorldUUIDMost", longTag(-42L),
                "seed", longArray(7L, -7L),
                "blob", byteArray((byte) 1, (byte) -2, (byte) 3),
                "Inventory", list(COMPOUND, inventory.toArray(Tag[]::new))));
        if (equipment != null) {
            entries.addAll(List.of("equipment", equipment));
        }
        entries.addAll(List.of(
                "EnderItems", list(COMPOUND, ender.toArray(Tag[]::new)),
                "LastDeathLocation", compound("dimension", string("minecraft:the_nether"), "pos", intArray(1, 2, 3)),
                "bukkit", compound("lastKnownName", string("Sam Şükrü ✓")),
                "recipeBook", compound("recipes", list(STRING, string("minecraft:bread"))),
                "future_tag", list(LIST, list(INT, intTag(1)), list(END))));
        return compound(entries.toArray());
    }

    private static Tag slotted(int slot, Tag item) {
        List<Object> entries = new ArrayList<>(List.of("Slot", byteTag(slot)));
        entries.addAll(((Compound) item).entries());
        return compound(entries.toArray());
    }

    private StoredSlots found(PlayerDataFiles files, ContainerLayout layout) throws IOException {
        PlayerDataFiles.Read read = files.read(SAM, layout);
        assertThat(read).isInstanceOf(PlayerDataFiles.Found.class);
        StoredSlots slots = ((PlayerDataFiles.Found) read).slots();
        assertThat(slots.dataVersion()).isEqualTo(VERSION);
        return slots;
    }

    private byte[] save(Tag root) throws IOException {
        return saveRaw(raw(root));
    }

    private byte[] saveRaw(byte[] uncompressed) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(uncompressed);
        }
        byte[] bytes = compressed.toByteArray();
        Files.write(players.resolve(SAM + ".dat"), bytes);
        return bytes;
    }

    /** The file as stored now, uncompressed. */
    private byte[] stored() throws IOException {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(players.resolve(SAM + ".dat")))) {
            return in.readAllBytes();
        }
    }

    private List<String> fileNames() throws IOException {
        try (Stream<Path> listed = Files.list(players)) {
            return listed.map(path -> path.getFileName().toString()).toList();
        }
    }

    private static byte[] raw(Tag root) {
        return bytes(out -> {
            out.writeByte(COMPOUND);
            out.writeUTF("");
            root.write(out);
        });
    }

    private static TagPayload payload(Tag compound) {
        return new TagPayload(bytes(compound::write));
    }

    // ---- a minimal NBT writer, independent of the code under test ---------------------------------------------

    private static final byte END = 0;
    private static final byte BYTE = 1;
    private static final byte SHORT = 2;
    private static final byte INT = 3;
    private static final byte LONG = 4;
    private static final byte FLOAT = 5;
    private static final byte DOUBLE = 6;
    private static final byte BYTE_ARRAY = 7;
    private static final byte STRING = 8;
    private static final byte LIST = 9;
    private static final byte COMPOUND = 10;
    private static final byte INT_ARRAY = 11;
    private static final byte LONG_ARRAY = 12;

    private interface Body {
        void write(DataOutputStream out) throws IOException;
    }

    private interface Tag {
        byte type();

        void write(DataOutputStream out) throws IOException;
    }

    private record Simple(byte type, Body body) implements Tag {
        @Override
        public void write(DataOutputStream out) throws IOException {
            body.write(out);
        }
    }

    private record Compound(List<Object> entries) implements Tag {
        @Override
        public byte type() {
            return COMPOUND;
        }

        @Override
        public void write(DataOutputStream out) throws IOException {
            for (int i = 0; i < entries.size(); i += 2) {
                Tag value = (Tag) entries.get(i + 1);
                out.writeByte(value.type());
                out.writeUTF((String) entries.get(i));
                value.write(out);
            }
            out.writeByte(END);
        }
    }

    private static Tag compound(Object... namesAndValues) {
        return new Compound(List.of(namesAndValues));
    }

    private static Tag list(byte type, Tag... elements) {
        return new Simple(LIST, out -> {
            out.writeByte(type);
            out.writeInt(elements.length);
            for (Tag element : elements) {
                element.write(out);
            }
        });
    }

    private static Tag byteTag(int value) {
        return new Simple(BYTE, out -> out.writeByte(value));
    }

    private static Tag shortTag(int value) {
        return new Simple(SHORT, out -> out.writeShort(value));
    }

    private static Tag intTag(int value) {
        return new Simple(INT, out -> out.writeInt(value));
    }

    private static Tag longTag(long value) {
        return new Simple(LONG, out -> out.writeLong(value));
    }

    private static Tag floatTag(float value) {
        return new Simple(FLOAT, out -> out.writeFloat(value));
    }

    private static Tag doubleTag(double value) {
        return new Simple(DOUBLE, out -> out.writeDouble(value));
    }

    private static Tag string(String value) {
        return new Simple(STRING, out -> out.writeUTF(value));
    }

    private static Tag byteArray(byte... values) {
        return new Simple(BYTE_ARRAY, out -> {
            out.writeInt(values.length);
            out.write(values);
        });
    }

    private static Tag intArray(int... values) {
        return new Simple(INT_ARRAY, out -> {
            out.writeInt(values.length);
            for (int value : values) {
                out.writeInt(value);
            }
        });
    }

    private static Tag longArray(long... values) {
        return new Simple(LONG_ARRAY, out -> {
            out.writeInt(values.length);
            for (long value : values) {
                out.writeLong(value);
            }
        });
    }

    private static byte[] bytes(Body body) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            body.write(out);
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return bytes.toByteArray();
    }
}
