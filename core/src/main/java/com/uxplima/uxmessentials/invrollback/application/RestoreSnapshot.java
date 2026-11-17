package com.uxplima.uxmessentials.invrollback.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.invrollback.application.port.PendingRestores;
import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;

/**
 * Restores one of a target's stored inventory snapshots, the application half of the {@code /invrestore} restore
 * action. It resolves the chosen snapshot, and before the caller overwrites the live inventory it captures the
 * target's <em>current</em> serialized inventory as a {@link SnapshotCause#RESTORE} safety copy (through the same
 * {@link CaptureSnapshot} use case, so the count cap applies), then hands the chosen snapshot back so the adapter
 * can decode it and set the live inventory. A stale id. A snapshot pruned or already restored between the GUI
 * open and the click: resolves to empty and captures nothing, so a dead click leaves no orphan safety row.
 *
 * <p>Pure application code: it never touches a Bukkit type. The adapter reads and re-applies the live inventory on
 * the target's entity thread and serializes it into the {@code currentContents} bytes it passes in; this use case
 * runs off the tick thread through the {@code Scheduler.async} port so the two DB touches (find + safety save)
 * never block a region thread.
 *
 * <p>A target who is not online has no live inventory to overwrite, so the chosen snapshot is kept in
 * {@link PendingRestores} instead ({@link #keepForNextJoin}) and put on them as they next join a server of the
 * database ({@link #takeKept}, after the same safety copy, {@link #safetyCopy}). Taking removes the kept
 * restore, so it applies once; a join that could not apply it keeps it again ({@link #keepAgain}).
 */
public final class RestoreSnapshot {

    private final SnapshotRepository repository;
    private final CaptureSnapshot safetyCapture;
    private final PendingRestores pending;

    /**
     * @param repository the durable snapshot store the chosen snapshot is resolved from
     * @param safetyCapture the capture use case that freezes the pre-restore inventory as a RESTORE snapshot
     * @param pending the restores kept for players who were not online
     */
    public RestoreSnapshot(SnapshotRepository repository, CaptureSnapshot safetyCapture, PendingRestores pending) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.safetyCapture = Objects.requireNonNull(safetyCapture, "safetyCapture");
        this.pending = Objects.requireNonNull(pending, "pending");
    }

    /**
     * Resolve the snapshot {@code id} for {@code target}; when it exists, freeze {@code currentContents} as a
     * RESTORE safety snapshot dated {@code now} and return the chosen snapshot for the caller to apply. Returns
     * empty (capturing no safety copy) when the id no longer resolves.
     */
    public Optional<Snapshot> restore(UUID target, SnapshotId id, byte[] currentContents, Instant now) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(currentContents, "currentContents");
        Objects.requireNonNull(now, "now");
        Optional<Snapshot> chosen = repository.find(id);
        if (chosen.isEmpty()) {
            return Optional.empty();
        }
        safetyCapture.capture(target, SnapshotCause.RESTORE, currentContents, now);
        return chosen;
    }

    /**
     * Keep the snapshot {@code id} to be put on {@code target} as they next join, for a target with no live inventory
     * here. Returns the kept snapshot, or empty (keeping nothing) when the id no longer resolves.
     */
    public Optional<Snapshot> keepForNextJoin(UUID target, SnapshotId id) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(id, "id");
        Optional<Snapshot> chosen = repository.find(id);
        chosen.ifPresent(snapshot -> pending.keep(target, snapshot));
        return chosen;
    }

    /** Take the restores kept for {@code target}, oldest first; each is handed out once. */
    public List<Snapshot> takeKept(UUID target) {
        Objects.requireNonNull(target, "target");
        return pending.take(target);
    }

    /** Keep {@code snapshots} again for {@code target}, whose join could not apply them. */
    public void keepAgain(UUID target, List<Snapshot> snapshots) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(snapshots, "snapshots");
        for (Snapshot snapshot : snapshots) {
            pending.keep(target, snapshot);
        }
    }

    /**
     * Freeze {@code currentContents} as a RESTORE safety snapshot dated {@code now}, before the caller puts a kept
     * snapshot on {@code target}.
     */
    public void safetyCopy(UUID target, byte[] currentContents, Instant now) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(currentContents, "currentContents");
        Objects.requireNonNull(now, "now");
        safetyCapture.capture(target, SnapshotCause.RESTORE, currentContents, now);
    }
}
