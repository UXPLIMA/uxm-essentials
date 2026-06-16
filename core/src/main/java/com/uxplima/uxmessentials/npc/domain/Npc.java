package com.uxplima.uxmessentials.npc.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;

/**
 * One server-wide fake-player NPC: a {@link NpcName}, the {@link Position} it stands at, an optional
 * {@link NpcSkin}, the optional command run when a player clicks it, and the moment it was created. An NPC is a
 * value object — moving it, re-skinning it, or rebinding its click command produces a new instance rather than
 * mutating in place, so the aggregate is always in a valid state and a repository save records a fully-formed
 * snapshot.
 *
 * <p>The position carries its own {@link com.uxplima.uxmessentials.shared.domain.WorldRef}, so the NPC's world
 * is read from {@code location().world()} rather than held separately. A {@code null} skin renders the default
 * (Steve) fake player; a {@code null} click command means clicking the NPC does nothing. The click command is
 * stored as raw text here — running it on interaction is an adapter concern, so the domain only carries the
 * binding. {@code lookAtPlayer} controls whether the fake player rotates to face each nearby viewer; it defaults
 * to {@code true} so a freshly created NPC tracks players out of the box.
 *
 * <p>{@code equipment} maps each worn {@link EquipmentSlot} to an opaque item <em>token</em> the domain stores
 * verbatim and never interprets — it is either a legacy Bukkit material name ({@code DIAMOND_HELMET}) or a
 * serialized full-item payload (a named/enchanted/custom item), and the adapter is the only place that resolves
 * a token to a real Bukkit item at render. Keeping the value a plain {@code String} is what keeps the aggregate
 * Bukkit-free even though the token may now carry a whole item's NBT. A slot absent from the map is empty.
 * {@code glowing} toggles the fake player's outline, and {@code glowColor} is the colour name ({@code RED}) the
 * outline is tinted, or {@code null} for the default white outline. The map is copied defensively on construction
 * so a stored snapshot is immutable.
 *
 * <p>{@code actions} is the ordered list of {@link NpcAction}s a click runs (the richer mechanism alongside the
 * single {@code clickCommand}, which still runs first). The list is copied defensively on construction so the
 * snapshot is immutable; {@code withActionAdded} / {@code withActionRemovedAt} / {@code withActionsCleared}
 * produce new instances, and every other transition preserves the actions unchanged.
 *
 * <p>{@code entityType} is the Bukkit {@code EntityType} <em>name</em> (uppercase, e.g. {@code "PLAYER"} or
 * {@code "VILLAGER"}) the NPC renders as — the default {@code "PLAYER"} keeps the fake-player path (tab entry +
 * skin), any other living type spawns that mob instead. It is a plain string so the domain stays Bukkit-free; the
 * adapter is the only place that resolves it to a real {@code EntityType} and decides whether the type is a valid
 * living one. The skin is kept across a type change, so flipping a mob back to {@code PLAYER} restores its skin.
 *
 * <p>{@code pose} is the body pose the NPC is frozen in — the uppercase pose name ({@code "STANDING"} by default,
 * or {@code "SITTING"}, {@code "SLEEPING"}, …). It is a plain string so the domain stays Bukkit-free: which pose
 * names are valid is resolved against the packet layer at render time, so an unknown name simply renders standing
 * rather than failing here. {@code scale} resizes the NPC ({@code 1.0} is the natural size; {@code 2.0} twice as
 * tall, {@code 0.5} half) and must be finite and positive; the command clamps it to the protocol's usable range.
 *
 * @param name the NPC's canonical, server-unique name
 * @param location where the NPC stands and which way it faces
 * @param skin the fake player's skin, or {@code null} for the default skin
 * @param clickCommand the command run when a player clicks the NPC, or {@code null} for no action
 * @param lookAtPlayer whether the NPC rotates to face each nearby viewer
 * @param equipment the worn items by slot as opaque tokens (a material name or a serialized item); absent = empty
 * @param glowing whether the fake player's outline glows
 * @param glowColor the glow outline colour name, or {@code null} for the default white outline
 * @param actions the ordered list of typed actions a click runs, after the single click command
 * @param entityType the uppercase Bukkit {@code EntityType} name the NPC renders as ({@code "PLAYER"} by default)
 * @param pose the uppercase pose name the NPC is frozen in ({@code "STANDING"} by default)
 * @param scale the NPC's size multiplier ({@code 1.0} is the natural size); finite and positive
 * @param createdAt when the NPC was first created (preserved across a move, re-skin, or rebind)
 */
