package com.uxplima.uxmessentials.npc.domain;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The "how an NPC looks" half of the {@link Npc} aggregate: its skin, the entity type it renders as, worn
 * equipment, the glow outline and its colour, the body pose, the size multiplier, and the per-entity-type
 * appearance metadata. Grouping these visual fields into one immutable value object keeps the {@link Npc}
 * aggregate small while leaving the public surface unchanged — {@code Npc} delegates every visual transition and
 * accessor here. An appearance is a value object: each {@code with*} produces a new instance rather than mutating.
 *
 * <p>{@code skin} is the fake player's skin, or {@code null} for the default (Steve). {@code entityType} is the
 * uppercase Bukkit {@code EntityType} name the NPC renders as ({@code "PLAYER"} by default — the one type with the
 * tab-entry + skin path); it is a plain string so the domain stays Bukkit-free, and the adapter resolves it. The
 * skin is kept across a type change, so flipping a mob back to {@code PLAYER} restores its skin.
 *
 * <p>{@code equipment} maps each worn {@link EquipmentSlot} to an opaque item <em>token</em> stored verbatim —
 * either a legacy material name ({@code DIAMOND_HELMET}) or a serialized full-item payload — that the render
 * adapter alone resolves to a real Bukkit item. A slot absent from the map is empty. {@code glowing} toggles the
 * outline; {@code glowColor} is the colour name ({@code RED}) it is tinted, or {@code null} for the default white.
 *
 * <p>{@code pose} is the uppercase pose name the NPC is frozen in ({@code "STANDING"} by default); an unknown name
 * renders standing rather than failing here. {@code scale} resizes the NPC ({@code 1.0} natural) and must be finite
 * and positive. {@code typeData} is the per-entity-type appearance metadata as opaque key/value strings the render
 * adapter alone interprets. The equipment and type-data maps are copied defensively so a stored snapshot is immutable.
 */
public record NpcAppearance(
        @Nullable NpcSkin skin,
        String entityType,
        Map<EquipmentSlot, String> equipment,
        boolean glowing,
        @Nullable String glowColor,
        String pose,
        double scale,
        Map<String, String> typeData) {

    /** The default entity type: a fake player, the one type with the tab-entry + skin path. */
    public static final String DEFAULT_ENTITY_TYPE = "PLAYER";

    /** The default body pose: the natural upright stance. */
    public static final String DEFAULT_POSE = "STANDING";

    /** The default size multiplier: the NPC's natural size. */
    public static final double DEFAULT_SCALE = 1.0;

    public NpcAppearance {
        equipment = copyEquipment(equipment);
        entityType = normalizeType(entityType);
        pose = normalizePose(pose);
        scale = validateScale(scale);
        typeData = copyTypeData(typeData);
    }

    /** The default appearance for a freshly created NPC carrying the given (possibly {@code null}) skin. */
    static NpcAppearance defaults(@Nullable NpcSkin skin) {
        return new NpcAppearance(
                skin, DEFAULT_ENTITY_TYPE, Map.of(), false, null, DEFAULT_POSE, DEFAULT_SCALE, Map.of());
    }

    NpcAppearance withSkin(@Nullable NpcSkin newSkin) {
        return new NpcAppearance(newSkin, entityType, equipment, glowing, glowColor, pose, scale, typeData);
    }

    NpcAppearance withEntityType(String newEntityType) {
        return new NpcAppearance(skin, newEntityType, equipment, glowing, glowColor, pose, scale, typeData);
    }

    NpcAppearance withEquipment(EquipmentSlot slot, @Nullable String itemToken) {
        Objects.requireNonNull(slot, "slot");
        // An EnumMap copy-constructor rejects an empty source map, so build it by class and fill it.
        Map<EquipmentSlot, String> updated = new EnumMap<>(EquipmentSlot.class);
        updated.putAll(equipment);
        if (itemToken == null || itemToken.isBlank()) {
            updated.remove(slot);
        } else {
            updated.put(slot, itemToken);
        }
        return new NpcAppearance(skin, entityType, updated, glowing, glowColor, pose, scale, typeData);
    }

    NpcAppearance withGlowing(boolean newGlowing) {
        return new NpcAppearance(skin, entityType, equipment, newGlowing, glowColor, pose, scale, typeData);
    }

    NpcAppearance withGlowColor(@Nullable String newColor) {
        String color = newColor == null || newColor.isBlank() ? null : newColor;
        return new NpcAppearance(skin, entityType, equipment, glowing, color, pose, scale, typeData);
    }

    NpcAppearance withPose(String newPose) {
        return new NpcAppearance(skin, entityType, equipment, glowing, glowColor, newPose, scale, typeData);
    }

    NpcAppearance withScale(double newScale) {
        return new NpcAppearance(skin, entityType, equipment, glowing, glowColor, pose, newScale, typeData);
    }

    NpcAppearance withTypeData(String key, @Nullable String value) {
        String trimmedKey = Objects.requireNonNull(key, "key").strip();
        if (trimmedKey.isEmpty()) {
            throw new IllegalArgumentException("type-data key must not be blank");
        }
        Map<String, String> updated = new LinkedHashMap<>(typeData);
        if (value == null || value.isBlank()) {
            updated.remove(trimmedKey);
        } else {
            updated.put(trimmedKey, value);
        }
        return new NpcAppearance(skin, entityType, equipment, glowing, glowColor, pose, scale, updated);
    }

    boolean isPlayerType() {
        return DEFAULT_ENTITY_TYPE.equals(entityType);
    }

    boolean hasSkin() {
        return skin != null;
    }

    boolean hasEquipment() {
        return !equipment.isEmpty();
    }

    boolean hasGlowColor() {
        return glowColor != null && !glowColor.isBlank();
    }

    boolean hasPose() {
        return !DEFAULT_POSE.equals(pose);
    }

    boolean hasScale() {
        return Double.compare(scale, DEFAULT_SCALE) != 0;
    }

    boolean hasTypeData() {
        return !typeData.isEmpty();
    }

    /** Upper-case the entity-type name and reject a blank one — the type is always a non-blank uppercase name. */
    private static String normalizeType(String entityType) {
        Objects.requireNonNull(entityType, "entityType");
        String trimmed = entityType.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("entityType must not be blank");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    /** Upper-case the pose name and reject a blank one — the pose is always a non-blank uppercase name. */
    private static String normalizePose(String pose) {
        Objects.requireNonNull(pose, "pose");
        String trimmed = pose.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("pose must not be blank");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    /** Reject a non-finite or non-positive scale — the size multiplier is always a finite, positive number. */
    private static double validateScale(double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0) {
            throw new IllegalArgumentException("scale must be finite and positive, was " + scale);
        }
        return scale;
    }

    /** An immutable, empty-tolerant copy of the equipment map keyed in slot order. */
    private static Map<EquipmentSlot, String> copyEquipment(@Nullable Map<EquipmentSlot, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new EnumMap<>(source));
    }

    /** An immutable, empty-tolerant copy of the type-data map. */
    private static Map<String, String> copyTypeData(@Nullable Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(source);
    }
}
