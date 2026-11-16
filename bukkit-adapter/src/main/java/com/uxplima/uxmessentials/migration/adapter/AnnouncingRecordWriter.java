package com.uxplima.uxmessentials.migration.adapter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.RecordOutcome;
import com.uxplima.uxmessentials.migration.RecordResult;
import com.uxplima.uxmessentials.migration.RecordWriter;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.HologramChanged;
import com.uxplima.uxmessentials.shared.network.WarpChanged;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The live import writer, which also tells peers which warps and holograms the import changed, once the import is
 * done rather than once per row. The bus queue that carries frames over plugin messaging is bounded and drops its
 * oldest frame when full, and it drains only while a player is online, so a frame per imported row would push out
 * the frames live commands send (a ban, a payment) and leave peers with only the last few hundred rows.
 *
 * <ul>
 *   <li><b>Warps</b>: one {@link WarpChanged} for the whole import. A peer drops its entire warp set on any such
 *       frame, so one is enough whatever the import held.
 *   <li><b>Holograms</b>: one {@link HologramChanged} per changed name, each name once, sent in small batches spaced
 *       apart, because a peer reloads and redraws a hologram by name.
 * </ul>
 *
 * <p>Mutes are not sent anywhere, but this server keeps its connected players' mutes in memory, so an import that
 * stored a sanction has it read them again when it ends.
 *
 * <p>Homes, balances and player warps send nothing: a peer's cache of those expires on its own and is reloaded
 * when a backend fills again, and an import has no frame that says "everything changed" for them.
 */
@NullMarked
public final class AnnouncingRecordWriter implements RecordWriter {

    /** Frames per batch: far below the default queue of 256, so live frames sent meanwhile keep their place. */
    static final int HOLOGRAM_BATCH = 16;

    /** The pause between batches, long enough for a carrier to drain the queue between them. */
    static final Duration BATCH_GAP = Duration.ofSeconds(10);

    private final RecordWriter delegate;
    private final BusPublisher bus;
    private final Scheduler scheduler;
    private final Runnable mutesWritten;
    private final Object lock = new Object();
    private boolean storedSanction;
    private @Nullable String changedWarp;
    private final Set<String> changedHolograms = new TreeSet<>();

    public AnnouncingRecordWriter(RecordWriter delegate, BusPublisher bus, Scheduler scheduler) {
        this(delegate, bus, scheduler, () -> {});
    }

    /** @param mutesWritten run when an import that stored a sanction ends */
    public AnnouncingRecordWriter(RecordWriter delegate, BusPublisher bus, Scheduler scheduler, Runnable mutesWritten) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.mutesWritten = Objects.requireNonNull(mutesWritten, "mutesWritten");
    }

    @Override
    public RecordResult write(ImportRecord record, ImportOptions options) {
        RecordResult result = delegate.write(record, options);
        if (stored(result.outcome())) {
            remember(record);
        }
        return result;
    }

    /**
     * Send what the import changed and forget it, so the next import starts clean. Called when an import ends,
     * whether it finished or stopped part way, since every row it stored before stopping is already in the database.
     */
    public void announce() {
        String warp;
        List<String> holograms;
        boolean sanctions;
        synchronized (lock) {
            warp = changedWarp;
            holograms = new ArrayList<>(changedHolograms);
            sanctions = storedSanction;
            changedWarp = null;
            changedHolograms.clear();
            storedSanction = false;
        }
        if (sanctions) {
            mutesWritten.run();
        }
        if (warp != null) {
            bus.publish(new WarpChanged(bus.serverId(), warp));
        }
        publishFrom(holograms, 0);
    }

    private void publishFrom(List<String> names, int from) {
        int end = Math.min(from + HOLOGRAM_BATCH, names.size());
        for (int index = from; index < end; index++) {
            bus.publish(new HologramChanged(bus.serverId(), names.get(index)));
        }
        if (end < names.size()) {
            scheduler.asyncAfter(BATCH_GAP, () -> publishFrom(names, end));
        }
    }

    private void remember(ImportRecord record) {
        synchronized (lock) {
            if (record instanceof ImportRecord.WarpRecord warp && changedWarp == null) {
                changedWarp = warp.warp().warp().name().value();
            } else if (record instanceof ImportRecord.HologramRecord hologram) {
                changedHolograms.add(hologram.hologram().hologram().name().value());
            } else if (record instanceof ImportRecord.ModerationRecord) {
                storedSanction = true;
            }
        }
    }

    private static boolean stored(RecordOutcome outcome) {
        return outcome != RecordOutcome.SKIPPED && outcome != RecordOutcome.FAILED;
    }
}
