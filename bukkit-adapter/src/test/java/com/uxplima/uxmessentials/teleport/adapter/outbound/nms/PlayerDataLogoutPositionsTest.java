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
 * compound holding the position, the facing and the world uid among the rest of the player's data.
 */
class PlayerDataLogoutPositionsTest {

    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");

    @TempDir
    Path players;

    private final Errors log = new Errors();

    private LogoutPositions positions() {
        return new PlayerDataLogoutPositions(players, log);
    }

    @Test
    void thePositionTheServerSavedIsReadBackWithItsWorld() throws IOException {
        save(playerData(true));

        assertThat(positions().read(SAM)).contains(new SavedSpot(WORLD, 12.5, 64, -80.25, 90f, -15f));
        assertThat(log.errors).isEmpty();
    }

    @Test
    void aPlayerWithNoFileOrNoWorldHasNoSavedPosition() throws IOException {
        assertThat(positions().read(SAM)).isEmpty();

        save(playerData(false));
        assertThat(positions().read(SAM)).isEmpty();
        assertThat(log.errors).isEmpty();
    }

    @Test
    void aFileCutShortWhileItIsWrittenIsUnreadableNotMissing() throws IOException {
        byte[] whole = playerData(true);
        save(Arrays.copyOf(whole, whole.length / 2));

        assertThatThrownBy(() -> positions().read(SAM)).isInstanceOf(LogoutPositions.Unreadable.class);
        assertThat(log.errors).hasSize(1);
    }

    private void save(byte[] compressed) throws IOException {
        Files.write(players.resolve(SAM + ".dat"), compressed);
    }

    /** A player's data as the server stores it, the world uid left out when {@code withWorld} is false. */
    private static byte[] playerData(boolean withWorld) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);
            out.writeUTF("");
            named(out, 3, "DataVersion");
            out.writeInt(4671);
            named(out, 9, "Inventory");
            out.writeByte(10);
            out.writeInt(1);
            named(out, 8, "id");
            out.writeUTF("minecraft:diamond");
            named(out, 1, "Slot");
            out.writeByte(0);
            out.writeByte(0);
            named(out, 11, "UUID");
            out.writeInt(4);
            for (int part = 0; part < 4; part++) {
                out.writeInt(part);
            }
            named(out, 9, "Pos");
            out.writeByte(6);
            out.writeInt(3);
            out.writeDouble(12.5);
            out.writeDouble(64);
            out.writeDouble(-80.25);
            named(out, 9, "Rotation");
            out.writeByte(5);
            out.writeInt(2);
            out.writeFloat(90f);
            out.writeFloat(-15f);
            named(out, 8, "Dimension");
            out.writeUTF("minecraft:overworld");
            if (withWorld) {
                named(out, 4, "WorldUUIDLeast");
                out.writeLong(WORLD.getLeastSignificantBits());
                named(out, 4, "WorldUUIDMost");
                out.writeLong(WORLD.getMostSignificantBits());
            }
            named(out, 10, "bukkit");
            named(out, 4, "lastPlayed");
            out.writeLong(1_900_000_000_000L);
            out.writeByte(0);
            out.writeByte(0);
        }
        return bytes.toByteArray();
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
