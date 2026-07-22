package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec.Summary;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * Proves the {@code /invrestore} info lore carries the facts the enriched GUI shows: the cause, an absolute and a
 * relative timestamp, the per-store item counts, and the capture location as world plus block coordinates.
 */
class SnapshotDisplayTest {

    private static final Instant WHEN = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void baseCarriesCauseTimeRelativeAndCounts() {
        PlayerRef target = new PlayerRef(UUID.randomUUID(), "Victim");
        Snapshot snapshot = Snapshot.of(SnapshotId.random(), target.uuid(), SnapshotCause.DEATH, WHEN, new byte[0]);
        Summary summary = new Summary(Optional.empty(), 10, 3, 1, 5);

        Map<String, String> base = SnapshotDisplay.base(target, snapshot, summary, WHEN.plusSeconds(300));

        assertThat(base).containsEntry("player", "Victim");
        assertThat(base).containsEntry("cause", "DEATH");
        assertThat(base).containsEntry("items", "11"); // 10 main + 1 offhand
        assertThat(base).containsEntry("armor", "3");
        assertThat(base).containsEntry("ender", "5");
        assertThat(base).containsEntry("ago", "5m");
        assertThat(base.get("time")).isNotBlank();
    }

    @Test
    void locationCarriesWorldAndBlockCoordinates() {
        Position position = new Position(new WorldRef(UUID.randomUUID(), "world"), 123.7, 64.0, -50.2, 12f, -3f);

        Map<String, String> location = SnapshotDisplay.location(position);

        assertThat(location).containsEntry("world", "world");
        assertThat(location).containsEntry("x", "123");
        assertThat(location).containsEntry("y", "64");
        assertThat(location).containsEntry("z", "-51"); // floor(-50.2)
    }

    @Test
    void labelIsAOneLineWorldAndCoordinates() {
        Position position = new Position(new WorldRef(UUID.randomUUID(), "nether"), 8.9, 70.0, -4.0, 0f, 0f);

        assertThat(SnapshotDisplay.label(position)).isEqualTo("nether 8, 70, -4");
    }
}
