package com.uxplima.uxmessentials.teleport.adapter.outbound.nms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.teleport.application.port.LogoutPositions;
import com.uxplima.uxmessentials.teleport.application.port.LogoutPositions.SavedSpot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The saved position read from a compressed player data file laid out as the server writes it: an unnamed root
 * compound holding the position, the facing and the world among the rest of the player's data, of every tag type.
 */
class PlayerDataLogoutPositionsTest {

    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");

    private static final int LONG = 4;
    private static final int FLOAT = 5;
    private static final int DOUBLE = 6;
    private static final int STRING = 8;
    private static final int LIST = 9;
    private static final int COMPOUND = 10;

    @TempDir
    Path players;

    private final Errors log = new Errors();

    private LogoutPositions positions() {
        return new PlayerDataLogoutPositions(players, log);
    }

    @Test
    void thePositionTheServerSavedIsReadBackWithItsWorld() throws IOException {
        save(playerData(true, true));

        assertThat(positions().read(SAM)).contains(SavedSpot.inWorld(WORLD, 12.5, 64, -80.25, 90f, -15f));
        assertThat(log.errors).isEmpty();
    }

    @Test
    void dataWithoutAWorldUidFallsBackToItsDimension() throws IOException {
        save(playerData(false, true));

        assertThat(positions().read(SAM))
                .contains(SavedSpot.inDimension("minecraft:the_nether", 12.5, 64, -80.25, 90f, -15f));
    }

    @Test
    void aPlayerWithNoFileOrNoWorldAtAllHasNoSavedPosition() throws IOException {
        assertThat(positions().read(SAM)).isEmpty();

        save(playerData(false, false));
        assertThat(positions().read(SAM)).isEmpty();
        assertThat(log.errors).isEmpty();
    }

    @Test
    void aFileCutShortWhileItIsWrittenIsUnreadableNotMissing() throws IOException {
        byte[] whole = playerData(true, true);
        save(Arrays.copyOf(whole, whole.length / 2));

        assertThatThrownBy(() -> positions().read(SAM)).isInstanceOf(LogoutPositions.Unreadable.class);
        assertThat(log.errors).hasSize(1);
    }

    private void save(byte[] compressed) throws IOException {
        Files.write(players.resolve(SAM + ".dat"), compressed);
    }

    /**
     * A player's data as the server stores it, with arrays, nested lists and compounds around the fields read. The
     * world uid and the dimension are left out when asked.
     */
    private static byte[] playerData(boolean withWorldUid, boolean withDimension) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(COMPOUND);
            out.writeUTF("");
            named(out, 3, "DataVersion");
            out.writeInt(4671);
            named(out, 1, "OnGround");
            out.writeByte(1);
            named(out, 2, "Air");
            out.writeShort(300);
            named(out, 7, "Seed");
            out.writeInt(3);
            out.write(new byte[] {1, 2, 3});
            named(out, 11, "UUID");
            out.writeInt(4);
            for (int part = 0; part < 4; part++) {
                out.writeInt(part);
            }
            named(out, 12, "Pairs");
            out.writeInt(2);
            out.writeLong(7L);
            out.writeLong(9L);
            inventory(out);
            nestedLists(out);
            named(out, LIST, "Pos");
            out.writeByte(DOUBLE);
            out.writeInt(3);
            out.writeDouble(12.5);
            out.writeDouble(64);
            out.writeDouble(-80.25);
            named(out, LIST, "Rotation");
            out.writeByte(FLOAT);
            out.writeInt(2);
            out.writeFloat(90f);
            out.writeFloat(-15f);
            if (withDimension) {
                named(out, STRING, "Dimension");
                out.writeUTF("minecraft:the_nether");
            }
            if (withWorldUid) {
                named(out, LONG, "WorldUUIDLeast");
                out.writeLong(WORLD.getLeastSignificantBits());
                named(out, LONG, "WorldUUIDMost");
                out.writeLong(WORLD.getMostSignificantBits());
            }
            named(out, COMPOUND, "bukkit");
            named(out, LONG, "lastPlayed");
            out.writeLong(1_900_000_000_000L);
            out.writeByte(0);
            out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    /** A list of item compounds, one of which carries its own compound of components. */
    private static void inventory(DataOutputStream out) throws IOException {
        named(out, LIST, "Inventory");
        out.writeByte(COMPOUND);
        out.writeInt(2);
        named(out, STRING, "id");
        out.writeUTF("minecraft:diamond");
        named(out, 1, "Slot");
        out.writeByte(0);
        out.writeByte(0);
        named(out, STRING, "id");
        out.writeUTF("minecraft:written_book");
        named(out, COMPOUND, "components");
        named(out, LIST, "pages");
        out.writeByte(STRING);
        out.writeInt(2);
        out.writeUTF("one");
        out.writeUTF("two");
        out.writeByte(0);
        out.writeByte(0);
    }

    /** A list of lists of longs and an empty list, as the server writes a list with nothing in it. */
    private static void nestedLists(DataOutputStream out) throws IOException {
        named(out, LIST, "Nested");
        out.writeByte(LIST);
        out.writeInt(2);
        out.writeByte(LONG);
        out.writeInt(2);
        out.writeLong(1L);
        out.writeLong(2L);
        out.writeByte(12);
        out.writeInt(1);
        out.writeInt(1);
        out.writeLong(3L);
        named(out, LIST, "Empty");
        out.writeByte(0);
        out.writeInt(0);
    }

    private static void named(DataOutputStream out, int type, String name) throws IOException {
        out.writeByte(type);
        out.writeUTF(name);
    }

    /** Keeps what was logged as an error. */
    private static final class Errors implements Logger {

        final List<String> errors = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {
            errors.add(message);
        }

        @Override
        public void debug(String message, Object... args) {}
    }
}
