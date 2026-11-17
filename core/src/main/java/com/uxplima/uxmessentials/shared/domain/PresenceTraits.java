package com.uxplima.uxmessentials.shared.domain;

/**
 * What the other backends need to know about a player online on one backend to list them: how deep they are hidden,
 * and whether they are staff. The backend the player is on announces it with their presence and announces it again
 * when it changes.
 *
 * <p>A player announced without traits, by a backend on an older release, counts as hidden at {@link #UNKNOWN_LEVEL},
 * above every level a permission grants, so only a viewer who sees every vanished player sees them, and as no staff.
 *
 * @param hiddenLevel {@link #VISIBLE} for a player nobody hides, else the vanish level a viewer has to see at least
 * @param staff whether the player holds the staff marker node
 */
public record PresenceTraits(int hiddenLevel, boolean staff) {

    /** The hidden level of a player nobody hides. */
    public static final int VISIBLE = 0;

    /**
     * The hidden level of a player whose backend did not say: the level a viewer who sees every vanished player (an
     * operator, or a wildcard see node) resolves to, so nobody else sees them.
     */
    public static final int UNKNOWN_LEVEL = 100_000;

    /** A player nobody hides, who is not staff. */
    public static final PresenceTraits PLAIN = new PresenceTraits(VISIBLE, false);

    /** A player whose backend did not say. */
    public static final PresenceTraits UNKNOWN = new PresenceTraits(UNKNOWN_LEVEL, false);

    public PresenceTraits {
        hiddenLevel = Math.max(VISIBLE, hiddenLevel);
    }

    /** Whether a viewer who sees vanished players up to {@code seeLevel} sees this player. */
    public boolean visibleTo(int seeLevel) {
        return hiddenLevel == VISIBLE || seeLevel >= hiddenLevel;
    }
}
