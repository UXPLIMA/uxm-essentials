package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
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
 *   <li>A save changes only the slots it names, and only those that still hold what the edit started from. A slot
 *       someone changed in between is never written over; the caller decides whether the rest still go ahead.
 *   <li>The new file is written in full to a temporary file beside the old one and forced to disk, the old one is
 *       kept as {@code <uuid>.dat_old} as the server keeps it, and the temporary file is then renamed over
 *       {@code <uuid>.dat} in one step, so the file is never missing or half written. A save that fails leaves the
 *       old file as it was and removes its temporary file.
 *   <li>A player who is joining or connected here ({@link PlayerPresence}) owns their file, so reads and saves are
 *       refused. A login takes the player's lock to mark them joining, so it waits for a save already under way and
 *       every later save sees the mark.
 * </ul>
 */
public final class PlayerDataFiles {

    private static final String DATA_VERSION = "DataVersion";
    private static final int STRIPES = 64;

    /** How many times a rename that the system refuses is tried, as the server's own replace does on Windows. */
    private static final int MOVE_ATTEMPTS = 10;

    // Shared by every instance: a module reload builds a new one while the old one may still be finishing a save,
    // and both write the same files.
    private static final ReentrantLock[] LOCKS = newLocks();

    private final Path directory;
    private final PlayerPresence presence;

    /** Reads and writes the files in {@code directory}, the server's player data directory. */
    public PlayerDataFiles(Path directory, PlayerPresence presence) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.presence = Objects.requireNonNull(presence, "presence");
    }

    /** What a read found. */
    public sealed interface Read permits Found, Refused {}

    /** The container's slots as the file holds them now. */
    public record Found(StoredSlots slots) implements Read {}

    /** Why there is nothing to read: the player is present or has no file. */
    public record Refused(SaveOutcome reason) implements Read {}

    /**
     * Mark {@code player} joining for a login that passed its pre-login, once any save of their file under way has
     * finished. Returns {@code false}, marking nothing, when that save takes longer than {@code wait}.
     */
    public boolean admit(UUID player, Duration wait) throws InterruptedException {
        ReentrantLock lock = lockOf(player);
        if (!lock.tryLock(wait.toNanos(), TimeUnit.NANOSECONDS)) {
            return false;
        }
        try {
            presence.joining(player);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Read {@code layout}'s slots of {@code player}'s file. */
    public Read read(UUID player, ContainerLayout layout) throws IOException {
        ReentrantLock lock = lockOf(player);
        lock.lock();
        try {
            if (presence.present(player)) {
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

    /** {@link #write(UUID, ContainerLayout, StoredSlots, List, Predicate)} that writes all of the slots or none. */
    public SaveResult write(UUID player, ContainerLayout layout, StoredSlots seen, List<SlotWrite> writes)
            throws IOException {
        return write(player, layout, seen, writes, conflicts -> false);
    }

    /**
     * Apply {@code writes} to {@code player}'s file. A slot that no longer holds what {@code seen} found is a conflict
     * and is never written. With no conflicts every slot is written. With some, the other slots are written only if
     * {@code partly} accepts the conflicting ones, and otherwise nothing is; a changed data version writes nothing.
     * Nothing else in the file changes. A failure to write throws and leaves the file as it was.
     */
    public SaveResult write(
            UUID player,
            ContainerLayout layout,
            StoredSlots seen,
            List<SlotWrite> writes,
            Predicate<Set<Integer>> partly)
            throws IOException {
        ReentrantLock lock = lockOf(player);
        lock.lock();
        try {
            if (presence.present(player)) {
                return SaveResult.of(SaveOutcome.PLAYER_PRESENT);
            }
            Path file = file(player);
            if (!Files.isRegularFile(file)) {
                return SaveResult.of(SaveOutcome.NO_DATA);
            }
            RawNbt.Root root = load(file);
            Set<Integer> conflicts = new HashSet<>();
            if (dataVersion(root) != seen.dataVersion()) {
                writes.forEach(write -> conflicts.add(write.slot()));
                return new SaveResult(SaveOutcome.CHANGED, conflicts);
            }
            Map<Integer, TagPayload> now = layout.slots(root.entries());
            List<SlotWrite> clean = new ArrayList<>();
            for (SlotWrite write : writes) {
                if (Objects.equals(now.get(write.slot()), seen.slot(write.slot()))) {
                    clean.add(write);
                } else {
                    conflicts.add(write.slot());
                }
            }
            if (!conflicts.isEmpty() && !partly.test(Set.copyOf(conflicts))) {
                return new SaveResult(SaveOutcome.CHANGED, conflicts);
            }
            if (!clean.isEmpty()) {
                replace(player, file, new RawNbt.Root(root.name(), layout.apply(root.entries(), clean)));
            }
            return new SaveResult(SaveOutcome.SAVED, conflicts);
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
            forceDirectory();
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Rename {@code source} over {@code target}, retrying while the system holds the target, as Windows can. */
    private static void moveOver(Path source, Path target) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (AccessDeniedException held) {
                if (attempt >= MOVE_ATTEMPTS) {
                    throw held;
                }
            }
        }
    }

    /** Make the rename itself durable. Windows cannot open a directory for this, and does not need to. */
    private void forceDirectory() {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException unsupported) {
            // The rename is done either way; only its durability across a power cut depends on this.
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

    /** The lock every read and write of {@code player}'s file holds. */
    static ReentrantLock lockOf(UUID player) {
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
