package com.uxplima.uxmessentials.persistence.holograms;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.domain.Appearance;
import com.uxplima.uxmessentials.holograms.domain.Billboard;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.HologramType;
import com.uxplima.uxmessentials.holograms.domain.Rotation;
import com.uxplima.uxmessentials.holograms.domain.TextAlignment;
import com.uxplima.uxmessentials.holograms.domain.Transform;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.persistence.jooq.tables.Holograms;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.HologramsRecord;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jooq.Record;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption mapping between a {@code holograms} row (plus its ordered {@code hologram_lines}
 * child rows) and the domain {@link Hologram}. The world uuid is stored as its canonical 36-character text
 * and the creation time as epoch milliseconds, so the column shape is identical on every backend. The V35
 * appearance columns and V36 visibility columns are nullable: an absent value reads back as the matching
 * {@link Appearance} default (or a static interval), and as {@link Visibility#everyone()}, so a pre-V35 row
 * keeps its current look and a pre-V36 row stays visible to everyone. The V37 type columns are likewise
 * nullable: a NULL type reads back as {@link HologramType#TEXT}, so a pre-V37 row keeps rendering its lines.
 * This class is the single place that translation lives.
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
        Hologram hologram = new Hologram(
                HologramName.of(row.get(HOLOGRAMS.NAME)),
                position,
                typeOf(row.get(HOLOGRAMS.TYPE)),
                lines,
                row.get(HOLOGRAMS.ITEM_MATERIAL),
                row.get(HOLOGRAMS.BLOCK_DATA),
                appearanceOf(row),
                visibilityOf(row),
                rotationOf(row),
                intOr(row.get(HOLOGRAMS.REFRESH_INTERVAL_TICKS), Hologram.STATIC),
                Instant.ofEpochMilli(row.get(HOLOGRAMS.CREATED_AT)));
        // A pre-V50 row (NULL linked_npc_name) — and any row that never linked — reads back unlinked, so an
        // existing hologram keeps anchoring to its own coordinates with no data migration.
        return linkedNpcOf(row, hologram);
    }

    private static Hologram linkedNpcOf(Record row, Hologram hologram) {
        String linkedNpcName = row.get(HOLOGRAMS.LINKED_NPC_NAME);
        return linkedNpcName == null ? hologram : hologram.linkedTo(linkedNpcName);
    }

    /** Populate a {@link HologramsRecord} from a domain {@link Hologram} for an upsert (the name row only). */
    static void apply(HologramsRecord record, Hologram hologram) {
        Position location = hologram.location();
        Appearance appearance = hologram.appearance();
        Visibility visibility = hologram.visibility();
        Rotation rotation = hologram.rotation();
        record.setName(hologram.name().value())
                .setType(hologram.type().name())
                .setItemMaterial(hologram.itemMaterial())
                .setBlockData(hologram.blockData())
                .setWorld(location.world().uid().toString())
                .setWorldName(location.world().name())
                .setX(location.x())
                .setY(location.y())
                .setZ(location.z())
                .setYaw(location.yaw())
                .setPitch(location.pitch())
                .setCreatedAt(hologram.createdAt().toEpochMilli())
                .setBillboard(appearance.billboard().name())
                .setBackgroundArgb(appearance.hasBackground() ? appearance.backgroundArgb() : null)
                .setTextShadow((short) (appearance.textShadow() ? 1 : 0))
                .setBrightnessBlock(brightnessColumn(appearance.brightnessBlock()))
                .setBrightnessSky(brightnessColumn(appearance.brightnessSky()))
                .setScale(appearance.scale())
                .setScaleY(appearance.transform().scaleY())
                .setScaleZ(appearance.transform().scaleZ())
                .setTranslationX(appearance.transform().translationX())
                .setTranslationY(appearance.transform().translationY())
                .setTranslationZ(appearance.transform().translationZ())
                .setSeeThrough((short) (appearance.seeThrough() ? 1 : 0))
                .setTextAlignment(appearance.alignment().name())
                .setShadowRadius(shadowColumn(appearance.shadowRadius()))
                .setShadowStrength(shadowColumn(appearance.shadowStrength()))
                .setLineWidth(appearance.lineWidth())
                .setViewRange(appearance.viewRange())
                .setVisibilityMode(visibility.mode().name())
                .setVisibilityPermission(visibility.permission())
                .setVisibilityDistance(visibility.distance())
                .setRotationYaw(rotation.yaw())
                .setRotationPitch(rotation.pitch())
                .setRefreshIntervalTicks(hologram.refreshIntervalTicks())
                .setLinkedNpcName(hologram.linkedNpcName());
    }

    private static Appearance appearanceOf(Record row) {
        Appearance defaults = Appearance.defaults();
        // The V35 uniform scale is the X axis and the per-axis fallback: a pre-V48 row (NULL scale_y/z) reads
        // back as a uniform scale, so an existing scaled hologram keeps its size on every axis.
        float scaleX = floatOr(row.get(HOLOGRAMS.SCALE), defaults.scale());
        Transform transform = new Transform(
                floatOr(row.get(HOLOGRAMS.TRANSLATION_X), 0f),
                floatOr(row.get(HOLOGRAMS.TRANSLATION_Y), 0f),
                floatOr(row.get(HOLOGRAMS.TRANSLATION_Z), 0f),
                scaleX,
                floatOr(row.get(HOLOGRAMS.SCALE_Y), scaleX),
                floatOr(row.get(HOLOGRAMS.SCALE_Z), scaleX));
        return new Appearance(
                billboardOf(row.get(HOLOGRAMS.BILLBOARD)),
                intOr(row.get(HOLOGRAMS.BACKGROUND_ARGB), Appearance.DEFAULT_BACKGROUND),
                shadowOf(row.get(HOLOGRAMS.TEXT_SHADOW)),
                brightnessOf(row.get(HOLOGRAMS.BRIGHTNESS_BLOCK)),
                brightnessOf(row.get(HOLOGRAMS.BRIGHTNESS_SKY)),
                transform,
                intOr(row.get(HOLOGRAMS.LINE_WIDTH), defaults.lineWidth()),
                floatOr(row.get(HOLOGRAMS.VIEW_RANGE), defaults.viewRange()),
                shadowOf(row.get(HOLOGRAMS.SEE_THROUGH)),
                alignmentOf(row.get(HOLOGRAMS.TEXT_ALIGNMENT)),
                shadowValueOf(row.get(HOLOGRAMS.SHADOW_RADIUS)),
                shadowValueOf(row.get(HOLOGRAMS.SHADOW_STRENGTH)));
    }

    private static TextAlignment alignmentOf(@Nullable String stored) {
        // A pre-V48 row (NULL alignment) — and any unknown token — reads back as CENTER, so a row never resolves
        // to an invalid alignment and an existing hologram keeps its centred lines.
        if (stored == null) {
            return TextAlignment.CENTER;
        }
        return TextAlignment.parse(stored).orElse(TextAlignment.CENTER);
    }

    private static float shadowValueOf(@Nullable Float stored) {
        return stored == null ? Appearance.DEFAULT_SHADOW : stored;
    }

    private static @Nullable Float shadowColumn(float shadow) {
        return Appearance.isDefaultShadow(shadow) ? null : shadow;
    }

    private static Visibility visibilityOf(Record row) {
        String mode = row.get(HOLOGRAMS.VISIBILITY_MODE);
        String permission = row.get(HOLOGRAMS.VISIBILITY_PERMISSION);
        int distance = intOr(row.get(HOLOGRAMS.VISIBILITY_DISTANCE), Visibility.UNLIMITED);
        // A pre-V36 row (NULL mode) — and a PERMISSION mode that somehow lost its node — both read back as
        // "visible to everyone", so a row never resolves to an invalid Visibility.
        if ("PERMISSION".equalsIgnoreCase(mode) && permission != null && !permission.isBlank()) {
            return new Visibility(Visibility.Mode.PERMISSION, permission, distance);
        }
        if ("MANUAL".equalsIgnoreCase(mode)) {
            return new Visibility(Visibility.Mode.MANUAL, null, distance);
        }
        return new Visibility(Visibility.Mode.ALL, null, distance);
    }

    private static Rotation rotationOf(Record row) {
        // The V43 columns are NOT NULL DEFAULT 0, so an existing pre-V43 row reads back as 0/0 = Rotation.NONE.
        return Rotation.of(
                floatOr(row.get(HOLOGRAMS.ROTATION_YAW), 0f), floatOr(row.get(HOLOGRAMS.ROTATION_PITCH), 0f));
    }

    private static HologramType typeOf(@Nullable String stored) {
        // A pre-V37 row (NULL type) — and any unknown token — reads back as TEXT, so a row never resolves to
        // an invalid type and an existing hologram keeps rendering its lines.
        return HologramType.parse(stored).orElse(HologramType.TEXT);
    }

    private static Billboard billboardOf(@Nullable String stored) {
        if (stored == null) {
            return Billboard.CENTER;
        }
        return Billboard.parse(stored.toUpperCase(Locale.ROOT)).orElse(Billboard.CENTER);
    }

    private static boolean shadowOf(@Nullable Short stored) {
        return stored != null && stored != 0;
    }

    private static int brightnessOf(@Nullable Integer stored) {
        return stored == null ? Appearance.DEFAULT_BRIGHTNESS : stored;
    }

    private static @Nullable Integer brightnessColumn(int brightness) {
        return Appearance.isDefaultBrightness(brightness) ? null : brightness;
    }

    private static int intOr(@Nullable Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static float floatOr(@Nullable Float value, float fallback) {
        return value == null ? fallback : value;
    }
}
