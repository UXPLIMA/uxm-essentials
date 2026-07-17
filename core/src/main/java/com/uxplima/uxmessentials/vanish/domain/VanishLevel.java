package com.uxplima.uxmessentials.vanish.domain;

/**
 * The vanish tier a hidden player is at. Phase 1 ships a single {@link #DEFAULT} level — every vanished player
 * hides behind the flat {@code uxmessentials.vanish.see} node — but the level is carried through the state and the
 * store from the start so Phase 2 can layer PremiumVanish-style see/use levels on top without reshaping the store.
 *
 * <p>A higher {@link #rank} is "more hidden": Phase 2's rule is that a viewer sees a vanished player only when the
 * viewer's see-level is at least the target's use-level. Phase 1 never compares ranks, so {@link #DEFAULT} (rank 0)
 * is the only value in play; the invariant (non-negative rank) is enforced here so an out-of-range level can never
 * enter the store.
 *
 * @param rank the tier, {@code 0} for the default level; higher hides from more viewers (Phase 2)
 */
public record VanishLevel(int rank) {

    /** The single Phase-1 level: a vanished player hides behind the flat vanish-see node. */
    public static final VanishLevel DEFAULT = new VanishLevel(0);

    public VanishLevel {
        if (rank < 0) {
            throw new IllegalArgumentException("rank must be >= 0: " + rank);
        }
    }
}
