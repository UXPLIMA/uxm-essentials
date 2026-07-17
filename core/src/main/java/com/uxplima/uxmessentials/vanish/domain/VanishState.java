package com.uxplima.uxmessentials.vanish.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * An immutable snapshot of who is vanished and at which {@link VanishLevel}. The vanish authority (the
 * {@code VanishStore} adapter) holds the live {@code ConcurrentHashMap<UUID, VanishLevel>}; this record is the pure
 * value a reader takes of it so the visibility rule can be evaluated without touching the mutable map or any Bukkit
 * type. The map holds only the currently-vanished players, so a snapshot is small (usually empty) and cheap to copy.
 *
 * <p>The visibility rule lives here as {@link #canSee}: Phase 1's rule is that a viewer sees a vanished target only
 * when the viewer holds the vanish-see node. The per-viewer permission itself is resolved by the adapter (it is not
 * a domain concern); this record answers the pure question given whether the viewer holds it. Phase 2 layers the
 * see/use level comparison on top of the same seam.
 *
 * @param vanished the currently-vanished players keyed by uuid, each at their level
 */
public record VanishState(Map<UUID, VanishLevel> vanished) {

    private static final VanishState EMPTY = new VanishState(Map.of());

    public VanishState {
        Objects.requireNonNull(vanished, "vanished");
        vanished = Map.copyOf(vanished);
    }

    /** The state with nobody vanished. */
    public static VanishState empty() {
        return EMPTY;
    }

    /** Whether {@code target} is currently vanished. */
    public boolean isVanished(UUID target) {
        Objects.requireNonNull(target, "target");
        return vanished.containsKey(target);
    }

    /** The level {@code target} is vanished at, or empty when they are not vanished. */
    public Optional<VanishLevel> levelOf(UUID target) {
        Objects.requireNonNull(target, "target");
        return Optional.ofNullable(vanished.get(target));
    }

    /** The set of currently-vanished players. */
    public Set<UUID> vanishedIds() {
        return vanished.keySet();
    }

    /** This state with {@code target} vanished at {@code level} (replacing any prior level). */
    public VanishState withVanished(UUID target, VanishLevel level) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(level, "level");
        java.util.Map<UUID, VanishLevel> next = new java.util.HashMap<>(vanished);
        next.put(target, level);
        return new VanishState(next);
    }

    /** This state with {@code target} revealed. */
    public VanishState withoutVanished(UUID target) {
        Objects.requireNonNull(target, "target");
        if (!vanished.containsKey(target)) {
            return this;
        }
        java.util.Map<UUID, VanishLevel> next = new java.util.HashMap<>(vanished);
        next.remove(target);
        return new VanishState(next);
    }

    /**
     * Whether {@code viewer} may see {@code target}. A player always sees themselves; a non-vanished target is seen
     * by everyone; a vanished target is seen only by a viewer holding the vanish-see node ({@code viewerHasSee}).
     * This is the Phase-1 rule — Phase 2 extends it with the see/use level comparison over {@link #levelOf}.
     */
    public boolean canSee(UUID viewer, UUID target, boolean viewerHasSee) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        if (viewer.equals(target)) {
            return true;
        }
        if (!isVanished(target)) {
            return true;
        }
        return viewerHasSee;
    }
}
