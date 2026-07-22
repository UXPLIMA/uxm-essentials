package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.util.Objects;

import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec.Summary;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import org.jspecify.annotations.NullMarked;

/**
 * One resolved restore-list row: a {@link Snapshot} paired with the item-free {@link Summary} of its payload
 * (capture location and per-store slot counts). The summary is decoded off the tick thread when the list is
 * resolved, so the list's icon renderer reads only these fields and never parses a payload on the entity thread.
 *
 * @param snapshot the stored snapshot this row represents
 * @param summary the location and occupied-slot counts read from the snapshot's payload
 */
@NullMarked
record SnapshotCard(Snapshot snapshot, Summary summary) {

    SnapshotCard {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(summary, "summary");
    }
}
