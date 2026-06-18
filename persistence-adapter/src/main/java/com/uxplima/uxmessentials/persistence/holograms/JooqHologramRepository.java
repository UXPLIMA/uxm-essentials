package com.uxplima.uxmessentials.persistence.holograms;

import static com.uxplima.uxmessentials.persistence.jooq.tables.HologramLines.HOLOGRAM_LINES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.HologramManualViewer.HOLOGRAM_MANUAL_VIEWER;
import static com.uxplima.uxmessentials.persistence.jooq.tables.Holograms.HOLOGRAMS;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.HologramsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Record;

/**
 * The jOOQ-backed {@link HologramRepository} over the generated {@code HOLOGRAMS} and {@code HOLOGRAM_LINES}
 * tables. Holograms are server-wide and keyed by name alone, so a lookup is a single-row {@code SELECT} on
 * the name primary key plus its ordered lines, the list reads every row in stored creation order, and a
 * {@code save} upserts the name row then rewrites that hologram's lines in one transaction (so a line edit
 * never leaves a stale row behind). Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
public final class JooqHologramRepository extends JooqRepository implements HologramRepository {

    public JooqHologramRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Optional<Hologram> find(HologramName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> dsl.selectFrom(HOLOGRAMS)
                .where(HOLOGRAMS.NAME.eq(name.value()))
                .fetchOptional()
                .map(row -> HologramRows.toHologram(row, lines(dsl, name.value()))));
    }

    @Override
    public List<Hologram> all() {
        return read(dsl -> {
            Map<String, List<String>> linesByName = allLines(dsl);
            return dsl.selectFrom(HOLOGRAMS)
                    .orderBy(HOLOGRAMS.CREATED_AT.asc(), HOLOGRAMS.NAME.asc())
                    .fetch()
                    .map(row ->
                            HologramRows.toHologram(row, linesByName.getOrDefault(row.get(HOLOGRAMS.NAME), List.of())));
        });
    }

    @Override
    public boolean exists(HologramName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> dsl.fetchExists(HOLOGRAMS, HOLOGRAMS.NAME.eq(name.value())));
    }

    @Override
    public void save(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        write(dsl -> {
            upsertNameRow(dsl, hologram);
            rewriteLines(dsl, hologram);
            return null;
        });
    }

    @Override
    public void delete(HologramName name) {
        Objects.requireNonNull(name, "name");
        write(dsl -> {
            dsl.deleteFrom(HOLOGRAM_LINES)
                    .where(HOLOGRAM_LINES.HOLOGRAM.eq(name.value()))
                    .execute();
            dsl.deleteFrom(HOLOGRAM_MANUAL_VIEWER)
                    .where(HOLOGRAM_MANUAL_VIEWER.HOLOGRAM_NAME.eq(name.value()))
                    .execute();
            return dsl.deleteFrom(HOLOGRAMS)
                    .where(HOLOGRAMS.NAME.eq(name.value()))
                    .execute();
        });
    }

    @Override
    public Set<UUID> manualViewers(HologramName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> {
            Set<UUID> viewers = new LinkedHashSet<>();
            for (String stored : dsl.select(HOLOGRAM_MANUAL_VIEWER.PLAYER_UUID)
                    .from(HOLOGRAM_MANUAL_VIEWER)
                    .where(HOLOGRAM_MANUAL_VIEWER.HOLOGRAM_NAME.eq(name.value()))
                    .fetch(HOLOGRAM_MANUAL_VIEWER.PLAYER_UUID)) {
                viewers.add(UUID.fromString(stored));
            }
            return viewers;
        });
    }

    @Override
    public void showTo(HologramName name, UUID viewer) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(viewer, "viewer");
        write(dsl -> dsl.insertInto(HOLOGRAM_MANUAL_VIEWER)
                .set(HOLOGRAM_MANUAL_VIEWER.HOLOGRAM_NAME, name.value())
                .set(HOLOGRAM_MANUAL_VIEWER.PLAYER_UUID, viewer.toString())
                .onConflict(HOLOGRAM_MANUAL_VIEWER.HOLOGRAM_NAME, HOLOGRAM_MANUAL_VIEWER.PLAYER_UUID)
                .doNothing()
                .execute());
    }

    @Override
    public void hideFrom(HologramName name, UUID viewer) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(viewer, "viewer");
        write(dsl -> dsl.deleteFrom(HOLOGRAM_MANUAL_VIEWER)
                .where(HOLOGRAM_MANUAL_VIEWER.HOLOGRAM_NAME.eq(name.value()))
                .and(HOLOGRAM_MANUAL_VIEWER.PLAYER_UUID.eq(viewer.toString()))
                .execute());
    }

    private static List<String> lines(DSLContext dsl, String name) {
        return dsl.select(HOLOGRAM_LINES.TEXT)
                .from(HOLOGRAM_LINES)
                .where(HOLOGRAM_LINES.HOLOGRAM.eq(name))
                .orderBy(HOLOGRAM_LINES.IDX.asc())
                .fetch(HOLOGRAM_LINES.TEXT);
    }

    private static Map<String, List<String>> allLines(DSLContext dsl) {
        Map<String, List<String>> byName = new LinkedHashMap<>();
        for (Record row : dsl.select(HOLOGRAM_LINES.HOLOGRAM, HOLOGRAM_LINES.TEXT)
                .from(HOLOGRAM_LINES)
                .orderBy(HOLOGRAM_LINES.HOLOGRAM.asc(), HOLOGRAM_LINES.IDX.asc())
                .fetch()) {
            byName.computeIfAbsent(row.get(HOLOGRAM_LINES.HOLOGRAM), key -> new ArrayList<>())
                    .add(row.get(HOLOGRAM_LINES.TEXT));
        }
        return byName;
    }

    private static void upsertNameRow(DSLContext dsl, Hologram hologram) {
        HologramsRecord record = dsl.newRecord(HOLOGRAMS);
        HologramRows.apply(record, hologram);
        dsl.insertInto(HOLOGRAMS)
                .set(record)
                .onConflict(HOLOGRAMS.NAME)
                .doUpdate()
                .set(HOLOGRAMS.TYPE, record.getType())
                .set(HOLOGRAMS.ITEM_MATERIAL, record.getItemMaterial())
                .set(HOLOGRAMS.BLOCK_DATA, record.getBlockData())
                .set(HOLOGRAMS.WORLD, record.getWorld())
                .set(HOLOGRAMS.WORLD_NAME, record.getWorldName())
                .set(HOLOGRAMS.X, record.getX())
                .set(HOLOGRAMS.Y, record.getY())
                .set(HOLOGRAMS.Z, record.getZ())
                .set(HOLOGRAMS.YAW, record.getYaw())
                .set(HOLOGRAMS.PITCH, record.getPitch())
                .set(HOLOGRAMS.CREATED_AT, record.getCreatedAt())
                .set(HOLOGRAMS.BILLBOARD, record.getBillboard())
                .set(HOLOGRAMS.BACKGROUND_ARGB, record.getBackgroundArgb())
                .set(HOLOGRAMS.TEXT_SHADOW, record.getTextShadow())
                .set(HOLOGRAMS.BRIGHTNESS_BLOCK, record.getBrightnessBlock())
                .set(HOLOGRAMS.BRIGHTNESS_SKY, record.getBrightnessSky())
                .set(HOLOGRAMS.SCALE, record.getScale())
                .set(HOLOGRAMS.SCALE_Y, record.getScaleY())
                .set(HOLOGRAMS.SCALE_Z, record.getScaleZ())
                .set(HOLOGRAMS.TRANSLATION_X, record.getTranslationX())
                .set(HOLOGRAMS.TRANSLATION_Y, record.getTranslationY())
                .set(HOLOGRAMS.TRANSLATION_Z, record.getTranslationZ())
                .set(HOLOGRAMS.SEE_THROUGH, record.getSeeThrough())
                .set(HOLOGRAMS.TEXT_ALIGNMENT, record.getTextAlignment())
                .set(HOLOGRAMS.SHADOW_RADIUS, record.getShadowRadius())
                .set(HOLOGRAMS.SHADOW_STRENGTH, record.getShadowStrength())
                .set(HOLOGRAMS.LINE_WIDTH, record.getLineWidth())
                .set(HOLOGRAMS.VIEW_RANGE, record.getViewRange())
                .set(HOLOGRAMS.VISIBILITY_MODE, record.getVisibilityMode())
                .set(HOLOGRAMS.VISIBILITY_PERMISSION, record.getVisibilityPermission())
                .set(HOLOGRAMS.VISIBILITY_DISTANCE, record.getVisibilityDistance())
                .set(HOLOGRAMS.ROTATION_YAW, record.getRotationYaw())
                .set(HOLOGRAMS.ROTATION_PITCH, record.getRotationPitch())
                .set(HOLOGRAMS.REFRESH_INTERVAL_TICKS, record.getRefreshIntervalTicks())
                .set(HOLOGRAMS.LINKED_NPC_NAME, record.getLinkedNpcName())
                // The V52-V56 columns are updated here too, so a setting applied to an already-saved hologram
                // (head/entity model, glow/opacity, click command, leaderboard) survives a restart rather than
                // only persisting on the row's first insert.
                .set(HOLOGRAMS.HEAD_TEXTURE, record.getHeadTexture())
                .set(HOLOGRAMS.ENTITY_TYPE, record.getEntityType())
                .set(HOLOGRAMS.GLOW_ARGB, record.getGlowArgb())
                .set(HOLOGRAMS.TEXT_OPACITY, record.getTextOpacity())
                .set(HOLOGRAMS.CLICK_COMMAND, record.getClickCommand())
                .set(HOLOGRAMS.LEADERBOARD_PROVIDER, record.getLeaderboardProvider())
                .set(HOLOGRAMS.LEADERBOARD_LIMIT, record.getLeaderboardLimit())
                .execute();
    }

    private static void rewriteLines(DSLContext dsl, Hologram hologram) {
        String name = hologram.name().value();
        dsl.deleteFrom(HOLOGRAM_LINES).where(HOLOGRAM_LINES.HOLOGRAM.eq(name)).execute();
        List<HologramLine> lines = hologram.lines();
        for (int idx = 0; idx < lines.size(); idx++) {
            dsl.insertInto(HOLOGRAM_LINES)
                    .set(HOLOGRAM_LINES.HOLOGRAM, name)
                    .set(HOLOGRAM_LINES.IDX, idx)
                    .set(HOLOGRAM_LINES.TEXT, lines.get(idx).value())
                    .execute();
        }
    }
}
