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
 * appearance metadata, plus the shown display name, the mirror-skin toggle, the collision and tab-visibility
 * toggles, the per-NPC view/turn distances, and the on-fire / invisible / silent state flags. Grouping these
 * visual fields into one immutable value object keeps the {@link Npc} aggregate small while leaving the public
 * surface unchanged — {@code Npc} delegates every visual transition and accessor here. An appearance is a value
 * object: each {@code with*} produces a new instance rather than mutating.
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
 *
 * <p>{@code displayName} is the name shown above the NPC, distinct from its id — {@code null} or blank hides the
 * name entirely (the tab/profile name stays the id; only the visible label changes). {@code mirrorSkin} renders
 * each viewer's own skin on the NPC (per-viewer, resolved at render time). {@code collidable} toggles whether the
 * NPC pushes players. {@code showInTab} keeps the NPC as a tab-list entry instead of hiding it after spawn.
 * {@code viewDistance}/{@code turnDistance} are per-NPC overrides of the module's render/look ranges, or
 * {@code null} to use the global default. {@code onFire}/{@code invisible}/{@code silent} are the shared-flags-and
 * -silence state toggles the adapter composes into one metadata frame.
 */
public record NpcAppearance(
        @Nullable NpcSkin skin,
        String entityType,
        Map<EquipmentSlot, String> equipment,
        boolean glowing,
        @Nullable String glowColor,
        String pose,
        double scale,
        Map<String, String> typeData,
        @Nullable String displayName,
        boolean mirrorSkin,
        boolean collidable,
        boolean showInTab,
        @Nullable Double viewDistance,
        @Nullable Double turnDistance,
        boolean onFire,
        boolean invisible,
        boolean silent) {

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
        displayName = blankToNull(displayName);
        viewDistance = validateDistance(viewDistance, "viewDistance");
        turnDistance = validateDistance(turnDistance, "turnDistance");
    }

    /**
     * The legacy eight-field constructor, retained so the {@link Npc} compat constructor and any caller that builds
     * the visual half field-by-field default the FancyNpcs-parity additions (display name, mirror, collidable,
     * show-in-tab, view/turn distance, state flags) to their natural values.
     */
    public NpcAppearance(
            @Nullable NpcSkin skin,
            String entityType,
            Map<EquipmentSlot, String> equipment,
            boolean glowing,
            @Nullable String glowColor,
            String pose,
            double scale,
            Map<String, String> typeData) {
        this(
                skin,
                entityType,
                equipment,
                glowing,
                glowColor,
                pose,
                scale,
                typeData,
                null,
                false,
                false,
                false,
                null,
                null,
                false,
                false,
                false);
    }

    /** The default appearance for a freshly created NPC carrying the given (possibly {@code null}) skin. */
    static NpcAppearance defaults(@Nullable NpcSkin skin) {
        return new NpcAppearance(
                skin, DEFAULT_ENTITY_TYPE, Map.of(), false, null, DEFAULT_POSE, DEFAULT_SCALE, Map.of());
    }

    NpcAppearance withSkin(@Nullable NpcSkin newSkin) {
        return copy(b -> b.skin = newSkin);
    }

    NpcAppearance withEntityType(String newEntityType) {
        return copy(b -> b.entityType = newEntityType);
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
        return copy(b -> b.equipment = updated);
    }

    NpcAppearance withGlowing(boolean newGlowing) {
        return copy(b -> b.glowing = newGlowing);
    }

    NpcAppearance withGlowColor(@Nullable String newColor) {
        return copy(b -> b.glowColor = newColor == null || newColor.isBlank() ? null : newColor);
    }

    NpcAppearance withPose(String newPose) {
        return copy(b -> b.pose = newPose);
    }

    NpcAppearance withScale(double newScale) {
        return copy(b -> b.scale = newScale);
    }

    NpcAppearance withDisplayName(@Nullable String newDisplayName) {
        return copy(b -> b.displayName = newDisplayName);
    }

    NpcAppearance withMirrorSkin(boolean newMirrorSkin) {
        return copy(b -> b.mirrorSkin = newMirrorSkin);
    }

    NpcAppearance withCollidable(boolean newCollidable) {
        return copy(b -> b.collidable = newCollidable);
    }

    NpcAppearance withShowInTab(boolean newShowInTab) {
        return copy(b -> b.showInTab = newShowInTab);
    }

    NpcAppearance withViewDistance(@Nullable Double newViewDistance) {
        return copy(b -> b.viewDistance = newViewDistance);
    }

    NpcAppearance withTurnDistance(@Nullable Double newTurnDistance) {
        return copy(b -> b.turnDistance = newTurnDistance);
    }

    NpcAppearance withOnFire(boolean newOnFire) {
        return copy(b -> b.onFire = newOnFire);
    }

    NpcAppearance withInvisible(boolean newInvisible) {
        return copy(b -> b.invisible = newInvisible);
    }

    NpcAppearance withSilent(boolean newSilent) {
        return copy(b -> b.silent = newSilent);
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
        return copy(b -> b.typeData = updated);
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

    boolean hasDisplayName() {
        return displayName != null && !displayName.isBlank();
    }

    /** Apply one field edit to a mutable snapshot of this appearance and rebuild — keeps each {@code with*} a line. */
    private NpcAppearance copy(java.util.function.Consumer<Fields> edit) {
        Fields fields = new Fields(this);
        edit.accept(fields);
        return fields.build();
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

    /** A per-NPC distance override is either absent ({@code null}) or a finite, non-negative number of blocks. */
    private static @Nullable Double validateDistance(@Nullable Double distance, String name) {
        if (distance == null) {
            return null;
        }
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative, was " + distance);
        }
        return distance;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
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

    /** A mutable carrier for the appearance fields, used only to keep each single-field {@code with*} a one-liner. */
    private static final class Fields {
        private @Nullable NpcSkin skin;
        private String entityType;
        private Map<EquipmentSlot, String> equipment;
        private boolean glowing;
        private @Nullable String glowColor;
        private String pose;
        private double scale;
        private Map<String, String> typeData;
        private @Nullable String displayName;
        private boolean mirrorSkin;
        private boolean collidable;
        private boolean showInTab;
        private @Nullable Double viewDistance;
        private @Nullable Double turnDistance;
        private boolean onFire;
        private boolean invisible;
        private boolean silent;

        private Fields(NpcAppearance a) {
            this.skin = a.skin;
            this.entityType = a.entityType;
            this.equipment = a.equipment;
            this.glowing = a.glowing;
            this.glowColor = a.glowColor;
            this.pose = a.pose;
            this.scale = a.scale;
            this.typeData = a.typeData;
            this.displayName = a.displayName;
            this.mirrorSkin = a.mirrorSkin;
            this.collidable = a.collidable;
            this.showInTab = a.showInTab;
            this.viewDistance = a.viewDistance;
            this.turnDistance = a.turnDistance;
            this.onFire = a.onFire;
            this.invisible = a.invisible;
            this.silent = a.silent;
        }

        private NpcAppearance build() {
            return new NpcAppearance(
                    skin,
                    entityType,
                    equipment,
                    glowing,
                    glowColor,
                    pose,
                    scale,
                    typeData,
                    displayName,
                    mirrorSkin,
                    collidable,
                    showInTab,
                    viewDistance,
                    turnDistance,
                    onFire,
                    invisible,
                    silent);
        }
    }
}
