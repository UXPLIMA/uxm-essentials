package com.uxplima.uxmessentials.persistence.holograms;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.persistence.jooq.tables.Holograms;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.HologramsRecord;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jooq.Record;

/**
 * The anti-corruption mapping between a {@code holograms} row (plus its ordered {@code hologram_lines}
 * child rows) and the domain {@link Hologram}. The world uuid is stored as its canonical 36-character text
 * and the creation time as epoch milliseconds, so the column shape is identical on every backend. This class
 * is the single place that translation lives.
 */
final class HologramRows {

    private static final Holograms HOLOGRAMS = Holograms.HOLOGRAMS;

    private HologramRows() {}

    /** Rebuild a {@link Hologram} from a name row and its already-ordered line texts. */
    static Hologram toHologram(Record row, List<String> orderedLineTexts) {
        WorldRef world = new WorldRef(UUID.fromString(row.get(HOLOGRAMS.WORLD)), row.get(HOLOGRAMS.WORLD_NAME));
        Position position = new Position(
                world,
                row.get(HOLOGRAMS.X),
                row.get(HOLOGRAMS.Y),
                row.get(HOLOGRAMS.Z),
                row.get(HOLOGRAMS.YAW),
                row.get(HOLOGRAMS.PITCH));
        List<HologramLine> lines =
                orderedLineTexts.stream().map(HologramLine::new).toList();
        return new Hologram(
                HologramName.of(row.get(HOLOGRAMS.NAME)),
                position,
                lines,
                Instant.ofEpochMilli(row.get(HOLOGRAMS.CREATED_AT)));
    }

    /** Populate a {@link HologramsRecord} from a domain {@link Hologram} for an upsert (the name row only). */
    static void apply(HologramsRecord record, Hologram hologram) {
        Position location = hologram.location();
        record.setName(hologram.name().value())
                .setWorld(location.world().uid().toString())
                .setWorldName(location.world().name())
                .setX(location.x())
                .setY(location.y())
                .setZ(location.z())
                .setYaw(location.yaw())
                .setPitch(location.pitch())
                .setCreatedAt(hologram.createdAt().toEpochMilli());
    }
}
