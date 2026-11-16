package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.RawNbt.Entry;

/**
 * The one way this plugin reads and writes offline players' data files, so that no write can corrupt a file, lose
 * another write, or reach a file the server is using.
 *
 * <ul>
 *   <li>Every read and write of one player's file holds that player's lock, so a save reads, checks and replaces the
 *       file in one step that no other save interleaves with.
 *   <li>A save changes only the slots it names, and only while each still holds what the window it came from was
 *       opened with. Two edits of different slots both land; an edit of a slot someone changed in between is
 *       refused rather than written over the change.
 *   <li>The new file is written in full to a temporary file beside the old one, the old one is kept as
 *       {@code <uuid>.dat_old} as the server keeps it, and the temporary file is then renamed over
 *       {@code <uuid>.dat} in one step, so the file is never missing or half written. A save that fails leaves the
 *       old file as it was and removes its temporary file.
 *   <li>A player who is joining or online here is <em>present</em>: their file belongs to the server, which reads
 *       it on login and writes it on quit, so reads and saves are refused. A login takes the player's lock to mark
 *       them present, so it waits for a save already under way and every later save sees the mark.
 * </ul>
 */
public final class PlayerDataFiles {

    private static final String DATA_VERSION = "DataVersion";
    private static final int STRIPES = 64;

    // Shared by every instance: a module reload builds a new one while the old one may still be finishing a save,
    // and both write the same files.
    private static final ReentrantLock[] LOCKS = newLocks();

    private final Path directory;
    private final Map<UUID, Integer> present = new ConcurrentHashMap<>();

    /** Reads and writes the files in {@code directory}, the server's player data directory. */
    public PlayerDataFiles(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /** What a read found. */
    public sealed interface Read permits Found, Refused {}

    /** The container's slots as the file holds them now. */
    public record Found(StoredSlots slots) implements Read {}

    /** Why there is nothing to read: the player is present or has no file. */
    public record Refused(SaveOutcome reason) implements Read {}

    /**
     * Mark {@code player} present for a connection that is logging in, once any save of their file under way has
     * finished. Returns {@code false}, marking nothing, when that save takes longer than {@code wait}.
     */
    public boolean admit(UUID player, Duration wait) throws InterruptedException {
        ReentrantLock lock = lockOf(player);
        if (!lock.tryLock(wait.toNanos(), TimeUnit.NANOSECONDS)) {
            return false;
        }
        try {
            present.merge(player, 1, Integer::sum);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Mark {@code player} present for a connection that was already in when this started. */
    public void adopt(UUID player) {
        present.merge(Objects.requireNonNull(player, "player"), 1, Integer::sum);
    }

    /** One of {@code player}'s connections closed, after the server saved their data. */
    public void release(UUID player) {
        present.computeIfPresent(player, (id, connections) -> connections > 1 ? connections - 1 : null);
    }

    /** Read {@code layout}'s slots of {@code player}'s file. */
    public Read read(UUID player, ContainerLayout layout) throws IOException {
        ReentrantLock lock = lockOf(player);
        lock.lock();
        try {
            if (present.containsKey(player)) {
                return new Refused(SaveOutcome.PLAYER_PRESENT);
            }
            Path file = file(player);
            if (!Files.isRegularFile(file)) {
                return new Refused(SaveOutcome.NO_DATA);
            }
            RawNbt.Root root = load(file);
            return new Found(new StoredSlots(dataVersion(root), layout.slots(root.entries())));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Apply {@code writes} to {@code player}'s file, provided every slot they change, and the data version, are still
     * as {@code seen} found them. Nothing else in the file changes. A failure to write throws and leaves the file as
     * it was.
     */
    public SaveOutcome write(UUID player, ContainerLayout layout, StoredSlots seen, List<SlotWrite> writes)
            throws IOException {
        ReentrantLock lock = lockOf(player);
        lock.lock();
        try {
            if (present.containsKey(player)) {
                return SaveOutcome.PLAYER_PRESENT;
            }
            Path file = file(player);
            if (!Files.isRegularFile(file)) {
                return SaveOutcome.NO_DATA;
            }
            RawNbt.Root root = load(file);
            if (dataVersion(root) != seen.dataVersion()) {
                return SaveOutcome.CHANGED;
            }
            Map<Integer, TagPayload> now = layout.slots(root.entries());
            for (SlotWrite write : writes) {
                if (!Objects.equals(now.get(write.slot()), seen.slot(write.slot()))) {
                    return SaveOutcome.CHANGED;
                }
            }
            if (!writes.isEmpty()) {
                replace(player, file, new RawNbt.Root(root.name(), layout.apply(root.entries(), writes)));
            }
            return SaveOutcome.SAVED;
        } finally {
            lock.unlock();
        }
    }

    private void replace(UUID player, Path file, RawNbt.Root root) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(RawNbt.root(root));
        }
        Path temporary = Files.createTempFile(directory, player + "-", ".dat");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap(compressed.toByteArray());
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                // On disk before the rename, so a crash cannot leave the new name on an empty file.
                channel.force(true);
            }
            Files.copy(file, directory.resolve(player + ".dat_old"), StandardCopyOption.REPLACE_EXISTING);
            moveOver(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveOver(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static RawNbt.Root load(Path file) throws IOException {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            return RawNbt.root(in.readAllBytes());
        }
    }

    private static int dataVersion(RawNbt.Root root) throws IOException {
        for (Entry entry : root.entries()) {
            if (entry.name().equals(DATA_VERSION)) {
                return entry.type() == RawNbt.INT
                        ? (int) RawNbt.number(entry.type(), entry.payload())
                        : StoredSlots.NO_VERSION;
            }
        }
        return StoredSlots.NO_VERSION;
    }

    private Path file(UUID player) {
        return directory.resolve(player + ".dat");
    }

    private static ReentrantLock lockOf(UUID player) {
        return LOCKS[Math.floorMod(player.hashCode(), STRIPES)];
    }

    private static ReentrantLock[] newLocks() {
        ReentrantLock[] locks = new ReentrantLock[STRIPES];
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }
}