public record Npc(
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
        Instant createdAt) {

    /** The default entity type: a fake player, the one type with the tab-entry + skin path. */
    public static final String DEFAULT_ENTITY_TYPE = "PLAYER";

    /** The default body pose: the natural upright stance. */
    public static final String DEFAULT_POSE = "STANDING";

    /** The default size multiplier: the NPC's natural size. */
    public static final double DEFAULT_SCALE = 1.0;

    public Npc {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(createdAt, "createdAt");
        equipment = copyEquipment(equipment);
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        entityType = normalizeType(entityType);
        pose = normalizePose(pose);
        scale = validateScale(scale);
    }

    /** A new NPC created now at {@code location} with the given (possibly {@code null}) skin, no command, looking. */
    public static Npc create(NpcName name, Position location, @Nullable NpcSkin skin, Instant createdAt) {
        return new Npc(
                name,
                location,
                skin,
                null,
                true,
                Map.of(),
                false,
                null,
                List.of(),
                DEFAULT_ENTITY_TYPE,
                DEFAULT_POSE,
                DEFAULT_SCALE,
                createdAt);
    }

    /** A copy re-anchored to {@code newLocation}, keeping everything else. */
    public Npc movedTo(Position newLocation) {
        Objects.requireNonNull(newLocation, "newLocation");
        return new Npc(
                name,
                newLocation,
                skin,
                clickCommand,
                lookAtPlayer,
                equipment,
                glowing,
                glowColor,
                actions,
                entityType,
                pose,
                scale,
                createdAt);
    }

    /** A copy wearing {@code newSkin} (or {@code null} to reset to the default skin), keeping everything else. */
    public Npc withSkin(@Nullable NpcSkin newSkin) {
        return new Npc(
                name,
                location,
                newSkin,
                clickCommand,
                lookAtPlayer,
                equipment,
                glowing,
                glowColor,
                actions,
                entityType,
                pose,
                scale,
                createdAt);
    }

    /** A copy whose click runs {@code newCommand} (or {@code null} to clear it), keeping everything else. */
    public Npc withClickCommand(@Nullable String newCommand) {
        return new Npc(
                name,
                location,
                skin,
                newCommand,
                lookAtPlayer,
                equipment,
                glowing,
                glowColor,
                actions,
                entityType,
                pose,
                scale,
                createdAt);
    }

    /** A copy that does or does not rotate to face nearby viewers, keeping everything else. */
    public Npc withLookAtPlayer(boolean newLookAtPlayer) {
        return new Npc(
                name,
                location,
                skin,
                clickCommand,
                newLookAtPlayer,
                equipment,
                glowing,
                glowColor,
                actions,
                entityType,
                pose,
                scale,
                createdAt);
    }

    /**
     * A copy rendered as {@code newEntityType} (the uppercase Bukkit {@code EntityType} name), keeping everything
     * else including the skin. The name is upper-cased and must be non-blank; whether it is a real, living type is
     * the adapter's concern, validated at the command boundary before this is called.
     */
    public Npc withEntityType(String newEntityType) {
        return new Npc(
                name,
                location,
                skin,
                clickCommand,
                lookAtPlayer,
                equipment,
                glowing,
                glowColor,
                actions,
                newEntityType,
                pose,
                scale,
                createdAt);
    }

    /**
     * A copy with {@code slot} set to {@code itemToken} (or cleared when {@code itemToken} is {@code null} or
     * blank), keeping everything else. The token is stored verbatim and uninterpreted — it is either a legacy
     * material name or a serialized full-item payload, and the adapter resolves it to a real item at render time,
     * so an unresolvable token simply renders no item in that slot rather than failing here.
     */
    public Npc withEquipment(EquipmentSlot slot, @Nullable String itemToken) {
        Objects.requireNonNull(slot, "slot");
        // An EnumMap copy-constructor rejects an empty source map, so build it by class and fill it.
        Map<EquipmentSlot, String> updated = new EnumMap<>(EquipmentSlot.class);
        updated.putAll(equipment);
        if (itemToken == null || itemToken.isBlank()) {
            updated.remove(slot);
        } else {
            updated.put(slot, itemToken);
        }
        return new Npc(
                name,
                location,
                skin,
                clickCommand,
                lookAtPlayer,
                updated,
                glowing,
                glowColor,
                actions,
                entityType,
                pose,
                scale,
                createdAt);
    }

    /** A copy whose outline does or does not glow, keeping everything else (and its colour). */
    public Npc withGlowing(boolean newGlowing) {
        return new Npc(
                name,
                location,
                skin,
                clickCommand,
                lookAtPlayer,
                equipment,
                newGlowing,
                glowColor,
                actions,
                entityType,
                pose,
                scale,
                createdAt);
    }

    /** A copy whose glow outline is tinted {@code newColor} (or {@code null} for the default white), keeping the rest. */
    public Npc withGlowColor(@Nullable String newColor) {
        String color = newColor == null || newColor.isBlank() ? null : newColor;
        return new Npc(
                name,
                location,
                skin,
                clickCommand,
                lookAtPlayer,
                equipment,
                glowing,
                color,
                actions,
                entityType,
                pose,
                scale,
                createdAt);
    }

    /**
     * A copy frozen in {@code newPose} (the pose name, upper-cased and required non-blank), keeping everything else.
     * Whether the name is one the renderer can strike is the adapter's concern, validated at the command boundary;
     * an unknown name renders standing rather than failing here.
     */
    public Npc withPose(String newPose) {
        return new Npc(
                name,
                location,
                skin,
                clickCommand,
                lookAtPlayer,
                equipment,
                glowing,
                glowColor,
                actions,
                entityType,
                newPose,
                scale,
                createdAt);
    }

    /** A copy resized to {@code newScale} ({@code 1.0} is the natural size), keeping everything else. */
    public Npc withScale(double newScale) {
        return new Npc(
                name,
                location,
                skin,
                clickCommand,
                lookAtPlayer,
                equipment,
                glowing,
                glowColor,
                actions,
                entityType,
                pose,
                newScale,
                createdAt);
    }

    /** A copy with {@code action} appended to the end of the action list, keeping everything else. */
    public Npc withActionAdded(NpcAction action) {
        Objects.requireNonNull(action, "action");
        List<NpcAction> updated = new ArrayList<>(actions);
        updated.add(action);
        return new Npc(
                name,
                location,
                skin,
                clickCommand,
                lookAtPlayer,
                equipment,
                glowing,
                glowColor,
                updated,
                entityType,
                pose,
                scale,
                createdAt);
    }

    /**
     * A copy with the action at the 0-based {@code index} removed, keeping everything else. Throws
     * {@link IndexOutOfBoundsException} when {@code index} is outside the current action list.
     */
    public Npc withActionRemovedAt(int index) {
        if (index < 0 || index >= actions.size()) {
            throw new IndexOutOfBoundsException("action index out of range: " + index);
        }
        List<NpcAction> updated = new ArrayList<>(actions);
        updated.remove(index);
        return new Npc(
                name,
                location,
                skin,
                clickCommand,
                lookAtPlayer,
                equipment,
                glowing,
                glowColor,
                updated,
                entityType,
                pose,
                scale,
                createdAt);
    }

    /** A copy with no actions, keeping everything else. */
    public Npc withActionsCleared() {
        return new Npc(
                name,
                location,
                skin,
                clickCommand,
                lookAtPlayer,
                equipment,
                glowing,
                glowColor,
                List.of(),
                entityType,
                pose,
                scale,
                createdAt);
    }

    /** Whether this NPC renders as a fake player (the default type with the tab-entry + skin path). */
    public boolean isPlayerType() {
        return DEFAULT_ENTITY_TYPE.equals(entityType);
    }

    /** Whether this NPC carries a skin (a fake player with no skin renders the default Steve). */
    public boolean hasSkin() {
        return skin != null;
    }

    /** Whether clicking this NPC runs a command. */
    public boolean hasClickCommand() {
        return clickCommand != null && !clickCommand.isBlank();
    }

    /** Whether this NPC wears anything in any slot. */
    public boolean hasEquipment() {
        return !equipment.isEmpty();
    }

    /** Whether a glow colour is set (a glowing NPC with no colour renders the default white outline). */
    public boolean hasGlowColor() {
        return glowColor != null && !glowColor.isBlank();
    }

    /** Whether clicking this NPC runs at least one action. */
    public boolean hasActions() {
        return !actions.isEmpty();
    }

    /** Whether this NPC is frozen in a non-default pose (a default-posed NPC needs no pose packet). */
    public boolean hasPose() {
        return !DEFAULT_POSE.equals(pose);
    }

    /** Whether this NPC is resized (a natural-size NPC needs no scale packet). */
    public boolean hasScale() {
        return Double.compare(scale, DEFAULT_SCALE) != 0;
    }

    /** Upper-case the entity-type name and reject a blank one — the type is always a non-blank uppercase name. */
    private static String normalizeType(String entityType) {
        Objects.requireNonNull(entityType, "entityType");
        String trimmed = entityType.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("entityType must not be blank");
        }
        return trimmed.toUpperCase(java.util.Locale.ROOT);
    }

    /** Upper-case the pose name and reject a blank one — the pose is always a non-blank uppercase name. */
    private static String normalizePose(String pose) {
        Objects.requireNonNull(pose, "pose");
        String trimmed = pose.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("pose must not be blank");
        }
        return trimmed.toUpperCase(java.util.Locale.ROOT);
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
}
