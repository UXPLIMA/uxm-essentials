package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.util.Objects;

import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * One open snapshot preview: who is looking, whose snapshot it is, and the snapshot itself. It is the subject the
 * menu carries, so the text placeholders, the three action buttons and the content region all read what they need
 * from here. The whole {@link Snapshot} is carried so the teleport and export actions read its location and items
 * without a second database round-trip.
 */
@NullMarked
record SnapshotPreview(PlayerRef staff, PlayerRef target, Snapshot snapshot) {

    SnapshotPreview {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(snapshot, "snapshot");
    }

    /** The snapshot the restore button applies. */
    SnapshotId snapshotId() {
        return snapshot.id();
    }
}
