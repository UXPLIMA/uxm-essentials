package com.uxplima.uxmessentials.npc.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;

/**
 * One server-wide fake-player NPC: a {@link NpcName}, the {@link Position} it stands at, its visual
 * {@link NpcAppearance} (skin, entity type, equipment, glow, pose, scale, type-data) and its interactive
 * {@link NpcBehavior} (click command, look-at-player, action list), and the moment it was created. An NPC is a
 * value object — moving it, re-skinning it, or rebinding its click produces a new instance rather than mutating in
 * place, so the aggregate is always in a valid state and a repository save records a fully-formed snapshot.
 *
 * <p>The visual and behavioural fields are grouped into the {@link NpcAppearance} and {@link NpcBehavior} value
 * objects to keep this aggregate small; {@code Npc} exposes the same per-field transitions and accessors it always
 * did ({@code withSkin}, {@code withEquipment}, {@code skin()}, {@code equipment()}, …) and simply delegates each to
 * the owning value object. The legacy fourteen-argument constructor is retained so the persistence mapper and any
 * caller that builds an NPC field-by-field are unaffected by the grouping.
 *
 * <p>The position carries its own {@link com.uxplima.uxmessentials.shared.domain.WorldRef}, so the NPC's world is
 * read from {@code location().world()} rather than held separately. {@code createdAt} is preserved across every
 * transition (a move, re-skin, rebind, or appearance change).
 *
 * @param name the NPC's canonical, server-unique name
 * @param location where the NPC stands and which way it faces
 * @param appearance the visual half: skin, entity type, equipment, glow, pose, scale, type-data
 * @param behavior the interactive half: click command, look-at-player, action list
 * @param createdAt when the NPC was first created (preserved across every transition)
 */
public record Npc(NpcName name, Position location, NpcAppearance appearance, NpcBehavior behavior, Instant createdAt) {

    /** The default entity type: a fake player, the one type with the tab-entry + skin path. */
    public static final String DEFAULT_ENTITY_TYPE = NpcAppearance.DEFAULT_ENTITY_TYPE;

    /** The default body pose: the natural upright stance. */
    public static final String DEFAULT_POSE = NpcAppearance.DEFAULT_POSE;

    /** The default size multiplier: the NPC's natural size. */
    public static final double DEFAULT_SCALE = NpcAppearance.DEFAULT_SCALE;

