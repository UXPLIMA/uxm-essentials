package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec.Summary;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.shared.domain.DurationText;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * Shared presentation of a {@link Snapshot} for the {@code /invrestore} surface: the capture cause, an absolute
 * and a relative timestamp, the per-store item counts, and the capture location. Both the engine-backed list entry
 * and the preview window read the same placeholder maps, so a snapshot reads identically wherever it is shown, and
 * there is exactly one place the timestamp format lives.
 */
@NullMarked
final class SnapshotDisplay {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private SnapshotDisplay() {}

    /**
     * The core placeholders every snapshot label shares: {@code player}, {@code cause}, {@code time} (absolute),
     * {@code ago} (relative, e.g. {@code 5m}), and the occupied-slot counts {@code items} / {@code armor} /
     * {@code ender}. The location placeholders are supplied separately by {@link #location} so a label can omit the
     * location line for a snapshot that predates location capture.
     */
    static Map<String, String> base(PlayerRef target, Snapshot snapshot, Summary summary, Instant now) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", target.name());
        placeholders.put("cause", snapshot.cause().name());
        placeholders.put("time", time(snapshot.createdAt()));
        placeholders.put("ago", DurationText.humanize(Duration.between(snapshot.createdAt(), now)));
        placeholders.put("items", Integer.toString(summary.carriedItems()));
        placeholders.put("armor", Integer.toString(summary.armor()));
        placeholders.put("ender", Integer.toString(summary.ender()));
        return placeholders;
    }

    /** The location placeholders for the location line: {@code world} plus block {@code x} / {@code y} / {@code z}. */
    static Map<String, String> location(Position position) {
        return Map.of(
                "world", position.world().name(),
                "x", Integer.toString(position.blockX()),
                "y", Integer.toString(position.blockY()),
                "z", Integer.toString(position.blockZ()));
    }

    /** A one-line {@code "world x, y, z"} label for a message placeholder (the teleport and export feedback). */
    static String label(Position position) {
        return position.world().name() + " " + position.blockX() + ", " + position.blockY() + ", " + position.blockZ();
    }

    /** The absolute capture timestamp in the shared short local-zone format. */
    static String time(Instant instant) {
        return TIMESTAMP.format(instant);
    }
}
