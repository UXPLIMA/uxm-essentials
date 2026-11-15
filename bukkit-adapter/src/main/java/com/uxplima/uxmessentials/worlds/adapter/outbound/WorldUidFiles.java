package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import org.jspecify.annotations.NullMarked;

/**
 * Reads the uid of a world that is not loaded from its folder, so the start-up claim can record the world this
 * backend really has before the reconcile loads it. Paper 26.2 keeps it in the dimension folder's
 * {@code data/paper/metadata.dat}, a gzip NBT compound holding {@code data.uuid} as four ints; a world folder copied
 * in from an older server still carries its {@code uid.dat}, two longs, which Paper turns into the former on first
 * load. Plain file reads, so it runs on the async lane and touches no Bukkit API.
 *
 * <p>Only the few tag types a saved-data file holds are walked; anything unreadable answers empty, and the claim then
 * records no uid, which the reconcile fills in on load.
 */
@NullMarked
public final class WorldUidFiles {

    /** Nesting a real metadata file never reaches, so a damaged file cannot recurse without bound. */
    private static final int MAX_DEPTH = 64;

    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;

    private WorldUidFiles() {}

    /** The uid recorded in {@code folder}, the world's dimension folder or a world folder beside the server. */
    public static Optional<UUID> read(Path folder) {
        Objects.requireNonNull(folder, "folder");
        Path metadata = folder.resolve("data").resolve("paper").resolve("metadata.dat");
        if (Files.isRegularFile(metadata)) {
            Optional<UUID> uid = fromMetadata(metadata);
            if (uid.isPresent()) {
                return uid;
            }
        }
        Path legacy = folder.resolve("uid.dat");
        return Files.isRegularFile(legacy) ? fromLegacy(legacy) : Optional.empty();
    }

    private static Optional<UUID> fromLegacy(Path file) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            return Optional.of(new UUID(in.readLong(), in.readLong()));
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    private static Optional<UUID> fromMetadata(Path file) {
        try (InputStream raw = Files.newInputStream(file);
                DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)))) {
            if (in.readUnsignedByte() != TAG_COMPOUND) {
                return Optional.empty();
            }
            skipString(in);
            if (!enter(in, "data")) {
                return Optional.empty();
            }
            return uuidIn(in);
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    /** Walk the compound {@code in} is inside until its compound named {@code name}, leaving the stream at its body. */
    private static boolean enter(DataInputStream in, String name) throws IOException {
        while (true) {
            int type = in.readUnsignedByte();
            if (type == TAG_END) {
                return false;
            }
            String key = in.readUTF();
            if (type == TAG_COMPOUND && key.equals(name)) {
                return true;
            }
            skip(in, type, 0);
        }
    }

    private static Optional<UUID> uuidIn(DataInputStream in) throws IOException {
        while (true) {
            int type = in.readUnsignedByte();
            if (type == TAG_END) {
                return Optional.empty();
            }
            String key = in.readUTF();
            if (type == TAG_INT_ARRAY && key.equals("uuid")) {
                int length = in.readInt();
                if (length != 4) {
                    return Optional.empty();
                }
                long most = ((long) in.readInt() << 32) | (in.readInt() & 0xFFFFFFFFL);
                long least = ((long) in.readInt() << 32) | (in.readInt() & 0xFFFFFFFFL);
                return Optional.of(new UUID(most, least));
            }
            skip(in, type, 0);
        }
    }

    private static void skip(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("metadata nested too deep");
        }
        switch (type) {
            case TAG_BYTE -> skipBytes(in, 1);
            case TAG_SHORT -> skipBytes(in, 2);
            case TAG_INT, TAG_FLOAT -> skipBytes(in, 4);
            case TAG_LONG, TAG_DOUBLE -> skipBytes(in, 8);
            case TAG_BYTE_ARRAY -> skipBytes(in, length(in));
            case TAG_STRING -> skipString(in);
            case TAG_INT_ARRAY -> skipBytes(in, (long) length(in) * 4);
            case TAG_LONG_ARRAY -> skipBytes(in, (long) length(in) * 8);
            case TAG_LIST -> {
                int element = in.readUnsignedByte();
                int count = length(in);
                for (int i = 0; i < count; i++) {
                    skip(in, element, depth + 1);
                }
            }
            case TAG_COMPOUND -> {
                while (true) {
                    int inner = in.readUnsignedByte();
                    if (inner == TAG_END) {
                        return;
                    }
                    skipString(in);
                    skip(in, inner, depth + 1);
                }
            }
            default -> throw new IOException("unknown tag type " + type);
        }
    }

    private static int length(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0) {
            throw new IOException("negative length");
        }
        return length;
    }

    private static void skipString(DataInputStream in) throws IOException {
        skipBytes(in, in.readUnsignedShort());
    }

    private static void skipBytes(DataInputStream in, long count) throws IOException {
        long left = count;
        while (left > 0) {
            long skipped = in.skip(left);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    throw new EOFException();
                }
                skipped = 1;
            }
            left -= skipped;
        }
    }
}
