package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Shared presentation of a {@link Snapshot} for the {@code /invrestore} surface: the capture cause as its constant
 * name (DEATH / LOGOUT / RESTORE) and the capture instant as a short local-zone timestamp. Both the engine-backed
 * list entry and the preview window title read the same {@link #placeholders} map, so a snapshot reads identically
 * wherever it is shown, and there is exactly one place the timestamp format lives.
 */
@NullMarked
final class SnapshotDisplay {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private SnapshotDisplay() {}

    /** The catalog placeholders every snapshot label shares: {@code player}, {@code cause}, {@code time}. */
    static Map<String, String> placeholders(PlayerRef target, Snapshot snapshot) {
        return Map.of(
                "player", target.name(),
                "cause", snapshot.cause().name(),
                "time", TIMESTAMP.format(snapshot.createdAt()));
    }
}