    public Npc {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(behavior, "behavior");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * The legacy field-by-field constructor, retained so the persistence mapper and field-level callers are
     * unaffected by the appearance/behavior grouping. The visual fields are folded into an {@link NpcAppearance}
     * and the interactive fields into an {@link NpcBehavior}, each of which applies the same validation and
     * defensive copying it always did.
     */
    public Npc(
            NpcName name,
            Position location,
            @Nullable NpcSkin skin,
            @Nullable String clickCommand,
            boolean lookAtPlayer,
            Map<EquipmentSlot, String> equipment,
            boolean glowing,
            @Nullable String glowColor,
            List<NpcAction> actions,
            String entityType,
            String pose,
            double scale,
            Map<String, String> typeData,
            Instant createdAt) {
        this(
                name,
                location,
                new NpcAppearance(skin, entityType, equipment, glowing, glowColor, pose, scale, typeData),
                new NpcBehavior(clickCommand, lookAtPlayer, actions),
                createdAt);
    }

    /** A new NPC created now at {@code location} with the given (possibly {@code null}) skin, no command, looking. */
    public static Npc create(NpcName name, Position location, @Nullable NpcSkin skin, Instant createdAt) {
        return new Npc(name, location, NpcAppearance.defaults(skin), NpcBehavior.defaults(), createdAt);
    }

    // --- Appearance accessors (delegated, so existing call sites are unchanged) ---

    public @Nullable NpcSkin skin() {
        return appearance.skin();
    }

    public Map<EquipmentSlot, String> equipment() {
        return appearance.equipment();
    }

    public boolean glowing() {
        return appearance.glowing();
    }

    public @Nullable String glowColor() {
        return appearance.glowColor();
    }

    public String entityType() {
        return appearance.entityType();
    }

    public String pose() {
        return appearance.pose();
    }

    public double scale() {
        return appearance.scale();
    }

    public Map<String, String> typeData() {
        return appearance.typeData();
    }

    // --- Behavior accessors (delegated) ---

    public @Nullable String clickCommand() {
        return behavior.clickCommand();
    }

    public boolean lookAtPlayer() {
        return behavior.lookAtPlayer();
    }

    public List<NpcAction> actions() {
        return behavior.actions();
    }

    // --- Transitions (delegated; createdAt and the untouched half are always preserved) ---

    /** A copy re-anchored to {@code newLocation}, keeping everything else. */
    public Npc movedTo(Position newLocation) {
        Objects.requireNonNull(newLocation, "newLocation");
        return new Npc(name, newLocation, appearance, behavior, createdAt);
    }

    /** A copy wearing {@code newSkin} (or {@code null} to reset to the default skin), keeping everything else. */
    public Npc withSkin(@Nullable NpcSkin newSkin) {
        return new Npc(name, location, appearance.withSkin(newSkin), behavior, createdAt);
    }

    /** A copy whose click runs {@code newCommand} (or {@code null} to clear it), keeping everything else. */
    public Npc withClickCommand(@Nullable String newCommand) {
        return new Npc(name, location, appearance, behavior.withClickCommand(newCommand), createdAt);
    }

    /** A copy that does or does not rotate to face nearby viewers, keeping everything else. */
    public Npc withLookAtPlayer(boolean newLookAtPlayer) {
        return new Npc(name, location, appearance, behavior.withLookAtPlayer(newLookAtPlayer), createdAt);
    }

    /**
     * A copy rendered as {@code newEntityType} (the uppercase Bukkit {@code EntityType} name), keeping everything
     * else including the skin. The name is upper-cased and must be non-blank; whether it is a real, living type is
     * the adapter's concern, validated at the command boundary before this is called.
     */
    public Npc withEntityType(String newEntityType) {
        return new Npc(name, location, appearance.withEntityType(newEntityType), behavior, createdAt);
    }

    /**
     * A copy with {@code slot} set to {@code itemToken} (or cleared when {@code itemToken} is {@code null} or
     * blank), keeping everything else. The token is stored verbatim and uninterpreted — it is either a legacy
     * material name or a serialized full-item payload, and the adapter resolves it to a real item at render time,
     * so an unresolvable token simply renders no item in that slot rather than failing here.
     */
    public Npc withEquipment(EquipmentSlot slot, @Nullable String itemToken) {
        return new Npc(name, location, appearance.withEquipment(slot, itemToken), behavior, createdAt);
    }

    /** A copy whose outline does or does not glow, keeping everything else (and its colour). */
    public Npc withGlowing(boolean newGlowing) {
        return new Npc(name, location, appearance.withGlowing(newGlowing), behavior, createdAt);
    }

    /** A copy whose glow outline is tinted {@code newColor} (or {@code null} for the default white), keeping the rest. */
    public Npc withGlowColor(@Nullable String newColor) {
        return new Npc(name, location, appearance.withGlowColor(newColor), behavior, createdAt);
    }

    /**
     * A copy frozen in {@code newPose} (the pose name, upper-cased and required non-blank), keeping everything else.
     * Whether the name is one the renderer can strike is the adapter's concern, validated at the command boundary;
     * an unknown name renders standing rather than failing here.
     */
    public Npc withPose(String newPose) {
        return new Npc(name, location, appearance.withPose(newPose), behavior, createdAt);
    }

    /** A copy resized to {@code newScale} ({@code 1.0} is the natural size), keeping everything else. */
    public Npc withScale(double newScale) {
        return new Npc(name, location, appearance.withScale(newScale), behavior, createdAt);
    }

    /**
     * A copy with the type-data {@code key} set to {@code value} (or cleared when {@code value} is {@code null} or
     * blank), keeping everything else. The key must be non-blank; both key and value are stored verbatim and never
     * interpreted here — whether the key applies to this NPC's entity type, and how the value parses, is the render
     * adapter's concern, so an unsupported or unparseable pair simply renders nothing rather than failing here.
     */
    public Npc withTypeData(String key, @Nullable String value) {
        return new Npc(name, location, appearance.withTypeData(key, value), behavior, createdAt);
    }

    /** A copy with {@code action} appended to the end of the action list, keeping everything else. */
    public Npc withActionAdded(NpcAction action) {
        return new Npc(name, location, appearance, behavior.withActionAdded(action), createdAt);
    }

    /**
     * A copy with the action at the 0-based {@code index} removed, keeping everything else. Throws
     * {@link IndexOutOfBoundsException} when {@code index} is outside the current action list.
     */
    public Npc withActionRemovedAt(int index) {
        return new Npc(name, location, appearance, behavior.withActionRemovedAt(index), createdAt);
    }

    /** A copy with no actions, keeping everything else. */
    public Npc withActionsCleared() {
        return new Npc(name, location, appearance, behavior.withActionsCleared(), createdAt);
    }

    // --- Predicates (delegated) ---

    /** Whether this NPC renders as a fake player (the default type with the tab-entry + skin path). */
    public boolean isPlayerType() {
        return appearance.isPlayerType();
    }

    /** Whether this NPC carries a skin (a fake player with no skin renders the default Steve). */
    public boolean hasSkin() {
        return appearance.hasSkin();
    }

    /** Whether clicking this NPC runs a command. */
    public boolean hasClickCommand() {
        return behavior.hasClickCommand();
    }

    /** Whether this NPC wears anything in any slot. */
    public boolean hasEquipment() {
        return appearance.hasEquipment();
    }

    /** Whether a glow colour is set (a glowing NPC with no colour renders the default white outline). */
    public boolean hasGlowColor() {
        return appearance.hasGlowColor();
    }

    /** Whether clicking this NPC runs at least one action. */
    public boolean hasActions() {
        return behavior.hasActions();
    }

    /** Whether this NPC is frozen in a non-default pose (a default-posed NPC needs no pose packet). */
    public boolean hasPose() {
        return appearance.hasPose();
    }

    /** Whether this NPC is resized (a natural-size NPC needs no scale packet). */
    public boolean hasScale() {
        return appearance.hasScale();
    }

    /** Whether this NPC carries any per-entity-type appearance metadata (an empty map needs no metadata packet). */
    public boolean hasTypeData() {
        return appearance.hasTypeData();
    }
}
