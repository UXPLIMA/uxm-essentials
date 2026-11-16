package com.uxplima.uxmessentials.teleport.adapter.outbound.nms;

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.teleport.application.port.LogoutPositions;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The {@link LogoutPositions} the server itself keeps: the position saved in an offline player's
 * {@code players/data/<uuid>.dat} when they left. The world is the Bukkit world uid the server stores beside the
 * position and reads first on login. Read only: nothing here writes the file.
 *
 * <p>The file is the server's compressed NBT, read here with a small reader that takes {@code Pos},
 * {@code Rotation} and the world uid (or, in data without one, the {@code Dimension} key) from the root compound
 * and steps over everything else, so reading needs none
 * of the server's internals; only {@link #ofServer} asks them where the directory is. Reading takes no lock: both
 * the server and offline {@code /invsee} write a whole new file and rename it into place, so a read never sees one
 * half written. A file that is cut short or malformed anyway is reported as {@link LogoutPositions.Unreadable},
 * never as a missing position.
 */
public final class PlayerDataLogoutPositions implements LogoutPositions {

    private static final String POSITION = "Pos";
    private static final String ROTATION = "Rotation";
    private static final String WORLD_MOST = "WorldUUIDMost";
    private static final String WORLD_LEAST = "WorldUUIDLeast";
    private static final String DIMENSION = "Dimension";

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

    /** The nesting the server itself allows; deeper is a broken file, not a deep one. */
    private static final int MAX_DEPTH = 512;

    private final Path directory;
    private final Logger log;

    /** Reads the files in {@code directory}, the server's player data directory. */
    public PlayerDataLogoutPositions(Path directory, Logger log) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * The reader over this server's player data directory, resolved here, on the thread that wires the module, so
     * the async lane only touches files. Throws a {@link LinkageError} when the server's internals moved.
     */
    public static PlayerDataLogoutPositions ofServer(Logger log) {
        Path directory = ((CraftServer) Bukkit.getServer()).getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR);
        return new PlayerDataLogoutPositions(directory, log);
    }

    @Override
    public Optional<SavedSpot> read(UUID player) {
        Path file = directory.resolve(Objects.requireNonNull(player, "player") + ".dat");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (InputStream raw = Files.newInputStream(file);
                DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)))) {
            return spotOf(in);
        } catch (IOException | RuntimeException unreadable) {
            log.error("Failed to read the saved position of " + player, unreadable);
            throw new Unreadable("the saved position of " + player + " could not be read");
        }
    }

    /** The root compound's position, facing and world uid; empty when the position or the world is not there. */
    private static Optional<SavedSpot> spotOf(DataInput in) throws IOException {
        if (in.readByte() != COMPOUND) {
            throw new IOException("the root tag is not a compound");
        }
        in.readUTF();
        double[] position = null;
        float[] rotation = null;
        Long most = null;
        Long least = null;
        String dimension = null;
        for (byte type = in.readByte(); type != END; type = in.readByte()) {
            String name = in.readUTF();
            if (type == LIST && name.equals(POSITION)) {
                position = doubles(in, 3);
            } else if (type == LIST && name.equals(ROTATION)) {
                rotation = floats(in, 2);
            } else if (type == LONG && name.equals(WORLD_MOST)) {
                most = in.readLong();
            } else if (type == LONG && name.equals(WORLD_LEAST)) {
                least = in.readLong();
            } else if (type == STRING && name.equals(DIMENSION)) {
                dimension = in.readUTF();
            } else {
                skip(in, type, 0);
            }
        }
        if (position == null) {
            return Optional.empty();
        }
        float yaw = rotation == null ? 0f : rotation[0];
        float pitch = rotation == null ? 0f : rotation[1];
        if (most != null && least != null) {
            return Optional.of(
                    SavedSpot.inWorld(new UUID(most, least), position[0], position[1], position[2], yaw, pitch));
        }
        // Data from before the server stored the world uid names only the dimension, which the server falls back to.
        if (dimension != null) {
            return Optional.of(SavedSpot.inDimension(dimension, position[0], position[1], position[2], yaw, pitch));
        }
        return Optional.empty();
    }

    private static double[] doubles(DataInput in, int count) throws IOException {
        byte type = in.readByte();
        int length = in.readInt();
        if (type != DOUBLE || length != count) {
            throw new IOException("expected " + count + " doubles, found " + length + " of type " + type);
        }
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            values[i] = in.readDouble();
        }
        return values;
    }

    private static float[] floats(DataInput in, int count) throws IOException {
        byte type = in.readByte();
        int length = in.readInt();
        if (type != FLOAT || length != count) {
            throw new IOException("expected " + count + " floats, found " + length + " of type " + type);
        }
        float[] values = new float[count];
        for (int i = 0; i < count; i++) {
            values[i] = in.readFloat();
        }
        return values;
    }

    /** Step over one payload of {@code type}. */
    private static void skip(DataInput in, byte type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("tags nested deeper than " + MAX_DEPTH);
        }
        switch (type) {
            case BYTE -> skipFully(in, 1);
            case SHORT -> skipFully(in, 2);
            case INT, FLOAT -> skipFully(in, 4);
            case LONG, DOUBLE -> skipFully(in, 8);
            case BYTE_ARRAY -> skipFully(in, length(in));
            case STRING -> in.readUTF();
            case INT_ARRAY -> skipFully(in, 4L * length(in));
            case LONG_ARRAY -> skipFully(in, 8L * length(in));
            case LIST -> {
                byte element = in.readByte();
                int length = length(in);
                for (int i = 0; i < length; i++) {
                    skip(in, element, depth + 1);
                }
            }
            case COMPOUND -> {
                for (byte entry = in.readByte(); entry != END; entry = in.readByte()) {
                    in.readUTF();
                    skip(in, entry, depth + 1);
                }
            }
            default -> throw new IOException("unknown tag type " + type);
        }
    }

    private static int length(DataInput in) throws IOException {
        int length = in.readInt();
        if (length < 0) {
            throw new IOException("negative length " + length);
        }
        return length;
    }

    /** Skip exactly {@code count} bytes, or fail: a file that ends early must not read as complete. */
    private static void skipFully(DataInput in, long count) throws IOException {
        long left = count;
        while (left > 0) {
            int step = (int) Math.min(left, Integer.MAX_VALUE);
            int skipped = in.skipBytes(step);
            if (skipped <= 0) {
                in.readByte(); // throws EOFException at the end of the file
                skipped = 1;
            }
            left -= skipped;
        }
    }
}
